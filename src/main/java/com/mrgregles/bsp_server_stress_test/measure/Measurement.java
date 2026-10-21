package com.mrgregles.bsp_server_stress_test.measure;

import com.mrgregles.bsp_server_stress_test.timing.TimingReport;
import com.mrgregles.bsp_server_stress_test.timing.TimingSession;
import com.mrgregles.bsp_server_stress_test.timing.Timings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One stretch of measuring (a step of a run, an analysis, {@code /stress measure}): every tick the server completes
 * after it started, overall and per dimension, the new chunks generated, and (through a {@link TimingSession}) where
 * block-entity and entity tick time went. {@link Meter} feeds it; {@link #finish} takes the counts and gives the
 * {@link Result}.
 */
public final class Measurement {
    private final String label;
    private final int startTick;
    private final long startNanos;
    private final long runNanos;
    private final TickStats all = new TickStats();
    private final Map<ResourceKey<Level>, TickStats> perDimension = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Integer> generated = new LinkedHashMap<>();
    private final TimingSession timing;
    private Result result;

    Measurement(MinecraftServer server, String label, long runNanos) {
        this.label = label;
        this.startTick = server.getTickCount();
        this.startNanos = System.nanoTime();
        this.runNanos = runNanos;
        this.timing = Timings.open(server, label);
    }

    /** Stops the timers without a result (the server is stopping). */
    void abandon() {
        timing.close();
    }

    public String label() {
        return label;
    }

    public boolean finished() {
        return result != null;
    }

    /** Seconds left of a timed measurement; 0 for an open one or one that has run its time. */
    public long secondsLeft() {
        return runNanos <= 0 ? 0 : Math.max(0, (runNanos - (System.nanoTime() - startNanos) + 999_999_999L) / 1_000_000_000L);
    }

    boolean timeIsUp() {
        return runNanos > 0 && System.nanoTime() - startNanos >= runNanos;
    }

    /** Tick {@code tick} has just completed; its times are in the server's tick history. */
    void onTick(MinecraftServer server, int tick) {
        if (tick <= startTick || result != null) {
            return; // the tick that started it, or ticks after the end
        }
        int slot = tick % 100;
        all.add(server.tickTimes[slot]);
        for (Level level : server.getAllLevels()) {
            long[] times = server.getTickTime(level.dimension());
            if (times != null) {
                perDimension.computeIfAbsent(level.dimension(), k -> new TickStats()).add(times[slot]);
            }
        }
    }

    void onNewChunk(ResourceKey<Level> dimension) {
        if (result == null) {
            generated.merge(dimension, 1, Integer::sum);
        }
    }

    /** Ends the measurement (again: the same result) and reads the counts. */
    public Result finish(MinecraftServer server) {
        if (result == null) {
            double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            Map<String, Result.DimensionTicks> dims = new LinkedHashMap<>();
            perDimension.forEach((key, stats) -> dims.put(key.location().toString(),
                    new Result.DimensionTicks(stats.averageMs(), stats.worstMs(), generated.getOrDefault(key, 0))));
            generated.forEach((key, n) -> dims.putIfAbsent(key.location().toString(), new Result.DimensionTicks(0, 0, n)));
            result = new Result(label, all.count(), seconds, all.averageMs(), all.worstMs(), all.slowShare(),
                    dims, Counts.snapshot(server), timing.close());
            Meter.forget(this);
        }
        return result;
    }

    /** What a measurement found. Times in milliseconds; {@code slowShare} 0 to 1. */
    public record Result(String label, long ticks, double seconds, double averageMs, double worstMs, double slowShare,
                         Map<String, DimensionTicks> dimensions, Counts counts, TimingReport timings) {

        public record DimensionTicks(double averageMs, double worstMs, int chunksGenerated) {
        }

        /** Ticks completed per second of wall-clock time. */
        public double tps() {
            return seconds <= 0 ? 0 : ticks / seconds;
        }

        public int chunksGenerated() {
            return dimensions.values().stream().mapToInt(DimensionTicks::chunksGenerated).sum();
        }
    }
}
