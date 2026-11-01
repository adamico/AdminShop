package com.ammonium.adminshop.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Tests that JEI is optional. Only the classes in {@code client/jei} name a JEI type. No other class names them, so
 * no JEI class loads unless JEI loads AdminShop's plugin. The test reads the compiled classes, whose constant pools
 * hold every class they name.
 */
class OptionalViewersTest {

    private static final String JEI_PACKAGE = "com/ammonium/adminshop/client/jei/";
    private static final String JEI_API = "mezz/jei/";
    private static final String JEI_PLUGIN_ANNOTATION = "mezz/jei/api/JeiPlugin";
    private static final String PLUGIN = "AdminShopJEI.class";

    /** The build passes the classes' directories because FML runs the tests from its own directory. */
    private static List<Path> mainClasses() throws IOException {
        List<Path> classes = new ArrayList<>();
        for (String dir : System.getProperty("adminshop.mainClasses").split(File.pathSeparator)) {
            Path root = Path.of(dir);
            if (Files.isDirectory(root)) {
                try (Stream<Path> files = Files.walk(root)) {
                    files.filter(file -> file.toString().endsWith(".class")).forEach(classes::add);
                }
            }
        }
        return classes;
    }

    private static String name(Path file) {
        for (String dir : System.getProperty("adminshop.mainClasses").split(File.pathSeparator)) {
            Path root = Path.of(dir);
            if (file.startsWith(root)) {
                return root.relativize(file).toString().replace(File.separatorChar, '/');
            }
        }
        return file.toString();
    }

    /**
     * Returns whether the class file names {@code internalName}. Class names are stored in the constant pool as
     * modified UTF-8, so a search of the raw bytes finds them.
     */
    private static boolean names(Path file, String internalName) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains(internalName);
    }

    /**
     * No class outside the JEI package names JEI or that package, and JEI finds only AdminShop's plugin.
     */
    @Test
    void onlyTheJeiPackageNamesJei() throws IOException {
        List<String> plugins = new ArrayList<>();
        List<String> leaks = new ArrayList<>();
        for (Path file : mainClasses()) {
            String name = name(file);
            if (name.startsWith(JEI_PACKAGE)) {
                if (names(file, JEI_PLUGIN_ANNOTATION)) {
                    plugins.add(name);
                }
            } else if (names(file, JEI_API) || names(file, JEI_PACKAGE)) {
                leaks.add(name);
            }
        }
        assertEquals(List.of(), leaks, "classes outside " + JEI_PACKAGE + " that name " + JEI_API + " or " + JEI_PACKAGE);
        assertEquals(List.of(JEI_PACKAGE + PLUGIN), plugins);
    }

    /** A wrong directory would make the check above pass on nothing. */
    @Test
    void theClassesAreThere() throws IOException {
        assertFalse(mainClasses().isEmpty());
        assertTrue(mainClasses().stream().map(OptionalViewersTest::name).anyMatch(name -> name.startsWith(JEI_PACKAGE)), JEI_PACKAGE);
    }
}
