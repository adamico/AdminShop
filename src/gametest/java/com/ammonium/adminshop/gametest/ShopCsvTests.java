package com.ammonium.adminshop.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.ammonium.adminshop.client.events.ServerEventListeners;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Tests the warning about {@code config/adminshop/shop.csv}, a file AdminShop no longer reads. The server looks for
 * the file when it starts and at every {@code /reload}, and while it has found the file it warns each player who logs
 * in.
 */
final class ShopCsvTests {

    private static final Path SHOP_CSV = FMLPaths.CONFIGDIR.get().resolve("adminshop/shop.csv");
    private static final List<String> WARNING =
            List.of("message.adminshop.shop_csv_found_1", "message.adminshop.shop_csv_found_2");

    private ShopCsvTests() {
    }

    /** One test covers every step, because each step looks for the same file at its real path. */
    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("shop_csv_warns_players_once_found", 1, ShopCsvTests::warnsOnceFound);
    }

    /**
     * Known bug (adamico/AdminShop#20): the warning lasts until the server restarts, even after the file is deleted
     * and the server runs {@code /reload}. Once {@code /reload} finds the file deleted, the warning should stop.
     * Each {@code /reload} is the check the server runs when it reloads its resources. The test deletes the file at
     * the end, so the next run starts without it.
     */
    private static void warnsOnceFound(GameTestHelper helper) {
        try {
            helper.assertValueEqual(warningHeardBy(helper, "csv_none"), List.of(), "the warning with no shop.csv");

            Files.createDirectories(SHOP_CSV.getParent());
            Files.writeString(SHOP_CSV, "item,minecraft:dirt,1\n");
            helper.assertValueEqual(warningHeardBy(helper, "csv_unseen"), List.of(),
                    "the warning before the server looked for shop.csv again");
            reload();
            helper.assertValueEqual(warningHeardBy(helper, "csv_present"), WARNING, "the warning with a shop.csv");

            Files.delete(SHOP_CSV);
            reload();
            helper.assertValueEqual(warningHeardBy(helper, "csv_deleted"), WARNING, "the warning once shop.csv is deleted");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(SHOP_CSV);
            } catch (IOException ignored) {
            }
        }
        helper.succeed();
    }

    /** Runs the check the server runs at {@code /reload}, which reads only whether the file exists. */
    private static void reload() {
        ServerEventListeners.addReloadListener(null);
    }

    /** The translation keys of the warning lines sent to a player who logs in now. */
    private static List<String> warningHeardBy(GameTestHelper helper, String name) {
        var heard = new ArrayList<String>();
        TestPlayers.session(helper, players -> players.join(name, message -> {
            if (message.getContents() instanceof TranslatableContents translatable
                    && WARNING.contains(translatable.getKey())) {
                heard.add(translatable.getKey());
            }
        }));
        return heard;
    }
}
