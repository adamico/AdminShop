package com.ammonium.adminshop.gametest;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.ammonium.adminshop.AdminShop;
import com.ammonium.adminshop.money.MoneyHelper;
import com.ammonium.adminshop.network.PacketAccountAddPermit;
import com.ammonium.adminshop.network.PacketBuyRequest;
import com.ammonium.adminshop.network.PacketSellRequest;
import com.ammonium.adminshop.network.PacketSetDetectorThreshold;
import com.ammonium.adminshop.network.PacketSetFluidBuyerRecipe;
import com.ammonium.adminshop.network.PacketSetItemBuyerRecipe;
import com.ammonium.adminshop.network.PacketSetSellerRecipe;
import com.ammonium.adminshop.network.PacketUpdateRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * What the game tests do with the Shop as players do: they place and use blocks through the server's own
 * interaction code, send the requests the client screens send, and read and set Balances.
 */
final class TestShop {

    private TestShop() {
    }

    static ResourceKey<Recipe<?>> offer(String path) {
        return ResourceKey.create(Registries.RECIPE, id(path));
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(AdminShop.MODID, path);
    }

    /** The Offer the server loaded under {@code key}. */
    static Recipe<?> recipe(GameTestHelper helper, ResourceKey<Recipe<?>> key) {
        return helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow().value();
    }

    static UUID team(GameTestHelper helper, ServerPlayer player) {
        return MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).teamId();
    }

    static void setBalance(GameTestHelper helper, ServerPlayer player, long balance) {
        setBalance(helper, team(helper, player), balance);
    }

    static void setBalance(GameTestHelper helper, UUID team, long balance) {
        MoneyHelper accounts = MoneyHelper.get(helper.getLevel());
        var account = accounts.getAccountById(team);
        helper.assertTrue(account != null, "the team has no Account");
        accounts.removeMoney(team, account.balance());
        accounts.addMoney(team, balance);
    }

    static void assertBalance(GameTestHelper helper, ServerPlayer player, long expected) {
        long balance = MoneyHelper.get(helper.getLevel()).getPlayerAccount(player).balance();
        helper.assertValueEqual(balance, expected, player.getPlainTextName() + "'s Balance");
    }

    static void assertBalance(GameTestHelper helper, UUID team, long expected) {
        var account = MoneyHelper.get(helper.getLevel()).getAccountById(team);
        helper.assertTrue(account != null, "the team has no Account");
        helper.assertValueEqual(account.balance(), expected, "the team's Balance");
    }

    /** {@code player} places {@code block} at {@code pos} on the platform, by clicking the floor with it in hand. */
    static void place(GameTestHelper helper, ServerPlayer player, Block block, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
        BlockPos floor = helper.absolutePos(pos.below());
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);

        InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);

        helper.assertTrue(result.consumesAction(), "placing " + block + " failed: " + result);
        helper.assertBlockPresent(block, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    /** A player with {@code balance} places {@code block} at {@code pos} and logs out. Returns their team. */
    static UUID placedBy(GameTestHelper helper, String name, long balance, Block block, BlockPos pos) {
        return TestPlayers.session(helper, players -> {
            ServerPlayer owner = players.join(name);
            setBalance(helper, owner, balance);
            place(helper, owner, block, pos);
            return team(helper, owner);
        });
    }

    /** {@code player} right-clicks the block at {@code pos} with whatever they hold. */
    static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** How many of {@code item} the player's inventory holds. */
    static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Handles {@code request} on the server as if {@code player}'s client had sent it. A request is handled as it
     * arrives from the network: an exception its handler throws is logged and does not reach the test.
     */
    static void send(ServerPlayer player, CustomPacketPayload request) {
        var context = new RequestContext(player, request.type().id());
        switch (request) {
            case PacketBuyRequest buy -> PacketBuyRequest.handle(buy, context);
            case PacketSellRequest sell -> PacketSellRequest.handle(sell, context);
            case PacketAccountAddPermit permit -> PacketAccountAddPermit.handle(permit, context);
            case PacketSetItemBuyerRecipe set -> PacketSetItemBuyerRecipe.handle(set, context);
            case PacketSetFluidBuyerRecipe set -> PacketSetFluidBuyerRecipe.handle(set, context);
            case PacketSetSellerRecipe set -> PacketSetSellerRecipe.handle(set, context);
            case PacketSetDetectorThreshold set -> PacketSetDetectorThreshold.handle(set, context);
            case PacketUpdateRequest update -> PacketUpdateRequest.handle(update, context);
            default -> throw new IllegalArgumentException("not a request AdminShop's server handles: " + request);
        }
    }

    /** The context of a request from a player's client, whose work runs at once on the server thread. */
    private record RequestContext(ServerPlayer player, Identifier payloadId) implements IPayloadContext {

        @Override
        public ICommonPacketListener listener() {
            return player.connection;
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            return NetworkRegistry.guard(CompletableFuture.runAsync(task, Runnable::run), payloadId);
        }

        @Override
        public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
            return NetworkRegistry.guard(CompletableFuture.supplyAsync(task, Runnable::run), payloadId);
        }

        @Override
        public PacketFlow flow() {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException("a test request handles no further payloads");
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            throw new UnsupportedOperationException("a test request is not part of the configuration phase");
        }
    }
}
