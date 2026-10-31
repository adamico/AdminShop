package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AdminShop.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ShopMenu>> SHOP_MENU = MENUS.register("shop_menu",
            () -> new MenuType<>(ShopMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<SellerMenu>> SELLER_MENU = MENUS.register("seller_menu",
            () -> IMenuTypeExtension.create(SellerMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
