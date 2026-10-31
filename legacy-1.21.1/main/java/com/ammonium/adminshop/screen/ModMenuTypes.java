// 26.1 port: the Seller (#7), the T1-T3 Buyers (#8), the Fluid Buyer and Fluid Seller (#10) and the Creative Buyer and
// Creative Seller (#11) and the Basic and Advanced Detectors (#12) are ported (src/main/...);
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

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
