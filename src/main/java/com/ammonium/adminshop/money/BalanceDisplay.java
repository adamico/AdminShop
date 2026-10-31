package com.ammonium.adminshop.money;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.setup.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Draws the player's Account Balance in the top left corner of the HUD. It updates as soon as the server
 * sends a change and also shows the average change per second over the last few seconds.
 */
@EventBusSubscriber(modid = AdminShop.MODID, value = Dist.CLIENT)
public class BalanceDisplay {
    public static final Identifier LAYER = Identifier.fromNamespaceAndPath(AdminShop.MODID, "balance");

    private static final long[] history = new long[]{0, 0};
    private static boolean sampled = false;
    private static int tick = 0;
    private static boolean BALANCE_DISPLAY = false;
    private static int BALANCE_DELTA_SECONDS = -1;
    private static int BALANCE_DELTA_TICKS = -1;

    private static void loadConfig() {
        BALANCE_DISPLAY = Config.balanceDisplay.get();
        BALANCE_DELTA_SECONDS = Config.balanceDelta.get();
        BALANCE_DELTA_TICKS = BALANCE_DELTA_SECONDS * 20;
    }

    private static boolean shouldRun() {
        return BALANCE_DISPLAY && (BALANCE_DELTA_SECONDS > 0);
    }

    private static void reset() {
        history[0] = history[1] = 0;
        sampled = false;
        tick = 0;
        ClientCache.clear();
    }

    private static String displayString(long balance) {
        long changePerSecond = (history[1] - history[0]) / BALANCE_DELTA_SECONDS;
        StringBuilder str = new StringBuilder(MoneyFormat.cfgformat(balance));

        if (changePerSecond != 0) {
            str.append(" ")
                    .append(changePerSecond > 0 ? ChatFormatting.GREEN + "+" : ChatFormatting.RED)
                    .append(MoneyFormat.format(changePerSecond, MoneyFormat.FormatType.SHORT, MoneyFormat.FormatType.RAW))
                    .append("/s");
        }

        return Component.translatable("gui.balance", str.toString()).getString();
    }

    @SubscribeEvent
    static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, BalanceDisplay::render);
    }

    @SubscribeEvent
    static void onConfigLoad(ModConfigEvent.Loading event) {
        onConfig(event.getConfig());
    }

    @SubscribeEvent
    static void onConfigReload(ModConfigEvent.Reloading event) {
        onConfig(event.getConfig());
    }

    private static void onConfig(ModConfig config) {
        if (config.getModId().equals(AdminShop.MODID) && config.getType() == ModConfig.Type.CLIENT) {
            loadConfig();
        }
    }

    /**
     * Samples the Balance at the configured interval for the change per second. The first sample is
     * compared with itself so the whole Balance does not show as a change.
     */
    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!shouldRun()) return;
        if (Minecraft.getInstance().player == null) return;

        if (tick <= 0) {
            tick = BALANCE_DELTA_TICKS;
            MoneyHelper.MoneyAccount account = ClientCache.getAccount();
            if (account == null) {
                return;
            }
            history[0] = sampled ? history[1] : account.balance();
            history[1] = account.balance();
            sampled = true;
        }
        tick--;
    }

    @SubscribeEvent
    static void clientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!shouldRun()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        MoneyHelper.MoneyAccount account = ClientCache.getAccount();
        if (account == null) return;
        graphics.text(mc.font, displayString(account.balance()), 2, 2, 0xFFFFFFFF);
    }
}
