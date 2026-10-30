package com.ammonium.adminshop.gametest;

import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.setup.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /adminshop give} and {@code /adminshop remove} change the Balance of the Account of the
 * player they name, for admins only, as on 1.21.1.
 */
final class CommandTests {

    private CommandTests() {
    }

    static void register(AdminShopGameTests.Registrar tests) {
        tests.test("give_adds_to_the_players_balance", 20, TestPlayers.withPlayers(CommandTests::giveAdds));
        tests.test("remove_takes_from_the_players_balance", 20, TestPlayers.withPlayers(CommandTests::removeTakes));
        tests.test("remove_never_takes_more_than_the_balance", 20, TestPlayers.withPlayers(CommandTests::removeNeverOverdraws));
        tests.test("give_and_remove_refuse_amounts_below_one", 20, TestPlayers.withPlayers(CommandTests::refusesNonPositive));
        tests.test("give_and_remove_are_for_admins_only", 20, TestPlayers.withPlayers(CommandTests::adminsOnly));
    }

    private static void giveAdds(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_give");
        run(helper, admin(helper), "adminshop give cmd_give 300");
        assertBalance(helper, player, Config.STARTING_MONEY.get() + 300);
    }

    private static void removeTakes(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_remove");
        run(helper, admin(helper), "adminshop give cmd_remove 500");
        run(helper, admin(helper), "adminshop remove cmd_remove 200");
        assertBalance(helper, player, Config.STARTING_MONEY.get() + 300);
    }

    private static void removeNeverOverdraws(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_overdraw");
        long balance = MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).balance();
        run(helper, admin(helper), "adminshop remove cmd_overdraw " + (balance + 1));
        assertBalance(helper, player, balance);
    }

    private static void refusesNonPositive(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_zero");
        run(helper, admin(helper), "adminshop give cmd_zero 0");
        run(helper, admin(helper), "adminshop give cmd_zero -5");
        run(helper, admin(helper), "adminshop remove cmd_zero 0");
        assertBalance(helper, player, Config.STARTING_MONEY.get());
    }

    private static void adminsOnly(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_not_admin");
        run(helper, player.createCommandSourceStack(), "adminshop give cmd_not_admin 300");
        run(helper, player.createCommandSourceStack(), "adminshop remove cmd_not_admin 50");
        assertBalance(helper, player, Config.STARTING_MONEY.get());
    }

    /** The server console, which holds every permission. */
    private static CommandSourceStack admin(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack();
    }

    private static void run(GameTestHelper helper, CommandSourceStack source, String command) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static void assertBalance(GameTestHelper helper, ServerPlayer player, long expected) {
        long balance = MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).balance();
        helper.assertValueEqual(balance, expected, player.getPlainTextName() + "'s Balance");
    }
}
