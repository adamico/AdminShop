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
 * A {@code config/adminshop/shop.csv}, which AdminShop 1.x read the shop from and AdminShop no longer reads, is
 * reported at every server start and {@code /reload}, and to each player as they log in, as on 1.21.1.
 */
final class ShopCsvTests {

    private static final List<String> WARNING =
            List.of("message.adminshop.shop_csv_found_1", "message.adminshop.shop_csv_found_2");

    private ShopCsvTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        // One test, as every step looks for the one file at its real place.
        tests.test("shop_csv_warns_players_while_present", 1, ShopCsvTests::warnsWhilePresent);
    }

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
                // The check below then keeps warning, which the next run's first step reports.
            }
            ObsoleteShopCsv.check();
        }
        helper.succeed();
    }

    /** The warning's lines, as translation keys, that a player logging in now is sent. */
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
