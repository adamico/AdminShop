package com.ammonium.adminshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * The Econ Handbook's files, as the jar holds them, are what Patchouli 26.1 reads: strict JSON, and every
 * item, texture, model, category and translation they name exists. Patchouli itself is absent here, as it
 * is from the game tests, so what it ships (the guide book item, its book textures) is not checked.
 */
class EconHandbookTest {

    private static final Identifier BOOK = Identifier.fromNamespaceAndPath(AdminShop.MODID, "econ_handbook");
    private static final Set<String> PAGE_TYPES = Set.of("patchouli:text", "patchouli:image");

    /** AdminShop's resources' root, as the test classpath holds it. */
    private static Path root() {
        try {
            // A file only AdminShop has: other mods' resources share the classpath.
            Path book = Path.of(EconHandbookTest.class.getResource(
                    "/data/" + BOOK.getNamespace() + "/patchouli_books/" + BOOK.getPath() + "/book.json").toURI());
            return book.getParent().getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path asset(String path) {
        return root().resolve("assets/" + AdminShop.MODID + "/" + path);
    }

    /** A resource under {@code assets/<namespace>/<folder>/}, or {@code null} for another mod's. */
    private static Path asset(Identifier id, String folder, String extension) {
        if (!id.getNamespace().equals(AdminShop.MODID)) {
            return null;
        }
        return asset(folder + id.getPath() + extension);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses strict JSON, refusing a key given twice in one object: Gson would keep the last silently. */
    private static JsonObject json(Path file) {
        String text = read(file);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            checkKeys(reader, file);
            assertEquals(JsonToken.END_DOCUMENT, reader.peek(), file + " has content after its JSON");
        } catch (IOException e) {
            fail(file + " is not valid JSON: " + e.getMessage());
        }
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static void checkKeys(JsonReader reader, Path file) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                Set<String> keys = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertTrue(keys.add(key), file + " gives \"" + key + "\" twice");
                    checkKeys(reader, file);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    checkKeys(reader, file);
                }
                reader.endArray();
            }
            default -> reader.skipValue();
        }
    }

    private static List<Path> jsonFiles(Path folder) {
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(".json")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Identifier id(JsonObject json, String key) {
        return Identifier.parse(json.get(key).getAsString());
    }

    private static void assertItem(Identifier item, Path file) {
        assertTrue(BuiltInRegistries.ITEM.containsKey(item), file + " names the unknown item " + item);
    }

    private static void assertExists(Path file, String what) {
        assertTrue(Files.isRegularFile(file), what + " " + file + " is missing");
    }

    private static JsonObject book() {
        return json(root().resolve("data/" + BOOK.getNamespace() + "/patchouli_books/" + BOOK.getPath() + "/book.json"));
    }

    private static Path contents() {
        return asset("patchouli_books/" + BOOK.getPath() + "/en_us");
    }

    @Test
    void everyBookFileIsStrictJson() {
        book();
        List<Path> files = jsonFiles(contents());
        assertFalse(files.isEmpty(), "the handbook has no categories or entries");
        files.forEach(EconHandbookTest::json);
    }

    @Test
    void theBookNamesItsTranslationModelAndCreativeTab() {
        JsonObject book = book();
        assertTrue(book.get("use_resource_pack").getAsBoolean(), "Patchouli 1.20+ reads book contents only from assets");
        JsonObject lang = json(asset("lang/en_us.json"));
        assertTrue(lang.has(book.get("name").getAsString()), "the book's name has no translation");
        assertTrue(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(id(book, "creative_tab")), "the book's creative tab is unknown");

        // Patchouli renders the book with the client item model it names, from assets/<namespace>/items/.
        Path definition = asset(id(book, "model"), "items/", ".json");
        assertExists(definition, "the book's item model");
        Path model = asset(id(json(definition).getAsJsonObject("model"), "model"), "models/", ".json");
        assertExists(model, "the book's model");
        Path texture = asset(id(json(model).getAsJsonObject("textures"), "layer0"), "textures/", ".png");
        assertExists(texture, "the book's texture");
    }

    @Test
    void everyCategoryAndEntryNamesWhatExists() {
        Path categories = contents().resolve("categories");
        for (Path category : jsonFiles(categories)) {
            JsonObject json = json(category);
            assertTrue(json.has("name") && json.has("description"), category + " has no name or description");
            assertItem(id(json, "icon"), category);
        }
        for (Path entry : jsonFiles(contents().resolve("entries"))) {
            JsonObject json = json(entry);
            assertTrue(json.has("name"), entry + " has no name");
            assertItem(id(json, "icon"), entry);
            Path category = asset(id(json, "category"), "patchouli_books/" + BOOK.getPath() + "/en_us/categories/", ".json");
            assertExists(category, entry + "'s category");
            JsonArray pages = json.getAsJsonArray("pages");
            assertFalse(pages.isEmpty(), entry + " has no pages");
            for (JsonElement element : pages) {
                JsonObject page = element.getAsJsonObject();
                String type = page.get("type").getAsString();
                assertTrue(PAGE_TYPES.contains(type), entry + " has a page of unexpected type " + type);
                if (type.equals("patchouli:image")) {
                    for (JsonElement image : page.getAsJsonArray("images")) {
                        Identifier texture = Identifier.parse(image.getAsString());
                        assertExists(asset(texture, "", ""), entry + "'s image");
                    }
                }
            }
        }
    }

    @Test
    void theRecipeNeedsPatchouliAndMakesThisBook() {
        Path file = root().resolve("data/" + AdminShop.MODID + "/recipe/" + BOOK.getPath() + ".json");
        JsonObject recipe = json(file);
        JsonObject condition = recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
        assertEquals("neoforge:mod_loaded", condition.get("type").getAsString());
        assertEquals("patchouli", condition.get("modid").getAsString());

        for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
            assertItem(Identifier.parse(ingredient.getAsString()), file);
        }
        JsonObject result = recipe.getAsJsonObject("result");
        assertEquals("patchouli:guide_book", result.get("id").getAsString());
        // Patchouli reads the book from its patchouli:book component, not from custom_data.
        assertEquals(BOOK.toString(), result.getAsJsonObject("components").get("patchouli:book").getAsString());
    }

    @Test
    void theModsTomlDeclaresPatchouliOptional() {
        String toml = read(root().resolve("META-INF/neoforge.mods.toml"));
        int at = toml.indexOf("modId=\"patchouli\"");
        assertTrue(at >= 0, "neoforge.mods.toml doesn't name Patchouli");
        int next = toml.indexOf("[[", at);
        String entry = toml.substring(at, next < 0 ? toml.length() : next);
        assertTrue(entry.contains("type=\"optional\""), "Patchouli is not optional: " + entry);
    }
}
