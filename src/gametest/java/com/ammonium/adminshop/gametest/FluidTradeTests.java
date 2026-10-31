package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.shop.ShopTrades;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;

/**
 * Trading fluid Offers by hand, as the shop screen does: buying fills a fluid container in the main inventory (an
 * empty bucket becomes a full one), selling drains one. Offers from the game tests' datapack:
 * {@code gametest/buy_water} gives 1000 mB of water for 30, {@code gametest/sell_lava} takes 1000 mB of lava for 20,
 * and, gated by the Permit {@code gametest_gate}, {@code gametest/buy_gated_lava} gives 1000 mB of lava for 10 and
 * {@code gametest/sell_gated_water} takes 250 mB of water for 4.
 */
final class FluidTradeTests {

    static final ResourceKey<Recipe<?>> BUY_WATER = ShopTradeTests.offer("gametest/buy_water");
    static final ResourceKey<Recipe<?>> SELL_LAVA = ShopTradeTests.offer("gametest/sell_lava");
    static final ResourceKey<Recipe<?>> BUY_GATED_LAVA = ShopTradeTests.offer("gametest/buy_gated_lava");
    static final ResourceKey<Recipe<?>> SELL_GATED_WATER = ShopTradeTests.offer("gametest/sell_gated_water");

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
    }

    private static void buying(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy", 100);
        player.getInventory().setItem(4, new ItemStack(Items.BUCKET));

        helper.assertValueEqual(ShopTrades.buy(player, BUY_WATER, 1), ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 70);
        helper.assertValueEqual(count(player, Items.WATER_BUCKET), 1, "water buckets bought");
        helper.assertValueEqual(count(player, Items.BUCKET), 0, "empty buckets left");
    }

    private static void buyingIntoAStack(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy_stack", 100);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET, 4));

        helper.assertValueEqual(ShopTrades.buy(player, BUY_WATER, 1), ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 70);
        helper.assertValueEqual(count(player, Items.WATER_BUCKET), 1, "water buckets bought");
        helper.assertValueEqual(count(player, Items.BUCKET), 3, "empty buckets left");
    }

    private static void buyingWithoutAContainer(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy_no_bucket", 100);
        // A bucket full of something else takes no water.
        player.getInventory().setItem(0, new ItemStack(Items.LAVA_BUCKET));

        helper.assertValueEqual(ShopTrades.buy(player, BUY_WATER, 1), ShopTrades.Result.NOT_ENOUGH_SPACE, "the trade's result");
        // Two trades are 2000 mB, more than a bucket holds.
        player.getInventory().setItem(1, new ItemStack(Items.BUCKET));
        helper.assertValueEqual(ShopTrades.buy(player, BUY_WATER, 2), ShopTrades.Result.NOT_ENOUGH_SPACE, "two trades into a bucket");
        assertBalance(helper, player, 100);
        helper.assertValueEqual(count(player, Items.WATER_BUCKET), 0, "water buckets bought");
    }

    private static void buyingTooDear(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_buy_poor", 29);
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET));

        helper.assertValueEqual(ShopTrades.buy(player, BUY_WATER, 1), ShopTrades.Result.NOT_ENOUGH_MONEY, "the trade's result");
        assertBalance(helper, player, 29);
        helper.assertValueEqual(count(player, Items.BUCKET), 1, "empty buckets left");
        helper.assertValueEqual(count(player, Items.WATER_BUCKET), 0, "water buckets bought");
    }

    private static void selling(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_sell", 0);
        player.getInventory().setItem(3, new ItemStack(Items.LAVA_BUCKET));

        // As a shift-click on the slot sends it.
        helper.assertValueEqual(ShopTrades.sell(player, SELL_LAVA, 3, ShopTrades.ALL), ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 20);
        helper.assertValueEqual(count(player, Items.LAVA_BUCKET), 0, "lava buckets left");
        helper.assertValueEqual(count(player, Items.BUCKET), 1, "empty buckets");
    }

    private static void sellingFromAnySlot(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_sell_any", 0);
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(7, new ItemStack(Items.LAVA_BUCKET));

        helper.assertValueEqual(ShopTrades.sell(player, SELL_LAVA, ShopTrades.ANY_SLOT, 1), ShopTrades.Result.DONE, "the trade's result");
        assertBalance(helper, player, 20);
        helper.assertValueEqual(count(player, Items.LAVA_BUCKET), 0, "lava buckets left");
        helper.assertValueEqual(count(player, Items.WATER_BUCKET), 1, "water buckets left");
    }

    private static void sellingWithoutTheFluid(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_sell_none", 0);
        player.getInventory().setItem(0, new ItemStack(Items.WATER_BUCKET));
        player.getInventory().setItem(1, new ItemStack(Items.LAVA_BUCKET));

        helper.assertValueEqual(ShopTrades.sell(player, SELL_LAVA, 0, ShopTrades.ALL), ShopTrades.Result.NOT_ENOUGH_GOODS,
                "selling water to a lava Offer");
        // Two trades are 2000 mB, more than a bucket holds.
        helper.assertValueEqual(ShopTrades.sell(player, SELL_LAVA, ShopTrades.ANY_SLOT, 2), ShopTrades.Result.NOT_ENOUGH_GOODS,
                "selling two trades from one bucket");
        assertBalance(helper, player, 0);
        helper.assertValueEqual(count(player, Items.LAVA_BUCKET), 1, "lava buckets left");
    }

    private static void permits(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = join(helper, players, "fluid_permit", 100);
        UUID team = MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).teamId();
        player.getInventory().setItem(0, new ItemStack(Items.BUCKET));
        player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));

        helper.assertValueEqual(ShopTrades.buy(player, BUY_GATED_LAVA, 1), ShopTrades.Result.NO_PERMIT, "buying without the Permit");
        helper.assertValueEqual(ShopTrades.sell(player, SELL_GATED_WATER, 1, ShopTrades.ALL), ShopTrades.Result.NO_PERMIT,
                "selling without the Permit");
        assertBalance(helper, player, 100);

        MoneyHelper.get(helper.getLevel()).addPermit(team, PermitTests.GATE);
        helper.assertValueEqual(ShopTrades.buy(player, BUY_GATED_LAVA, 1), ShopTrades.Result.DONE, "buying with the Permit");
        helper.assertValueEqual(count(player, Items.LAVA_BUCKET), 1, "lava buckets bought with the Permit");
        // A bucket holds a whole bucket or nothing: a quarter of it can't be drained, but four quarters can.
        helper.assertValueEqual(ShopTrades.sell(player, SELL_GATED_WATER, ShopTrades.ANY_SLOT, 1), ShopTrades.Result.NOT_ENOUGH_GOODS,
                "selling a quarter bucket from a bucket");
        helper.assertValueEqual(ShopTrades.sell(player, SELL_GATED_WATER, 1, ShopTrades.ALL), ShopTrades.Result.DONE,
                "selling a bucket in quarters with the Permit");
        assertBalance(helper, player, 100 - 10 + 4 * 4);
        helper.assertValueEqual(count(player, Items.WATER_BUCKET), 0, "water buckets left");
    }

    private static ServerPlayer join(GameTestHelper helper, TestPlayers players, String name, long balance) {
        ServerPlayer player = players.join(name);
        player.setGameMode(GameType.SURVIVAL);
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        var account = accounts.getPlayerAccount(player);
        accounts.removeMoney(account.teamId(), account.balance());
        accounts.addMoney(account.teamId(), balance);
        return player;
    }

    private static void assertBalance(GameTestHelper helper, ServerPlayer player, long expected) {
        long balance = MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).balance();
        helper.assertValueEqual(balance, expected, player.getPlainTextName() + "'s Balance");
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
