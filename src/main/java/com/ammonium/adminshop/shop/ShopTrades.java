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
 * Server-side Shop trades. Players buy from Buy Offers and sell to Sell Offers by hand, through the Shop block or
 * the Shop Tablet. Machines trade for their own Account. For a player, the player's Account pays or is paid, and the
 * goods move to or from the main inventory, never the armour or offhand slots. Items go into or out of its slots,
 * and fluids go into or out of a fluid container in one of them.
 */
public final class ShopTrades {

    /** Slot value for {@link #sell} that sells from the first slot holding enough goods. */
    public static final int ANY_SLOT = -1;
    /** Quantity value for {@link #sell} from a given slot that sells as many trades as the slot's stack holds. */
    public static final int ALL = 0;

    public enum Result {
        DONE,
        /** No Offer of that kind has that key. */
        NO_SUCH_OFFER,
        /** The request was invalid, such as a quantity below one or a slot outside the main inventory. */
        REFUSED,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_SPACE,
        NOT_ENOUGH_GOODS,
        /** The machine has no Account to trade for. Nobody has claimed it, or its team no longer exists. */
        NO_ACCOUNT,
        /** The Offer needs a Permit the team does not hold. */
        NO_PERMIT
    }

    private ShopTrades() {
    }

    /**
     * Buys {@code quantity} trades of a Buy Offer. If the inventory has room for fewer, it buys as many as fit. If
     * nothing fits, or the Account cannot pay, it buys nothing. For a fluid Offer, all trades go into the first fluid
     * container in the main inventory that can take them all.
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
     * Buys one trade of the Buy Offer {@code offerKey} for a Buyer, once per cycle. It charges the machine's Account
     * and puts the goods into {@code output}. It buys nothing if the goods do not all fit, if the Account cannot pay,
     * or if the machine has no owner or its team no longer exists.
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

    /**
     * How many of {@code wanted} trades of {@code offer} fit completely into {@code inventory}. The insertion is
     * simulated and rolled back.
     */
    private static int tradesThatFit(BuyItemRecipe offer, ResourceHandler<ItemResource> inventory, int wanted) {
        int perTrade = offer.getCount();
        try (var simulation = Transaction.openRoot()) {
            int amount = (int) Math.min((long) perTrade * wanted, Integer.MAX_VALUE);
            int fit = ResourceHandlerUtil.insertStacking(inventory, ItemResource.of(offer.result()), amount, simulation);
            return Math.min(wanted, fit / perTrade);
        }
    }

    /**
     * Charges {@code team} for {@code trades} trades of {@code offer} and puts the goods into {@code inventory}, or
     * does neither. {@code inventory} must have room for the goods. Both buying by hand and the Buyer use this.
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

    /** Buys {@code trades} trades of a fluid Buy Offer into the first main inventory container that fits them. */
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
     * Buys one trade of the fluid Buy Offer {@code offerKey} for a Fluid Buyer, once per cycle. It charges the
     * machine's Account and fills {@code tank}. It buys nothing if the whole trade does not fit, if the Account cannot
     * pay, or if the machine has no owner or its team no longer exists.
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

    /** Whether {@code handler} can take all {@code amount} of {@code fluid}. The insertion is simulated and undone. */
    private static boolean fills(ResourceHandler<FluidResource> handler, FluidResource fluid, int amount) {
        try (var simulation = Transaction.openRoot()) {
            return handler.insert(fluid, amount, simulation) == amount;
        }
    }

    /**
     * Charges {@code team} for {@code trades} trades of {@code offer} and fills the fluid into {@code handler}, or
     * does neither. Both buying a fluid by hand and the Fluid Buyer use this.
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
     * Sells {@code quantity} trades of a Sell Offer, all from one stack in the main inventory. That is the stack in
     * {@code slot}, or with {@link #ANY_SLOT} the first stack that holds enough. With a given slot, {@link #ALL} sells
     * as many trades as its stack holds. A fluid Offer drains all trades from one fluid container in the same way.
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

    /**
     * Sells {@code quantity} trades of a fluid Sell Offer from the fluid container in {@code slot}, or from the first
     * container that holds enough.
     */
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

    /** How much of {@code fluid} can be drained from {@code handler}. The extraction is simulated and rolled back. */
    private static int drainable(ResourceHandler<FluidResource> handler, FluidResource fluid) {
        try (var simulation = Transaction.openRoot()) {
            return handler.extract(fluid, Integer.MAX_VALUE, simulation);
        }
    }

    /**
     * The fluid handler of the item in main inventory {@code slot}, or null if the item holds no fluid. It works on
     * one item at a time, so a stack of buckets fills or empties one bucket and the result goes elsewhere in the
     * inventory.
     */
    private static @Nullable ResourceHandler<FluidResource> fluidContainer(ServerPlayer player, int slot) {
        if (player.getInventory().getItem(slot).isEmpty()) {
            return null;
        }
        return ItemAccess.forPlayerSlot(player, slot).oneByOne().getCapability(Capabilities.Fluid.ITEM);
    }

    /** Every fluid Sell Offer the server loaded. */
    public static Collection<RecipeHolder<SellFluidRecipe>> sellFluidOffers(MinecraftServer server) {
        return server.getRecipeManager().recipeMap().byType(ModRecipeTypes.SHOP_SELL_FLUID.get());
    }

    /**
     * Whether any fluid Sell Offer among {@code offers} takes {@code fluid}. If {@code only} is set, only that Offer
     * is checked. The Fluid Seller uses this to accept fluid piped into it.
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
     * Sells one trade from {@code tank} for a Fluid Seller, once per cycle. It uses the first fluid Sell Offer the
     * tank holds enough for, and credits the machine's Account. If {@code only} is set, only that Offer is used.
     * Offers gated by a Permit are skipped as in {@link #sellFrom}.
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
     * or does neither. Both selling a fluid by hand and the Fluid Seller use this.
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

    /** Every item Sell Offer the server loaded. */
    public static Collection<RecipeHolder<SellItemRecipe>> sellOffers(MinecraftServer server) {
        return server.getRecipeManager().recipeMap().byType(ModRecipeTypes.SHOP_SELL_ITEM.get());
    }

    /**
     * Whether any item Sell Offer among {@code offers} takes {@code stack}, whatever its count. If {@code only} is
     * set, only that Offer is checked. The Seller uses this to accept items put into it. On the server it passes
     * {@link #sellOffers}, and on the client the Offers in the {@link ShopCache}.
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
     * Sells one trade for a Seller, once per cycle. It sells from the first slot of {@code goods} that holds enough
     * for a Sell Offer, and credits the machine's Account. If {@code only} is set, only that Offer is used. Sell Offers
     * gated by a Permit the team does not hold are skipped, unless the machine bypasses Permits. A machine with no
     * owner, or whose team no longer exists, sells nothing.
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
     * Takes {@code trades} trades of {@code offer}'s goods from slot {@code index} of {@code goods} and credits
     * {@code team} their price, or does neither. Both selling by hand and the Seller use this.
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
     * Whether {@code team} may trade {@code offer}. That is true if the Offer needs no Permit, the team holds it, or
     * {@code machine} bypasses Permits. Every trade is checked here. For a trade by hand, {@code machine} is null.
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
