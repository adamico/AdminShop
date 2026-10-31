package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.recipes.ModRecipeTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends the Offers to clients (ADR-0001) with NeoForge's recipe sync, which runs as a player joins and
 * for every player after {@code /reload}. The client keeps them in the {@link ShopCache}.
 */
@EventBusSubscriber(modid = AdminShop.MODID)
public final class ShopSync {

    private ShopSync() {
    }

    @SubscribeEvent
    static void sendOffers(OnDatapackSyncEvent event) {
        event.sendRecipes(ModRecipeTypes.offerTypes());
    }
}
