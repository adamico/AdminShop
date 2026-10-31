package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.SellFluidRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.shop.ShopGoods;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluids;

/**
 * Tests that the server loads the Offers of every datapack, skips a malformed Offer without crashing, and keeps
 * every Offer out of the vanilla recipe book.
 */
final class OfferLoadingTests {

    private OfferLoadingTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("offers_load_from_datapacks", 1, OfferLoadingTests::offersLoad);
        tests.test("a_malformed_offer_is_skipped", 1, OfferLoadingTests::malformedSkipped);
        tests.test("offers_never_reach_the_recipe_book", 1, OfferLoadingTests::notInRecipeBook);
        tests.test("every_shipped_example_offer_loads", 1, OfferLoadingTests::examplesLoad);
        tests.test("a_tag_sell_offer_shows_every_item_of_its_tag", 1, OfferLoadingTests::tagGoods);
    }

    private static RecipeManager recipes(GameTestHelper helper) {
        return helper.getLevel().getServer().getRecipeManager();
    }

    private static void offersLoad(GameTestHelper helper) {
        var buy = recipes(helper).byKey(ShopTradeTests.offer("gametest/buy_diamonds"));
        var sell = recipes(helper).byKey(ShopTradeTests.offer("gametest/sell_wheat"));
        var shipped = recipes(helper).byKey(ShopTradeTests.offer("example_buy_item"));
        helper.assertTrue(buy.isPresent() && buy.get().value() instanceof BuyItemRecipe, "the test Buy Offer did not load");
        helper.assertTrue(sell.isPresent() && sell.get().value() instanceof SellItemRecipe, "the test Sell Offer did not load");
        helper.assertTrue(shipped.isPresent(), "the shipped example Buy Offer did not load");
        helper.succeed();
    }

    /** The server started despite the malformed Offers to reach this test, and it logged why each one is malformed. */
    private static void malformedSkipped(GameTestHelper helper) {
        for (String offer : new String[] {"gametest/malformed_negative_price", "gametest/malformed_item_and_tag",
                "gametest/malformed_no_goods"}) {
            helper.assertTrue(recipes(helper).byKey(ShopTradeTests.offer(offer)).isEmpty(), "the malformed Offer " + offer + " loaded");
        }
        helper.succeed();
    }

    /**
     * The fluid Offer's name is checked here because it reads the fluid's components, which only a loaded world has.
     */
    private static void examplesLoad(GameTestHelper helper) {
        for (String offer : new String[] {"example_buy_item", "example_buy_nbt"}) {
            var loaded = recipes(helper).byKey(ShopTradeTests.offer(offer));
            helper.assertTrue(loaded.isPresent() && loaded.get().value() instanceof BuyItemRecipe, "the example Buy Offer " + offer + " did not load");
        }
        for (String offer : new String[] {"example_sell_item", "example_sell_nbt", "example_sell_item_tag"}) {
            var loaded = recipes(helper).byKey(ShopTradeTests.offer(offer));
            helper.assertTrue(loaded.isPresent() && loaded.get().value() instanceof SellItemRecipe, "the example Sell Offer " + offer + " did not load");
        }
        var water = recipes(helper).byKey(ShopTradeTests.offer("example_buy_fluid"));
        helper.assertTrue(water.isPresent() && water.get().value() instanceof BuyFluidRecipe, "the example fluid Buy Offer did not load");
        var lava = recipes(helper).byKey(ShopTradeTests.offer("example_sell_fluid"));
        helper.assertTrue(lava.isPresent() && lava.get().value() instanceof SellFluidRecipe, "the example fluid Sell Offer did not load");
        helper.assertValueEqual(((BuyFluidRecipe) water.get().value()).name(), Fluids.WATER.getFluidType().getDescription(), "the example fluid's name");
        var named = (BuyItemRecipe) recipes(helper).byKey(ShopTradeTests.offer("example_buy_nbt")).orElseThrow().value();
        helper.assertValueEqual(named.getItem().getHoverName().getString(), "Special Paper!", "the example's paper name");
        helper.succeed();
    }

    private static void tagGoods(GameTestHelper helper) {
        var offer = (SellItemRecipe) recipes(helper).byKey(ShopTradeTests.offer("example_sell_item_tag")).orElseThrow().value();
        var wool = BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.WOOL);
        int members = 0;
        for (var ignored : wool) {
            members++;
        }

        var stacks = ((ShopGoods.Items) offer.goods()).stacks();
        helper.assertValueEqual(stacks.size(), members, "items shown for minecraft:wool");
        helper.assertTrue(members > 1, "minecraft:wool has a single item");
        for (ItemStackTemplate stack : stacks) {
            helper.assertTrue(stack.item().is(ItemTags.WOOL), stack.item().getRegisteredName() + " shown for minecraft:wool");
            helper.assertValueEqual(stack.count(), 16, "count shown for " + stack.item().getRegisteredName());
        }
        helper.assertValueEqual(offer.getCount(), 16, "the Offer's count");
        helper.assertValueEqual(offer.name(), Component.translatable("sellitem.recipe.tag", "minecraft:wool"), "the Offer's name");
        helper.assertValueEqual(offer.searchTerm(), "minecraft:wool", "the Offer's search term");
        helper.succeed();
    }

    private static void notInRecipeBook(GameTestHelper helper) {
        for (String offer : new String[] {"gametest/buy_diamonds", "gametest/sell_wheat", "gametest/sell_wool", "example_buy_fluid",
                "example_sell_fluid"}) {
            int[] displays = {0};
            recipes(helper).listDisplaysForRecipe(ShopTradeTests.offer(offer), display -> displays[0]++);
            helper.assertValueEqual(displays[0], 0, "recipe book displays of " + offer);
        }
        helper.succeed();
    }
}
