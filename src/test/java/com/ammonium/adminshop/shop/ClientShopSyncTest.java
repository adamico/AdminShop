package com.ammonium.adminshop.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import org.junit.jupiter.api.Test;

/**
 * Tests that the cache fills from a recipe sync before the recipe viewers rebuild their categories from it. EMI
 * listens at the default priority and JEI at the lowest, so the cache's listener must run higher than both.
 */
class ClientShopSyncTest {

    @Test
    void theCacheFillsBeforeTheViewersReload() throws NoSuchMethodException {
        SubscribeEvent subscription = ClientShopSync.class
                .getDeclaredMethod("receiveOffers", RecipesReceivedEvent.class).getAnnotation(SubscribeEvent.class);
        assertEquals(EventPriority.HIGH, subscription.priority());
    }
}
