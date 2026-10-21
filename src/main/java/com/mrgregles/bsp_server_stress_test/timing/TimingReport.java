package com.mrgregles.bsp_server_stress_test.timing;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where block-entity and entity tick time went during a {@link TimingSession}: by what ticked, by chunk, and by mod,
 * busiest first. Times are totals over the session; {@link #msPerTick} turns a total into milliseconds per server tick.
 */
public record TimingReport(String label, long ticks, double seconds, List<Thing> things, List<Chunk> chunks,
                           long overheadNanos, boolean blockEntityHookSeen, boolean entityHookSeen) {

    public TimingReport {
        things = things.stream().sorted(Comparator.comparingLong(Thing::nanos).reversed()).toList();
        chunks = chunks.stream().sorted(Comparator.comparingLong(Chunk::nanos).reversed()).toList();
    }

    /** A block (its block entity) or an entity type: total time, how many ticks of it ran, and the longest one. */
    public record Thing(ResourceLocation id, boolean blockEntity, long nanos, long calls, long worstNanos) {
        public String modId() {
            return id.getNamespace();
        }
    }

    /** A chunk: its time from block entities and entities, how many of each ticked, and what cost most in it. */
    public record Chunk(ResourceLocation dimension, int x, int z, long nanos, long blockEntityCalls, long entityCalls,
                        ResourceLocation top, long topNanos) {
    }

    /** A mod's share: its blocks' and entities' time added up. */
    public record Mod(String modId, long nanos, long calls) {
    }

    public double msPerTick(long nanos) {
        return ticks == 0 ? 0 : nanos / (double) ticks / 1_000_000.0;
    }

    /** How many of a thing ticked in an average server tick (how many of them there were). */
    public double perTick(long calls) {
        return ticks == 0 ? 0 : calls / (double) ticks;
    }

    public long totalNanos() {
        return things.stream().mapToLong(Thing::nanos).sum();
    }

    public List<Mod> mods() {
        Map<String, long[]> sum = new LinkedHashMap<>();
        for (Thing t : things) {
            long[] s = sum.computeIfAbsent(t.modId(), k -> new long[2]);
            s[0] += t.nanos;
            s[1] += t.calls;
        }
        List<Mod> out = new ArrayList<>();
        sum.forEach((k, v) -> out.add(new Mod(k, v[0], v[1])));
        out.sort(Comparator.comparingLong(Mod::nanos).reversed());
        return out;
    }
}
