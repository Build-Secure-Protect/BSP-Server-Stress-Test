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
 * player, stats and advancement files and its line in {@code usercache.json} are deleted, and BSP-Core 1.4.5 or newer
 * forgets its totems.
 * <p>
 * A bot's channel is laid out as a real connection's is ({@code timeout}, {@code legacy_query}, {@code splitter},
 * {@code decoder}, {@code prepender}, {@code encoder}, {@code packet_handler}), so plugins and mods that hook their
 * own handlers in by those names on every join find them; it carries what a Forge client with every mod the server has
 * hands over in its login handshake (the network version, the mods, the channels), which bots skip, so Forge and the
 * mods see an ordinary modded player; and it gives a loopback network address. A packet that cannot
 * be encoded for a bot is dropped for it, and any other error in its channel is ignored for it; each is noted once per
 * kind in the log with its stack trace, instead of closing the bot's connection as it would a real one's. When a bot is
 * disconnected all the same, the log says why.
 */
public final class Bots {
    public static final String PREFIX = "SSTBot_";
    private static final Map<UUID, Bot> ONLINE = new LinkedHashMap<>();
    private static final Set<UUID> LOGIN_SEEN = new HashSet<>();
    private static final Set<String> DROPPED_KINDS = new HashSet<>();
    private static final Set<String> ERROR_KINDS = new HashSet<>();
    private static int dropped;
    private static int errors;

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
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            releaseAll(e.getServer());
            Spots.forget();
        });
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

    /** Packets dropped because they could not be encoded for a bot, since the server started. */
    public static synchronized int droppedPackets() {
        return dropped;
    }

    /**
     * Sits last in a bot's channel, where every packet sent to it starts: a packet the channel fails to encode is noted
     * and counted, and its send counted as done, so the failure never reaches the connection (which would close).
     */
    static final class DropFailures extends io.netty.channel.ChannelOutboundHandlerAdapter {
        private final String bot;

        DropFailures(String bot) {
            this.bot = bot;
        }

        @Override
        public void write(io.netty.channel.ChannelHandlerContext ctx, Object msg, io.netty.channel.ChannelPromise promise) {
            io.netty.channel.ChannelPromise inner = ctx.newPromise();
            inner.addListener(f -> {
                if (!f.isSuccess()) {
                    noteDropped(bot, msg, f.cause());
                }
                promise.trySuccess();
            });
            ctx.write(msg, inner);
        }
    }

    /** Errors in bots' channels ignored for them, since the server started. */
    public static synchronized int connectionErrors() {
        return errors;
    }

    /**
     * Sits first and last in a bot's channel: an error that would reach the bot's connection (and close it) or run off
     * the end of the channel is noted and goes no further.
     */
    static final class IgnoreErrors extends io.netty.channel.ChannelInboundHandlerAdapter {
        private final String bot;

        IgnoreErrors(String bot) {
            this.bot = bot;
        }

        @Override
        public void exceptionCaught(io.netty.channel.ChannelHandlerContext ctx, Throwable cause) {
            noteError(bot, cause);
        }
    }

    static synchronized void noteError(String bot, Throwable cause) {
        errors++;
        if (ERROR_KINDS.add(cause.getClass().getName() + ": " + cause.getMessage())) {
            BSPServerStressTest.LOGGER.warn("An error in bot {}'s connection is ignored for bots (a real player's connection would close; said once per kind): {}",
                    bot, String.valueOf(cause), cause);
        }
    }

    static synchronized void noteDropped(String bot, Object packet, Throwable cause) {
        dropped++;
        String kind = packet.getClass().getName();
        if (DROPPED_KINDS.add(kind)) {
            BSPServerStressTest.LOGGER.warn("A {} could not be encoded for bot {}; packets like it that fail for a bot are dropped instead of closing its connection (said once per kind): {}",
                    kind, bot, String.valueOf(cause), cause);
        }
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
            // each bot its own spot by its number, across the groups: shared between the places in turn
            int number = next++;
            Place place = places.get((number - 1) % places.size());
            if (login(server, number, behaviour, index, behaviour.create(place, (number - 1) / places.size())) == null) {
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
        EmbeddedChannel channel = new EmbeddedChannel(false, false); // laid out first, then opened, as a real one
        io.netty.channel.ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("bsp_server_stress_test_ignore_errors", new IgnoreErrors(name));
        // a real server connection's handlers by name and in order (ServerConnectionListener), so code that hooks its
        // own in by those names finds them; the timeout and legacy query ones do nothing for a bot
        pipeline.addLast("timeout", new io.netty.channel.ChannelDuplexHandler());
        pipeline.addLast("legacy_query", new io.netty.channel.ChannelDuplexHandler());
        try {
            // packets are encoded as they would be for a player, then thrown away
            Connection.configureSerialization(pipeline, PacketFlow.SERVERBOUND);
        } catch (RuntimeException | LinkageError e) {
            BSPServerStressTest.LOGGER.warn("Bot packets will not be encoded: {}", e.toString());
        }
        pipeline.addLast("packet_handler", connection);
        pipeline.addLast("bsp_server_stress_test_drop_failures", new DropFailures(name));
        pipeline.addLast("bsp_server_stress_test_ignore_errors_last", new IgnoreErrors(name));
        asForgeClient(channel);
        try {
            channel.register(); // opens it: the connection takes the channel, as from a real socket
            connection.setProtocol(ConnectionProtocol.PLAY);
        } catch (Exception e) {
            BSPServerStressTest.LOGGER.error("Bot {} could not open its connection", name, e);
            channel.close();
            return null;
        }
        loopbackAddress(connection, number);
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

    /**
     * Gives the connection a loopback network address (port 40000 and the bot's number): an in-memory channel has none,
     * and code written for real connections (Bukkit's on Mohist, plugins) may expect one.
     */
    private static void loopbackAddress(Connection connection, int number) {
        java.net.InetSocketAddress address = new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 40000 + number);
        for (java.lang.reflect.Field f : Connection.class.getDeclaredFields()) {
            if (f.getType() == java.net.SocketAddress.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                try {
                    f.setAccessible(true);
                    f.set(connection, address);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    BSPServerStressTest.LOGGER.debug("Could not give a bot a network address: {}", e.toString());
                }
            }
        }
    }

    /**
     * Sets on the channel what Forge's login handshake records for a Forge client that has every mod and channel this
     * server has (Forge's {@code NetworkConstants.FML_NETVERSION} and {@code FML_CONNECTION_DATA}): without them Forge
     * and the mods cannot tell what kind of client a bot is and fail on it. Forge keeps these package-private, so they
     * are set by reflection; a Forge that has moved them leaves the bot without, which the log says.
     */
    @SuppressWarnings("unchecked")
    private static void asForgeClient(EmbeddedChannel channel) {
        try {
            Class<?> constants = Class.forName("net.minecraftforge.network.NetworkConstants");
            java.lang.reflect.Field versionKey = constants.getDeclaredField("FML_NETVERSION");
            versionKey.setAccessible(true);
            channel.attr((io.netty.util.AttributeKey<String>) versionKey.get(null)).set((String) constants.getField("NETVERSION").get(null));

            Map<String, org.apache.commons.lang3.tuple.Pair<String, String>> mods = new LinkedHashMap<>();
            for (net.minecraftforge.forgespi.language.IModInfo m : net.minecraftforge.fml.ModList.get().getMods()) {
                mods.put(m.getModId(), org.apache.commons.lang3.tuple.Pair.of(m.getDisplayName(), m.getVersion().toString()));
            }
            java.lang.reflect.Method channels = Class.forName("net.minecraftforge.network.NetworkRegistry").getDeclaredMethod("buildChannelVersions");
            channels.setAccessible(true);
            Class<?> dataClass = Class.forName("net.minecraftforge.network.ConnectionData");
            java.lang.reflect.Constructor<?> make = dataClass.getDeclaredConstructor(Map.class, Map.class);
            make.setAccessible(true);
            Object data = make.newInstance(mods, channels.invoke(null));
            java.lang.reflect.Field dataKey = constants.getDeclaredField("FML_CONNECTION_DATA");
            dataKey.setAccessible(true);
            channel.attr((io.netty.util.AttributeKey<Object>) dataKey.get(null)).set(data);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BSPServerStressTest.LOGGER.warn("A bot's connection could not be marked as a Forge client's: {}", e.toString());
        }
    }

    /** Why a bot's connection closed, as the server or a mod said it, or empty when nothing was said. */
    private static String reasonOf(Bot bot) {
        try {
            net.minecraft.network.chat.Component reason = bot.player.connection.connection.getDisconnectedReason();
            return reason == null ? "" : reason.getString();
        } catch (RuntimeException e) {
            return "";
        }
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
        // after BSP-Core's own logout, which places the totems a player carries: those go too (ST-C1)
        com.mrgregles.bsp_server_stress_test.compat.BspCore.forgetPlayer(server, uuid);
    }

    /**
     * {@code /stress cleanup}: has BSP-Core forget the totems of every bot name up to {@code limits.maxBots} that is not
     * online, also those of bots a server that stopped mid-run left behind. Returns the totems taken, or -1 when BSP-Core
     * cannot (absent, or older than 1.4.5).
     */
    public static synchronized int forgetBspCoreRecords(MinecraftServer server) {
        int taken = 0;
        for (int n = 1; n <= StressConfig.MAX_BOTS.get(); n++) {
            UUID uuid = uuidOf(nameOf(n));
            if (server.getPlayerList().getPlayer(uuid) != null) {
                continue;
            }
            int t = com.mrgregles.bsp_server_stress_test.compat.BspCore.forgetPlayer(server, uuid);
            if (t < 0) {
                return -1;
            }
            taken += t;
        }
        return taken;
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
                    String why = reasonOf(bot);
                    String name = bot.player.getGameProfile().getName();
                    BSPServerStressTest.LOGGER.warn(Text.raw("bots.kicked_log", name, why.isEmpty() ? Text.raw("bots.no_reason") : why));
                    logout(server, bot, why.isEmpty() ? Text.raw("bots.kicked") : Text.raw("bots.kicked_because", why));
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
