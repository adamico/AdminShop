package com.ammonium.adminshop.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.ammonium.adminshop.setup.ObsoleteShopCsv;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Tests the warning about {@code config/adminshop/shop.csv}, a file AdminShop no longer reads. While the file
 * exists, it is reported at every server start and {@code /reload}, and to each player when they log in.
 */
final class ShopCsvTests {

    private static final List<String> WARNING =
            List.of("message.adminshop.shop_csv_found_1", "message.adminshop.shop_csv_found_2");

    private ShopCsvTests() {
    }

    /** One test covers every step, because each step looks for the same file at its real path. */
    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("shop_csv_warns_players_while_present", 1, ShopCsvTests::warnsWhilePresent);
    }

    /** If the file cannot be deleted at the end, the check keeps warning, and the next run's first step reports it. */
    private static void warnsWhilePresent(GameTestHelper helper) {
        try {
            helper.assertValueEqual(warningHeardBy(helper, "csv_none"), List.of(), "the warning with no shop.csv");

            Files.createDirectories(ObsoleteShopCsv.PATH.getParent());
            Files.writeString(ObsoleteShopCsv.PATH, "item,minecraft:dirt,1\n");
            helper.assertValueEqual(warningHeardBy(helper, "csv_unseen"), List.of(),
                    "the warning before the server looked for shop.csv again");
            ObsoleteShopCsv.check();
            helper.assertValueEqual(warningHeardBy(helper, "csv_present"), WARNING, "the warning with a shop.csv");

            Files.delete(ObsoleteShopCsv.PATH);
            ObsoleteShopCsv.check();
            helper.assertValueEqual(warningHeardBy(helper, "csv_deleted"), List.of(),
                    "the warning once shop.csv is deleted");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(ObsoleteShopCsv.PATH);
            } catch (IOException ignored) {
            }
            ObsoleteShopCsv.check();
        }
        helper.succeed();
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
