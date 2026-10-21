package com.mrgregles.bsp_server_stress_test.analyse;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.command.StressCommands;
import com.mrgregles.bsp_server_stress_test.measure.Meter;
import com.mrgregles.bsp_server_stress_test.run.RunEngine;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * The watch (owner decision D18): when it is on, it reads the tick times the server keeps anyway, and when the average
 * tick stays above {@code watch.limitMs} for {@code watch.overSeconds} it starts an analysis by itself, then keeps quiet
 * for {@code watch.quietMinutes}. Not while a run or another analysis is going. The finished analysis is told to the
 * people allowed to use {@code /stress} who are online, and to the server log.
 */
public final class Watch {
    private static long watchingSince;
    private static long lastTrigger = Long.MIN_VALUE / 2;

    private Watch() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> watchingSince = System.nanoTime());
        MinecraftForge.EVENT_BUS.addListener(Watch::onServerTick);
    }

    /** Turns the watch on or off and saves it in {@code settings.toml}. */
    public static void set(boolean on) {
        StressConfig.WATCH_ENABLED.set(on);
        StressConfig.WATCH_ENABLED.save();
        watchingSince = System.nanoTime();
    }

    /** Whether the watch should start an analysis now (every figure handed in, so it can be tested). */
    public static boolean shouldStart(boolean enabled, boolean busy, long now, long since, long last, long overNanos,
                                      long quietNanos, double averageMs, double limitMs) {
        return enabled && !busy && now - since >= overNanos && now - last >= quietNanos && averageMs > limitMs;
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !StressConfig.WATCH_ENABLED.get()) {
            return;
        }
        MinecraftServer server = e.getServer();
        if (server.getTickCount() % 20 != 0) {
            return; // once a second is plenty
        }
        long now = System.nanoTime();
        long over = StressConfig.WATCH_OVER_SECONDS.get() * 1_000_000_000L;
        double average = Meter.averageMsSince(now - over);
        boolean busy = RunEngine.current() != null || Analysis.running();
        if (!shouldStart(true, busy, now, watchingSince, lastTrigger, over, StressConfig.WATCH_QUIET_MINUTES.get() * 60_000_000_000L,
                average, StressConfig.WATCH_LIMIT_MS.get())) {
            return;
        }
        lastTrigger = now;
        String why = Text.raw("watch.why", Fmt.one(average), StressConfig.WATCH_OVER_SECONDS.get(), StressConfig.WATCH_LIMIT_MS.get());
        tell(server, Text.raw("watch.started", why));
        Analysis.start(server, StressConfig.WATCH_ANALYSE_SECONDS.get(), why, done -> {
            for (String line : done.report().headline()) {
                tell(server, "- " + line);
            }
            if (done.txt() != null) {
                tell(server, Text.raw("report.written", StressFiles.shown() + StressFiles.root().relativize(done.txt()).toString().replace('\\', '/')));
            }
        });
    }

    private static void tell(MinecraftServer server, String line) {
        BSPServerStressTest.LOGGER.info("[stress watch] {}", line);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (StressCommands.canUse(p)) {
                p.sendSystemMessage(Text.of(line));
            }
        }
    }
}
