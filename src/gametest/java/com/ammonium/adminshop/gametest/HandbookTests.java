package com.ammonium.adminshop.gametest;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;

import com.ammonium.adminshop.AdminShop;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Patchouli is optional: the game test server runs without it, loads AdminShop, and has no Econ Handbook
 * recipe, whose result is Patchouli's item.
 */
final class HandbookTests {

    private static final Identifier RECIPE_FILE = Identifier.fromNamespaceAndPath(AdminShop.MODID, "recipe/econ_handbook.json");

    private HandbookTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("without_patchouli_there_is_no_handbook_recipe", 1, HandbookTests::noRecipe);
    }

    private static void noRecipe(GameTestHelper helper) {
        // The test means something only while the game test server runs without Patchouli.
        helper.assertFalse(ModList.get().isLoaded("patchouli"), "the game test server loaded Patchouli");
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getRecipeManager().byKey(ShopTradeTests.offer("econ_handbook")).isEmpty(),
                "the Econ Handbook recipe loaded without Patchouli");

        // Without its condition the recipe would be missing too, but as a load error naming Patchouli's
        // unknown item: the server reads it as the recipe loader does, and it must be skipped cleanly.
        var resource = server.getResourceManager().getResource(RECIPE_FILE);
        helper.assertTrue(resource.isPresent(), "AdminShop ships no Econ Handbook recipe");
        var ops = new ConditionalOps<>(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), ICondition.IContext.EMPTY);
        try (Reader reader = resource.get().openAsReader()) {
            var decoded = ConditionalOps.createConditionalCodec(Recipe.CODEC).parse(ops, JsonParser.parseReader(reader));
            helper.assertTrue(decoded.isSuccess(), "the Econ Handbook recipe failed to load: " + decoded);
            helper.assertTrue(decoded.getOrThrow().isEmpty(), "the Econ Handbook recipe's conditions held without Patchouli");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }
}
