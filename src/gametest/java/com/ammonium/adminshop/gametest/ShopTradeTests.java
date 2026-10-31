package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.List;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Trading by hand through the Shop's server-side trade handling, which the shop screen's trade requests
 * reach. The Offers come from the game tests' datapack: {@code gametest/buy_diamonds} sells 2 diamonds
 * for 50, {@code gametest/sell_wheat} buys 10 wheat for 7.
 */
final class ShopTradeTests {

    private static final ResourceKey<Recipe<?>> BUY_DIAMONDS = offer("gametest/buy_diamonds");
    private static final ResourceKey<Recipe<?>> SELL_WHEAT = offer("gametest/sell_wheat");

    private ShopTradeTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("buying_charges_the_account_and_gives_the_goods", 20, TestPlayers.withPlayers(ShopTradeTests::buying));
        tests.test("buying_is_refused_when_the_balance_is_too_low", 20, TestPlayers.withPlayers(ShopTradeTests::buyingTooDear));
        tests.test("buying_is_refused_when_the_inventory_is_full", 20, TestPlayers.withPlayers(ShopTradeTests::buyingIntoAFullInventory));
        tests.test("buying_is_capped_to_what_the_inventory_holds", 20, TestPlayers.withPlayers(ShopTradeTests::buyingIntoANearlyFullInventory));
        tests.test("selling_credits_the_account_and_takes_the_goods", 20, TestPlayers.withPlayers(ShopTradeTests::selling));
        tests.test("selling_from_a_slot_sells_all_it_can", 20, TestPlayers.withPlayers(ShopTradeTests::sellingFromASlot));
        tests.test("selling_without_the_goods_is_refused", 20, TestPlayers.withPlayers(ShopTradeTests::sellingWithoutGoods));
        tests.test("a_buy_offer_cannot_be_sold_to", 20, TestPlayers.withPlayers(ShopTradeTests::wrongSide));
        tests.test("trades_need_a_quantity_of_at_least_one", 20, TestPlayers.withPlayers(ShopTradeTests::nonPositiveQuantity));
    }

    private static void buying(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_ok");
        setBalance(helper, player, 1000);

        var result = ShopTrades.buy(player, BUY_DIAMONDS, 3);

        helper.assertValueEqual(result, ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 1000 - 3 * 50);
        helper.assertValueEqual(count(player, Items.DIAMOND), 6, "diamonds bought");
    }

    private static void buyingTooDear(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_poor");
        setBalance(helper, player, 99);

        var result = ShopTrades.buy(player, BUY_DIAMONDS, 2);

        helper.assertValueEqual(result, ShopTrades.Result.NOT_ENOUGH_MONEY, "the trade's result");
        assertBalance(helper, player, 99);
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "diamonds bought");
    }

    private static void buyingIntoAFullInventory(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_full");
        setBalance(helper, player, 1000);
        fillMainInventory(player, Inventory.INVENTORY_SIZE);

        var result = ShopTrades.buy(player, BUY_DIAMONDS, 1);

        helper.assertValueEqual(result, ShopTrades.Result.NOT_ENOUGH_SPACE, "the trade's result");
        assertBalance(helper, player, 1000);
        helper.assertValueEqual(count(player, Items.DIAMOND), 0, "diamonds bought");
    }

    private static void buyingIntoANearlyFullInventory(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_nearly_full");
        setBalance(helper, player, 1000);
        fillMainInventory(player, Inventory.INVENTORY_SIZE - 1);
        // Room for 5 more diamonds: two trades of 2, not the 5 trades asked for.
        player.getInventory().setItem(Inventory.INVENTORY_SIZE - 1, new ItemStack(Items.DIAMOND, 59));

        var result = ShopTrades.buy(player, BUY_DIAMONDS, 5);

        helper.assertValueEqual(result, ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 1000 - 2 * 50);
        helper.assertValueEqual(count(player, Items.DIAMOND), 63, "diamonds held");
    }

    private static void selling(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_ok");
        setBalance(helper, player, 0);
        player.getInventory().setItem(4, new ItemStack(Items.WHEAT, 25));

        var result = ShopTrades.sell(player, SELL_WHEAT, ShopTrades.ANY_SLOT, 2);

        helper.assertValueEqual(result, ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 2 * 7);
        helper.assertValueEqual(count(player, Items.WHEAT), 5, "wheat left");
    }

    private static void sellingFromASlot(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_slot");
        setBalance(helper, player, 0);
        player.getInventory().setItem(7, new ItemStack(Items.WHEAT, 34));

        // As a shift-click on the slot sends it: as many trades as the stack holds.
        var result = ShopTrades.sell(player, SELL_WHEAT, 7, ShopTrades.ALL);

        helper.assertValueEqual(result, ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 3 * 7);
        helper.assertValueEqual(player.getInventory().getItem(7).getCount(), 4, "wheat left in the slot");
    }

    private static void sellingWithoutGoods(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_none");
        setBalance(helper, player, 0);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 9));

        var result = ShopTrades.sell(player, SELL_WHEAT, ShopTrades.ANY_SLOT, 1);

        helper.assertValueEqual(result, ShopTrades.Result.NOT_ENOUGH_GOODS, "the trade's result");
        assertBalance(helper, player, 0);
        helper.assertValueEqual(count(player, Items.WHEAT), 9, "wheat left");
    }

    private static void wrongSide(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_to_buy");
        setBalance(helper, player, 1000);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));

        helper.assertValueEqual(ShopTrades.sell(player, BUY_DIAMONDS, ShopTrades.ANY_SLOT, 1), ShopTrades.Result.NO_SUCH_OFFER,
                "selling to a Buy Offer");
        helper.assertValueEqual(ShopTrades.buy(player, SELL_WHEAT, 1), ShopTrades.Result.NO_SUCH_OFFER,
                "buying from a Sell Offer");
        helper.assertValueEqual(ShopTrades.buy(player, offer("gametest/no_such_offer"), 1), ShopTrades.Result.NO_SUCH_OFFER,
                "buying from an Offer that doesn't exist");
        assertBalance(helper, player, 1000);
        helper.assertValueEqual(count(player, Items.DIAMOND), 10, "diamonds held");
    }

    private static void nonPositiveQuantity(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("zero_quantity");
        setBalance(helper, player, 1000);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 64));

        helper.assertValueEqual(ShopTrades.buy(player, BUY_DIAMONDS, 0), ShopTrades.Result.REFUSED, "buying 0");
        helper.assertValueEqual(ShopTrades.buy(player, BUY_DIAMONDS, -3), ShopTrades.Result.REFUSED, "buying -3");
        helper.assertValueEqual(ShopTrades.sell(player, SELL_WHEAT, ShopTrades.ANY_SLOT, -3), ShopTrades.Result.REFUSED, "selling -3");
        assertBalance(helper, player, 1000);
        helper.assertValueEqual(count(player, Items.WHEAT), 64, "wheat held");
    }

    static ResourceKey<Recipe<?>> offer(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(AdminShop.MODID, path));
    }

    private static void setBalance(GameTestHelper helper, ServerPlayer player, long balance) {
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        var account = accounts.getPlayerAccount(player);
        accounts.removeMoney(account.teamId(), account.balance());
        accounts.addMoney(account.teamId(), balance);
    }

    private static void assertBalance(GameTestHelper helper, ServerPlayer player, long expected) {
        long balance = MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).balance();
        helper.assertValueEqual(balance, expected, player.getPlainTextName() + "'s Balance");
    }

    /** Fills the first {@code slots} slots of the main inventory with full stacks of cobblestone. */
    private static void fillMainInventory(ServerPlayer player, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        List<ItemStack> stacks = new ArrayList<>(player.getInventory().getNonEquipmentItems());
        for (ItemStack stack : stacks) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
