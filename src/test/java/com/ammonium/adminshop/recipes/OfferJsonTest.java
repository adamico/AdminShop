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
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
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

    /** The goods of the components examples: paper named "Special Paper!", not in italics. */
    private static final DataComponentPatch SPECIAL_PAPER = DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_NAME, Component.literal("Special Paper!").withStyle(style -> style.withItalic(false)))
            .build();

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

    @Test
    void theExampleTagSellOfferDecodes() {
        assertEquals(new SellItemRecipe(100, ItemTags.WOOL, 16, ""), decode(shipped("example_sell_item_tag")));
    }

    @Test
    void theExampleComponentOffersDecode() {
        assertEquals(new BuyItemRecipe(100, new ItemStackTemplate(Items.PAPER, SPECIAL_PAPER), ""),
                decode(shipped("example_buy_nbt")));
        assertEquals(new SellItemRecipe(100, new ItemStackTemplate(Items.PAPER, 4, SPECIAL_PAPER), ""),
                decode(shipped("example_sell_nbt")));
    }

    // The 1.21.1 example Offers that use only AdminShop's own fields, verbatim: they load unchanged.

    @Test
    void the1211ExampleBuyOfferDecodesUnchanged() {
        assertEquals(new BuyItemRecipe(100, new ItemStackTemplate(Items.IRON_INGOT), ""), decode("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:iron_ingot", "count": 1}}"""));
    }

    @Test
    void the1211ExampleSellOfferDecodesUnchanged() {
        assertEquals(new SellItemRecipe(100, new ItemStackTemplate(Items.COBBLESTONE, 32), ""), decode("""
                {"type": "adminshop:item_selling", "price": 100, "item": {"id": "minecraft:cobblestone", "count": 32}}"""));
    }

    @Test
    void the1211ExampleTagSellOfferDecodesUnchanged() {
        assertEquals(new SellItemRecipe(100, ItemTags.WOOL, 16, ""), decode("""
                {"type": "adminshop:item_selling", "price": 100, "tag": "minecraft:wool", "count": 16}"""));
    }

    @Test
    void the1211ExamplePermitOfferDecodesUnchanged() {
        assertEquals(new BuyItemRecipe(100, new ItemStackTemplate(Items.DIAMOND, 16), "gems"), decode("""
                {"type": "adminshop:item_buying", "price": 100, "permit": "gems", "result": {"id": "minecraft:diamond", "count": 16}}"""));
    }

    @Test
    void a1211NameWrittenAsAJsonStringIsReadAsPlainText() {
        // 1.21.5 dropped text components written as JSON strings: the 1.21.1 example still loads, but
        // the name it gives is the JSON itself. The changelog's migration section says so.
        var offer = (BuyItemRecipe) decode("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:paper", "count": 1,
                 "components": {"minecraft:custom_name": "{\\"text\\":\\"Special Paper!\\",\\"italic\\":false}"}}}""");
        assertEquals(Component.literal("{\"text\":\"Special Paper!\",\"italic\":false}"),
                offer.result().components().get(DataComponentMap.EMPTY, DataComponents.CUSTOM_NAME));
    }

    @Test
    void aTagSellOfferEncodesBackToItsJson() {
        String full = """
                {"type": "adminshop:item_selling", "price": 3, "tag": "minecraft:logs", "count": 8, "permit": "forestry"}""";
        assertEquals(json(full), encode(decode(full)));
    }

    @Test
    void aTagSellOfferTakesOneItemWithoutACount() {
        assertEquals(new SellItemRecipe(2, ItemTags.PLANKS, 1, ""), decode("""
                {"type": "adminshop:item_selling", "price": 2, "tag": "minecraft:planks"}"""));
        assertEquals(json("""
                {"type": "adminshop:item_selling", "price": 2, "tag": "minecraft:planks"}"""),
                encode(new SellItemRecipe(2, ItemTags.PLANKS, 1, "")));
    }

    @Test
    void aSellOfferWithBothAnItemAndATagIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1, "item": "minecraft:white_wool", "tag": "minecraft:wool"}"""));
    }

    @Test
    void aTagSellOfferWithoutAPositiveCountIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1, "tag": "minecraft:wool", "count": 0}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": 1, "tag": "minecraft:wool", "count": -4}"""));
    }

    @Test
    void aTagSellOfferWithoutAPriceIsRejected() {
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "tag": "minecraft:wool", "count": 4}"""));
        assertTrue(rejects("""
                {"type": "adminshop:item_selling", "price": -1, "tag": "minecraft:wool", "count": 4}"""));
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
}
