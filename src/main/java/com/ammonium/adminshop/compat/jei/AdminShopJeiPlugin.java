package com.ammonium.adminshop.compat.jei;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.compat.ShopCategory;
import com.ammonium.adminshop.shop.OfferDisplay;
import com.ammonium.adminshop.shop.ShopCache;
import com.ammonium.adminshop.shop.ShopDisplay;
import java.util.EnumMap;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

/**
 * Adds the Shop's Offers to JEI as the Shop Buy and Shop Sell categories. JEI finds this class by its annotation and
 * loads it only when JEI is installed. No code outside this package uses JEI types, so AdminShop runs without JEI.
 *
 * <p>The Offers come from the client shop cache, never from a recipe manager. JEI restarts on each recipe
 * sync, at login and after every {@code /reload}. It listens at the lowest priority, so the cache already holds the
 * synced Offers when {@link #registerRecipes} reads them.
 */
@JeiPlugin
public final class AdminShopJeiPlugin implements IModPlugin {

    /** The JEI recipe type of each category. */
    private static final Map<ShopCategory, IRecipeType<OfferDisplay>> TYPES = new EnumMap<>(Map.of(
            ShopCategory.BUY, IRecipeType.create(AdminShop.MODID, "jei_buy_recipe", OfferDisplay.class),
            ShopCategory.SELL, IRecipeType.create(AdminShop.MODID, "jei_sell_recipe", OfferDisplay.class)));

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(AdminShop.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        TYPES.forEach((category, type) -> registration.addRecipeCategories(new ShopJeiCategory(category, type, guiHelper)));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ShopDisplay display = ShopCache.CLIENT.display();
        TYPES.forEach((category, type) -> registration.addRecipes(type, category.offers(display)));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        TYPES.forEach((category, type) -> registration.addCraftingStation(type, category.catalysts().toArray(ItemLike[]::new)));
    }
}
