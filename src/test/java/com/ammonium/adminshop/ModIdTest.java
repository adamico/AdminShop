package com.ammonium.adminshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The mod id in the code is the one the build names, and one the game accepts as a namespace. The
 * code's id names the game tests and the build's selects them, so a mismatch runs none.
 */
class ModIdTest {

    @Test
    void theCodeNamesTheBuildsModId() throws IOException {
        var properties = new Properties();
        // FML runs the tests from a directory of its own, so the build passes the file's path.
        try (Reader reader = Files.newBufferedReader(Path.of(System.getProperty("adminshop.gradleProperties")))) {
            properties.load(reader);
        }
        assertEquals(properties.getProperty("mod_id"), AdminShop.MODID);
    }

    @Test
    void theModIdIsANamespace() {
        assertTrue(AdminShop.MODID.matches("[a-z0-9_.-]+"), AdminShop.MODID);
    }
}
