package com.ammonium.adminshop.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.core.registries.Registries;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that the client keeps the Offers the server synced, which the shop screen and JEI list. The client reads
 * them with no server level, so these tests pass none. Buy Offers and Sell Offers are listed separately, each with
 * its price and Permit. A new sync, such as after {@code /reload}, replaces the previous one. The goods are not
 * tested here because they are item and fluid stacks, which only a loaded world can make.
 */
class ClientRecipesTest {

    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE, id(path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AdminShop.MODID, path);
    }

    private static RecipeHolder<?> buy(String path, long price, ItemStackTemplate goods, String permit) {
        return new RecipeHolder<>(key(path), new BuyItemRecipe(price, goods, permit));
    }

    private static RecipeHolder<?> sell(String path, long price, ItemStackTemplate goods) {
        return new RecipeHolder<>(key(path), new SellItemRecipe(id(path), price, goods, "", null, 1));
    }

    /** Hands the client the recipes of every synced type, as {@code RecipesReceivedEvent} does. */
    private static void sync(RecipeHolder<?>... recipes) {
        RecipeManager.setClientRecipes(RecipeMap.create(List.of(recipes)));
    }

    private static Set<ResourceKey<Recipe<?>>> keys(List<? extends RecipeHolder<?>> holders) {
        return holders.stream().map(RecipeHolder::id).collect(Collectors.toSet());
    }

    @AfterEach
    void forgetTheSync() {
        RecipeManager.setClientRecipes(RecipeMap.EMPTY);
    }

    @Test
    void anEmptySyncListsNothing() {
        sync();
        assertTrue(RecipeManager.getAllBuyItemRecipes(null).isEmpty());
        assertTrue(RecipeManager.getAllSellItemRecipes(null).isEmpty());
        assertEquals(0, RecipeManager.getAllBuyRecipes(null).count());
        assertEquals(0, RecipeManager.getAllSellRecipes(null).count());
    }

    @Test
    void buyAndSellOffersAreListedApart() {
        sync(buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""),
                sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32)));

        assertEquals(Set.of(key("iron")), keys(RecipeManager.getAllBuyItemRecipes(null)));
        assertEquals(Set.of(key("cobble")), keys(RecipeManager.getAllSellItemRecipes(null)));
    }

    @Test
    void anOfferCarriesItsGoodsPriceAndPermit() {
        sync(buy("diamonds", 250, new ItemStackTemplate(Items.DIAMOND, 3), "gems"));

        BuyItemRecipe offer = RecipeManager.getAllBuyItemRecipes(null).getFirst().value();
        assertEquals(250, offer.getPrice());
        assertEquals("gems", offer.getPermit());
        assertEquals("adminshop.permit.gems", offer.getPermitTranslationKey());
    }

    @Test
    void aSellOfferIsNotABuyOffer() {
        sync(sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32)));

        assertTrue(RecipeManager.getAllBuyItemRecipes(null).isEmpty());
        assertTrue(RecipeManager.getShopBuyItemRecipe(null, id("cobble")).isEmpty());
        assertEquals(Set.of(key("cobble")), keys(RecipeManager.getAllSellItemRecipes(null)));
    }

    @Test
    void recipesOfOtherTypesAreLeftOut() {
        var stonecutting = new RecipeHolder<>(key("not_an_offer"), new StonecutterRecipe(
                new Recipe.CommonInfo(true), Ingredient.of(Items.STONE), new ItemStackTemplate(Items.STONE_BRICKS)));
        sync(stonecutting, buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""));

        assertEquals(Set.of(key("iron")), RecipeManager.getAllBuyRecipes(null).map(RecipeHolder::id).collect(Collectors.toSet()));
        assertEquals(0, RecipeManager.getAllSellRecipes(null).count());
        assertTrue(RecipeManager.getShopBuyItemRecipe(null, id("not_an_offer")).isEmpty());
    }

    /** After {@code /reload} the iron Offer costs more, the cobblestone Offer is gone and a gold Offer is new. */
    @Test
    void aNewSyncReplacesTheOffersOfTheLastOne() {
        sync(buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""),
                sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32)));

        sync(buy("iron", 150, new ItemStackTemplate(Items.IRON_INGOT), ""),
                buy("gold", 300, new ItemStackTemplate(Items.GOLD_INGOT), ""));

        assertEquals(Set.of(key("gold"), key("iron")), keys(RecipeManager.getAllBuyItemRecipes(null)));
        assertEquals(150, RecipeManager.getShopBuyItemRecipe(null, id("iron")).orElseThrow().getPrice());
        assertTrue(RecipeManager.getAllSellItemRecipes(null).isEmpty());
        assertTrue(RecipeManager.getShopBuyItemRecipe(null, id("gold")).isPresent());
    }

    @Test
    void fluidOffersAreListedWithTheItemOffers() {
        sync(buy("iron", 100, new ItemStackTemplate(Items.IRON_INGOT), ""),
                new RecipeHolder<>(key("water"), new BuyFluidRecipe(20, new FluidStackTemplate(Fluids.WATER, 1000), "")),
                new RecipeHolder<>(key("lava"), new SellFluidRecipe(30, new FluidStackTemplate(Fluids.LAVA, 500), "nether")),
                sell("cobble", 100, new ItemStackTemplate(Items.COBBLESTONE, 32)));

        assertEquals(Set.of(key("iron"), key("water")), RecipeManager.getAllBuyRecipes(null).map(RecipeHolder::id).collect(Collectors.toSet()));
        assertEquals(Set.of(key("cobble"), key("lava")), RecipeManager.getAllSellRecipes(null).map(RecipeHolder::id).collect(Collectors.toSet()));
        assertEquals(Set.of(key("water")), keys(RecipeManager.getAllBuyFluidRecipes(null)));
        assertEquals(Set.of(key("lava")), keys(RecipeManager.getAllSellFluidRecipes(null)));
    }

    @Test
    void aFluidOfferCarriesItsPriceAndPermit() {
        sync(new RecipeHolder<>(key("lava"), new SellFluidRecipe(30, new FluidStackTemplate(Fluids.LAVA, 500), "nether")));

        SellFluidRecipe offer = RecipeManager.getAllSellFluidRecipes(null).getFirst().value();
        assertEquals(30, offer.getPrice());
        assertEquals("nether", offer.getPermit());
        assertEquals("adminshop.permit.nether", offer.getPermitTranslationKey());
    }
}
