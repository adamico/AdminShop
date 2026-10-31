// 26.1 port: the Seller (#7) and the T1-T3 Buyers (#8) are ported (src/main/...); their screen registrations are gone from here.
// The Creative Seller's screen (#11) is src's SellerScreen; the Creative Buyer's is src's BuyerScreen (tier THREE's texture).
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
        event.register(ModMenuTypes.FLUID_BUYER_MENU.get(), (FluidBuyerMenu menu, Inventory playerInventory, Component title) ->
                new FluidBuyerScreen(menu, playerInventory, title, menu.getBlockEntity().getBlockPos()));
        event.register(ModMenuTypes.FLUID_SELLER_MENU.get(), (FluidSellerMenu menu, Inventory playerInventory, Component title) ->
                new FluidSellerScreen(menu, playerInventory, title, menu.getBlockEntity().getBlockPos()));
        event.register(ModMenuTypes.CREATIVE_BUYER_MENU.get(), (CreativeBuyerMenu menu, Inventory playerInventory, Component title) ->
                new AbstractBuyerScreen<>("textures/gui/buyer_3.png", menu, playerInventory, title, menu.getBlockEntity().getBlockPos()));
        event.register(ModMenuTypes.CREATIVE_SELLER_MENU.get(), (CreativeSellerMenu menu, Inventory playerInventory, Component title) ->
                new SellerScreen<>(menu, playerInventory, title, menu.getBlockEntity().getBlockPos()));
        event.register(ModMenuTypes.BASIC_DETECTOR_MENU.get(), (BasicDetectorMenu menu, Inventory playerInventory, Component title) ->
                new BasicDetectorScreen(menu, playerInventory, title));
        event.register(ModMenuTypes.ADVANCED_DETECTOR_MENU.get(), (AdvancedDetectorMenu menu, Inventory playerInventory, Component title) ->
                new AdvancedDetectorScreen(menu, playerInventory, title));
    }
}
