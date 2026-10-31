package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.interfaces.ShopMachine;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.ModRecipeTypes;
import com.ammonium.adminshop.recipes.SellFluidRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.recipes.ShopRecipe;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Shop's server-side trade handling: a player buying from a Buy Offer or selling to a Sell Offer
 * by hand, from the Shop block or the Shop Tablet, and the machines trading for their Account. A player's
 * Account pays or is paid, and the goods go to or come from the player's main inventory (never the armour
 * or offhand slots): items into or out of its slots, fluids into or out of a fluid container in one.
 */
public final class ShopTrades {

    /** For {@link #sell}: sell from the first slot that holds enough goods. */
    public static final int ANY_SLOT = -1;
    /** For {@link #sell} from a slot: as many trades as the slot's stack holds. */
    public static final int ALL = 0;

    public enum Result {
        DONE,
        /** No Offer of that kind has that key. */
        NO_SUCH_OFFER,
        /** The request itself was invalid: a quantity below one, or a slot outside the main inventory. */
        REFUSED,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_SPACE,
        NOT_ENOUGH_GOODS,
        /** The machine has no Account to trade for: nobody has claimed it, or its team is gone. */
        NO_ACCOUNT,
        /** The Offer is gated by a Permit the team doesn't hold. */
        NO_PERMIT
    }

    private ShopTrades() {
    }

    /**
     * Buys {@code quantity} trades of a Buy Offer. If the inventory holds fewer, buys as many as fit;
     * if it holds none, or the Account can't pay for them, buys nothing. A fluid Offer's trades all go into
     * one fluid container of the main inventory, the first that takes them whole.
     */
    public static Result buy(ServerPlayer player, ResourceKey<Recipe<?>> offerKey, int quantity) {
        Optional<ShopRecipe> found = offer(player, offerKey, ShopRecipe.class)
                .filter(offer -> offer instanceof BuyItemRecipe || offer instanceof BuyFluidRecipe);
        if (found.isEmpty()) {
            return Result.NO_SUCH_OFFER;
        }
        if (quantity < 1) {
            return Result.REFUSED;
        }
        ShopRecipe shopOffer = found.get();
        if (!permitted(player.level(), team(player), shopOffer, null)) {
            return refuseWithoutPermit(player, shopOffer);
        }
        if (shopOffer instanceof BuyFluidRecipe fluidOffer) {
            return buyFluid(player, fluidOffer, quantity);
        }
        BuyItemRecipe offer = (BuyItemRecipe) shopOffer;
        ResourceHandler<ItemResource> inventory = mainInventory(player);
        int trades = tradesThatFit(offer, inventory, quantity);
        if (trades == 0) {
            player.sendSystemMessage(Component.translatable("message.adminshop.not_enough_space"));
            return Result.NOT_ENOUGH_SPACE;
        }
        Result result = payAndGive(player.level(), team(player), offerKey, offer, inventory, trades);
        if (result == Result.NOT_ENOUGH_MONEY) {
            player.sendSystemMessage(Component.translatable("message.adminshop.not_enough_money"));
        }
        return result;
    }

    /**
     * Buys one trade of the Buy Offer {@code offerKey} for a machine, charging the machine's Account and putting
     * the goods into {@code output}; if they don't all fit, or the Account can't pay, buys nothing. A machine nobody
     * owns, or whose team is gone, buys nothing. This is the Buyer's purchase, once a cycle.
     */
    public static Result buyInto(ServerLevel level, ShopMachine machine, ResourceHandler<ItemResource> output,
                                 ResourceKey<Recipe<?>> offerKey) {
        @Nullable UUID team = machine.getTeamId();
        if (team == null || MoneyHelper.get(level).getAccountById(team) == null) {
            return Result.NO_ACCOUNT;
        }
        Optional<BuyItemRecipe> found = offer(level.getServer(), offerKey, BuyItemRecipe.class);
        if (found.isEmpty()) {
            return Result.NO_SUCH_OFFER;
        }
        BuyItemRecipe offer = found.get();
        if (!permitted(level, team, offer, machine)) {
            return Result.NO_PERMIT;
        }
        if (tradesThatFit(offer, output, 1) == 0) {
            return Result.NOT_ENOUGH_SPACE;
        }
        return payAndGive(level, team, offerKey, offer, output, 1);
    }

