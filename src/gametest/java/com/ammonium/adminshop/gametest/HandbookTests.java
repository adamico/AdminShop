package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.fml.ModList;

/**
 * Tests the Econ Handbook recipe. AdminShop requires Patchouli, so the game test server loads it, and the recipe
 * crafts Patchouli's guide book from a book and an emerald.
 */
final class HandbookTests {

    private static final String BOOK = AdminShop.MODID + ":econ_handbook";

    private HandbookTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("the_handbook_recipe_crafts_a_guide_book", 1, HandbookTests::recipe);
    }

    /**
     * Known bug (adamico/AdminShop#20): the recipe puts the book's id in {@code minecraft:custom_data}, where
     * Patchouli doesn't read it, so a crafted Econ Handbook opens no book. The result should carry the
     * {@code patchouli:book} component instead.
     */
    private static void recipe(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("patchouli"), "the game test server did not load Patchouli");
        var server = helper.getLevel().getServer();
        var recipe = server.getRecipeManager().byKey(TestShop.offer("econ_handbook"));
        helper.assertTrue(recipe.isPresent(), "the Econ Handbook recipe did not load");

        var displays = recipe.get().value().display();
        helper.assertFalse(displays.isEmpty(), "the Econ Handbook recipe shows no result");
        ItemStack result = displays.getFirst().result().resolveForFirstStack(SlotDisplayContext.fromLevel(helper.getLevel()));
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem()), Identifier.parse("patchouli:guide_book"),
                "the recipe's result");
        String customBook = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("patchouli:book", "");
        helper.assertValueEqual(customBook, BOOK, "the book id in the result's custom data");
        var bookComponent = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse("patchouli:book"));
        helper.assertTrue(bookComponent.isPresent(), "Patchouli registers no patchouli:book component");
        helper.assertFalse(result.has(bookComponent.get()), "the result carries the patchouli:book component");
        helper.succeed();
    }
}
