package com.ammonium.adminshop.setup;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.screen.ModMenuTypes;
import com.ammonium.adminshop.screen.BuyerScreen;
import com.ammonium.adminshop.screen.FluidBuyerScreen;
import com.ammonium.adminshop.screen.FluidSellerScreen;
import com.ammonium.adminshop.screen.SellerScreen;
import com.ammonium.adminshop.screen.ShopScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The client's screens for AdminShop's menus. */
@EventBusSubscriber(modid = AdminShop.MODID, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SHOP_MENU.get(), ShopScreen::new);
        event.register(ModMenuTypes.SELLER_MENU.get(), SellerScreen::new);
        event.register(ModMenuTypes.BUYER_1_MENU.get(), BuyerScreen::new);
        event.register(ModMenuTypes.BUYER_2_MENU.get(), BuyerScreen::new);
        event.register(ModMenuTypes.BUYER_3_MENU.get(), BuyerScreen::new);
        event.register(ModMenuTypes.FLUID_BUYER_MENU.get(), FluidBuyerScreen::new);
        event.register(ModMenuTypes.FLUID_SELLER_MENU.get(), FluidSellerScreen::new);
    }
}
