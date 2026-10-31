package com.ammonium.adminshop.setup;

import java.nio.file.Files;
import java.nio.file.Path;

import com.ammonium.adminshop.AdminShop;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Warns about a leftover {@code config/adminshop/shop.csv}. Offers are datapack recipes and the file is never
 * read. The server looks for the file each time it loads its datapacks, at start and on {@code /reload}. While
 * the file exists, it logs an error and warns each player who logs in.
 */
@EventBusSubscriber(modid = AdminShop.MODID)
public final class ObsoleteShopCsv {

    public static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("adminshop/shop.csv");

    private static volatile boolean found = false;

    private ObsoleteShopCsv() {
    }

    /** Looks for the file again. Players who log in from now on are warned if it was found. */
    public static void check() {
        found = Files.exists(PATH);
        if (found) {
            AdminShop.LOGGER.error("Shop.csv found in config folder. This is no longer used and will not be read from, use datapack recipes!");
            AdminShop.LOGGER.error("Please delete the file at config/adminshop/shop.csv to disable this error.");
        }
    }

    @SubscribeEvent
    static void onDatapackLoad(AddServerReloadListenersEvent event) {
        check();
    }

    @SubscribeEvent
    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (found && event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.adminshop.shop_csv_found_1"));
            player.sendSystemMessage(Component.translatable("message.adminshop.shop_csv_found_2"));
        }
    }
}