    /** How many of {@code wanted} trades of {@code offer} fit whole into {@code inventory}. */
    private static int tradesThatFit(BuyItemRecipe offer, ResourceHandler<ItemResource> inventory, int wanted) {
        int perTrade = offer.getCount();
        try (var simulation = Transaction.openRoot()) {
            int amount = (int) Math.min((long) perTrade * wanted, Integer.MAX_VALUE);
            int fit = ResourceHandlerUtil.insertStacking(inventory, ItemResource.of(offer.result()), amount, simulation);
            // Not committed: closing rolls the insertion back.
            return Math.min(wanted, fit / perTrade);
        }
    }

    /**
     * Charges {@code team} for {@code trades} trades of {@code offer} and puts their goods into {@code inventory},
     * which must have room for them, or does neither. Buying by hand and the Buyer both buy through here.
     */
    private static Result payAndGive(ServerLevel level, UUID team, ResourceKey<Recipe<?>> offerKey, BuyItemRecipe offer,
                                     ResourceHandler<ItemResource> inventory, int trades) {
        long price;
        try {
            price = Math.multiplyExact(offer.price(), trades);
        } catch (ArithmeticException e) {
            return Result.NOT_ENOUGH_MONEY;
        }
        if (!MoneyHelper.get(level).removeMoney(team, price)) {
            return Result.NOT_ENOUGH_MONEY;
        }
        int amount = trades * offer.getCount();
        try (var transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(inventory, ItemResource.of(offer.result()), amount, transaction);
            transaction.commit();
            if (inserted != amount) {
                AdminShop.LOGGER.warn("Bought {} of {} for team {}, but only {} fit", amount, offerKey.identifier(), team, inserted);
            }
        }
        return Result.DONE;
    }

    /** Buys {@code trades} trades of a fluid Buy Offer into the first fluid container of the main inventory that takes them. */
    private static Result buyFluid(ServerPlayer player, BuyFluidRecipe offer, int trades) {
        long amount = (long) offer.amount() * trades;
        if (amount > Integer.MAX_VALUE) {
            return Result.REFUSED;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ResourceHandler<FluidResource> container = fluidContainer(player, slot);
            if (container != null && fills(container, offer.fluid(), (int) amount)) {
                Result result = payAndFill(player.level(), team(player), offer, container, trades);
                if (result == Result.NOT_ENOUGH_MONEY) {
                    player.sendSystemMessage(Component.translatable("message.adminshop.not_enough_money"));
                }
                return result;
            }
        }
        player.sendSystemMessage(Component.translatable("message.adminshop.no_container"));
        return Result.NOT_ENOUGH_SPACE;
    }

    /**
     * Buys one trade of the fluid Buy Offer {@code offerKey} for a machine, charging the machine's Account and filling
     * {@code tank}; if the trade doesn't fit whole, or the Account can't pay, buys nothing. A machine nobody owns, or
     * whose team is gone, buys nothing. This is the Fluid Buyer's purchase, once a cycle.
     */
    public static Result buyFluidInto(ServerLevel level, ShopMachine machine, ResourceHandler<FluidResource> tank,
                                      ResourceKey<Recipe<?>> offerKey) {
        @Nullable UUID team = machine.getTeamId();
        if (team == null || MoneyHelper.get(level).getAccountById(team) == null) {
            return Result.NO_ACCOUNT;
        }
        Optional<BuyFluidRecipe> found = offer(level.getServer(), offerKey, BuyFluidRecipe.class);
        if (found.isEmpty()) {
            return Result.NO_SUCH_OFFER;
        }
        BuyFluidRecipe offer = found.get();
        if (!permitted(level, team, offer, machine)) {
            return Result.NO_PERMIT;
        }
        return payAndFill(level, team, offer, tank, 1);
    }

    /** Whether {@code handler} takes {@code amount} of {@code fluid} whole; it is left as it was. */
    private static boolean fills(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (var simulation = Transaction.openRoot()) {
            // Not committed: closing rolls the insertion back.
            return handler.insert(fluid, amount, simulation) == amount;
        }
    }

