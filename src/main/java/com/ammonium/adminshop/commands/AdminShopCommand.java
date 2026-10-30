package com.ammonium.adminshop.commands;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.money.MoneyHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
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

/** {@code /adminshop}: lets admins moderate the economy. */
@EventBusSubscriber(modid = AdminShop.MODID)
public class AdminShopCommand {

    @SubscribeEvent
    static void onCommandRegistration(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        LiteralArgumentBuilder<CommandSourceStack> adminShopCommand = Commands.literal("adminshop");

        // adminshop give {player} {amount}
        LiteralArgumentBuilder<CommandSourceStack> giveMoneyCommand = Commands.literal("give")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("player", EntityArgument.players())
                .then(Commands.argument("amount", LongArgumentType.longArg())
                .executes(context -> {
                    EntitySelector playerSelector = context.getArgument("player", EntitySelector.class);
                    long amount = LongArgumentType.getLong(context, "amount");
                    return giveMoney(context.getSource(), playerSelector, amount);
                })));

        // adminshop remove {player} {amount}
        LiteralArgumentBuilder<CommandSourceStack> removeMoneyCommand = Commands.literal("remove")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.argument("player", EntityArgument.players())
                .then(Commands.argument("amount", LongArgumentType.longArg())
                .executes(context -> {
                    EntitySelector playerSelector = context.getArgument("player", EntitySelector.class);
                    long amount = LongArgumentType.getLong(context, "amount");
                    return removeMoney(context.getSource(), playerSelector, amount);
                })));

        adminShopCommand.then(giveMoneyCommand)
                        .then(removeMoneyCommand);

        dispatcher.register(adminShopCommand);
    }

    static int giveMoney(CommandSourceStack source, EntitySelector selector, long amount) throws CommandSyntaxException {
        // Skip non-positive values
        if (!(amount>0)) {
            source.sendFailure(Component.translatable("message.adminshop.error.not_positive"));
            return 0;
        }

        // Get player and MoneyManager
        ServerLevel level = source.getLevel();
        ServerPlayer player = selector.findSinglePlayer(source);
        MoneyHelper.MoneyAccount account = MoneyHelper.get(level).getPlayerAccount(player);

        // Give money
        MoneyHelper.get(level).addMoney(account.teamId(), amount);
        source.sendSuccess(() -> Component.translatable("message.adminshop.give.success", amount), true);
        return 1;
    }

    static int removeMoney(CommandSourceStack source, EntitySelector selector, long amount) throws CommandSyntaxException {
        // Skip non-positive values
        if (!(amount>0)) {
            source.sendFailure(Component.translatable("message.adminshop.error.not_positive"));
            return 0;
        }

        // Get player and account
        ServerLevel level = source.getLevel();
        ServerPlayer player = selector.findSinglePlayer(source);
        MoneyHelper.MoneyAccount account = MoneyHelper.get(level).getPlayerAccount(player);

        // Remove money
        boolean success = MoneyHelper.get(level).removeMoney(account.teamId(), amount);
        if (!success) {
            source.sendFailure(Component.translatable("message.adminshop.remove.failure"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("message.adminshop.remove.success", amount), true);
        return 1;
    }
}
