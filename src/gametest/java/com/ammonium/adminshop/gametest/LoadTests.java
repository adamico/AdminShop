package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/**
 * AdminShop loads on a dedicated server at the version its build gave it. It is the harness's own
 * check too: a run that reports this test ran AdminShop's tests, not vanilla's.
 */
final class LoadTests {

    private LoadTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("loads_on_a_server", 1, LoadTests::loads);
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
}
