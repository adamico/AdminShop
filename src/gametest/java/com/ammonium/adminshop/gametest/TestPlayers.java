package com.ammonium.adminshop.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Players who log in for real, so FTB Teams gives each their own team, with names of their own,
 * which FTB Teams looks players up by. {@link GameTestHelper#makeMockServerPlayerInLevel} names
 * them all alike, and its connection takes no mod's payloads.
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

    /** Logs in a player with this name, which must be unique among the tests. */
    ServerPlayer join(String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // As a NeoForge client with every mod's payloads: FTB Library and AdminShop send theirs on login.
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        joined.add(player);
        return player;
    }

    private void leave() {
        joined.forEach(server.getPlayerList()::remove);
    }
}