    /**
     * Charges {@code team} for {@code trades} trades of {@code offer} and fills their fluid into {@code handler}, or
     * does neither. Buying a fluid by hand and the Fluid Buyer both buy through here.
     */
    private static Result payAndFill(ServerLevel level, UUID team, BuyFluidRecipe offer, ResourceHandler<FluidResource> handler,
                                     int trades) {
        long price;
        int amount;
        try {
            price = Math.multiplyExact(offer.price(), trades);
            amount = Math.multiplyExact(offer.amount(), trades);
        } catch (ArithmeticException e) {
            return Result.REFUSED;
        }
        try (var transaction = Transaction.openRoot()) {
            if (handler.insert(offer.fluid(), amount, transaction) != amount) {
                return Result.NOT_ENOUGH_SPACE;
            }
            if (!MoneyHelper.get(level).removeMoney(team, price)) {
                return Result.NOT_ENOUGH_MONEY;
            }
            transaction.commit();
        }
        return Result.DONE;
    }

    /**
     * Sells {@code quantity} trades of a Sell Offer, all from one stack of the main inventory: the stack
     * in {@code slot}, or with {@link #ANY_SLOT} the first that holds enough. From a slot, {@link #ALL}
     * sells as many trades as its stack holds. A fluid Offer's trades are all drained from one fluid
     * container in the same way.
     */
    public static Result sell(ServerPlayer player, ResourceKey<Recipe<?>> offerKey, int slot, int quantity) {
        Optional<ShopRecipe> found = offer(player, offerKey, ShopRecipe.class)
                .filter(offer -> offer instanceof SellItemRecipe || offer instanceof SellFluidRecipe);
        if (found.isEmpty()) {
            return Result.NO_SUCH_OFFER;
        }
        boolean anySlot = slot == ANY_SLOT;
        if (quantity < 0 || (quantity == ALL && anySlot) || (!anySlot && (slot < 0 || slot >= Inventory.INVENTORY_SIZE))) {
            return Result.REFUSED;
        }
        ShopRecipe shopOffer = found.get();
        if (!permitted(player.level(), team(player), shopOffer, null)) {
            return refuseWithoutPermit(player, shopOffer);
        }
        if (shopOffer instanceof SellFluidRecipe fluidOffer) {
            return sellFluid(player, fluidOffer, slot, quantity);
        }
        SellItemRecipe offer = (SellItemRecipe) shopOffer;
        int perTrade = offer.getCount();
        Inventory inventory = player.getInventory();

        if (anySlot) {
            long wanted = (long) perTrade * quantity;
            slot = ANY_SLOT;
            for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
                ItemStack stack = inventory.getItem(index);
                if (offer.isMatchingItemNoCount(stack) && stack.getCount() >= wanted) {
                    slot = index;
                    break;
                }
            }
            if (slot == ANY_SLOT) {
                return Result.NOT_ENOUGH_GOODS;
            }
        }
        ItemStack stack = inventory.getItem(slot);
        if (!offer.isMatchingItemStack(stack)) {
            return Result.NOT_ENOUGH_GOODS;
        }
        int trades = quantity == ALL ? stack.getCount() / perTrade : quantity;
        if ((long) trades * perTrade > stack.getCount()) {
            return Result.NOT_ENOUGH_GOODS;
        }
        return takeAndPay(player.level(), team(player), offer, mainInventory(player), slot, trades);
    }

    /** Sells {@code quantity} trades of a fluid Sell Offer, all from the fluid container in {@code slot}, or the first that holds enough. */
    private static Result sellFluid(ServerPlayer player, SellFluidRecipe offer, int slot, int quantity) {
        int perTrade = offer.amount();
        if (slot == ANY_SLOT) {
            long wanted = (long) perTrade * quantity;
            for (int index = 0; index < Inventory.INVENTORY_SIZE; index++) {
                ResourceHandler<FluidResource> container = fluidContainer(player, index);
                if (container != null && drainable(container, offer.fluid()) >= wanted) {
                    return takeAndPayFluid(player.level(), team(player), offer, container, quantity);
                }
            }
            return Result.NOT_ENOUGH_GOODS;
        }
        ResourceHandler<FluidResource> container = fluidContainer(player, slot);
        if (container == null) {
            return Result.NOT_ENOUGH_GOODS;
        }
        int trades = quantity == ALL ? drainable(container, offer.fluid()) / perTrade : quantity;
        if (trades == 0) {
            return Result.NOT_ENOUGH_GOODS;
        }
        return takeAndPayFluid(player.level(), team(player), offer, container, trades);
    }

    /** How much of {@code fluid} can be drained from {@code handler}; it is left as it was. */
    private static int drainable(ResourceHandler<FluidResource> handler, FluidResource fluid) {
        try (var simulation = Transaction.openRoot()) {
            // Not committed: closing rolls the extraction back.
            return handler.extract(fluid, Integer.MAX_VALUE, simulation);
        }
    }

    /**
     * The fluid handler of the item in the main inventory's {@code slot}, one item at a time, so a stack of buckets
     * fills or empties one bucket and the new one goes elsewhere in the inventory; null if the item holds no fluid.
     */
    private static @Nullable ResourceHandler<FluidResource> fluidContainer(ServerPlayer player, int slot) {
        if (player.getInventory().getItem(slot).isEmpty()) {
            return null;
        }
        return ItemAccess.forPlayerSlot(player, slot).oneByOne().getCapability(Capabilities.Fluid.ITEM);
    }

    /** Every Sell Offer for fluids the server loaded. */
    public static Collection<RecipeHolder<SellFluidRecipe>> sellFluidOffers(MinecraftServer server) {
        return server.getRecipeManager().recipeMap().byType(ModRecipeTypes.SHOP_SELL_FLUID.get());
    }

    /**
     * Whether a fluid Sell Offer among {@code offers} takes {@code fluid}; with {@code only} set, whether that one does.
     * The Fluid Seller asks this of what is piped into it.
     */
    public static boolean anySellOfferTakes(Iterable<? extends RecipeHolder<?>> offers, @Nullable ResourceKey<Recipe<?>> only,
                                            FluidResource fluid) {
        for (RecipeHolder<?> holder : offers) {
            if ((only == null || holder.id().equals(only)) && holder.value() instanceof SellFluidRecipe offer && offer.takes(fluid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sells one trade for a machine from {@code tank}, through the first fluid Sell Offer it holds enough for, crediting
     * the machine's Account; with {@code only} set, only through that Offer. Gated Offers are passed over as in
     * {@link #sellFrom}. This is the Fluid Seller's sale, once a cycle.
     */
    public static Result sellFluidFrom(ServerLevel level, ShopMachine machine, ResourceHandler<FluidResource> tank,
                                       @Nullable ResourceKey<Recipe<?>> only) {
        @Nullable UUID team = machine.getTeamId();
        if (team == null || MoneyHelper.get(level).getAccountById(team) == null) {
            return Result.NO_ACCOUNT;
        }
        boolean gated = false;
        for (RecipeHolder<SellFluidRecipe> holder : sellFluidOffers(level.getServer())) {
            if (only != null && !holder.id().equals(only)) {
                continue;
            }
            SellFluidRecipe offer = holder.value();
            if (drainable(tank, offer.fluid()) < offer.amount()) {
                continue;
            }
            if (!permitted(level, team, offer, machine)) {
                gated = true;
            } else if (takeAndPayFluid(level, team, offer, tank, 1) == Result.DONE) {
                return Result.DONE;
            }
        }
        return gated ? Result.NO_PERMIT : Result.NOT_ENOUGH_GOODS;
    }

    /**
     * Drains {@code trades} trades of {@code offer}'s fluid from {@code handler} and credits {@code team} their price,
     * or does neither. Selling a fluid by hand and the Fluid Seller both sell through here.
     */
    private static Result takeAndPayFluid(ServerLevel level, UUID team, SellFluidRecipe offer, ResourceHandler<FluidResource> handler,
                                          int trades) {
        long price;
        int amount;
        try {
            price = Math.multiplyExact(offer.price(), trades);
            amount = Math.multiplyExact(offer.amount(), trades);
        } catch (ArithmeticException e) {
            return Result.REFUSED;
        }
        try (var transaction = Transaction.openRoot()) {
            if (handler.extract(offer.fluid(), amount, transaction) != amount) {
                return Result.NOT_ENOUGH_GOODS;
            }
            transaction.commit();
        }
        MoneyHelper.get(level).addMoney(team, price);
        return Result.DONE;
    }

    /** Every Sell Offer for items the server loaded. */
    public static Collection<RecipeHolder<SellItemRecipe>> sellOffers(MinecraftServer server) {
        return server.getRecipeManager().recipeMap().byType(ModRecipeTypes.SHOP_SELL_ITEM.get());
    }

    /**
     * Whether a Sell Offer for items among {@code offers} takes {@code stack}, whatever its count; with {@code only}
     * set, whether that one does. The Seller asks this of what is put into it: on the server of {@link #sellOffers},
     * on the client of the client shop cache.
     */
    public static boolean anySellOfferTakes(Iterable<? extends RecipeHolder<?>> offers, @Nullable ResourceKey<Recipe<?>> only,
                                            ItemStack stack) {
        for (RecipeHolder<?> holder : offers) {
            if ((only == null || holder.id().equals(only)) && holder.value() instanceof SellItemRecipe offer
                    && offer.isMatchingItemNoCount(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sells one trade for a machine, from the first of {@code goods}' slots that holds enough for a Sell Offer,
     * crediting the machine's Account; with {@code only} set, only through that Offer. Sell Offers gated by a Permit
     * the team doesn't hold are passed over, unless the machine bypasses Permits. A machine nobody owns, or whose
     * team is gone, sells nothing. This is the Seller's sale, once a cycle.
     */
    public static Result sellFrom(ServerLevel level, ShopMachine machine, ResourceHandler<ItemResource> goods,
                                  @Nullable ResourceKey<Recipe<?>> only) {
        @Nullable UUID team = machine.getTeamId();
        if (team == null || MoneyHelper.get(level).getAccountById(team) == null) {
            return Result.NO_ACCOUNT;
        }
        boolean gated = false;
        for (RecipeHolder<SellItemRecipe> holder : sellOffers(level.getServer())) {
            if (only != null && !holder.id().equals(only)) {
                continue;
            }
            SellItemRecipe offer = holder.value();
            boolean permitted = permitted(level, team, offer, machine);
            for (int index = 0; index < goods.size(); index++) {
                ItemStack stack = goods.getResource(index).toStack(goods.getAmountAsInt(index));
                if (!offer.isMatchingItemStack(stack)) {
                    continue;
                }
                if (!permitted) {
                    gated = true;
                } else if (takeAndPay(level, team, offer, goods, index, 1) == Result.DONE) {
                    return Result.DONE;
                }
            }
        }
        return gated ? Result.NO_PERMIT : Result.NOT_ENOUGH_GOODS;
    }

    /**
     * Takes {@code trades} trades of {@code offer}'s goods from {@code goods}' slot {@code index} and credits
     * {@code team} their price, or does neither. Selling by hand and the Seller both sell through here.
     */
    private static Result takeAndPay(ServerLevel level, UUID team, SellItemRecipe offer, ResourceHandler<ItemResource> goods,
                                     int index, int trades) {
        long price;
        try {
            price = Math.multiplyExact(offer.price(), trades);
        } catch (ArithmeticException e) {
            return Result.REFUSED;
        }
        int amount = trades * offer.getCount();
        try (var transaction = Transaction.openRoot()) {
            int extracted = goods.extract(index, goods.getResource(index), amount, transaction);
            if (extracted != amount) {
                return Result.NOT_ENOUGH_GOODS;
            }
            transaction.commit();
        }
        MoneyHelper.get(level).addMoney(team, price);
        return Result.DONE;
    }

    /**
     * Whether {@code team} may trade {@code offer}: the Offer needs no Permit, the team holds it, or {@code machine}
     * bypasses Permits. Every trade, by hand ({@code machine} null) or by a machine, is checked here.
     */
    private static boolean permitted(ServerLevel level, UUID team, ShopRecipe offer, @Nullable ShopMachine machine) {
        return (machine != null && machine.bypassesPermits()) || MoneyHelper.get(level).hasPermit(team, offer.permit());
    }

    private static Result refuseWithoutPermit(ServerPlayer player, ShopRecipe offer) {
        player.sendSystemMessage(Component.translatable("message.adminshop.no_permit", offer.permit()));
        return Result.NO_PERMIT;
    }

    /** The Offer of this kind with this key, as the server loaded it. */
    private static <T extends Recipe<?>> Optional<T> offer(ServerPlayer player, ResourceKey<Recipe<?>> key, Class<T> kind) {
        return offer(player.level().getServer(), key, kind);
    }

    private static <T extends Recipe<?>> Optional<T> offer(MinecraftServer server, ResourceKey<Recipe<?>> key, Class<T> kind) {
        return server.getRecipeManager().byKey(key)
                .map(holder -> holder.value())
                .filter(kind::isInstance)
                .map(kind::cast);
    }

    private static ResourceHandler<ItemResource> mainInventory(ServerPlayer player) {
        return RangedResourceHandler.of(PlayerInventoryWrapper.of(player), 0, Inventory.INVENTORY_SIZE);
    }

    private static UUID team(ServerPlayer player) {
        return MoneyHelper.get(player.level()).getPlayerAccount(player).teamId();
    }
}
