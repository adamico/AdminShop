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
 * JEI is optional: only the classes in {@code compat/jei} name a JEI type, and no other class names them, so no
 * JEI class loads unless JEI loads AdminShop's plugin. It reads the compiled classes, whose constant pools hold
 * every class they name.
 */
class OptionalViewersTest {

    private static final String JEI_PACKAGE = "com/ammonium/adminshop/compat/jei/";

    private static List<Path> mainClasses() throws IOException {
        List<Path> classes = new ArrayList<>();
        // FML runs the tests from a directory of its own, so the build passes the classes' directories.
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

    /** Whether the class names {@code internalName}: class names sit in the constant pool as modified UTF-8. */
    private static boolean names(Path file, String internalName) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).contains(internalName);
    }

    @Test
    void onlyTheJeiPackageNamesJei() throws IOException {
        List<String> jeiPlugin = new ArrayList<>();
        List<String> leaks = new ArrayList<>();
        for (Path file : mainClasses()) {
            String name = name(file);
            if (name.startsWith(JEI_PACKAGE)) {
                if (names(file, "mezz/jei/api/JeiPlugin")) {
                    jeiPlugin.add(name);
                }
            } else if (names(file, "mezz/jei/") || names(file, JEI_PACKAGE)) {
                leaks.add(name);
            }
        }
        assertEquals(List.of(), leaks, "classes outside compat/jei that name JEI or compat/jei");
        assertEquals(List.of(JEI_PACKAGE + "AdminShopJeiPlugin.class"), jeiPlugin);
    }

    @Test
    void theClassesAreThere() throws IOException {
        // A wrong directory would make the check above pass on nothing.
        assertFalse(mainClasses().isEmpty());
        assertTrue(mainClasses().stream().map(OptionalViewersTest::name).anyMatch(name -> name.startsWith(JEI_PACKAGE)));
    }
}
