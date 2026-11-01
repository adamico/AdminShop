package com.ammonium.adminshop.gametest;

import java.util.List;
import java.util.UUID;

import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.setup.Config;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Tests that Accounts belong to FTB Teams. Every member of a team spends from and earns into the same Balance. A
 * player who leaves a party goes back to their own Account. Balances are saved with the world.
 */
final class AccountTests {

    private AccountTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("party_members_share_one_account", 20, TestPlayers.withPlayers(AccountTests::partyMembersShareOneAccount));
        tests.test("leaving_a_party_returns_to_the_own_account", 20, TestPlayers.withPlayers(AccountTests::leavingReturnsToOwnAccount));
        tests.test("a_balance_survives_a_save_and_reload", 20, TestPlayers.withPlayers(AccountTests::balanceSurvivesSaveAndReload));
        tests.test("a_new_account_starts_with_the_configured_money", 20, TestPlayers.withPlayers(AccountTests::newAccountStartsWithConfiguredMoney));
    }

    private static void partyMembersShareOneAccount(GameTestHelper helper, TestPlayers players) throws CommandSyntaxException {
        ServerPlayer owner = players.join("shared_owner");
        ServerPlayer member = players.join("shared_member");
        joinParty(owner, member);
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());

        UUID team = accounts.getPlayerAccount(owner).teamId();
        accounts.addMoney(team, 250);
        boolean paid = accounts.removeMoney(accounts.getPlayerAccount(member).teamId(), 50);

        helper.assertTrue(paid, "the member could not spend from the team's Account");
        long expected = Config.STARTING_MONEY.get() + 200;
        helper.assertValueEqual(accounts.getPlayerAccount(owner).balance(), expected, "the owner's Balance");
        helper.assertValueEqual(accounts.getPlayerAccount(member).balance(), expected, "the member's Balance");
    }

    /**
     * A new Account starts with the server config's {@code starting_money}, read when the Account opens. An Account
     * opened before the config changes keeps its Balance.
     */
    private static void newAccountStartsWithConfiguredMoney(GameTestHelper helper, TestPlayers players) {
        long configured = Config.STARTING_MONEY.get();
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        ServerPlayer before = players.join("starting_before");
        try {
            Config.STARTING_MONEY.set(1234L);
            ServerPlayer after = players.join("starting_after");

            helper.assertValueEqual(accounts.getPlayerAccount(after).balance(), 1234L, "the new Account's Balance");
            helper.assertValueEqual(accounts.getPlayerAccount(before).balance(), configured,
                    "the Balance of the Account opened before the change");
        } finally {
            Config.STARTING_MONEY.set(configured);
        }
    }

    /**
     * The party's Account opens when a member first trades or opens the shop, which the test does by reading the
     * owner's Account before paying into it.
     */
    private static void leavingReturnsToOwnAccount(GameTestHelper helper, TestPlayers players) throws CommandSyntaxException {
        ServerPlayer owner = players.join("leaving_owner");
        ServerPlayer member = players.join("leaving_member");
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        UUID ownTeam = accounts.getPlayerAccount(member).teamId();
        accounts.addMoney(ownTeam, 1000);

        PartyTeam party = joinParty(owner, member);
        accounts.getPlayerAccount(owner);
        accounts.addMoney(party.getId(), 7);
        helper.assertValueEqual(accounts.getPlayerAccount(member).balance(), Config.STARTING_MONEY.get() + 7,
                "the member's Balance in the party");

        party.leave(member.getUUID());

        var account = accounts.getPlayerAccount(member);
        helper.assertValueEqual(account.teamId(), ownTeam, "the Account of the member who left");
        helper.assertValueEqual(account.balance(), Config.STARTING_MONEY.get() + 1000, "the Balance of the member who left");
        helper.assertValueEqual(accounts.getPlayerAccount(owner).balance(), Config.STARTING_MONEY.get() + 7,
                "the Balance of the party after the member left");
    }

    /**
     * Reads the Accounts back from disk through a second storage on the overworld's data folder, which has nothing
     * cached. The second storage reads them as {@code adminshop:accounts}, the id MoneyHelper saves them under.
     */
    private static void balanceSurvivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("saving_player");
        MinecraftServer server = helper.getLevel().getServer();
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        UUID team = accounts.getPlayerAccount(player).teamId();
        accounts.addMoney(team, 4321);
        long balance = accounts.getPlayerAccount(player).balance();

        server.overworld().getDataStorage().saveAndJoin();
        var folder = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("data");
        var type = new SavedDataType<>(TestShop.id("accounts"), MoneyHelper::new,
                CompoundTag.CODEC.xmap(MoneyHelper::new, saved -> saved.save(new CompoundTag())));
        var reloaded = new SavedDataStorage(null, folder, server.getFixerUpper(), server.registryAccess()).get(type);

        helper.assertTrue(reloaded != null, "no Accounts were saved");
        var account = reloaded.getAccountById(team);
        helper.assertTrue(account != null, "the team's Account was not saved");
        helper.assertValueEqual(account.balance(), balance, "the reloaded Balance");
    }

    /** Puts {@code member} in a new party that {@code owner} owns. */
    static PartyTeam joinParty(ServerPlayer owner, ServerPlayer member) throws CommandSyntaxException {
        Team team = FTBTeamsAPI.api().getManager().createPartyTeam(owner, owner.getPlainTextName() + "'s party", "", Color4I.WHITE);
        PartyTeam party = (PartyTeam) team;
        party.invite(owner, List.of(new NameAndId(member.getGameProfile())));
        party.join(member);
        return party;
    }
}
