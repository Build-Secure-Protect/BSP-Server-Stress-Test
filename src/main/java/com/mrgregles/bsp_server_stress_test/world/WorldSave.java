package com.mrgregles.bsp_server_stress_test.world;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Map;
import java.util.Set;

/**
 * {@code world.save} (owner decision D34): the level is whole-world saves a minute. Before each save every chunk the mod
 * holds is marked as changed, so the save really writes all of them (region files in the throw-away world, D11). A world
 * whose saving is turned off ({@code /save-off}) is left so.
 */
public final class WorldSave implements StressOption {
    public static final String ID = "world.save";

    private static int perMinute;
    private static int ticks;
    private static int saves;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String describe(int level) {
        return Text.raw("option.save", Fmt.count(level));
    }

    @Override
    public int defaultStep() {
        return 1;
    }

    @Override
    public int defaultCeiling() {
        return 12;
    }

    @Override
    public Limit limit() {
        return Limit.SAVES_PER_MINUTE;
    }

    @Override
    public void apply(MinecraftServer server, int level) {
        synchronized (WorldSave.class) {
            perMinute = Math.max(0, level);
            ticks = 0;
        }
    }

    @Override
    public void release(MinecraftServer server) {
        synchronized (WorldSave.class) {
            perMinute = 0;
            ticks = 0;
        }
    }

    public static synchronized int saves() {
        return saves;
    }

    /** Ticks between two saves at this many a minute. */
    public static int everyTicks(int perMinute) {
        return perMinute <= 0 ? 0 : Math.max(1, 1200 / perMinute);
    }

    /** Called at the end of every server tick: saves when one is due. */
    static void tick(MinecraftServer server) {
        synchronized (WorldSave.class) {
            int every = everyTicks(perMinute);
            if (every == 0 || ++ticks < every) {
                return;
            }
            ticks = 0;
            saves++;
        }
        int marked = markHeld(server);
        server.saveEverything(true, false, false);
        BSPServerStressTest.LOGGER.debug("world.save: saved with {} held chunks marked", marked);
    }

    /** Marks every loaded chunk the mod holds as changed; returns how many. */
    public static int markHeld(MinecraftServer server) {
        int marked = 0;
        for (Map.Entry<ResourceKey<Level>, Set<Long>> e : ChunkHold.everyChunk().entrySet()) {
            ServerLevel level = server.getLevel(e.getKey());
            if (level == null) {
                continue;
            }
            for (long c : e.getValue()) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c));
                if (chunk != null) {
                    chunk.setUnsaved(true);
                    marked++;
                }
            }
        }
        return marked;
    }
}
