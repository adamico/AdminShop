package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.ammonium.adminshop.network.PacketBuyRequest;
import com.ammonium.adminshop.network.PacketSellRequest;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Tests trading by hand through the requests the shop screen sends. A buy or sell button sends a request for some
 * trades of an Offer, and a shift-click on an inventory slot sends a sale of as many trades as the slot holds. The
 * Offers come from the game tests' datapack. {@code gametest/buy_diamonds} sells 2 diamonds for 50,
 * {@code gametest/sell_wheat} buys 10 wheat for 7, {@code gametest/sell_wool} buys 4 of any wool for 3,
 * {@code gametest/buy_enchanted_book} sells a named, enchanted book for 40 and {@code gametest/sell_named_paper}
 * buys 2 paper named "Ledger" for 9.
 */
final class ShopTradeTests {

    static final ResourceKey<Recipe<?>> BUY_DIAMONDS = TestShop.offer("gametest/buy_diamonds");
    static final ResourceKey<Recipe<?>> SELL_WHEAT = TestShop.offer("gametest/sell_wheat");
    static final ResourceKey<Recipe<?>> SELL_WOOL = TestShop.offer("gametest/sell_wool");
    static final ResourceKey<Recipe<?>> BUY_BOOK = TestShop.offer("gametest/buy_enchanted_book");
    static final ResourceKey<Recipe<?>> SELL_NAMED_PAPER = TestShop.offer("gametest/sell_named_paper");
    /** The slot a request from the buy and sell buttons names: any slot that holds the goods. */
    static final int ANY_SLOT = -1;

