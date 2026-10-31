// 26.1 port: the Seller (#7), the T1-T3 Buyers (#8), the Fluid Buyer and Fluid Seller (#10) and the Creative Buyer and
// Creative Seller (#11) and the Basic and Advanced Detectors (#12) are ported (src/main/...);
// their screen registrations are gone from here.
package com.ammonium.adminshop.setup;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.client.KeyInit;
import com.ammonium.adminshop.screen.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AdminShop.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(KeyInit::init);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
    }
}
