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
        // The server got this far, and logged the Offer's negative price.
        helper.assertTrue(recipes(helper).byKey(ShopTradeTests.offer("gametest/malformed_negative_price")).isEmpty(),
                "the malformed Offer loaded");
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
