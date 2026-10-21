package com.mrgregles.bsp_server_stress_test.timing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One stretch of timing: every block-entity and entity tick while it is open, added up by what ticked (the block, or
 * the entity type), by chunk and by mod. {@link #close} gives the {@link TimingReport}.
 */
public final class TimingSession {
    private final MinecraftServer server;
    private final String label;
    private final int startTick;
    private final long startNanos;
    private final Map<Object, Stat> byThing = new IdentityHashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, ChunkStat>> byChunk = new HashMap<>();
    private TimingReport report;

    static final class Stat {
        final boolean blockEntity;
        long nanos;
        long calls;
        long worst;

        Stat(boolean blockEntity) {
            this.blockEntity = blockEntity;
        }

        void add(long n) {
            nanos += n;
            calls++;
            if (n > worst) {
                worst = n;
            }
        }
    }

    static final class ChunkStat {
        long nanos;
        long blockEntityCalls;
        long entityCalls;
        final Map<Object, Long> byThing = new IdentityHashMap<>();
    }

    TimingSession(MinecraftServer server, String label) {
        this.server = server;
        this.label = label;
        this.startTick = server.getTickCount();
        this.startNanos = System.nanoTime();
    }

    void addBlockEntity(BlockEntity be, long nanos) {
        Block block = be.getBlockState().getBlock();
        byThing.computeIfAbsent(block, k -> new Stat(true)).add(nanos);
        Level level = be.getLevel();
        if (level != null) {
            BlockPos pos = be.getBlockPos();
            ChunkStat c = chunk(level, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            c.nanos += nanos;
            c.blockEntityCalls++;
            c.byThing.merge(block, nanos, Long::sum);
        }
    }

    void addEntity(Entity entity, long nanos) {
        EntityType<?> type = entity.getType();
        byThing.computeIfAbsent(type, k -> new Stat(false)).add(nanos);
        ChunkStat c = chunk(entity.level(), entity.chunkPosition().toLong());
        c.nanos += nanos;
        c.entityCalls++;
        c.byThing.merge(type, nanos, Long::sum);
    }

    private ChunkStat chunk(Level level, long key) {
        return byChunk.computeIfAbsent(level.dimension(), k -> new HashMap<>()).computeIfAbsent(key, k -> new ChunkStat());
    }

    public String label() {
        return label;
    }

    /** Stops timing and gives what was found (again: the same report). */
    public TimingReport close() {
        if (report != null) {
            return report;
        }
        Timings.close(this);
        long ticks = Math.max(0, server.getTickCount() - startTick);
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        List<TimingReport.Thing> things = new ArrayList<>();
        long calls = 0;
        for (Map.Entry<Object, Stat> e : byThing.entrySet()) {
            Stat s = e.getValue();
            calls += s.calls;
            things.add(new TimingReport.Thing(idOf(e.getKey()), s.blockEntity, s.nanos, s.calls, s.worst));
        }
        List<TimingReport.Chunk> chunks = new ArrayList<>();
        for (Map.Entry<ResourceKey<Level>, Map<Long, ChunkStat>> d : byChunk.entrySet()) {
            for (Map.Entry<Long, ChunkStat> c : d.getValue().entrySet()) {
                ChunkStat s = c.getValue();
                Object top = null;
                long topNanos = -1;
                for (Map.Entry<Object, Long> t : s.byThing.entrySet()) {
                    if (t.getValue() > topNanos) {
                        top = t.getKey();
                        topNanos = t.getValue();
                    }
                }
                ChunkPos pos = new ChunkPos(c.getKey());
                chunks.add(new TimingReport.Chunk(d.getKey().location(), pos.x, pos.z, s.nanos, s.blockEntityCalls,
                        s.entityCalls, top == null ? null : idOf(top), Math.max(0, topNanos)));
            }
        }
        report = new TimingReport(label, ticks, seconds, things, chunks, calls * Timings.clockPairNanos(),
                Timings.blockEntityHookSeen(), Timings.entityHookSeen());
        return report;
    }

    private static ResourceLocation idOf(Object thing) {
        if (thing instanceof Block b) {
            return BuiltInRegistries.BLOCK.getKey(b);
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) thing);
    }
}
