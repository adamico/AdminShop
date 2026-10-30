package com.ammonium.adminshop.commands;

import com.ammonium.adminshop.item.ModItems;
import com.ammonium.adminshop.money.MoneyHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class AdminShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        LiteralArgumentBuilder<CommandSourceStack> adminShopCommand = Commands.literal("adminshop");

        // adminshop getPermit {permit}
        LiteralArgumentBuilder<CommandSourceStack> getPermitCommand = Commands.literal("getPermit").requires(source -> source.hasPermission(3));
        RequiredArgumentBuilder<CommandSourceStack, String> getPermitCommandTier = Commands.argument("tier", StringArgumentType.string())
                .executes(command -> {
                    String tier = StringArgumentType.getString(command, "tier");
                    return getPermit(command.getSource(), tier);
                });
        getPermitCommand.then(getPermitCommandTier);

        // adminshop removePermit {permit}
        LiteralArgumentBuilder<CommandSourceStack> removePermitCommand = Commands.literal("removePermit")
                .requires(source -> source.hasPermission(0))
                .then(Commands.argument("tier", StringArgumentType.string())
                .executes(command -> {
                    String tier = StringArgumentType.getString(command, "tier");
                    return removePermit(command.getSource(), tier);
                }));
        
        // adminshop listOwnedPermits
        LiteralArgumentBuilder<CommandSourceStack> listOwnedPermitsCommand = Commands.literal("listOwnedPermits")
                .requires(source -> source.hasPermission(0))
                .executes(command -> listOwnedPermits(command.getSource()));


        adminShopCommand.then(getPermitCommand)
                        .then(removePermitCommand)
                        .then(listOwnedPermitsCommand);

        dispatcher.register(adminShopCommand);
    }

    private static int listOwnedPermits(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        MoneyHelper.MoneyAccount account = MoneyHelper.get(level).getPlayerAccount(player);

        if (account.permits().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.adminshop.permits.empty"), true);
            return 0;
        }

        StringBuilder permitsBuilder = new StringBuilder();
        for (String permit : account.permits()) {
            if (permit == null || permit.isEmpty()) { continue; }

            if (!permitsBuilder.isEmpty()) {
                permitsBuilder.append(", ");
            }

            permitsBuilder.append(permit);
        }
        source.sendSuccess(() -> Component.literal(permitsBuilder.toString()), true);
        return 1;
    }

    static int getPermit(CommandSourceStack source, String tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (tier == null || tier.isEmpty()) {
            source.sendFailure(Component.translatable("message.adminshop.permit.default"));
            return 0;
        }

        // Give item
        ItemStack permit = new ItemStack(ModItems.PERMIT.get());
        CompoundTag key = new CompoundTag();
        key.putString("key", tier);
        permit.set(DataComponents.CUSTOM_DATA, CustomData.of(key));

        boolean success = player.getInventory().add(permit);
        if (!success) {
            source.sendFailure(Component.translatable("message.adminshop.permit.give.failure"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.adminshop.permit.give.success", tier), true);
        return 1;
    }

    static int removePermit(CommandSourceStack source, String tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        if (tier == null || tier.isEmpty()) {
            source.sendFailure(Component.translatable("message.adminshop.permit.default"));
            return 0;
        }

        // Remove permit
        MoneyHelper.MoneyAccount account = MoneyHelper.get(level).getPlayerAccount(player);
        MoneyHelper.get(level).removePermit(account.teamId(), tier);
        source.sendSuccess(() -> Component.translatable("message.adminshop.permit.remove.success", tier), true);
        return 1;
    }
}
