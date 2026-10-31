package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/**
 * AdminShop loads on a dedicated server at the version its build gave it. It is the harness's own
 * check too: a run that reports this test ran AdminShop's tests, not vanilla's. JEI is optional: the game
 * test server runs without it, and still has the Shop's Offers.
 */
final class LoadTests {

    private LoadTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("loads_on_a_server", 1, LoadTests::loads);
        tests.test("loads_without_jei", 1, LoadTests::loadsWithoutJei);
    }

    private static void loads(GameTestHelper helper) {
        var container = ModList.get().getModContainerById(AdminShop.MODID);
        if (container.isEmpty()) {
            helper.fail("the server has not loaded " + AdminShop.MODID);
            return;
        }
        // processResources writes gradle.properties into neoforge.mods.toml; unexpanded, the
        // version would still read ${mod_version}.
        String version = container.get().getModInfo().getVersion().toString();
        if (version.contains("$")) {
            helper.fail("neoforge.mods.toml's version was not expanded: " + version);
            return;
        }
        helper.succeed();
    }

    private static void loadsWithoutJei(GameTestHelper helper) {
        // The test means something only while the game test server runs without JEI.
        helper.assertFalse(ModList.get().isLoaded("jei"), "the game test server loaded JEI");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(ShopTradeTests.offer("gametest/buy_diamonds")).isPresent(),
                "the Shop has no Offers without JEI");
        helper.succeed();
    }
}
