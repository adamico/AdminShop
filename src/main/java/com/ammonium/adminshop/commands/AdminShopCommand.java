package com.ammonium.adminshop.commands;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.item.PermitItem;
import com.ammonium.adminshop.money.MoneyHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /adminshop} command. Admins use it to moderate the economy, and players use it to list and drop
 * their Account's Permits.
 */
@EventBusSubscriber(modid = AdminShop.MODID)
public class AdminShopCommand {

    @SubscribeEvent
    static void onCommandRegistration(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Registers the subcommands {@code give <player> <amount>}, {@code remove <player> <amount>} and
     * {@code getPermit <permit>}, which need admin permission, and {@code removePermit <permit>} and
     * {@code listOwnedPermits}, which any player may run.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        LiteralArgumentBuilder<CommandSourceStack> adminShopCommand = Commands.literal("adminshop");

        LiteralArgumentBuilder<CommandSourceStack> giveMoneyCommand = Commands.literal("give")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("player", EntityArgument.players())
                .then(Commands.argument("amount", LongArgumentType.longArg())
                .executes(context -> {
                    EntitySelector playerSelector = context.getArgument("player", EntitySelector.class);
                    long amount = LongArgumentType.getLong(context, "amount");
                    return giveMoney(context.getSource(), playerSelector, amount);
                })));

        LiteralArgumentBuilder<CommandSourceStack> removeMoneyCommand = Commands.literal("remove")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("player", EntityArgument.players())
                .then(Commands.argument("amount", LongArgumentType.longArg())
                .executes(context -> {
                    EntitySelector playerSelector = context.getArgument("player", EntitySelector.class);
                    long amount = LongArgumentType.getLong(context, "amount");
                    return removeMoney(context.getSource(), playerSelector, amount);
                })));

        LiteralArgumentBuilder<CommandSourceStack> getPermitCommand = Commands.literal("getPermit")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("tier", StringArgumentType.string())
                .executes(context -> getPermit(context.getSource(), StringArgumentType.getString(context, "tier"))));

        LiteralArgumentBuilder<CommandSourceStack> removePermitCommand = Commands.literal("removePermit")
                .then(Commands.argument("tier", StringArgumentType.string())
                .executes(context -> removePermit(context.getSource(), StringArgumentType.getString(context, "tier"))));

        LiteralArgumentBuilder<CommandSourceStack> listOwnedPermitsCommand = Commands.literal("listOwnedPermits")
                .executes(context -> listOwnedPermits(context.getSource()));

        adminShopCommand.then(giveMoneyCommand)
                        .then(removeMoneyCommand)
                        .then(getPermitCommand)
                        .then(removePermitCommand)
                        .then(listOwnedPermitsCommand);

        dispatcher.register(adminShopCommand);
    }

    static int giveMoney(CommandSourceStack source, EntitySelector selector, long amount) throws CommandSyntaxException {
        if (!(amount>0)) {
            source.sendFailure(Component.translatable("message.adminshop.error.not_positive"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        ServerPlayer player = selector.findSinglePlayer(source);
        MoneyHelper.MoneyAccount account = MoneyHelper.get(level).getPlayerAccount(player);

        MoneyHelper.get(level).addMoney(account.teamId(), amount);
        source.sendSuccess(() -> Component.translatable("message.adminshop.give.success", amount), true);
        return 1;
    }

    static int removeMoney(CommandSourceStack source, EntitySelector selector, long amount) throws CommandSyntaxException {
        if (!(amount>0)) {
            source.sendFailure(Component.translatable("message.adminshop.error.not_positive"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        ServerPlayer player = selector.findSinglePlayer(source);
        MoneyHelper.MoneyAccount account = MoneyHelper.get(level).getPlayerAccount(player);

        boolean success = MoneyHelper.get(level).removeMoney(account.teamId(), amount);
        if (!success) {
            source.sendFailure(Component.translatable("message.adminshop.remove.failure"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.adminshop.remove.success", amount), true);
        return 1;
    }

    static int listOwnedPermits(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MoneyHelper.MoneyAccount account = MoneyHelper.get(source.getLevel()).getPlayerAccount(player);
        if (account.permits().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.adminshop.permits.empty"), true);
            return 0;
        }
        String permits = String.join(", ", account.permits());
        source.sendSuccess(() -> Component.literal(permits), true);
        return 1;
    }

    /** Gives the player running the command a Trade Permit for {@code permit}. It unlocks nothing until redeemed. */
    static int getPermit(CommandSourceStack source, String permit) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (permit.isEmpty()) {
            source.sendFailure(Component.translatable("message.adminshop.permit.default"));
            return 0;
        }
        if (!player.getInventory().add(PermitItem.of(permit))) {
            source.sendFailure(Component.translatable("message.adminshop.permit.give.failure"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.adminshop.permit.give.success", permit), true);
        return 1;
    }

    static int removePermit(CommandSourceStack source, String permit) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (permit.isEmpty()) {
            source.sendFailure(Component.translatable("message.adminshop.permit.default"));
            return 0;
        }
        MoneyHelper accounts = MoneyHelper.get(source.getLevel());
        accounts.removePermit(accounts.getPlayerAccount(player).teamId(), permit);
        source.sendSuccess(() -> Component.translatable("message.adminshop.permit.remove.success", permit), true);
        return 1;
    }
}
