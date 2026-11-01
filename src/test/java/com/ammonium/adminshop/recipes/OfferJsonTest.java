package com.ammonium.adminshop.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;

/**
 * Tests that an Offer written in a datapack decodes to the Offer it describes and encodes back to the same
 * JSON. The JSON uses AdminShop's own fields ({@code price}, {@code permit}, {@code count}) around a vanilla
 * item stack, or NeoForge's fluid stack format ({@code id}, {@code amount}).
 */
class OfferJsonTest {

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static Recipe<?> decode(String text) {
        return Recipe.CODEC.parse(JsonOps.INSTANCE, json(text)).getOrThrow();
    }

    private static boolean rejects(String text) {
        return Recipe.CODEC.parse(JsonOps.INSTANCE, json(text)).isError();
    }

    private static JsonElement encode(Recipe<?> offer) {
        return Recipe.CODEC.encodeStart(JsonOps.INSTANCE, offer).getOrThrow();
    }

    /** Reads a shipped example Offer from the jar. */
    private static String shipped(String name) {
        try (InputStream in = OfferJsonTest.class.getResourceAsStream("/data/adminshop/recipe/" + name + ".json")) {
            if (in == null) {
                throw new IllegalStateException("no shipped Offer " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final String EXAMPLE_BUY = shipped("example_buy_item");
    private static final String EXAMPLE_SELL = shipped("example_sell_item");

    /** The goods of the component examples: paper named "Special Paper!" in non-italic text. */
    private static String specialPaper(String count) {
        return "{\"id\": \"minecraft:paper\", " + count
                + "\"components\": {\"minecraft:custom_name\": {\"text\": \"Special Paper!\", \"italic\": false}}}";
    }

    /**
     * The goods of a decoded Offer, as the item or fluid stack format writes them. An item stack needs a loaded
     * world, so the goods are compared in the format they were written in.
     */
    private static JsonElement goods(Recipe<?> offer, String field) {
        return encode(offer).getAsJsonObject().get(field);
    }

    private static void assertBuy(Recipe<?> decoded, long price, String goods, String permit) {
        BuyItemRecipe offer = assertInstanceOf(BuyItemRecipe.class, decoded);
        assertEquals(price, offer.getPrice());
        assertEquals(permit, offer.getPermit());
        assertEquals(json(goods), goods(offer, "result"));
    }

    private static void assertSell(Recipe<?> decoded, long price, String goods, String permit) {
        SellItemRecipe offer = assertInstanceOf(SellItemRecipe.class, decoded);
        assertEquals(SellItemRecipe.SellTypes.ITEM, offer.getSellType());
        assertEquals(price, offer.getPrice());
        assertEquals(permit, offer.getPermit());
        assertEquals(json(goods), goods(offer, "item"));
    }

    private static void assertTagSell(Recipe<?> decoded, long price, String tag, int count, String permit) {
        SellItemRecipe offer = assertInstanceOf(SellItemRecipe.class, decoded);
        assertEquals(SellItemRecipe.SellTypes.TAG, offer.getSellType());
        assertEquals(price, offer.getPrice());
        assertEquals(permit, offer.getPermit());
        assertEquals(Identifier.parse(tag), offer.getTagId());
        assertEquals(count, offer.getCount());
    }

    private static void assertFluid(Recipe<?> offer, String fluid, int amount) {
        assertEquals(json("{\"id\": \"" + fluid + "\", \"amount\": " + amount + "}"), goods(offer, "result"));
    }

    @Test
    void theExampleBuyOfferDecodes() {
        assertBuy(decode(EXAMPLE_BUY), 100, "{\"id\": \"minecraft:iron_ingot\"}", "");
    }

    @Test
    void theExampleSellOfferDecodes() {
        assertSell(decode(EXAMPLE_SELL), 100, "{\"id\": \"minecraft:cobblestone\", \"count\": 32}", "");
    }

    @Test
    void aBuyOfferEncodesBackToItsJson() {
        String full = """
                {"type": "adminshop:item_buying", "price": 250, "result": {"id": "minecraft:diamond", "count": 3}, "permit": "gems"}""";
        assertEquals(json(full), encode(decode(full)));
    }

    @Test
    void aSellOfferEncodesBackToItsJson() {
        String full = """
                {"type": "adminshop:item_selling", "price": 5, "item": {"id": "minecraft:wheat", "count": 16}, "permit": "farming"}""";
        assertEquals(json(full), encode(decode(full)));
    }

    /** The item stack format leaves out a count of 1, and an Offer with no Permit has no permit field. */
    @Test
    void anOfferWithoutAPermitEncodesWithoutOne() {
        assertEquals(json("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:iron_ingot"}}"""),
                encode(decode(EXAMPLE_BUY)));
    }

    @Test
    void theGoodsMayBeJustAnItemId() {
        assertBuy(decode("""
                {"type": "adminshop:item_buying", "price": 7, "result": "minecraft:apple"}"""), 7, "{\"id\": \"minecraft:apple\"}", "");
    }

    @Test
    void anOfferWithoutAPriceIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "result": {"id": "minecraft:iron_ingot"}}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "item": {"id": "minecraft:iron_ingot"}}"""));
    }

    @Test
    void aBuyOfferWithoutGoodsIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "price": 1}"""));
    }

    @Test
    void anOfferOfAnUnknownItemIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "price": 1, "result": {"id": "adminshop:no_such_item"}}"""));
    }

    @Test
    void anOfferOfNothingIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "price": 1, "result": {"id": "minecraft:air"}}"""));
    }

    @Test
    void theExampleTagSellOfferDecodes() {
        assertTagSell(decode(shipped("example_sell_item_tag")), 100, "minecraft:wool", 16, "");
    }

    @Test
    void theExampleComponentOffersDecode() {
        assertBuy(decode(shipped("example_buy_nbt")), 100, specialPaper(""), "");
        assertSell(decode(shipped("example_sell_nbt")), 100, specialPaper("\"count\": 4, "), "");
    }

    /** The example Buy Offer shipped with the mod's documentation loads unchanged. */
    @Test
    void theExampleBuyOfferDecodesUnchanged() {
        assertBuy(decode("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:iron_ingot", "count": 1}}"""),
                100, "{\"id\": \"minecraft:iron_ingot\"}", "");
    }

    /** The example Sell Offer shipped with the mod's documentation loads unchanged. */
    @Test
    void theExampleSellOfferDecodesUnchanged() {
        assertSell(decode("""
                {"type": "adminshop:item_selling", "price": 100, "item": {"id": "minecraft:cobblestone", "count": 32}}"""),
                100, "{\"id\": \"minecraft:cobblestone\", \"count\": 32}", "");
    }

    /** The example tag Sell Offer shipped with the mod's documentation loads unchanged. */
    @Test
    void theExampleTagSellOfferDecodesUnchanged() {
        assertTagSell(decode("""
                {"type": "adminshop:item_selling", "price": 100, "tag": "minecraft:wool", "count": 16}"""),
                100, "minecraft:wool", 16, "");
    }

    /** The example Offer with a Permit shipped with the mod's documentation loads unchanged. */
    @Test
    void theExamplePermitOfferDecodesUnchanged() {
        assertBuy(decode("""
                {"type": "adminshop:item_buying", "price": 100, "permit": "gems", "result": {"id": "minecraft:diamond", "count": 16}}"""),
                100, "{\"id\": \"minecraft:diamond\", \"count\": 16}", "gems");
    }

    /**
     * A custom name written as a JSON string, as in the example shipped with the mod's documentation, is read
     * as plain text. The Offer still loads, but the item's name is the JSON itself, which writes back as a string.
     */
    @Test
    void aNameWrittenAsAJsonStringIsReadAsPlainText() {
        var offer = (BuyItemRecipe) decode("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:paper", "count": 1,
                 "components": {"minecraft:custom_name": "{\\"text\\":\\"Special Paper!\\",\\"italic\\":false}"}}}""");
        JsonElement name = goods(offer, "result").getAsJsonObject().getAsJsonObject("components").get("minecraft:custom_name");
        assertEquals(new JsonPrimitive("{\"text\":\"Special Paper!\",\"italic\":false}"), name);
    }

    @Test
    void aTagSellOfferEncodesBackToItsJson() {
        String full = """
                {"type": "adminshop:item_selling", "price": 3, "tag": "minecraft:logs", "count": 8, "permit": "forestry"}""";
        assertEquals(json(full), encode(decode(full)));
    }

    @Test
    void aTagSellOfferTakesOneItemWithoutACount() {
        String noCount = """
                {"type": "adminshop:item_selling", "price": 2, "tag": "minecraft:planks"}""";
        assertTagSell(decode(noCount), 2, "minecraft:planks", 1, "");
        assertEquals(json(noCount), encode(decode(noCount)));
    }

    /**
     * Known bug (adamico/AdminShop#20): a tag Sell Offer with a {@code count} of 0 or less loads, and then takes no
     * items for its price. It should be rejected at load.
     */
    @Test
    void aTagSellOfferWithoutAPositiveCountLoads() {
        assertTagSell(decode("""
                {"type": "adminshop:item_selling", "price": 1, "tag": "minecraft:wool", "count": 0}"""),
                1, "minecraft:wool", 0, "");
        assertTagSell(decode("""
                {"type": "adminshop:item_selling", "price": 1, "tag": "minecraft:wool", "count": -4}"""),
                1, "minecraft:wool", -4, "");
    }

    @Test
    void aTagSellOfferWithoutAPriceIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "tag": "minecraft:wool", "count": 4}"""));
    }

    @Test
    void aTagSellOfferWithAMalformedTagIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1, "tag": "Not A Tag!"}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1, "tag": "#minecraft:wool"}"""));
    }

    @Test
    void aSellOfferOfAMalformedItemIsRejectedEvenWithATag() {
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1, "item": {"id": "adminshop:no_such_item"}, "tag": "minecraft:wool"}"""));
    }

    /** A fluid Offer's goods are a fluid stack in {@code result}, with its amount in millibuckets. */
    @Test
    void theExampleFluidOffersDecode() {
        var water = assertInstanceOf(BuyFluidRecipe.class, decode(shipped("example_buy_fluid")));
        assertEquals(100, water.getPrice());
        assertEquals("", water.getPermit());
        assertFluid(water, "minecraft:water", 1000);
        var lava = assertInstanceOf(SellFluidRecipe.class, decode(shipped("example_sell_fluid")));
        assertEquals(100, lava.getPrice());
        assertEquals("", lava.getPermit());
        assertFluid(lava, "minecraft:lava", 1000);
    }

    /** The example fluid Offers shipped with the mod's documentation load unchanged. */
    @Test
    void theExampleFluidOffersDecodeUnchanged() {
        var water = assertInstanceOf(BuyFluidRecipe.class, decode("""
                {"type": "adminshop:fluid_buying", "price": 100, "result": {"id": "minecraft:water", "amount": 1000}}"""));
        assertEquals(100, water.getPrice());
        assertFluid(water, "minecraft:water", 1000);
        var lava = assertInstanceOf(SellFluidRecipe.class, decode("""
                {"type": "adminshop:fluid_selling", "price": 100, "result": {"id": "minecraft:lava", "amount": 1000}}"""));
        assertEquals(100, lava.getPrice());
        assertFluid(lava, "minecraft:lava", 1000);
    }

    @Test
    void aFluidBuyOfferEncodesBackToItsJson() {
        String full = """
                {"type": "adminshop:fluid_buying", "price": 40, "result": {"id": "minecraft:lava", "amount": 250}, "permit": "nether"}""";
        assertEquals(json(full), encode(decode(full)));
    }

    @Test
    void aFluidSellOfferEncodesBackToItsJson() {
        String full = """
                {"type": "adminshop:fluid_selling", "price": 3, "result": {"id": "minecraft:water", "amount": 500}, "permit": "farming"}""";
        assertEquals(json(full), encode(decode(full)));
    }

    @Test
    void aFluidMayBeJustAFluidIdForABucketsWorth() {
        var offer = assertInstanceOf(BuyFluidRecipe.class, decode("""
                {"type": "adminshop:fluid_buying", "price": 7, "result": "minecraft:water"}"""));
        assertFluid(offer, "minecraft:water", 1000);
    }

    /** A fluid Offer is rejected if it has no price, no fluid, an unknown fluid, the empty fluid or a zero amount. */
    @Test
    void aMalformedFluidOfferIsRejected() {
        for (String type : new String[] {"adminshop:fluid_buying", "adminshop:fluid_selling"}) {
            assertTrue(rejects("{\"type\": \"" + type + "\", \"result\": {\"id\": \"minecraft:water\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1, \"result\": {\"id\": \"adminshop:no_such_fluid\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1, \"result\": {\"id\": \"minecraft:empty\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1, \"result\": {\"id\": \"minecraft:water\", \"amount\": 0}}"), type);
        }
    }
}
