package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.interfaces.ShopMachine;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.ModRecipeTypes;
import com.ammonium.adminshop.recipes.SellItemRecipe;
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
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Shop's server-side trade handling: a player buying from a Buy Offer or selling to a Sell Offer
 * by hand, from the Shop block or the Shop Tablet, and the machines trading for their Account. A player's
 * Account pays or is paid, and the goods go to or come from the player's main inventory (never the armour
 * or offhand slots).
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
        NO_ACCOUNT
    }

    private ShopTrades() {
    }

    /**
     * Buys {@code quantity} trades of a Buy Offer. If the inventory holds fewer, buys as many as fit;
     * if it holds none, or the Account can't pay for them, buys nothing.
     */
    public static Result buy(ServerPlayer player, ResourceKey<Recipe<?>> offerKey, int quantity) {
        Optional<BuyItemRecipe> found = offer(player, offerKey, BuyItemRecipe.class);
        if (found.isEmpty()) {
            return Result.NO_SUCH_OFFER;
        }
        if (quantity < 1) {
            return Result.REFUSED;
        }
        BuyItemRecipe offer = found.get();
        ResourceHandler<ItemResource> inventory = mainInventory(player);
        ItemResource goods = ItemResource.of(offer.result());
        int perTrade = offer.getCount();

        int fit;
        try (var simulation = Transaction.openRoot()) {
            int wanted = (int) Math.min((long) perTrade * quantity, Integer.MAX_VALUE);
            fit = ResourceHandlerUtil.insertStacking(inventory, goods, wanted, simulation);
            // Not committed: closing rolls the insertion back.
        }
        int trades = Math.min(quantity, fit / perTrade);
        if (trades == 0) {
            player.sendSystemMessage(Component.translatable("message.adminshop.not_enough_space"));
            return Result.NOT_ENOUGH_SPACE;
        }

        long price;
        try {
            price = Math.multiplyExact(offer.price(), trades);
        } catch (ArithmeticException e) {
            player.sendSystemMessage(Component.translatable("message.adminshop.not_enough_money"));
            return Result.NOT_ENOUGH_MONEY;
        }
        if (!MoneyHelper.get(player.level()).removeMoney(team(player), price)) {
            player.sendSystemMessage(Component.translatable("message.adminshop.not_enough_money"));
            return Result.NOT_ENOUGH_MONEY;
        }
        try (var transaction = Transaction.openRoot()) {
            int inserted = ResourceHandlerUtil.insertStacking(inventory, goods, trades * perTrade, transaction);
            transaction.commit();
            if (inserted != trades * perTrade) {
                AdminShop.LOGGER.warn("Bought {} of {} for {}, but only {} fit", trades * perTrade, offerKey.identifier(),
                        player.getPlainTextName(), inserted);
            }
        }
        return Result.DONE;
    }

    /**
     * Sells {@code quantity} trades of a Sell Offer, all from one stack of the main inventory: the stack
     * in {@code slot}, or with {@link #ANY_SLOT} the first that holds enough. From a slot, {@link #ALL}
     * sells as many trades as its stack holds.
     */
    public static Result sell(ServerPlayer player, ResourceKey<Recipe<?>> offerKey, int slot, int quantity) {
        Optional<SellItemRecipe> found = offer(player, offerKey, SellItemRecipe.class);
        if (found.isEmpty()) {
            return Result.NO_SUCH_OFFER;
        }
        boolean anySlot = slot == ANY_SLOT;
        if (quantity < 0 || (quantity == ALL && anySlot) || (!anySlot && (slot < 0 || slot >= Inventory.INVENTORY_SIZE))) {
            return Result.REFUSED;
        }
        SellItemRecipe offer = found.get();
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
     * crediting the machine's Account; with {@code only} set, only through that Offer. A machine nobody owns, or
     * whose team is gone, sells nothing. This is the Seller's sale, once a cycle.
     */
    public static Result sellFrom(ServerLevel level, ShopMachine machine, ResourceHandler<ItemResource> goods,
                                  @Nullable ResourceKey<Recipe<?>> only) {
        @Nullable UUID team = machine.getTeamId();
        if (team == null || MoneyHelper.get(level).getAccountById(team) == null) {
            return Result.NO_ACCOUNT;
        }
        for (RecipeHolder<SellItemRecipe> holder : sellOffers(level.getServer())) {
            if (only != null && !holder.id().equals(only)) {
                continue;
            }
            SellItemRecipe offer = holder.value();
            for (int index = 0; index < goods.size(); index++) {
                ItemStack stack = goods.getResource(index).toStack(goods.getAmountAsInt(index));
                if (offer.isMatchingItemStack(stack) && takeAndPay(level, team, offer, goods, index, 1) == Result.DONE) {
                    return Result.DONE;
                }
            }
        }
        return Result.NOT_ENOUGH_GOODS;
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

    /** The Offer of this kind with this key, as the server loaded it. */
    private static <T extends Recipe<?>> Optional<T> offer(ServerPlayer player, ResourceKey<Recipe<?>> key, Class<T> kind) {
        return player.level().getServer().getRecipeManager().byKey(key)
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
