package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/**
 * Tests that AdminShop loads on a dedicated server with the version its build set. This also checks the harness,
 * because a run that reports this test ran AdminShop's tests and not vanilla's. JEI is optional. The game test
 * server runs without it and still has the Shop's Offers.
 */
final class LoadTests {

    private LoadTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("loads_on_a_server", 1, LoadTests::loads);
        tests.test("loads_without_jei", 1, LoadTests::loadsWithoutJei);
    }

    /**
     * processResources expands the version from gradle.properties into neoforge.mods.toml. If it did not, the version
     * would still read {@code ${mod_version}}.
     */
    private static void loads(GameTestHelper helper) {
        var container = ModList.get().getModContainerById(AdminShop.MODID);
        if (container.isEmpty()) {
            helper.fail("the server has not loaded " + AdminShop.MODID);
            return;
        }
        String version = container.get().getModInfo().getVersion().toString();
        if (version.contains("$")) {
            helper.fail("neoforge.mods.toml's version was not expanded: " + version);
            return;
        }
        helper.succeed();
    }

    /** Only meaningful while the game test server runs without JEI. */
    private static void loadsWithoutJei(GameTestHelper helper) {
        helper.assertFalse(ModList.get().isLoaded("jei"), "the game test server loaded JEI");
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(TestShop.offer("gametest/buy_diamonds")).isPresent(),
                "the Shop has no Offers without JEI");
        helper.succeed();
    }
}