    private ShopTradeTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("buying_charges_the_account_and_gives_the_goods", 20, TestPlayers.withPlayers(ShopTradeTests::buying));
        tests.test("buying_is_refused_when_the_balance_is_too_low", 20, TestPlayers.withPlayers(ShopTradeTests::buyingTooDear));
        tests.test("buying_into_a_full_inventory_charges_and_gives_nothing", 20, TestPlayers.withPlayers(ShopTradeTests::buyingIntoAFullInventory));
        tests.test("buying_into_a_nearly_full_inventory_charges_every_trade", 20, TestPlayers.withPlayers(ShopTradeTests::buyingIntoANearlyFullInventory));
        tests.test("selling_credits_the_account_and_takes_the_goods", 20, TestPlayers.withPlayers(ShopTradeTests::selling));
        tests.test("selling_from_a_slot_sells_all_it_can", 20, TestPlayers.withPlayers(ShopTradeTests::sellingFromASlot));
        tests.test("selling_without_the_goods_is_refused", 20, TestPlayers.withPlayers(ShopTradeTests::sellingWithoutGoods));
        tests.test("a_buy_offer_cannot_be_sold_to", 20, TestPlayers.withPlayers(ShopTradeTests::wrongSide));
        tests.test("a_trade_of_fewer_than_one_is_free_or_sells_everything", 20, TestPlayers.withPlayers(ShopTradeTests::nonPositiveQuantity));
        tests.test("selling_a_tagged_item", 20, TestPlayers.withPlayers(ShopTradeTests::sellingATaggedItem));
        tests.test("selling_an_item_outside_the_tag_is_refused", 20, TestPlayers.withPlayers(ShopTradeTests::sellingOutsideTheTag));
        tests.test("buying_an_item_with_components", 20, TestPlayers.withPlayers(ShopTradeTests::buyingWithComponents));
        tests.test("selling_needs_the_offers_components", 20, TestPlayers.withPlayers(ShopTradeTests::sellingWithComponents));
    }

    private static void buying(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_ok");
        TestShop.setBalance(helper, player, 1000);

        buy(helper, player, BUY_DIAMONDS, 3);

        TestShop.assertBalance(helper, player, 1000 - 3 * 50);
        helper.assertValueEqual(TestShop.count(player, Items.DIAMOND), 6, "diamonds bought");
    }

    private static void buyingTooDear(GameTestHelper helper, TestPlayers players) {
        var heard = new ArrayList<String>();
        ServerPlayer player = players.join("buy_poor", translations(heard));
        TestShop.setBalance(helper, player, 99);

        buy(helper, player, BUY_DIAMONDS, 2);

        TestShop.assertBalance(helper, player, 99);
        helper.assertValueEqual(TestShop.count(player, Items.DIAMOND), 0, "diamonds bought");
        helper.assertTrue(heard.contains("message.adminshop.not_enough_money"), "the player was not told the Balance is too low: " + heard);
    }

    /**
     * Known bug (adamico/AdminShop#20): buying into an inventory that can't hold everything charges the full price
     * and loses the rest. The trade should be refused, with the Balance and the inventory left as they were.
     */
    private static void buyingIntoAFullInventory(GameTestHelper helper, TestPlayers players) {
        var heard = new ArrayList<String>();
        ServerPlayer player = players.join("buy_full", translations(heard));
        TestShop.setBalance(helper, player, 1000);
        fillMainInventory(player, Inventory.INVENTORY_SIZE);

        buy(helper, player, BUY_DIAMONDS, 1);

        TestShop.assertBalance(helper, player, 1000 - 50);
        helper.assertValueEqual(TestShop.count(player, Items.DIAMOND), 0, "diamonds bought");
        helper.assertTrue(heard.contains("message.adminshop.not_enough_space"), "the player was not told the inventory is full: " + heard);
    }

    /**
     * Known bug (adamico/AdminShop#20): buying into an inventory that can't hold everything charges the full price
     * and loses the rest. The inventory has room for 5 more diamonds, and all 5 trades asked for are charged. Only
     * the two trades that fit should go through.
     */
    private static void buyingIntoANearlyFullInventory(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_nearly_full");
        TestShop.setBalance(helper, player, 1000);
        fillMainInventory(player, Inventory.INVENTORY_SIZE - 1);
        player.getInventory().setItem(Inventory.INVENTORY_SIZE - 1, new ItemStack(Items.DIAMOND, 59));

        buy(helper, player, BUY_DIAMONDS, 5);

        TestShop.assertBalance(helper, player, 1000 - 5 * 50);
        helper.assertValueEqual(TestShop.count(player, Items.DIAMOND), 64, "diamonds held");
    }

    private static void selling(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_ok");
        TestShop.setBalance(helper, player, 0);
        player.getInventory().setItem(4, new ItemStack(Items.WHEAT, 25));

        sell(helper, player, SELL_WHEAT, ANY_SLOT, 2);

        TestShop.assertBalance(helper, player, 2 * 7);
        helper.assertValueEqual(TestShop.count(player, Items.WHEAT), 5, "wheat left");
    }

    /** Sells as a shift-click on the slot does, which asks for as many trades as the stack holds. */
    private static void sellingFromASlot(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_slot");
        TestShop.setBalance(helper, player, 0);
        player.getInventory().setItem(7, new ItemStack(Items.WHEAT, 34));

        shiftClick(helper, player, SELL_WHEAT, 7);

        TestShop.assertBalance(helper, player, 3 * 7);
        helper.assertValueEqual(player.getInventory().getItem(7).getCount(), 4, "wheat left in the slot");
    }

    private static void sellingWithoutGoods(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_none");
        TestShop.setBalance(helper, player, 0);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 9));

        sell(helper, player, SELL_WHEAT, ANY_SLOT, 1);

        TestShop.assertBalance(helper, player, 0);
        helper.assertValueEqual(TestShop.count(player, Items.WHEAT), 9, "wheat left");
    }

    private static void wrongSide(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_to_buy");
        TestShop.setBalance(helper, player, 1000);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 10));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT, 10));

        sell(helper, player, BUY_DIAMONDS, ANY_SLOT, 1);
        buy(helper, player, SELL_WHEAT, 1);
        buy(helper, player, TestShop.offer("gametest/no_such_offer"), 1);

        TestShop.assertBalance(helper, player, 1000);
        helper.assertValueEqual(TestShop.count(player, Items.DIAMOND), 10, "diamonds held");
        helper.assertValueEqual(TestShop.count(player, Items.WHEAT), 10, "wheat held");
    }

    /**
     * Known bug (adamico/AdminShop#20): trade requests accept quantities below one. Buying 0 trades goes through for
     * nothing. Buying -3 fails only because the Account refuses a negative charge. Selling -3 trades sells as many
     * as the stack holds. A request for fewer than one trade should be refused.
     */
    private static void nonPositiveQuantity(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("zero_quantity");
        TestShop.setBalance(helper, player, 1000);
        player.getInventory().setItem(0, new ItemStack(Items.WHEAT, 64));

        buy(helper, player, BUY_DIAMONDS, 0);
        buy(helper, player, BUY_DIAMONDS, -3);
        TestShop.assertBalance(helper, player, 1000);
        helper.assertValueEqual(TestShop.count(player, Items.DIAMOND), 0, "diamonds bought");

        sell(helper, player, SELL_WHEAT, ANY_SLOT, -3);
        TestShop.assertBalance(helper, player, 1000 + 6 * 7);
        helper.assertValueEqual(TestShop.count(player, Items.WHEAT), 4, "wheat held");
    }

    /**
     * Two trades from any slot take all 8 wool from the blue stack, because the red one holds too few. The second
     * sale is sent as a shift-click on the red stack.
     */
    private static void sellingATaggedItem(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_tag");
        TestShop.setBalance(helper, player, 0);
        player.getInventory().setItem(2, new ItemStack(Items.RED_WOOL, 6));
        player.getInventory().setItem(5, new ItemStack(Items.BLUE_WOOL, 8));

        sell(helper, player, SELL_WOOL, ANY_SLOT, 2);
        shiftClick(helper, player, SELL_WOOL, 2);

        TestShop.assertBalance(helper, player, 3 * 3);
        helper.assertValueEqual(TestShop.count(player, Items.BLUE_WOOL), 0, "blue wool left");
        helper.assertValueEqual(TestShop.count(player, Items.RED_WOOL), 2, "red wool left");
    }

    private static void sellingOutsideTheTag(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_not_tag");
        TestShop.setBalance(helper, player, 0);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));

        sell(helper, player, SELL_WOOL, ANY_SLOT, 1);
        sell(helper, player, SELL_WOOL, 0, 15);

        TestShop.assertBalance(helper, player, 0);
        helper.assertValueEqual(TestShop.count(player, Items.COBBLESTONE), 64, "cobblestone held");
    }

    private static void buyingWithComponents(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("buy_components");
        TestShop.setBalance(helper, player, 1000);
        var offer = (BuyItemRecipe) TestShop.recipe(helper, BUY_BOOK);

        buy(helper, player, BUY_BOOK, 2);

        TestShop.assertBalance(helper, player, 1000 - 2 * 40);
        helper.assertValueEqual(TestShop.count(player, Items.ENCHANTED_BOOK), 2, "books bought");
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(Items.ENCHANTED_BOOK)) {
                helper.assertValueEqual(stack.getComponentsPatch(), offer.getItem().orElseThrow().getComponentsPatch(),
                        "a bought book's components");
            }
        }
        var efficiency = helper.getLevel().registryAccess().getOrThrow(Enchantments.EFFICIENCY);
        ItemStack book = player.getInventory().getItem(0);
        helper.assertValueEqual(book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(efficiency), 3,
                "the bought book's Efficiency level");
        helper.assertValueEqual(book.getHoverName().getString(), "Miner's Manual", "the bought book's name");
    }

    private static void sellingWithComponents(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("sell_components");
        TestShop.setBalance(helper, player, 0);
        player.getInventory().setItem(0, new ItemStack(Items.PAPER, 10));
        ItemStack ledgers = new ItemStack(Items.PAPER, 5);
        ledgers.set(DataComponents.CUSTOM_NAME, Component.literal("Ledger"));
        player.getInventory().setItem(1, ledgers);

        sell(helper, player, SELL_NAMED_PAPER, 0, 5);
        sell(helper, player, SELL_NAMED_PAPER, ANY_SLOT, 2);

        TestShop.assertBalance(helper, player, 2 * 9);
        helper.assertValueEqual(player.getInventory().getItem(0).getCount(), 10, "plain paper left");
        helper.assertValueEqual(player.getInventory().getItem(1).getCount(), 1, "named paper left");
    }

    /** Sends the request the shop screen's buy button sends for {@code quantity} trades of {@code offer}. */
    static void buy(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer, int quantity) {
        TestShop.send(player, new PacketBuyRequest(TestShop.team(helper, player), offer.identifier(), quantity));
    }

    /** Sends a request to sell {@code quantity} trades of {@code offer} from {@code slot}, or from any slot. */
    static void sell(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer, int slot, int quantity) {
        TestShop.send(player, new PacketSellRequest(TestShop.team(helper, player), offer.identifier(), slot, quantity));
    }

    /** Sends the sale a shift-click on {@code slot} sends: as many trades of {@code offer} as the slot holds. */
    static void shiftClick(GameTestHelper helper, ServerPlayer player, ResourceKey<Recipe<?>> offer, int slot) {
        var recipe = (SellItemRecipe) TestShop.recipe(helper, offer);
        sell(helper, player, offer, slot, player.getInventory().getItem(slot).getCount() / recipe.getCount());
    }

    /** Collects the translation key of every translated message a player is sent. */
    static Consumer<Component> translations(List<String> heard) {
        return message -> {
            if (message.getContents() instanceof TranslatableContents translatable) {
                heard.add(translatable.getKey());
            }
        };
    }

    /** Fills the first {@code slots} slots of the main inventory with full stacks of cobblestone. */
    private static void fillMainInventory(ServerPlayer player, int slots) {
        for (int slot = 0; slot < slots; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }
}
