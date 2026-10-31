package com.ammonium.adminshop.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.SellFluidRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import org.junit.jupiter.api.Test;

/**
 * The client shop cache keeps the Offers the server synced and the shop display layer turns them into
 * what the shop screen (and later JEI and EMI) list: Buy Offers and Sell Offers apart, each with its
 * goods, price and Permit. A new sync, as after {@code /reload}, replaces what an earlier one left.
 * The goods' names and the search read item components, which only a loaded world has: the shop screen
 * shows those.
 */
class ShopDisplayTest {

    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(AdminShop.MODID, path));
    }

    private static RecipeHolder<?> buy(String path, long price, ItemStackTemplate goods, String permit) {
        return new RecipeHolder<>(key(path), new BuyItemRecipe(price, goods, permit));
    }

    private static RecipeHolder<?> sell(String path, long price, ItemStackTemplate goods) {
        return new RecipeHolder<>(key(path), new SellItemRecipe(price, goods, ""));
    }

    /** What the client receives: a recipe map of every synced type, as {@code RecipesReceivedEvent} hands it over. */
    private static RecipeMap synced(RecipeHolder<?>... recipes) {
        return RecipeMap.create(List.of(recipes));
    }

    private static List<ResourceKey<Recipe<?>>> offers(List<OfferDisplay> displays) {
        return displays.stream().map(OfferDisplay::offer).toList();
    }

    @Test
    void nothingIsListedBeforeTheFirstSync() {
        var cache = new ShopCache();
        assertTrue(cache.display().buyOffers().isEmpty());
        assertTrue(cache.display().sellOffers().isEmpty());
    }

    @Test
    void buyAndSellOffersAreListedApart() {
        var cache = new ShopCache();
        cache.replace(synced(
                buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""),
                sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32))));

        assertEquals(List.of(key("iron")), offers(cache.display().buyOffers()));
        assertEquals(List.of(key("cobble")), offers(cache.display().sellOffers()));
    }

    @Test
    void aDisplayCarriesTheOffersGoodsPriceAndPermit() {
        var cache = new ShopCache();
        cache.replace(synced(buy("diamonds", 250, new ItemStackTemplate(Items.DIAMOND, 3), "gems")));

        OfferDisplay display = cache.display().buyOffers().getFirst();
        assertTrue(display.isBuy());
        assertEquals(250, display.price());
        assertEquals("gems", display.permit());
        ShopGoods.Items goods = (ShopGoods.Items) display.goods();
        assertEquals(1, goods.stacks().size());
        assertEquals(new ItemStackTemplate(Items.DIAMOND, 3), goods.stacks().getFirst());
        assertEquals(3, display.count());
    }

    @Test
    void aDisplayNamesThePermitTheOfferNeeds() {
        var cache = new ShopCache();
        cache.replace(synced(buy("diamonds", 250, new ItemStackTemplate(Items.DIAMOND, 3), "gems")));

        Component name = cache.display().buyOffers().getFirst().permitName().orElseThrow();
        // Translated from adminshop.permit.<permit>; an untranslated Permit shows as itself.
        var contents = assertInstanceOf(TranslatableContents.class, name.getContents());
        assertEquals("adminshop.permit.gems", contents.getKey());
        assertEquals("gems", contents.getFallback());
    }

    @Test
    void aDisplayNamesNoPermitForAnOfferThatNeedsNone() {
        var cache = new ShopCache();
        cache.replace(synced(
                buy("free", 10, new ItemStackTemplate(Items.APPLE), ""),
                // 1.21.1's shop screen showed no Permit "0" either.
                buy("zero", 10, new ItemStackTemplate(Items.BREAD), "0")));

        for (OfferDisplay display : cache.display().buyOffers()) {
            assertEquals(Optional.empty(), display.permitName(), display.offer().toString());
        }
    }

    @Test
    void aSellOfferIsNotABuyOffer() {
        var cache = new ShopCache();
        cache.replace(synced(sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32))));

        OfferDisplay display = cache.display().sellOffers().getFirst();
        assertTrue(!display.isBuy());
        assertEquals(32, display.count());
    }

    @Test
    void recipesOfOtherTypesAreLeftOut() {
        var cache = new ShopCache();
        var stonecutting = new RecipeHolder<>(key("not_an_offer"), new StonecutterRecipe(
                new Recipe.CommonInfo(true), Ingredient.of(Items.STONE), new ItemStackTemplate(Items.STONE_BRICKS)));
        cache.replace(synced(stonecutting, buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), "")));

        assertEquals(List.of(key("iron")), offers(cache.display().buyOffers()));
        assertTrue(cache.display().sellOffers().isEmpty());
        assertTrue(cache.offer(key("not_an_offer")).isEmpty());
    }

    @Test
    void aNewSyncReplacesTheOffersOfTheLastOne() {
        var cache = new ShopCache();
        cache.replace(synced(
                buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""),
                sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32))));

        // After /reload the iron Offer costs more, the cobblestone Offer is gone and a gold Offer is new.
        cache.replace(synced(
                buy("iron", 150, new ItemStackTemplate(Items.IRON_INGOT), ""),
                buy("gold", 300, new ItemStackTemplate(Items.GOLD_INGOT), "")));

        assertEquals(List.of(key("gold"), key("iron")), offers(cache.display().buyOffers()));
        assertEquals(150, cache.display().buyOffers().get(1).price());
        assertTrue(cache.display().sellOffers().isEmpty());
        assertTrue(cache.offer(key("cobble")).isEmpty());
        assertTrue(cache.offer(key("gold")).isPresent());
    }

    @Test
    void leavingTheServerForgetsTheOffers() {
        var cache = new ShopCache();
        cache.replace(synced(buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), "")));

        cache.clear();

        assertTrue(cache.display().buyOffers().isEmpty());
        assertTrue(cache.offer(key("iron")).isEmpty());
    }

    @Test
    void offersAreSortedByIdWithNumberedIdsInNumericOrder() {
        var cache = new ShopCache();
        var iron = new ItemStackTemplate(Items.IRON_INGOT);
        cache.replace(synced(
                buy("ore10", 1, iron, ""), buy("ore2", 1, iron, ""), buy("apple", 1, iron, ""), buy("ore", 1, iron, ""),
                buy("ore1", 1, iron, "")));

        assertEquals(List.of(key("apple"), key("ore"), key("ore1"), key("ore2"), key("ore10")),
                offers(cache.display().buyOffers()));
    }

    @Test
    void fluidOffersAreListedWithTheItemOffers() {
        var cache = new ShopCache();
        cache.replace(synced(
                buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""),
                new RecipeHolder<>(key("water"), new BuyFluidRecipe(20, new FluidStackTemplate(Fluids.WATER, 1000), "")),
                new RecipeHolder<>(key("lava"), new SellFluidRecipe(30, new FluidStackTemplate(Fluids.LAVA, 500), "nether")),
                sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32))));

        assertEquals(List.of(key("iron"), key("water")), offers(cache.display().buyOffers()));
        assertEquals(List.of(key("cobble"), key("lava")), offers(cache.display().sellOffers()));
    }

    @Test
    void aFluidDisplayCarriesTheFluidItsAmountPriceAndPermit() {
        var cache = new ShopCache();
        cache.replace(synced(new RecipeHolder<>(key("lava"), new SellFluidRecipe(30, new FluidStackTemplate(Fluids.LAVA, 500), "nether"))));

        OfferDisplay display = cache.display().sellOffers().getFirst();
        assertTrue(!display.isBuy());
        assertEquals(30, display.price());
        assertEquals("nether", display.permit());
        ShopGoods.Fluid goods = assertInstanceOf(ShopGoods.Fluid.class, display.goods());
        assertEquals(new FluidStackTemplate(Fluids.LAVA, 500), goods.fluid());
        // In millibuckets.
        assertEquals(500, display.count());
    }
}
