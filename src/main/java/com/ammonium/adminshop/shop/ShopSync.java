package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.recipes.ModRecipeTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Sends the Offers to clients through NeoForge's recipe sync. The sync runs when a player joins, and for
 * every player after {@code /reload}. The client keeps the Offers in the {@link ShopCache}.
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
