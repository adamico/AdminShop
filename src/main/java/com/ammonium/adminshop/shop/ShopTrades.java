package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Shop's server-side trade handling: a player buying from a Buy Offer or selling to a Sell Offer
 * by hand, from the Shop block or the Shop Tablet. The player's Account pays or is paid, and the goods
 * go to or come from the player's main inventory (never the armour or offhand slots).
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
        NOT_ENOUGH_GOODS
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

        long price;
        try {
            price = Math.multiplyExact(offer.price(), trades);
        } catch (ArithmeticException e) {
            return Result.REFUSED;
        }
        ResourceHandler<ItemResource> slotHandler = RangedResourceHandler.ofSingleIndex(mainInventory(player), slot);
        try (var transaction = Transaction.openRoot()) {
            int extracted = slotHandler.extract(0, ItemResource.of(stack), trades * perTrade, transaction);
            if (extracted != trades * perTrade) {
                return Result.NOT_ENOUGH_GOODS;
            }
            transaction.commit();
        }
        MoneyHelper.get(player.level()).addMoney(team(player), price);
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
