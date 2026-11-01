package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Logs in players for real, so FTB Teams gives each one its own team. Each player has a distinct name, because
 * FTB Teams looks players up by name. {@link GameTestHelper#makeMockServerPlayerInLevel} gives every player the
 * same name, and its connection accepts no mod payloads.
 */
final class TestPlayers {

    /** A test body that logs players in. */
    interface Body {
        void run(GameTestHelper helper, TestPlayers players) throws Exception;
    }

    private final MinecraftServer server;
    private final List<ServerPlayer> joined = new ArrayList<>();

    private TestPlayers(MinecraftServer server) {
        this.server = server;
    }

    /** Runs the body, logs its players out again and succeeds unless the body failed. */
    static Consumer<GameTestHelper> withPlayers(Body body) {
        return helper -> {
            var players = new TestPlayers(helper.getLevel().getServer());
            try {
                body.run(helper, players);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                players.leave();
            }
            helper.succeed();
        };
    }

    /** Work a test does while players are logged in, and the value it returns. */
    interface Session<T> {
        T run(TestPlayers players) throws Exception;
    }

    /**
     * Runs {@code session} and logs its players out before returning. Tests that run over several ticks, such as
     * machine tests, need this because a mock player cannot stay logged in across a tick. NeoForge would then
     * expect the config sync of a real login. Teams and Accounts remain, and a player who joins again with the
     * same name is the same player.
     */
    static <T> T session(GameTestHelper helper, Session<T> session) {
        var players = new TestPlayers(helper.getLevel().getServer());
        try {
            return session.run(players);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            players.leave();
        }
    }

    /** Logs in the player with this name, which must be unique among the tests. */
    ServerPlayer join(String name) {
        return join(name, message -> {
        });
    }

    /**
     * Logs in the player with this name and passes every system message the player receives from login on to
     * {@code heard}. The connection is set up as a NeoForge client with every mod's payloads, because FTB Library
     * and AdminShop send theirs on login.
     */
    ServerPlayer join(String name, Consumer<Component> heard) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name), false);
        var player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void sendSystemMessage(Component message, boolean overlay) {
                heard.accept(message);
                super.sendSystemMessage(message, overlay);
            }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        joined.add(player);
        return player;
    }

    private void leave() {
        joined.forEach(server.getPlayerList()::remove);
    }
}
