package com.mrgregles.bsp_server_stress_test.measure;

/**
 * Tick times added one at a time: how many, the average, the worst, and how many took longer than 50 ms (a server that
 * takes longer than 50 ms per tick runs below 20 ticks a second).
 */
public final class TickStats {
    public static final long SLOW_NANOS = 50_000_000L;

    private long count;
    private long sum;
    private long worst;
    private long slow;

    public void add(long nanos) {
        count++;
        sum += nanos;
        worst = Math.max(worst, nanos);
        if (nanos > SLOW_NANOS) {
            slow++;
        }
    }

    public long count() {
        return count;
    }

    public double averageMs() {
        return count == 0 ? 0 : sum / (double) count / 1_000_000.0;
    }

    public double worstMs() {
        return worst / 1_000_000.0;
    }

    /** The share of ticks over 50 ms, 0 to 1. */
    public double slowShare() {
        return count == 0 ? 0 : slow / (double) count;
    }
}
