package com.mrgregles.bsp_server_stress_test.bots;

import com.mojang.authlib.GameProfile;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import com.mrgregles.bsp_server_stress_test.text.Text;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Bots (owner decisions D7, D9, D22): real players placed in the server's player list through the normal login path,
 * on an in-memory connection, so every mod's login and per-player work runs for them, chunks load around them and the
 * packets a player is sent are built and encoded, then thrown away. Named {@code SSTBot_0001} and on, with the
 * offline-mode UUID of the name. The server never ticks a connection it did not open, so the bots are ticked here
 * (their player tick, their move, and the channel emptied), and they never answer or miss a keep-alive. Each bot belongs
 * to a group by how it behaves (stand, wander, fly) and is placed at one of the chunk places (or spawn). When a bot leaves, its
 * player, stats and advancement files and its line in {@code usercache.json} are deleted.
 */
public final class Bots {
    public static final String PREFIX = "SSTBot_";
    private static final Map<UUID, Bot> ONLINE = new LinkedHashMap<>();
    private static final Set<UUID> LOGIN_SEEN = new HashSet<>();

    /** How a group of bots behaves (D8), and the mover each bot of it gets. */
    public enum Behaviour {
        STAND, WANDER, FLY;

        Mover create(Place place, int index) {
            return switch (this) {
                case STAND -> new Mover.Stand(place, index);
                case WANDER -> new Mover.Wander(place, index);
                case FLY -> new Mover.Fly(place, index);
            };
        }
    }

    /** A bot: its number, its player and channel, its group, its place in the group, and how it moves. */
    public record Bot(int number, ServerPlayer player, EmbeddedChannel channel, Behaviour behaviour, int index, Mover mover) {
    }

