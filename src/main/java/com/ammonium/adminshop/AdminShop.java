package com.ammonium.adminshop;

import com.ammonium.adminshop.setup.Config;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * AdminShop's entry point. The 26.1.2 port registers each feature here as it moves over from
 * legacy-1.21.1/. The game tests hook themselves in from the gametest source set, which never ships.
 */
@Mod(AdminShop.MODID)
public class AdminShop {
    public static final Logger LOGGER = LogUtils.getLogger();
    /** The mod id, which gradle.properties' {@code mod_id} must match. */
    public static final String MODID = "adminshop";

    public AdminShop(IEventBus eventBus, ModContainer modContainer) {
        Config.register(modContainer);
    }
}
