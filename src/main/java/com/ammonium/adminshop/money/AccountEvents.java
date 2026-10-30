package com.ammonium.adminshop.money;

import com.ammonium.adminshop.AdminShop;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Keeps each client's copy of its Account current as the player joins the server or changes team. */
@EventBusSubscriber(modid = AdminShop.MODID)
public final class AccountEvents {

    private AccountEvents() {
    }

    // Opens the Account of a team seen for the first time, with the starting Balance.
    @SubscribeEvent
    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MoneyHelper.get(player.level()).syncToPlayer(player);
        }
    }

    // A player who joins or leaves a party now spends from another team's Account.
    @SubscribeEvent
    static void onPlayerChangedTeam(FTBTeamsEvent.PlayerChangedTeam event) {
        ServerPlayer player = event.getEventData().player();
        if (player != null) {
            MoneyHelper.get(player.level()).syncToPlayer(player);
        }
    }
}
