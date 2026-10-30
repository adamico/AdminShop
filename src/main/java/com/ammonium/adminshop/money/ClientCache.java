package com.ammonium.adminshop.money;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/** The client's copy of its player's Account, as the server last sent it. */
public class ClientCache {

    private static MoneyHelper.MoneyAccount account = null;

    public static @Nullable MoneyHelper.MoneyAccount getAccount() {
        return account;
    }

    public static boolean hasPermit(String permit) {
        if (permit == null || permit.isEmpty()) { return true; }
        if (account == null) { return false; }
        return account.permits().contains(permit);
    }

    public static void setAccount(UUID teamId, long balance, Collection<String> permits) {
        var api = FTBTeamsAPI.api();
        Component name = api.isClientManagerLoaded() ? api.getClientManager().selfTeam().getName() : Component.empty();
        account = new MoneyHelper.MoneyAccount(teamId, name, balance, permits);
    }

    /** Forgets the Account when the client leaves the server. */
    public static void clear() {
        account = null;
    }
}
