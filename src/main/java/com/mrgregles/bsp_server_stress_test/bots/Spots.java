package com.mrgregles.bsp_server_stress_test.bots;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Spiral;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Where standing and wandering bots stand (owner feedback 2026-10-21: real players are thousands of blocks apart; no new
 * land): spots {@code bots.spacing} blocks apart on a square spiral out from each place, using only spots whose land the
 * bot would see (the server's view distance around it) is already generated, so a bot never makes new land. Each bot
 * has its own spot by its number. When a place has fewer generated spots than bots, the bots share them, and the log
 * says so once. Whether land is generated is read from the chunks' saved status, which loads and generates nothing.
 */
public final class Spots {
    /** How far out the spiral is searched: 50 rings, 10,201 spots. */
    static final int MAX_RINGS = 50;
    private static final List<ChunkPos> SPIRAL = Spiral.around(new ChunkPos(0, 0), (2 * MAX_RINGS + 1) * (2 * MAX_RINGS + 1));
    private static final Map<String, Found> FOUND = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    /** The generated spots found so far for one place and spacing, and how far along the spiral the search is. */
    private static final class Found {
        final List<int[]> spots = new ArrayList<>();
        int next;
    }

    private Spots() {
    }

    /** Forgets what was found (the spacing or the land may have changed). */
    public static synchronized void forget() {
        FOUND.clear();
        WARNED.clear();
    }

    /** The block (x, z) the bot in {@code slot} at this place stands on. */
    public static synchronized int[] spot(MinecraftServer server, Place place, int slot) {
        ServerLevel level = server.getLevel(place.dimension()) == null ? server.overworld() : server.getLevel(place.dimension());
        int cx = place.centre().getMiddleBlockX();
        int cz = place.centre().getMiddleBlockZ();
        int spacing = StressConfig.BOTS_SPACING.get();
        int view = server.getPlayerList().getViewDistance();
        String key = place.dimension().location() + "|" + cx + "," + cz + "|" + spacing + "|" + view;
        Found f = FOUND.computeIfAbsent(key, k -> new Found());
        f.next = pick(f.spots, f.next, slot + 1, cx, cz, spacing, (x, z) -> generated(level, x, z, view));
        if (f.spots.isEmpty()) {
            warnOnce(key, Text.raw("bots.no_generated_spot", place.name()));
            return new int[]{cx, cz};
        }
        if (slot >= f.spots.size()) {
            warnOnce(key, Text.raw("bots.few_generated_spots", place.name(), f.spots.size(), spacing));
        }
        return f.spots.get(slot % f.spots.size());
    }

    private static void warnOnce(String key, String line) {
        if (WARNED.add(key)) {
            BSPServerStressTest.LOGGER.warn(line);
        }
    }

    /**
     * Walks the spiral from {@code from} until {@code spots} holds {@code wanted} spots that {@code ok} accepts (or the
     * spiral ends), adding them; returns where the walk stopped. Spots are block (x, z), {@code spacing} apart from the
     * centre outward.
     */
    public static int pick(List<int[]> spots, int from, int wanted, int cx, int cz, int spacing, BiPredicate<Integer, Integer> ok) {
        int i = from;
        while (spots.size() < wanted && i < SPIRAL.size()) {
            ChunkPos cell = SPIRAL.get(i++);
            long x = cx + (long) cell.x * spacing;
            long z = cz + (long) cell.z * spacing;
            if (Math.abs(x) < 29_000_000 && Math.abs(z) < 29_000_000 && ok.test((int) x, (int) z)) {
                spots.add(new int[]{(int) x, (int) z});
            }
        }
        return i;
    }

    /**
     * Whether the land a bot standing at block (x, z) would see is already generated: the chunks across the square of
     * the view distance around it, 5 by 5 of them evenly spread (its edges, corners and middle), each fully generated.
     * Its own chunk is read first, so a spot out in new land costs one read.
     */
    public static boolean generated(ServerLevel level, int x, int z, int view) {
        int centreX = x >> 4;
        int centreZ = z >> 4;
        if (!full(level, new ChunkPos(centreX, centreZ))) {
            return false;
        }
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (!full(level, new ChunkPos(centreX + i * view / 2, centreZ + j * view / 2))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether a chunk is fully generated: loaded, or saved as full. Reads the saved status only; loads nothing. */
    public static boolean full(ServerLevel level, ChunkPos pos) {
        if (level.getChunkSource().hasChunk(pos.x, pos.z)) {
            return true;
        }
        return level.getChunkSource().chunkMap.isExistingChunkFull(pos);
    }
}
