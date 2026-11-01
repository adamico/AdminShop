package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.UUID;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.AbstractBuyerEntity;
import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketAccountAddPermit;
import com.ammonium.adminshop.network.PacketSetItemBuyerRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Tests Permits. An Offer gated by a Permit trades only for an Account that holds it, by hand and through machines.
 * Redeeming a Trade Permit in the shop screen unlocks its Permit. The Offers come from the game tests' datapack.
 * Gated by the Permit {@code gametest_gate}, {@code gametest/buy_gated_emeralds} gives 1 emerald for 10 and
 * {@code gametest/sell_gated_flint} buys 4 flint for 5.
 */
final class PermitTests {

    static final String GATE = "gametest_gate";
    static final ResourceKey<Recipe<?>> BUY_EMERALDS = TestShop.offer("gametest/buy_gated_emeralds");
    static final ResourceKey<Recipe<?>> SELL_FLINT = TestShop.offer("gametest/sell_gated_flint");
    private static final BlockPos MACHINE = new BlockPos(4, 1, 4);
    /** Long enough for a Seller or T1 Buyer to trade twice. */
    private static final int TWO_CYCLES = 90;

    private PermitTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("buying_a_gated_offer_needs_its_permit", 20, TestPlayers.withPlayers(PermitTests::buyingNeedsThePermit));
        tests.test("selling_to_a_gated_offer_needs_its_permit", 20, TestPlayers.withPlayers(PermitTests::sellingNeedsThePermit));
        tests.test("a_seller_sells_to_a_gated_offer_only_with_its_permit", 2 * TWO_CYCLES + 20, PermitTests::sellerNeedsThePermit);
        tests.test("a_buyer_buys_a_gated_offer_only_with_its_permit", 2 * TWO_CYCLES + 20, PermitTests::buyerNeedsThePermit);
        tests.test("redeeming_a_trade_permit_unlocks_its_permit", 20, TestPlayers.withPlayers(PermitTests::redeeming));
        tests.test("the_add_permit_request_redeems_any_item", 20, TestPlayers.withPlayers(PermitTests::redeemingOthers));
    }

    private static void buyingNeedsThePermit(GameTestHelper helper, TestPlayers players) {
        var heard = new ArrayList<String>();
        ServerPlayer player = players.join("permit_buy", ShopTradeTests.translations(heard));
        UUID team = TestShop.team(helper, player);
        TestShop.setBalance(helper, team, 100);

        ShopTradeTests.buy(helper, player, BUY_EMERALDS, 2);
        TestShop.assertBalance(helper, team, 100);
        helper.assertValueEqual(TestShop.count(player, Items.EMERALD), 0, "emeralds bought without the Permit");
        helper.assertTrue(heard.contains("message.adminshop.no_permit"), "the player was not told the Permit is missing: " + heard);

        MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        ShopTradeTests.buy(helper, player, BUY_EMERALDS, 2);
        TestShop.assertBalance(helper, team, 80);
        helper.assertValueEqual(TestShop.count(player, Items.EMERALD), 2, "emeralds bought with the Permit");
    }

    /** One sale is sent as a shift-click on the slot sends it. */
    private static void sellingNeedsThePermit(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("permit_sell");
        UUID team = TestShop.team(helper, player);
        TestShop.setBalance(helper, team, 0);
        player.getInventory().setItem(3, new ItemStack(Items.FLINT, 12));

        ShopTradeTests.sell(helper, player, SELL_FLINT, ShopTradeTests.ANY_SLOT, 1);
        ShopTradeTests.shiftClick(helper, player, SELL_FLINT, 3);
        TestShop.assertBalance(helper, team, 0);
        helper.assertValueEqual(TestShop.count(player, Items.FLINT), 12, "flint left without the Permit");

        MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        ShopTradeTests.sell(helper, player, SELL_FLINT, ShopTradeTests.ANY_SLOT, 1);
        TestShop.assertBalance(helper, team, 5);
        helper.assertValueEqual(TestShop.count(player, Items.FLINT), 8, "flint left with the Permit");
    }

    private static void sellerNeedsThePermit(GameTestHelper helper) {
        UUID team = TestShop.placedBy(helper, "permit_seller", 0, ModBlocks.SELLER.get(), MACHINE);
        int inserted = SellerTests.insert(helper, new ItemStack(Items.FLINT, 8));
        helper.assertValueEqual(inserted, 8, "flint piped in");

        helper.runAfterDelay(TWO_CYCLES, () -> {
            TestShop.assertBalance(helper, team, 0);
            helper.assertValueEqual(SellerTests.handler(helper).getAmountAsInt(0), 8, "flint left in the Seller without the Permit");
            MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        });
        helper.runAfterDelay(2L * TWO_CYCLES, () -> {
            TestShop.assertBalance(helper, team, 10);
            helper.assertValueEqual(SellerTests.handler(helper).getAmountAsInt(0), 0, "flint left in the Seller with the Permit");
            helper.succeed();
        });
    }

    /** The server lets the Buyer be set to a gated Offer, but the Buyer does not buy it without the Permit. */
    private static void buyerNeedsThePermit(GameTestHelper helper) {
        UUID team = TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join("permit_buyer");
            TestShop.place(helper, owner, ModBlocks.BUYER_1.get(), MACHINE);
            TestShop.use(helper, owner, MACHINE);
            TestShop.send(owner, new PacketSetItemBuyerRecipe(helper.absolutePos(MACHINE), BUY_EMERALDS.identifier()));
            BuyerTests.assertOffer(helper, buyer(helper), BUY_EMERALDS, "the Buyer's Offer");
            return TestShop.team(helper, owner);
        });
        TestShop.setBalance(helper, team, 100);

        helper.runAfterDelay(TWO_CYCLES, () -> {
            TestShop.assertBalance(helper, team, 100);
            helper.assertValueEqual(buyer(helper).countItem(Items.EMERALD), 0, "emeralds bought without the Permit");
            MoneyHelper.get(helper.getLevel()).addPermit(team, GATE);
        });
        helper.runAfterDelay(2L * TWO_CYCLES, () -> {
            TestShop.assertBalance(helper, team, 80);
            helper.assertValueEqual(buyer(helper).countItem(Items.EMERALD), 2, "emeralds bought with the Permit");
            helper.succeed();
        });
    }

    /** Redeems the Trade Permit as a shift-click on it in the shop screen does. */
    private static void redeeming(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("permit_redeem");
        player.getInventory().setItem(5, tradePermit("gametest_redeemed", 2));

        redeem(helper, player, "gametest_redeemed", 5);

        helper.assertTrue(MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).permits().contains("gametest_redeemed"),
                "the Account didn't get the Permit");
        helper.assertValueEqual(player.getInventory().getItem(5).getCount(), 1, "Trade Permits left in the slot");
    }

    /**
     * Known bug (adamico/AdminShop#20): the add-Permit request lets a client give any team any Permit, and destroy
     * any item in its inventory. A request naming a slot of paper unlocks the Permit and takes the paper. The server
     * should redeem only a Trade Permit that holds the Permit it names.
     */
    private static void redeemingOthers(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("permit_redeem_other");
        player.getInventory().setItem(0, new ItemStack(Items.PAPER));

        redeem(helper, player, "gametest_paper", 0);

        helper.assertTrue(MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).permits().contains("gametest_paper"),
                "the Account didn't get the Permit");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "the paper is still in its slot");
    }

    /** A Trade Permit for {@code permit}, as {@code /adminshop getPermit} gives one. */
    private static ItemStack tradePermit(String permit, int count) {
        ItemStack stack = new ItemStack(ModItems.PERMIT.get(), count);
        CompoundTag key = new CompoundTag();
        key.putString("key", permit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(key));
        return stack;
    }

    /** Sends the request the shop screen sends when the player shift-clicks a Trade Permit in {@code slot}. */
    private static void redeem(GameTestHelper helper, ServerPlayer player, String permit, int slot) {
        TestShop.send(player, new PacketAccountAddPermit(TestShop.team(helper, player), permit, slot));
    }

    private static AbstractBuyerEntity buyer(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE, AbstractBuyerEntity.class);
    }
}
