package com.ammonium.adminshop;

import com.ammonium.adminshop.blocks.ModBlocks;
import com.ammonium.adminshop.blocks.entity.ModBlockEntities;
import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.recipes.ModRecipeSerializers;
import com.ammonium.adminshop.recipes.ModRecipeTypes;
import com.ammonium.adminshop.screen.ModMenuTypes;
import com.ammonium.adminshop.setup.Config;
import com.ammonium.adminshop.setup.CreativeTab;
import com.ammonium.adminshop.setup.Messages;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The mod entry point. It registers every feature. Game tests register themselves from the gametest source set,
 * which is not shipped.
 */
@Mod(AdminShop.MODID)
public class AdminShop {
    public static final Logger LOGGER = LogUtils.getLogger();
    /** The mod id. It must match {@code mod_id} in gradle.properties. */
    public static final String MODID = "adminshop";

    public AdminShop(IEventBus eventBus, ModContainer modContainer) {
        Config.register(modContainer);
        eventBus.addListener(Messages::register);
        ModItems.register(eventBus);
        ModBlocks.register(eventBus);
        ModBlockEntities.register(eventBus);
        ModMenuTypes.register(eventBus);
        CreativeTab.register(eventBus);
        ModRecipeTypes.register(eventBus);
        ModRecipeSerializers.register(eventBus);
    }
}
