package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.money.MoneyHelper;
import com.google.common.collect.ImmutableSet;
import com.ammonium.adminshop.setup.Config;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Tests the commands. {@code /adminshop give} and {@code /adminshop remove} change the Balance of the named
 * player's Account and are for admins only. {@code /adminshop getPermit} gives an admin a Trade Permit. Any
 * player can list their Account's Permits with {@code listOwnedPermits} and drop one with {@code removePermit}.
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
        tests.test("get_permit_gives_an_admin_a_trade_permit", 20, TestPlayers.withPlayers(CommandTests::getPermit));
        tests.test("remove_permit_takes_a_permit_from_the_players_account", 20, TestPlayers.withPlayers(CommandTests::removePermit));
        tests.test("list_owned_permits_lists_the_accounts_permits", 20, TestPlayers.withPlayers(CommandTests::listOwnedPermits));
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

    private static void getPermit(GameTestHelper helper, TestPlayers players) {
        ServerPlayer admin = players.join("cmd_get_permit");
        ServerPlayer player = players.join("cmd_get_permit_not_admin");

        run(helper, admin.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ADMIN), "adminshop getPermit gems");
        run(helper, player.createCommandSourceStack(), "adminshop getPermit gems");

        ItemStack permit = admin.getInventory().getItem(0);
        helper.assertValueEqual(permit.getItem(), ModItems.PERMIT.get(), "the admin's first slot");
        String key = permit.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getStringOr("key", "");
        helper.assertValueEqual(key, "gems", "the Trade Permit's Permit");
        helper.assertTrue(player.getInventory().isEmpty(), "a player who is no admin got a Trade Permit");
        helper.assertTrue(MoneyHelper.get(helper.getLevel()).getPlayerAccount(admin).permits().isEmpty(),
                "getPermit unlocked the Permit itself");
    }

    private static void removePermit(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_remove_permit");
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        UUID team = accounts.getPlayerAccount(player).teamId();
        accounts.addPermit(team, "gems");
        accounts.addPermit(team, "mining");

        run(helper, player.createCommandSourceStack(), "adminshop removePermit gems");

        helper.assertValueEqual(accounts.getPlayerAccount(player).permits(), ImmutableSet.of("mining"), "the Account's Permits");
    }

    private static void listOwnedPermits(GameTestHelper helper, TestPlayers players) {
        ServerPlayer player = players.join("cmd_list_permits");
        var heard = new ArrayList<String>();
        CommandSourceStack source = player.createCommandSourceStack().withSource(listener(heard));

        run(helper, source, "adminshop listOwnedPermits");
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        UUID team = accounts.getPlayerAccount(player).teamId();
        accounts.addPermit(team, "gems");
        accounts.addPermit(team, "mining");
        run(helper, source, "adminshop listOwnedPermits");

        helper.assertValueEqual(heard.size(), 2, "the number of lines listOwnedPermits said");
        helper.assertValueEqual(heard.getFirst(), "Your account has not unlocked any permits yet.", "what listOwnedPermits said first");
        helper.assertValueEqual(Set.of(heard.get(1).split(", ")), Set.of("gems", "mining"), "the Permits listOwnedPermits listed");
    }

    /** A command source that keeps the text of what it is told. */
    private static CommandSource listener(List<String> heard) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                heard.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }

    /** The server console, which has every permission. */
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
