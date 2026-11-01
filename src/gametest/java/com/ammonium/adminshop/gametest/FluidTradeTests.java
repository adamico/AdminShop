package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.UUID;

import com.ammonium.adminshop.money.MoneyHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;

/**
 * Tests trading fluid Offers by hand, through the requests the shop screen sends. Buying fills a fluid container in
 * the main inventory, so an empty bucket becomes a full one. Selling drains one. The Offers come from the game tests'
 * datapack. {@code gametest/buy_water} gives 1000 mB of water for 30 and {@code gametest/sell_lava} takes 1000 mB of
 * lava for 20. Gated by the Permit {@code gametest_gate}, {@code gametest/buy_gated_lava} gives 1000 mB of lava for
 * 10 and {@code gametest/sell_gated_water} takes 250 mB of water for 4.
 */
final class FluidTradeTests {

    static final ResourceKey<Recipe<?>> BUY_WATER = TestShop.offer("gametest/buy_water");
    static final ResourceKey<Recipe<?>> SELL_LAVA = TestShop.offer("gametest/sell_lava");
    static final ResourceKey<Recipe<?>> BUY_GATED_LAVA = TestShop.offer("gametest/buy_gated_lava");
    static final ResourceKey<Recipe<?>> SELL_GATED_WATER = TestShop.offer("gametest/sell_gated_water");

