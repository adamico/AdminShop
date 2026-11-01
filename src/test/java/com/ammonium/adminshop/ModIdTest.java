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
 * Tests that the mod id in the code matches the build's and is a valid namespace. The code's id names the
 * game tests and the build's id selects them, so a mismatch runs none.
 */
class ModIdTest {

    /** The build passes the properties file's path because FML runs the tests from its own directory. */
    @Test
    void theCodeNamesTheBuildsModId() throws IOException {
        var properties = new Properties();
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
