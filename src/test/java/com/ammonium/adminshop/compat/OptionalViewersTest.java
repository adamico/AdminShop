package com.ammonium.adminshop.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ammonium.adminshop.AdminShop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * JEI and EMI are optional: only the classes in {@code compat/jei} name a JEI type and only those in
 * {@code compat/emi} an EMI type, and no other class names them, so no viewer class loads unless that viewer
 * loads AdminShop's plugin. It reads the compiled classes, whose constant pools hold every class they name.
 */
class OptionalViewersTest {

    /** A viewer: the package that alone may name it, its API's package, and the annotation it finds plugins by. */
    private record Viewer(String compatPackage, String api, String pluginAnnotation, String plugin) {
    }

    private static final Viewer JEI = new Viewer("com/ammonium/adminshop/compat/jei/", "mezz/jei/",
            "mezz/jei/api/JeiPlugin", "AdminShopJeiPlugin.class");
    private static final Viewer EMI = new Viewer("com/ammonium/adminshop/compat/emi/", "dev/emi/",
            "dev/emi/emi/api/EmiEntrypoint", "AdminShopEmiPlugin.class");

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

    /** The classes outside the viewer's package that name it or its package, and the plugins it would find. */
    private static void assertOnlyItsPackageNames(Viewer viewer) throws IOException {
        List<String> plugins = new ArrayList<>();
        List<String> leaks = new ArrayList<>();
        for (Path file : mainClasses()) {
            String name = name(file);
            if (name.startsWith(viewer.compatPackage())) {
                if (names(file, viewer.pluginAnnotation())) {
                    plugins.add(name);
                }
            } else if (names(file, viewer.api()) || names(file, viewer.compatPackage())) {
                leaks.add(name);
            }
        }
        assertEquals(List.of(), leaks, "classes outside " + viewer.compatPackage() + " that name " + viewer.api()
                + " or " + viewer.compatPackage());
        assertEquals(List.of(viewer.compatPackage() + viewer.plugin()), plugins);
    }

    @Test
    void onlyTheJeiPackageNamesJei() throws IOException {
        assertOnlyItsPackageNames(JEI);
    }

    @Test
    void onlyTheEmiPackageNamesEmi() throws IOException {
        assertOnlyItsPackageNames(EMI);
    }

    /**
     * With JEI and EMI both installed, EMI's JEI layer (JEMI) skips every JEI category whose id's namespace is the
     * mod id of a native EMI plugin, so the Shop categories show once, as AdminShop's EMI ones. EMI takes a plugin's
     * mod id from the first mod its jar declares, and AdminShop's JEI ids are in {@link AdminShop#MODID}'s namespace:
     * the jar must declare that mod alone.
     */
    @Test
    void emiSkipsTheJeiCategoriesWhenBothAreInstalled() throws IOException {
        String modsToml = Files.readString(Path.of(System.getProperty("adminshop.modsToml")));
        // Each [[mods]] table and the modId that opens it.
        Matcher tables = Pattern.compile("(?m)^\\[\\[mods]]\\s*$(?:\\R\\s*modId\\s*=\\s*\"([^\"]*)\")?").matcher(modsToml);
        List<String> mods = new ArrayList<>();
        while (tables.find()) {
            mods.add(tables.group(1));
        }
        assertEquals(List.of(AdminShop.MODID), mods);
    }

    @Test
    void theClassesAreThere() throws IOException {
        // A wrong directory would make the checks above pass on nothing.
        assertFalse(mainClasses().isEmpty());
        for (Viewer viewer : List.of(JEI, EMI)) {
            assertTrue(mainClasses().stream().map(OptionalViewersTest::name).anyMatch(name -> name.startsWith(viewer.compatPackage())),
                    viewer.compatPackage());
        }
    }
}
