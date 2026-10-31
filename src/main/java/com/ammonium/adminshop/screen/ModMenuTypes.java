package com.ammonium.adminshop.screen;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.blocks.BuyerTier;
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
    public static final DeferredHolder<MenuType<?>, MenuType<BuyerMenu>> BUYER_1_MENU = registerBuyer("buyer_menu", BuyerTier.ONE);
    public static final DeferredHolder<MenuType<?>, MenuType<BuyerMenu>> BUYER_2_MENU = registerBuyer("buyer_2_menu", BuyerTier.TWO);
    public static final DeferredHolder<MenuType<?>, MenuType<BuyerMenu>> BUYER_3_MENU = registerBuyer("buyer_3_menu", BuyerTier.THREE);

    private static DeferredHolder<MenuType<?>, MenuType<BuyerMenu>> registerBuyer(String name, BuyerTier tier) {
        return MENUS.register(name, () -> IMenuTypeExtension.create((containerId, inventory, buffer) ->
                BuyerMenu.client(tier, containerId, inventory)));
    }

    /** The menu type of a Buyer of this tier. */
    public static MenuType<BuyerMenu> buyer(BuyerTier tier) {
        return switch (tier) {
            case ONE -> BUYER_1_MENU.get();
            case TWO -> BUYER_2_MENU.get();
            case THREE -> BUYER_3_MENU.get();
        };
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
