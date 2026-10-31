package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * The server loads the Offers of every datapack, skips a malformed one without crashing, and keeps
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

    private static void malformedSkipped(GameTestHelper helper) {
        // The server got this far, and logged why each Offer is malformed.
        for (String offer : new String[] {"gametest/malformed_negative_price", "gametest/malformed_no_goods"}) {
            helper.assertTrue(recipes(helper).byKey(ShopTradeTests.offer(offer)).isEmpty(), "the malformed Offer " + offer + " loaded");
        }
        helper.succeed();
    }

    private static void examplesLoad(GameTestHelper helper) {
        for (String offer : new String[] {"example_buy_item", "example_buy_nbt"}) {
            var loaded = recipes(helper).byKey(ShopTradeTests.offer(offer));
            helper.assertTrue(loaded.isPresent() && loaded.get().value() instanceof BuyItemRecipe, "the example Buy Offer " + offer + " did not load");
        }
        for (String offer : new String[] {"example_sell_item", "example_sell_nbt"}) {
            var loaded = recipes(helper).byKey(ShopTradeTests.offer(offer));
            helper.assertTrue(loaded.isPresent() && loaded.get().value() instanceof SellItemRecipe, "the example Sell Offer " + offer + " did not load");
        }
        var named = (BuyItemRecipe) recipes(helper).byKey(ShopTradeTests.offer("example_buy_nbt")).orElseThrow().value();
        helper.assertValueEqual(named.getItem().getHoverName().getString(), "Special Paper!", "the example's paper name");
        helper.succeed();
    }

    private static void notInRecipeBook(GameTestHelper helper) {
        for (String offer : new String[] {"gametest/buy_diamonds", "gametest/sell_wheat"}) {
            int[] displays = {0};
            recipes(helper).listDisplaysForRecipe(ShopTradeTests.offer(offer), display -> displays[0]++);
            helper.assertValueEqual(displays[0], 0, "recipe book displays of " + offer);
        }
        helper.succeed();
    }
}
