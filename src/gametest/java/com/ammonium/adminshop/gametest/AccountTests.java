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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Accounts belong to FTB Teams: every member of a team spends from and earns into the same Balance,
 * a player who leaves a party is back on their own Account, and Balances are saved with the world.
 */
final class AccountTests {

    private AccountTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("party_members_share_one_account", 20, TestPlayers.withPlayers(AccountTests::partyMembersShareOneAccount));
        tests.test("leaving_a_party_returns_to_the_own_account", 20, TestPlayers.withPlayers(AccountTests::leavingReturnsToOwnAccount));
        tests.test("a_balance_survives_a_save_and_reload", 20, TestPlayers.withPlayers(AccountTests::balanceSurvivesSaveAndReload));
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

    private static void leavingReturnsToOwnAccount(GameTestHelper helper, TestPlayers players) throws CommandSyntaxException {
        ServerPlayer owner = players.join("leaving_owner");
        ServerPlayer member = players.join("leaving_member");
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        UUID ownTeam = accounts.getPlayerAccount(member).teamId();
        accounts.addMoney(ownTeam, 1000);

        PartyTeam party = joinParty(owner, member);
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

    private static void balanceSurvivesSaveAndReload(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("saving_player");
        MinecraftServer server = helper.getLevel().getServer();
        MoneyHelper accounts = MoneyHelper.get(server);
        UUID team = accounts.getPlayerAccount(player).teamId();
        accounts.addMoney(team, 4321);
        long balance = accounts.getPlayerAccount(player).balance();

        server.getDataStorage().saveAndJoin();
        // A second storage on the same folder has nothing cached: it reads the Accounts back from disk.
        var reloaded = new SavedDataStorage(null, server.getWorldPath(LevelResource.DATA), server.getFixerUpper(), server.registryAccess())
                .get(MoneyHelper.TYPE);

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
