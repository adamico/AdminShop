package com.ammonium.adminshop.money;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.network.PacketSyncMoneyToClient;
import com.ammonium.adminshop.setup.Config;
import com.ammonium.adminshop.setup.Messages;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores every Account on the server with its Balance and Permits, keyed by the id of the FTB Team that
 * owns it. It is saved once per server in {@code data/adminshop/accounts.dat}. Each change to an Account
 * is sent to the team's online members immediately.
 */
public class MoneyHelper extends SavedData {

    /** One saved Account. The name is not stored because it is the team's name. */
    private record Ledger(UUID team, long balance, List<String> permits) {
        static final Codec<Ledger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("team").forGetter(Ledger::team),
                Codec.LONG.fieldOf("balance").forGetter(Ledger::balance),
                Codec.STRING.listOf().optionalFieldOf("permits", List.of()).forGetter(Ledger::permits)
        ).apply(instance, Ledger::new));
    }

    private static final Codec<MoneyHelper> CODEC = Ledger.CODEC.listOf().fieldOf("accounts").codec()
            .xmap(MoneyHelper::new, MoneyHelper::ledgers);

    public static final SavedDataType<MoneyHelper> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(AdminShop.MODID, "accounts"), MoneyHelper::new, CODEC);

    /** Balance and Permits by team id, in the order the Accounts were opened. */
    private final Map<UUID, Ledger> ledger = new LinkedHashMap<>();

    public MoneyHelper() {}

    private MoneyHelper(List<Ledger> ledgers) {
        ledgers.forEach(entry -> ledger.put(entry.team(), entry));
    }

    private List<Ledger> ledgers() {
        return List.copyOf(ledger.values());
    }

    /** Returns the server's Accounts. Throws if called on the client. */
    public static MoneyHelper get(Level checkLevel) {
        if (!(checkLevel instanceof ServerLevel level)) {
            throw new RuntimeException("Don't access this client-side!");
        }
        return get(level.getServer());
    }

    public static MoneyHelper get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public record MoneyAccount(UUID teamId, Component name, long balance, ImmutableSet<String> permits) {
        public MoneyAccount(UUID teamId, Component name, long balance, Collection<String> permits) {
            this(teamId, name, balance, ImmutableSet.copyOf(permits));
        }
    }

    public static boolean hasPermit(MoneyAccount account, String permit) {
        if (permit == null || permit.isEmpty()) { return true; }
        return account.permits.contains(permit);
    }

    private static MoneyAccount toAccount(Component name, Ledger entry) {
        return new MoneyAccount(entry.team(), name, entry.balance(), entry.permits());
    }

    private void updateAccount(MoneyAccount account, TeamManager manager) {
        ledger.put(account.teamId, new Ledger(account.teamId, account.balance, List.copyOf(account.permits)));
        this.setDirty();

        Optional<Team> oTeam = getFTBTeam(account.teamId, manager);
        if (oTeam.isEmpty()) {
            AdminShop.LOGGER.warn("Team not found for UUID: {}", account.teamId);
            return;
        }
        for (ServerPlayer player : oTeam.get().getOnlineMembers()) {
            sendToPlayer(account, player);
        }
    }

    /** Sends the player their current team's Account, opening it if it does not exist yet. */
    public void syncToPlayer(ServerPlayer player) {
        sendToPlayer(getPlayerAccount(player), player);
    }

    private static void sendToPlayer(MoneyAccount account, ServerPlayer player) {
        Messages.sendToPlayer(new PacketSyncMoneyToClient(account), player);
    }

    public @NotNull MoneyAccount getPlayerAccount(@NotNull ServerPlayer player) {
        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> oTeam = manager.getTeamForPlayer(player);

        if (oTeam.isEmpty()) {
            AdminShop.LOGGER.debug("Player {} is not in a team", player.getName().getString());
            MoneyAccount account = new MoneyAccount(
                    player.getUUID(), player.getName(), Config.STARTING_MONEY.get(), ImmutableSet.of());
            updateAccount(account, manager);
            return account;
        }

        Team team = oTeam.get();
        Ledger entry = ledger.get(team.getId());

        if (entry == null) {
            AdminShop.LOGGER.debug("Creating new account for team: {}", team.getName().getString());
            MoneyAccount account = new MoneyAccount(
                    team.getId(), team.getName(), Config.STARTING_MONEY.get(), ImmutableSet.of());
            updateAccount(account, manager);
            return account;
        }

        return toAccount(team.getName(), entry);
    }

    public @Nullable MoneyAccount getAccountById(UUID teamId) {
        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> oTeam = getFTBTeam(teamId, manager);
        if (oTeam.isEmpty()) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return null;
        }
        Ledger entry = ledger.get(teamId);
        return entry == null ? null : toAccount(oTeam.get().getName(), entry);
    }

    private static Optional<Team> getFTBTeam(UUID teamId, TeamManager manager) {
        if (teamId == null || manager == null) {
            AdminShop.LOGGER.debug("Team ID or manager is null");
            return Optional.empty();
        }
        return manager.getTeamByID(teamId);
    }

    public void addMoney(UUID teamId, long amount) {
        AdminShop.LOGGER.debug("Adding money: {} {}", teamId, amount);
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        MoneyAccount account = getAccountById(teamId);
        if (account == null) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return;
        }

        long newBalance = account.balance + amount;
        updateAccount(new MoneyAccount(teamId, account.name, newBalance, account.permits), manager);
    }

    public boolean removeMoney(UUID teamId, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        MoneyAccount account = getAccountById(teamId);
        if (account == null) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return false;
        }

        if (account.balance < amount) {
            return false;
        }
        long newBalance = account.balance - amount;
        updateAccount(new MoneyAccount(teamId, account.name, newBalance, account.permits), manager);
        return true;
    }

    public boolean hasPermit(UUID teamId, String permit) {
        if (permit == null || permit.isEmpty()) {
            return true;
        }
        MoneyAccount account = getAccountById(teamId);
        if (account == null) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return false;
        }
        return account.permits.contains(permit);
    }

    public void addPermit(UUID teamId, String permit) {
        if (permit.isEmpty()) {
            throw new IllegalArgumentException("Permit cannot be empty");
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        MoneyAccount account = getAccountById(teamId);
        if (account == null) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return;
        }
        Set<String> newPermits = new LinkedHashSet<>(account.permits);
        newPermits.add(permit);
        updateAccount(new MoneyAccount(teamId, account.name, account.balance, newPermits), manager);
    }

    public void removePermit(UUID teamId, String permit) {
        if (permit.isEmpty()) {
            throw new IllegalArgumentException("Permit cannot be empty");
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        MoneyAccount account = getAccountById(teamId);
        if (account == null) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return;
        }
        Set<String> newPermits = new LinkedHashSet<>(account.permits);
        newPermits.remove(permit);
        updateAccount(new MoneyAccount(teamId, account.name, account.balance, newPermits), manager);
    }

    public boolean isMemberOfTeam(UUID teamId, ServerPlayer player) {
        assert teamId != null && player != null;
        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> oTeam = getFTBTeam(teamId, manager);
        if (oTeam.isEmpty()) {
            AdminShop.LOGGER.debug("Team not found for UUID: {}", teamId);
            return false;
        }
        return oTeam.get().getMembers().contains(player.getUUID());
    }

    public @Nullable UUID getTeamUUIDForPlayer(ServerPlayer player) {
        TeamManager manager = FTBTeamsAPI.api().getManager();
        Optional<Team> oTeam = manager.getTeamForPlayer(player);
        if (oTeam.isEmpty()) {
            AdminShop.LOGGER.debug("Team not found for player: {}", player.getName().getString());
            return null;
        }
        return oTeam.get().getId();
    }
}