    private FluidTradeTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("buying_a_fluid_fills_a_container", 20, TestPlayers.withPlayers(FluidTradeTests::buying));
        tests.test("buying_a_fluid_fills_one_bucket_of_a_stack", 20, TestPlayers.withPlayers(FluidTradeTests::buyingIntoAStack));
        tests.test("buying_a_fluid_without_a_container_is_refused", 20, TestPlayers.withPlayers(FluidTradeTests::buyingWithoutAContainer));
        tests.test("buying_a_fluid_is_refused_when_the_balance_is_too_low", 20, TestPlayers.withPlayers(FluidTradeTests::buyingTooDear));
        tests.test("selling_a_fluid_drains_its_container", 20, TestPlayers.withPlayers(FluidTradeTests::selling));
        tests.test("selling_a_fluid_from_any_slot", 20, TestPlayers.withPlayers(FluidTradeTests::sellingFromAnySlot));
        tests.test("selling_a_fluid_without_it_is_refused", 20, TestPlayers.withPlayers(FluidTradeTests::sellingWithoutTheFluid));
        tests.test("trading_a_gated_fluid_offer_needs_its_permit", 20, TestPlayers.withPlayers(FluidTradeTests::permits));
        tests.test("a_shift_clicked_bucket_sells_one_trade", 20, TestPlayers.withPlayers(FluidTradeTests::shiftClickSellsOneTrade));
    }

    private static void buying(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy", 100);
        player.getInventory().setItem(4, new ItemStack(Items.BUCKET));

        ShopTradeTests.buy(helper, player, BUY_WATER, 1);

        TestShop.assertBalance(helper, player, 70);
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 1, "water buckets bought");
        helper.assertValueEqual(TestShop.count(player, Items.BUCKET), 0, "empty buckets left");
    }

    private static void buyingIntoAStack(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy_stack", 100);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET, 4));

        ShopTradeTests.buy(helper, player, BUY_WATER, 1);

        TestShop.assertBalance(helper, player, 70);
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 1, "water buckets bought");
        helper.assertValueEqual(TestShop.count(player, Items.BUCKET), 3, "empty buckets left");
    }

    /** A bucket full of another fluid takes no water. Two trades are 2000 mB, more than a bucket holds. */
    private static void buyingWithoutAContainer(GameTestHelper helper, TestPlayers players) {
        var heard = new ArrayList<String>();
        ServerPlayer player = players.join("fluid_buy_no_bucket", ShopTradeTests.translations(heard));
        setUp(helper, player, 100);
        player.getInventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));

        ShopTradeTests.buy(helper, player, BUY_WATER, 1);
        helper.assertTrue(heard.contains("message.adminshop.no_container"), "the player was not told no container takes the fluid: " + heard);
        player.getInventory().setItem(1, new ItemStack(Items.BUCKET));
        ShopTradeTests.buy(helper, player, BUY_WATER, 2);

        TestShop.assertBalance(helper, player, 100);
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 0, "water buckets bought");
    }

    private static void buyingTooDear(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy_poor", 29);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET));

        ShopTradeTests.buy(helper, player, BUY_WATER, 1);

        TestShop.assertBalance(helper, player, 29);
        helper.assertValueEqual(TestShop.count(player, Items.BUCKET), 1, "empty buckets left");
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 0, "water buckets bought");
    }

    /** Sells one trade from the bucket's slot. */
    private static void selling(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_sell", 0);
        player.getInventory().setItem(3, new ItemStack(Items.LAVA_BUCKET));

        ShopTradeTests.sell(helper, player, SELL_LAVA, 3, 1);

        TestShop.assertBalance(helper, player, 20);
        helper.assertValueEqual(TestShop.count(player, Items.LAVA_BUCKET), 0, "lava buckets left");
        helper.assertValueEqual(TestShop.count(player, Items.BUCKET), 1, "empty buckets");
    }

    private static void sellingFromAnySlot(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_sell_any", 0);
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(7, new ItemStack(Items.LAVA_BUCKET));

        ShopTradeTests.sell(helper, player, SELL_LAVA, ShopTradeTests.ANY_SLOT, 1);

        TestShop.assertBalance(helper, player, 20);
        helper.assertValueEqual(TestShop.count(player, Items.LAVA_BUCKET), 0, "lava buckets left");
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 1, "water buckets left");
    }

    /** Two trades are 2000 mB, more than a bucket holds. */
    private static void sellingWithoutTheFluid(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_sell_none", 0);
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(1, new ItemStack(Items.LAVA_BUCKET));

        ShopTradeTests.sell(helper, player, SELL_LAVA, 0, 1);
        ShopTradeTests.sell(helper, player, SELL_LAVA, ShopTradeTests.ANY_SLOT, 2);

        TestShop.assertBalance(helper, player, 0);
        helper.assertValueEqual(TestShop.count(player, Items.LAVA_BUCKET), 1, "lava buckets left");
    }

    /** A bucket is drained whole or not at all, so a quarter bucket cannot be sold from it, but four quarters can. */
    private static void permits(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_permit", 100);
        UUID team = TestShop.team(helper, player);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET));
        player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));

        ShopTradeTests.buy(helper, player, BUY_GATED_LAVA, 1);
        ShopTradeTests.sell(helper, player, SELL_GATED_WATER, ShopTradeTests.ANY_SLOT, 4);
        TestShop.assertBalance(helper, player, 100);
        helper.assertValueEqual(TestShop.count(player, Items.LAVA_BUCKET), 0, "lava buckets bought without the Permit");

        MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        ShopTradeTests.buy(helper, player, BUY_GATED_LAVA, 1);
        helper.assertValueEqual(TestShop.count(player, Items.LAVA_BUCKET), 1, "lava buckets bought with the Permit");
        ShopTradeTests.sell(helper, player, SELL_GATED_WATER, ShopTradeTests.ANY_SLOT, 1);
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 1, "water buckets left after selling a quarter bucket");
        ShopTradeTests.sell(helper, player, SELL_GATED_WATER, ShopTradeTests.ANY_SLOT, 4);
        TestShop.assertBalance(helper, player, 100 - 10 + 4 * 4);
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 0, "water buckets left");
    }

    /**
     * Known bug (adamico/AdminShop#20): shift-clicking a fluid container in the shop screen sells one trade, so a
     * bucket can't be sold to an Offer of less than 1000 mB. The shift-click on a water bucket asks for one trade of
     * 250 mB, which a bucket cannot give, and sells nothing. It should sell the four trades the bucket holds.
     */
    private static void shiftClickSellsOneTrade(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_shift_click", 0);
        MoneyHelper.get(helper.getLevel()).addPermit(TestShop.team(helper, player), PermitTests.GATE);
        player.getInventory().setItem(2, new ItemStack(Items.WATER_BUCKET));

        ShopTradeTests.sell(helper, player, SELL_GATED_WATER, 2, 1);

        TestShop.assertBalance(helper, player, 0);
        helper.assertValueEqual(TestShop.count(player, Items.WATER_BUCKET), 1, "water buckets left");
    }

    private static ServerPlayer join(GameTestHelper helper, TestPlayers players, String name, long balance) {
        ServerPlayer player = players.join(name);
        setUp(helper, player, balance);
        return player;
    }

    private static void setUp(GameTestHelper helper, ServerPlayer player, long balance) {
        player.setGameMode(GameType.SURVIVAL);
        TestShop.setBalance(helper, player, balance);
    }
}
