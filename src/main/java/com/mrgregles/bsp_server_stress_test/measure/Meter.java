package com.mrgregles.bsp_server_stress_test.measure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Feeds every running {@link Measurement}. At the start of each server tick the tick before it is complete, and the
 * server's own tick history (overall, and Forge's per dimension) holds its time at {@code tickCount % 100}: reading it
 * there is right whatever order the end-of-tick listeners run in. New chunks are counted from Forge's chunk load event.
 */
public final class Meter {
    private static final List<Measurement> ACTIVE = new ArrayList<>();
    private static final List<Timed> TIMED = new ArrayList<>();
    /** The most recent ticks, always kept (two minutes at 20 a second): when each was read and how long it took. */
    private static final int RECENT = 2400;
    private static final long[] RECENT_AT = new long[RECENT];
    private static final long[] RECENT_NANOS = new long[RECENT];
    private static int recentCount;
    private static int recentNext;

    private record Timed(Measurement measurement, Consumer<Measurement.Result> then) {
    }

    private Meter() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(Meter::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(Meter::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            ACTIVE.forEach(Measurement::abandon);
            ACTIVE.clear();
            TIMED.clear();
            recentCount = 0;
        });
    }

    /** Starts an open measurement; the caller ends it with {@link Measurement#finish}. */
    public static Measurement start(MinecraftServer server, String label) {
        Measurement m = new Measurement(server, label, 0);
        ACTIVE.add(m);
        return m;
    }

    /** Starts a measurement that ends by itself after {@code seconds} of wall-clock time and hands its result on. */
    public static Measurement startTimed(MinecraftServer server, String label, int seconds, Consumer<Measurement.Result> then) {
        Measurement m = new Measurement(server, label, seconds * 1_000_000_000L);
        ACTIVE.add(m);
        TIMED.add(new Timed(m, then));
        return m;
    }

    /** The running timed measurement with this label, if any. */
    public static Measurement running(String label) {
        for (Timed t : TIMED) {
            if (t.measurement.label().equals(label)) {
                return t.measurement;
            }
        }
        return null;
    }

    static void forget(Measurement m) {
        ACTIVE.remove(m);
    }

    /** How long the last completed tick took, in nanoseconds; 0 before the first. */
    public static long lastTickNanos() {
        return recentCount == 0 ? 0 : RECENT_NANOS[(recentNext - 1 + RECENT) % RECENT];
    }

    /** The average of the ticks read at or after {@code sinceNanos} ({@link System#nanoTime}), in ms; -1 when there are none. */
    public static double averageMsSince(long sinceNanos) {
        long sum = 0;
        int n = 0;
        for (int i = 1; i <= recentCount; i++) {
            int slot = (recentNext - i + RECENT) % RECENT;
            if (RECENT_AT[slot] < sinceNanos) {
                break;
            }
            sum += RECENT_NANOS[slot];
            n++;
        }
        return n == 0 ? -1 : sum / (double) n / 1_000_000.0;
    }

    private static void noteTick(long nanos) {
        RECENT_AT[recentNext] = System.nanoTime();
        RECENT_NANOS[recentNext] = nanos;
        recentNext = (recentNext + 1) % RECENT;
        recentCount = Math.min(recentCount + 1, RECENT);
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        MinecraftServer server = e.getServer();
        int completed = server.getTickCount(); // not yet raised for the tick that is starting
        if (completed > 0) {
            noteTick(server.tickTimes[completed % 100]);
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (Measurement m : List.copyOf(ACTIVE)) {
            m.onTick(server, completed);
        }
        for (Timed t : List.copyOf(TIMED)) {
            if (t.measurement.timeIsUp()) {
                TIMED.remove(t);
                t.then.accept(t.measurement.finish(server));
            }
        }
    }

    private static void onChunkLoad(ChunkEvent.Load e) {
        if (e.isNewChunk() && e.getLevel() instanceof ServerLevel level && !ACTIVE.isEmpty()) {
            for (Measurement m : ACTIVE) {
                m.onNewChunk(level.dimension());
            }
        }
    }
}