    private Bots() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(Bots::onServerTick);
        // the login event, seen after every other mod's listener: proof the whole login ran for a bot
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerEvent.PlayerLoggedInEvent e) -> {
            if (isBot(e.getEntity())) {
                LOGIN_SEEN.add(e.getEntity().getUUID());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> releaseAll(e.getServer()));
    }

    public static String nameOf(int number) {
        return PREFIX + String.format(Locale.ROOT, "%04d", number);
    }

    public static UUID uuidOf(String name) {
        return UUIDUtil.createOfflinePlayerUUID(name);
    }

    public static synchronized boolean isBot(UUID uuid) {
        return ONLINE.containsKey(uuid);
    }

    public static boolean isBot(Player player) {
        return isBot(player.getUUID());
    }

    public static synchronized int count() {
        return ONLINE.size();
    }

    public static synchronized List<Bot> online() {
        return List.copyOf(ONLINE.values());
    }

    /** Whether the server's login event reached the end of its listeners for this bot. */
    public static synchronized boolean loginSeen(UUID uuid) {
        return LOGIN_SEEN.contains(uuid);
    }

    public static synchronized int count(Behaviour behaviour) {
        return (int) ONLINE.values().stream().filter(b -> b.behaviour == behaviour).count();
    }

    /** How many a group may have when the other groups have {@code others}: never past {@code limits.maxBots}. */
    public static int allowed(int requested, int others, int max) {
        return Math.max(0, Math.min(requested, max - others));
    }

    /**
     * Logs bots of a group in or out until {@code n} of it are online (fewer when {@code limits.maxBots} does not allow
     * it). New bots take the lowest free numbers and are shared between the chunk places (or spawn) in turn; the newest
     * of the group leave first. Returns how many of the group are online.
     */
    public static synchronized int setCount(MinecraftServer server, Behaviour behaviour, int n) {
        int target = allowed(n, ONLINE.size() - count(behaviour), StressConfig.MAX_BOTS.get());
        List<Place> places = Places.inUse(server);
        int next = 1;
        while (count(behaviour) < target) {
            while (server.getPlayerList().getPlayer(uuidOf(nameOf(next))) != null) {
                next++; // that number is online already (a bot, or someone using the name)
            }
            int index = count(behaviour);
            Place place = places.get(index % places.size());
            if (login(server, next++, behaviour, index, behaviour.create(place, index / places.size())) == null) {
                break;
            }
        }
        while (count(behaviour) > Math.max(0, target)) {
            List<Bot> group = ONLINE.values().stream().filter(b -> b.behaviour == behaviour).toList();
            logout(server, group.get(group.size() - 1), Text.raw("bots.left"));
        }
        return count(behaviour);
    }

    /** Logs every bot out. */
    public static synchronized void releaseAll(MinecraftServer server) {
        for (Bot b : List.copyOf(ONLINE.values())) {
            logout(server, b, Text.raw("bots.left"));
        }
    }

    private static ServerPlayer login(MinecraftServer server, int number, Behaviour behaviour, int index, Mover mover) {
        String name = nameOf(number);
        GameProfile profile = new GameProfile(uuidOf(name), name);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection); // the connection takes the channel when it opens
        try {
            // what a real connection has: packets are encoded as they would be for a player, then thrown away
            Connection.configureSerialization(channel.pipeline(), PacketFlow.SERVERBOUND);
            connection.setProtocol(ConnectionProtocol.PLAY);
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.warn("Bot packets will not be encoded: {}", e.toString());
        }
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile);
        Bot bot = new Bot(number, player, channel, behaviour, index, mover);
        ONLINE.put(profile.getId(), bot); // before the login, so every listener can tell it is a bot
        try {
            server.getPlayerList().placeNewPlayer(connection, player);
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.error("Bot {} could not log in", name, e);
            ONLINE.remove(profile.getId());
            channel.close();
            return null;
        }
        // bots take no harm (mobs still chase them): a dead bot would wait for a respawn click that never comes
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        try {
            mover.start(server, player);
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.error("Bot {} could not be placed", name, e);
        }
        drain(channel);
        return player;
    }

    private static void logout(MinecraftServer server, Bot bot, String reason) {
        UUID uuid = bot.player.getUUID();
        try {
            if (server.getPlayerList().getPlayer(uuid) == bot.player) {
                bot.player.connection.onDisconnect(Text.of(reason));
            }
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.error("Bot {} did not log out cleanly", bot.player.getGameProfile().getName(), e);
        }
        bot.channel.close();
        drain(bot.channel);
        ONLINE.remove(uuid);
        LOGIN_SEEN.remove(uuid);
        forgetFiles(server, uuid, bot.player.getGameProfile().getName());
    }

    /** Deletes what the server saved for a bot that left: its player files, stats, advancements, name cache line (D22). */
    static void forgetFiles(MinecraftServer server, UUID uuid, String name) {
        String id = uuid.toString();
        for (LevelResource dir : new LevelResource[]{LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR,
                LevelResource.PLAYER_ADVANCEMENTS_DIR}) {
            Path folder = server.getWorldPath(dir);
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                for (Path f : (Iterable<Path>) files::iterator) {
                    if (f.getFileName().toString().startsWith(id)) {
                        Files.deleteIfExists(f);
                    }
                }
            } catch (IOException e) {
                BSPServerStressTest.LOGGER.warn("Could not delete the files of bot {} in {}: {}", name, folder, e.toString());
            }
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            boolean changed = cache.profilesByName.remove(name.toLowerCase(Locale.ROOT)) != null;
            changed |= cache.profilesByUUID.remove(uuid) != null;
            if (changed) {
                cache.save();
            }
        }
    }

    /** Whether the server holds anything saved for this player (tests). */
    public static boolean hasFiles(MinecraftServer server, UUID uuid, String name) {
        String id = uuid.toString();
        for (LevelResource dir : new LevelResource[]{LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR,
                LevelResource.PLAYER_ADVANCEMENTS_DIR}) {
            Path folder = server.getWorldPath(dir);
            if (Files.isDirectory(folder)) {
                try (Stream<Path> files = Files.list(folder)) {
                    if (files.anyMatch(f -> f.getFileName().toString().startsWith(id))) {
                        return true;
                    }
                } catch (IOException e) {
                    return true;
                }
            }
        }
        GameProfileCache cache = server.getProfileCache();
        return cache != null && (cache.profilesByName.containsKey(name.toLowerCase(Locale.ROOT)) || cache.profilesByUUID.containsKey(uuid));
    }

    private static void drain(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.releaseOutbound();
        channel.releaseInbound();
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = e.getServer();
        synchronized (Bots.class) {
            for (Bot bot : List.copyOf(ONLINE.values())) {
                if (!bot.channel.isOpen() || bot.player.hasDisconnected()) {
                    // kicked by the server or a mod: finish the logout the server would have done for a real connection
                    logout(server, bot, Text.raw("bots.kicked"));
                    continue;
                }
                try {
                    bot.player.doTick();
                    bot.mover.tick(bot.player);
                } catch (RuntimeException ex) {
                    BSPServerStressTest.LOGGER.error("Bot {} failed its tick", bot.player.getGameProfile().getName(), ex);
                }
                drain(bot.channel);
            }
        }
    }
}
