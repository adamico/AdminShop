package com.ammonium.adminshop.compat.emi;

import com.ammonium.adminshop.compat.ShopCategory;
import com.ammonium.adminshop.shop.ShopCache;
import com.ammonium.adminshop.shop.ShopDisplay;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Adds the Shop's Offers to EMI as the Shop Buy and Shop Sell categories. EMI finds this class by its annotation and
 * loads it only when EMI is installed. No code outside this package uses EMI types, so AdminShop runs without EMI.
 *
 * <p>The Offers come from the client shop cache, never from EMI's recipe map. EMI reloads on each recipe
 * sync, at login and after every {@code /reload}, on a thread started from its own listener. {@code ClientShopSync}
 * fills the cache at a higher priority, so the cache already holds the synced Offers when {@link #register} reads
 * them.
 */
@EmiEntrypoint
public final class AdminShopEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        ShopDisplay display = ShopCache.CLIENT.display();
        for (ShopCategory category : ShopCategory.values()) {
            ShopEmiCategory emiCategory = new ShopEmiCategory(category);
            registry.addCategory(emiCategory);
            category.catalysts().forEach(catalyst -> registry.addWorkstation(emiCategory, EmiStack.of(catalyst)));
            category.offers(display).forEach(offer -> registry.addRecipe(new ShopEmiRecipe(emiCategory, offer)));
        }
    }
}
