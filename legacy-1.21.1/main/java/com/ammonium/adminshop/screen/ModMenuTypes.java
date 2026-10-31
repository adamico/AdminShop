// 26.1 port: the Seller (#7), the T1-T3 Buyers (#8) and the Fluid Buyer and Fluid Seller (#10) are ported (src/main/...);
// their menu types are gone from here.
package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, AdminShop.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CreativeBuyerMenu>> CREATIVE_BUYER_MENU = MENUS.register("creative_buyer_menu",
            () -> IMenuTypeExtension.create((CreativeBuyerMenu::new)));

    public static final DeferredHolder<MenuType<?>, MenuType<CreativeSellerMenu>> CREATIVE_SELLER_MENU = MENUS.register("creative_seller_menu",
            () -> IMenuTypeExtension.create((CreativeSellerMenu::new)));

    public static final DeferredHolder<MenuType<?>, MenuType<BasicDetectorMenu>> BASIC_DETECTOR_MENU = MENUS.register("detector_menu",
            () -> IMenuTypeExtension.create((BasicDetectorMenu::new)));

    public static final DeferredHolder<MenuType<?>, MenuType<AdvancedDetectorMenu>> ADVANCED_DETECTOR_MENU = MENUS.register("advanced_detector_menu",
            () -> IMenuTypeExtension.create((AdvancedDetectorMenu::new)));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
