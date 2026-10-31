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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
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

    /** The goods of the component examples, which are paper named "Special Paper!" in non-italic text. */
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

    /** The item stack format leaves out a count of 1, and an Offer with no Permit has no permit field. */
    @Test
    void anOfferWithoutAPermitEncodesWithoutOne() {
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

    /** The example Buy Offer shipped with the mod's documentation loads unchanged. */
    @Test
    void theExampleBuyOfferDecodesUnchanged() {
        assertEquals(new BuyItemRecipe(100, new ItemStackTemplate(Items.IRON_INGOT), ""), decode("""
                {"type": "adminshop:item_buying", "price": 100, "result": {"id": "minecraft:iron_ingot", "count": 1}}"""));
    }

    /** The example Sell Offer shipped with the mod's documentation loads unchanged. */
    @Test
    void theExampleSellOfferDecodesUnchanged() {
        assertEquals(new SellItemRecipe(100, new ItemStackTemplate(Items.COBBLESTONE, 32), ""), decode("""
                {"type": "adminshop:item_selling", "price": 100, "item": {"id": "minecraft:cobblestone", "count": 32}}"""));
    }

    /** The example tag Sell Offer shipped with the mod's documentation loads unchanged. */
    @Test
    void theExampleTagSellOfferDecodesUnchanged() {
        assertEquals(new SellItemRecipe(100, ItemTags.WOOL, 16, ""), decode("""
                {"type": "adminshop:item_selling", "price": 100, "tag": "minecraft:wool", "count": 16}"""));
    }

    /** The example Offer with a Permit shipped with the mod's documentation loads unchanged. */
    @Test
    void theExamplePermitOfferDecodesUnchanged() {
        assertEquals(new BuyItemRecipe(100, new ItemStackTemplate(Items.DIAMOND, 16), "gems"), decode("""
                {"type": "adminshop:item_buying", "price": 100, "permit": "gems", "result": {"id": "minecraft:diamond", "count": 16}}"""));
    }

    /**
     * A custom name written as a JSON string, as in the example shipped with the mod's documentation, is read
     * as plain text. The Offer still loads, but the item's name is the JSON itself. The changelog's migration
     * section documents this.
     */
    @Test
    void aNameWrittenAsAJsonStringIsReadAsPlainText() {
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

    /** A fluid Offer's goods are a fluid stack in {@code result}, with its amount in millibuckets. */
    @Test
    void theExampleFluidOffersDecode() {
        assertEquals(new BuyFluidRecipe(100, new FluidStackTemplate(Fluids.WATER, 1000), ""), decode(shipped("example_buy_fluid")));
        assertEquals(new SellFluidRecipe(100, new FluidStackTemplate(Fluids.LAVA, 1000), ""), decode(shipped("example_sell_fluid")));
    }

    /** The example fluid Offers shipped with the mod's documentation load unchanged. */
    @Test
    void theExampleFluidOffersDecodeUnchanged() {
        assertEquals(new BuyFluidRecipe(100, new FluidStackTemplate(Fluids.WATER, 1000), ""), decode("""
                {"type": "adminshop:fluid_buying", "price": 100, "result": {"id": "minecraft:water", "amount": 1000}}"""));
        assertEquals(new SellFluidRecipe(100, new FluidStackTemplate(Fluids.LAVA, 1000), ""), decode("""
                {"type": "adminshop:fluid_selling", "price": 100, "result": {"id": "minecraft:lava", "amount": 1000}}"""));
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
        assertEquals(new BuyFluidRecipe(7, new FluidStackTemplate(Fluids.WATER, 1000), ""), decode("""
                {"type": "adminshop:fluid_buying", "price": 7, "result": "minecraft:water"}"""));
    }

    /**
     * A fluid Offer is rejected if it has no price, a negative price, no fluid, an unknown fluid, the empty
     * fluid or a zero amount.
     */
    @Test
    void aMalformedFluidOfferIsRejected() {
        for (String type : new String[] {"adminshop:fluid_buying", "adminshop:fluid_selling"}) {
            assertTrue(rejects("{\"type\": \"" + type + "\", \"result\": {\"id\": \"minecraft:water\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": -1, \"result\": {\"id\": \"minecraft:water\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1, \"result\": {\"id\": \"adminshop:no_such_fluid\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1, \"result\": {\"id\": \"minecraft:empty\", \"amount\": 1000}}"), type);
            assertTrue(rejects("{\"type\": \"" + type + "\", \"price\": 1, \"result\": {\"id\": \"minecraft:water\", \"amount\": 0}}"), type);
        }
    }
}
