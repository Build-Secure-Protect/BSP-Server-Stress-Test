package com.mrgregles.bsp_server_stress_test.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import java.util.UUID;

/**
 * Server players for tests, as in the other BSP mods. {@link GameTestHelper#makeMockServerPlayerInLevel()} gives its
 * connection no channel, and Forge's packet listener touches the channel in its constructor, so this builds the player
 * on an in-memory netty channel instead. Packets sent to the player go nowhere. A mock player is not in the server's
 * player list, so nothing broadcast reaches it and {@code getPlayer(uuid)} does not find it; the bots of a later task
 * are placed in the list and are made elsewhere.
 */
final class TestPlayers {
    private static int counter;

    private TestPlayers() {
    }

    static ServerPlayer mockServerPlayer(GameTestHelper h) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // registers and activates the handler, which gives the connection its channel
        String name = "sst_test_" + (++counter);
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
        return player;
    }
}
