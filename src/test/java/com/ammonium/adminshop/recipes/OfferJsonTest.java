package com.ammonium.adminshop.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import org.junit.jupiter.api.Test;

/**
 * An item Offer written in a datapack decodes to the Offer it describes and encodes back to the same
 * JSON. The JSON uses AdminShop's own 1.21.1 fields ({@code price}, {@code permit}, {@code count})
 * around 26.1's vanilla item stack format.
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

    /** A shipped example Offer, as the jar holds it. */
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

    @Test
    void theExampleBuyOfferDecodes() {
        assertEquals(new BuyItemRecipe(100, new ItemStackTemplate(Items.IRON_INGOT), ""), decode(EXAMPLE_BUY));
    }

    @Test
    void theExampleSellOfferDecodes() {
        assertEquals(new SellItemRecipe(100, new ItemStackTemplate(Items.COBBLESTONE, 32), ""), decode(EXAMPLE_SELL));
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

    @Test
    void anOfferWithoutAPermitEncodesWithoutOne() {
        // 26.1's item stack format leaves a count of 1 out, and an Offer with no Permit has no permit field.
        assertEquals(json("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:iron_ingot"}}"""),
                encode(decode(EXAMPLE_BUY)));
    }

    @Test
    void theGoodsMayBeJustAnItemId() {
        assertEquals(new BuyItemRecipe(7, new ItemStackTemplate(Items.APPLE), ""), decode("""
                {"type": "adminshop:item_buying", "price": 7, "result": "minecraft:apple"}"""));
    }

    @Test
    void anOfferWithoutAPriceIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "result": {"id": "minecraft:iron_ingot"}}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "item": {"id": "minecraft:iron_ingot"}}"""));
    }

    @Test
    void anOfferWithANegativePriceIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "price": -1, "result": {"id": "minecraft:iron_ingot"}}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": -1, "item": {"id": "minecraft:iron_ingot"}}"""));
    }

    @Test
    void anOfferWithoutGoodsIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_buying", "price": 1}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1}"""));
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
}
