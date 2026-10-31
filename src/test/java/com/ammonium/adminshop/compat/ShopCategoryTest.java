package com.ammonium.adminshop.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.recipes.BuyFluidRecipe;
import com.ammonium.adminshop.recipes.BuyItemRecipe;
import com.ammonium.adminshop.recipes.SellFluidRecipe;
import com.ammonium.adminshop.recipes.SellItemRecipe;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopCache;
import com.ammonium.adminshop.shop.ShopDisplay;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import org.junit.jupiter.api.Test;

/**
 * Tests the Shop Buy and Shop Sell categories that the recipe viewers show. Each lists its kind of Offer from
 * the shop display layer, items and fluids alike, in the shop screen's order. Each names the Offer's Permit
 * as the shop screen does and has the Shop, the Shop Tablet and the matching machines as catalysts. The price
 * line and the drawing need the client, so the client checklist covers them.
 */
class ShopCategoryTest {

    private static ResourceKey<Recipe<?>> key(String path) {
        return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(AdminShop.MODID, path));
    }

    private static ShopDisplay synced(RecipeHolder<?>... recipes) {
        var cache = new ShopCache();
        cache.replace(RecipeMap.create(List.of(recipes)));
        return cache.display();
    }

    private static final ShopDisplay SHOP = synced(
            new RecipeHolder<>(key("iron"), new BuyItemRecipe(100, new ItemStackTemplate(Items.IRON_INGOT), "")),
            new RecipeHolder<>(key("water"), new BuyFluidRecipe(20, new FluidStackTemplate(Fluids.WATER, 1000), "")),
            new RecipeHolder<>(key("cobble"), new SellItemRecipe(5, new ItemStackTemplate(Items.COBBLESTONE, 32), "")),
            new RecipeHolder<>(key("lava"), new SellFluidRecipe(30, new FluidStackTemplate(Fluids.LAVA, 500), "nether")));

    private static List<ResourceKey<Recipe<?>>> keys(List<OfferDisplay> offers) {
        return offers.stream().map(OfferDisplay::offer).toList();
    }

    private static List<Item> items(List<ItemLike> items) {
        return items.stream().map(ItemLike::asItem).toList();
    }

    @Test
    void shopBuyListsTheBuyOffersItemsAndFluids() {
        assertTrue(ShopCategory.BUY.isBuy());
        assertEquals(List.of(key("iron"), key("water")), keys(ShopCategory.BUY.offers(SHOP)));
    }

    @Test
    void shopSellListsTheSellOffersItemsAndFluids() {
        assertFalse(ShopCategory.SELL.isBuy());
        assertEquals(List.of(key("cobble"), key("lava")), keys(ShopCategory.SELL.offers(SHOP)));
    }

    /** After {@code /reload} the cache holds a new display, which the viewers read when they restart. */
    @Test
    void theCategoriesListWhatTheLatestSyncHolds() {
        var reloaded = synced(new RecipeHolder<>(key("gold"), new BuyItemRecipe(300, new ItemStackTemplate(Items.GOLD_INGOT), "")));
        assertEquals(List.of(key("gold")), keys(ShopCategory.BUY.offers(reloaded)));
        assertTrue(ShopCategory.SELL.offers(reloaded).isEmpty());
    }

    @Test
    void anOfferThatNeedsAPermitNamesIt() {
        OfferDisplay lava = ShopCategory.SELL.offers(SHOP).get(1);
        Component line = ShopCategory.SELL.permit(lava).orElseThrow();
        var contents = assertInstanceOf(TranslatableContents.class, line.getContents());
        assertEquals("jei.requires_permit", contents.getKey());
        assertEquals(lava.permitName().orElseThrow(), contents.getArgs()[0]);
    }

    @Test
    void anOfferThatNeedsNoPermitNamesNone() {
        assertEquals(Optional.empty(), ShopCategory.BUY.permit(ShopCategory.BUY.offers(SHOP).getFirst()));
    }

    @Test
    void theCategoriesHaveTheirTitlesAndTextures() {
        assertEquals("jei.category.buy.title", ((TranslatableContents) ShopCategory.BUY.title().getContents()).getKey());
        assertEquals("jei.category.sell.title", ((TranslatableContents) ShopCategory.SELL.title().getContents()).getKey());
        for (ShopCategory category : ShopCategory.values()) {
            String path = "assets/" + category.background().getNamespace() + "/" + category.background().getPath();
            assertNotNull(ShopCategory.class.getClassLoader().getResource(path), path);
        }
    }

    @Test
    void theShopTheTabletAndTheBuyersBuy() {
        assertEquals(List.of(ModBlocks.SHOP.get().asItem(), ModItems.TABLET.get(), ModBlocks.BUYER_1.get().asItem(),
                ModBlocks.BUYER_2.get().asItem(), ModBlocks.BUYER_3.get().asItem()), items(ShopCategory.BUY.catalysts()));
        assertEquals(ModBlocks.BUYER_1.get().asItem(), ShopCategory.BUY.icon().asItem());
    }

    @Test
    void theShopTheTabletAndTheSellerSell() {
        assertEquals(List.of(ModBlocks.SHOP.get().asItem(), ModItems.TABLET.get(), ModBlocks.SELLER.get().asItem()),
                items(ShopCategory.SELL.catalysts()));
        assertEquals(ModBlocks.SELLER.get().asItem(), ShopCategory.SELL.icon().asItem());
    }
}
