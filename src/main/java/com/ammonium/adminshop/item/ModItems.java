package com.ammonium.adminshop.item;

import com.ammonium.adminshop.AdminShop;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** AdminShop's items. The block items register here too, from {@code ModBlocks}. */
public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AdminShop.MODID);

    public static final DeferredItem<ShopTablet> TABLET = ITEMS.registerItem("tablet", ShopTablet::new,
            properties -> properties.stacksTo(1));
    public static final DeferredItem<PermitItem> PERMIT = ITEMS.registerItem("permit", PermitItem::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
