package com.mrgregles.bsp_server_stress_test.chunks;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The chunks the mod holds loaded (owner decisions D10, D23 to D25), with its own ticket type so the analyser and spark
 * name it. Two groups: {@link #PLACES} (the saved places, or spawn; set by {@code /stress chunks} or the
 * {@code chunks.hold} option) and {@link #FRESH} (new land far out, the {@code chunks.generate_fresh} option). Each group's
 * number is shared evenly between its places, each a square growing outward from its centre. Every group together stays
 * within {@code limits.maxChunks}. Tickets are never saved: a restart lets every chunk go, and nothing reaches the world
 * folder from here.
 */
public final class ChunkHold {
    public static final String PLACES = "places";
    public static final String FRESH = "fresh";
    public static final TicketType<ChunkPos> TICKET = TicketType.create(BSPServerStressTest.MODID + ":hold",
            Comparator.comparingLong(ChunkPos::toLong));
    /** Ticket distance: 2 makes the chunk entity-ticking (level 31), 0 only loaded (level 33). */
    static final int TICKING_DISTANCE = 2;
    static final int LOADED_DISTANCE = 0;

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();

    private static final class Group {
        List<Place> places = List.of();
        int wanted;
        /** What is held now, per dimension, with the distance each ticket was added at. */
        final Map<ResourceKey<Level>, Set<Long>> held = new LinkedHashMap<>();
        int distance = -1;

        int count() {
            return held.values().stream().mapToInt(Set::size).sum();
        }
    }

    private ChunkHold() {
    }

    /** How many a group may hold when the others hold {@code others}: never past the hard ceiling (D25). */
    public static int allowed(int requested, int others, int max) {
        return Math.max(0, Math.min(requested, max - others));
    }

    /**
     * Holds {@code count} chunks in the group, shared between {@code places}; fewer when the ceiling does not allow it.
     * Returns how many it now holds.
     */
    public static synchronized int set(MinecraftServer server, String group, List<Place> places, int count) {
        Group g = GROUPS.computeIfAbsent(group, k -> new Group());
        int others = total() - g.count();
        int target = allowed(count, others, StressConfig.MAX_CHUNKS.get());
        int distance = StressConfig.CHUNKS_TICKING.get() ? TICKING_DISTANCE : LOADED_DISTANCE;
        if (!g.places.equals(places) || g.distance != distance) {
            releaseGroup(server, g); // new places or a new mode: start the squares again
        }
        g.places = List.copyOf(places);
        g.distance = distance;
        g.wanted = count;
        Map<ResourceKey<Level>, Set<Long>> want = new LinkedHashMap<>();
        int[] share = Spiral.share(target, places.size());
        for (int i = 0; i < places.size(); i++) {
            Place p = places.get(i);
            Set<Long> set = want.computeIfAbsent(p.dimension(), k -> new LinkedHashSet<>());
            for (ChunkPos c : Spiral.around(p.centre(), share[i])) {
                set.add(c.toLong());
            }
        }
        apply(server, g, want);
        return g.count();
    }

    /**
     * Holds exactly these chunks for a group, always ticking (the chunks under placed machines, which must tick to be
     * measured). They count in the total the bar shows; the machines decide how many there are.
     */
    public static synchronized int setExact(MinecraftServer server, String group, Map<ResourceKey<Level>, Set<Long>> want) {
        Group g = GROUPS.computeIfAbsent(group, k -> new Group());
        if (g.distance != TICKING_DISTANCE) {
            releaseGroup(server, g);
            g.distance = TICKING_DISTANCE;
        }
        apply(server, g, want);
        return g.count();
    }

    /** Lets go of what is no longer wanted, then holds what is new. */
    private static void apply(MinecraftServer server, Group g, Map<ResourceKey<Level>, Set<Long>> want) {
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : g.held.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            Set<Long> keep = want.getOrDefault(e.getKey(), Set.of());
            for (Long c : List.copyOf(e.getValue())) {
                if (!keep.contains(c)) {
                    if (level != null) {
                        ChunkPos pos = new ChunkPos(c);
                        level.getChunkSource().removeRegionTicket(TICKET, pos, g.distance, pos);
                    }
                    e.getValue().remove(c);
                }
            }
        }
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : want.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            Set<Long> now = g.held.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>());
            for (Long c : e.getValue()) {
                if (now.add(c)) {
                    ChunkPos pos = new ChunkPos(c);
                    level.getChunkSource().addRegionTicket(TICKET, pos, g.distance, pos);
                }
            }
        }
    }

    /** Lets go of every chunk of a group. */
    public static synchronized void release(MinecraftServer server, String group) {
        Group g = GROUPS.get(group);
        if (g != null) {
            releaseGroup(server, g);
            g.wanted = 0;
        }
    }

    /** Lets go of every chunk the mod holds. */
    public static synchronized void releaseAll(MinecraftServer server) {
        for (Group g : GROUPS.values()) {
            releaseGroup(server, g);
            g.wanted = 0;
        }
    }

    private static void releaseGroup(MinecraftServer server, Group g) {
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : g.held.entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level != null) {
                for (Long c : e.getValue()) {
                    ChunkPos pos = new ChunkPos(c);
                    level.getChunkSource().removeRegionTicket(TICKET, pos, g.distance, pos);
                }
            }
        }
        g.held.clear();
    }

    /** Forgets everything without touching the server (it is stopping and takes its tickets with it). */
    public static synchronized void forget() {
        GROUPS.clear();
    }

    public static synchronized int total() {
        return GROUPS.values().stream().mapToInt(Group::count).sum();
    }

    public static synchronized int held(String group) {
        Group g = GROUPS.get(group);
        return g == null ? 0 : g.count();
    }

    public static synchronized List<Place> places(String group) {
        Group g = GROUPS.get(group);
        return g == null ? List.of() : g.places;
    }

    /** Whether a chunk is held by the mod (for tests and the analyser). */
    public static synchronized boolean holds(ResourceKey<Level> dimension, ChunkPos pos) {
        for (Group g : GROUPS.values()) {
            Set<Long> s = g.held.get(dimension);
            if (s != null && s.contains(pos.toLong())) {
                return true;
            }
        }
        return false;
    }

    /** Every chunk the mod holds, in every group, by dimension (world.save marks them changed before it saves). */
    public static synchronized Map<ResourceKey<Level>, Set<Long>> everyChunk() {
        Map<ResourceKey<Level>, Set<Long>> out = new LinkedHashMap<>();
        for (Group g : GROUPS.values()) {
            g.held.forEach((dim, set) -> out.computeIfAbsent(dim, k -> new LinkedHashSet<>()).addAll(set));
        }
        return out;
    }

    /** Every held chunk of a group, for tests. */
    public static synchronized List<ChunkPos> chunks(String group) {
        List<ChunkPos> out = new ArrayList<>();
        Group g = GROUPS.get(group);
        if (g != null) {
            g.held.values().forEach(s -> s.forEach(c -> out.add(new ChunkPos(c))));
        }
        return out;
    }
}
