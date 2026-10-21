package com.mrgregles.bsp_server_stress_test.timing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * The timers on every block-entity tick and every entity tick (owner decision D16), fed by the two mixins in
 * {@code mixin/}. They do nothing but read one flag unless a {@link TimingSession} is open: a run's step, a measurement,
 * an analysis. Each timed tick costs two clock reads; {@link #clockPairNanos()} measures what that costs on this
 * machine, so a report can say how much of the tick the timers themselves took.
 *
 * <p>Called on the server thread only.
 */
public final class Timings {
    /** Read by the mixins on every tick: true while at least one session is open. */
    public static volatile boolean on;
    private static TimingSession[] sessions = new TimingSession[0];
    /** Whether each hook has ever run: a hook Mohist or another mod kept from applying never does. */
    private static boolean blockEntityHookSeen;
    private static boolean entityHookSeen;
    private static long clockPairNanos = -1;

    private Timings() {
    }

    public static synchronized TimingSession open(net.minecraft.server.MinecraftServer server, String label) {
        TimingSession s = new TimingSession(server, label);
        List<TimingSession> list = new ArrayList<>(List.of(sessions));
        list.add(s);
        sessions = list.toArray(new TimingSession[0]);
        on = true;
        return s;
    }

    static synchronized void close(TimingSession s) {
        List<TimingSession> list = new ArrayList<>(List.of(sessions));
        list.remove(s);
        sessions = list.toArray(new TimingSession[0]);
        on = sessions.length > 0;
    }

    public static void blockEntity(BlockEntity be, long nanos) {
        blockEntityHookSeen = true;
        for (TimingSession s : sessions) {
            s.addBlockEntity(be, nanos);
        }
    }

    public static void entity(Entity entity, long nanos) {
        entityHookSeen = true;
        for (TimingSession s : sessions) {
            s.addEntity(entity, nanos);
        }
    }

    public static boolean blockEntityHookSeen() {
        return blockEntityHookSeen;
    }

    public static boolean entityHookSeen() {
        return entityHookSeen;
    }

    /** What one timed tick's two clock reads cost here, in nanoseconds (measured once). */
    public static long clockPairNanos() {
        if (clockPairNanos < 0) {
            int n = 20_000;
            long sink = 0;
            long start = System.nanoTime();
            for (int i = 0; i < n; i++) {
                sink += System.nanoTime();
                sink -= System.nanoTime();
            }
            long took = System.nanoTime() - start;
            clockPairNanos = Math.max(1, took / n + (sink == 42 ? 1 : 0));
        }
        return clockPairNanos;
    }
}
