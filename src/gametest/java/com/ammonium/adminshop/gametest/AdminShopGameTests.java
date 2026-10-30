package com.ammonium.adminshop.gametest;

import java.util.List;
import java.util.function.Consumer;

import com.ammonium.adminshop.AdminShop;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * AdminShop's game tests, run by the {@code gameTestServer} Gradle run. Each test stands on the
 * {@code adminshop:gametest/platform} structure, a 9x5x9 stone floor, and places what it needs itself.
 * This class lives in the {@code gametest} source set, which only the mod's own runs load, and hooks
 * itself in, so the {@code @Mod} class names no game-test code.
 */
@EventBusSubscriber(modid = AdminShop.MODID)
public final class AdminShopGameTests {

    private static final Identifier PLATFORM = id("gametest/platform");

    private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_TYPES =
            DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, AdminShop.MODID);

    static {
        TEST_TYPES.register("code", () -> CodeGameTest.CODEC);
    }

    private AdminShopGameTests() {
    }

    @SubscribeEvent
    static void construct(FMLConstructModEvent event) {
        IEventBus modBus = event.getContainer().getEventBus();
        TEST_TYPES.register(modBus);
        // Posted only when game tests are enabled, which a production game never is.
        modBus.addListener(AdminShopGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("default"), new TestEnvironmentDefinition.AllOf(List.of()));
        var tests = new Registrar(event, environment);
        LoadTests.register(tests);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AdminShop.MODID, path);
    }

    /** What a test class is handed: a name, a tick budget and a body per test. */
    record Registrar(RegisterGameTestsEvent event, Holder<TestEnvironmentDefinition<?>> environment) {

        void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
            var id = id(name);
            CodeGameTest.define(id, body);
            event.registerTest(id, new CodeGameTest(id, new TestData<>(environment, PLATFORM, maxTicks, 0, true, Rotation.NONE)));
        }
    }
}
