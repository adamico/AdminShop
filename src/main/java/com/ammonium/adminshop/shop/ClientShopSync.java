package com.ammonium.adminshop.shop;

import com.ammonium.adminshop.AdminShop;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** Fills the client's {@link ShopCache} from each recipe sync, and empties it when the client leaves. */
@EventBusSubscriber(modid = AdminShop.MODID, value = Dist.CLIENT)
public final class ClientShopSync {

    private ClientShopSync() {
    }

    /**
     * Ahead of the recipe viewers' own listeners, which rebuild their categories from the cache: EMI's listens at
     * the default priority and JEI's at the lowest.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void receiveOffers(RecipesReceivedEvent event) {
        ShopCache.CLIENT.replace(event.getRecipeMap());
    }

    @SubscribeEvent
    static void forgetOffers(ClientPlayerNetworkEvent.LoggingOut event) {
        ShopCache.CLIENT.clear();
    }
}
