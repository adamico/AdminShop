package com.ammonium.adminshop.compat.emi;

import com.ammonium.adminshop.compat.ShopCategory;
import com.ammonium.adminshop.shop.ShopCache;
import com.ammonium.adminshop.shop.ShopDisplay;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Puts the Shop's Offers in EMI, as the Shop Buy and Shop Sell categories. EMI finds this by its annotation and
 * loads it only when EMI is installed; nothing outside this package names an EMI type, so AdminShop loads without it.
 *
 * <p>The Offers come from the client shop cache (ADR-0001), never EMI's recipe map. EMI reloads on each recipe sync
 * (login and every {@code /reload}), on a thread it starts from its own listener; {@code ClientShopSync} fills the
 * cache at a higher priority, so the cache already holds the synced Offers when {@link #register} reads them.
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
