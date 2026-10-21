package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.command.StressCommands;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.measure.Meter;
import com.mrgregles.bsp_server_stress_test.timing.TimingReport;
import com.mrgregles.bsp_server_stress_test.timing.TimingSession;
import com.mrgregles.bsp_server_stress_test.timing.Timings;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * ST-4, the timers: a hopper's and a zombie's ticks are timed by what they are and by chunk, the measurement carries
 * them and its summary names the busiest, the report's sums and order are right, and the timers are off unless a
 * session is open.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class TimingGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final ResourceLocation HOPPER = ResourceLocation.tryBuild("minecraft", "hopper");
    static final ResourceLocation ZOMBIE = ResourceLocation.tryBuild("minecraft", "zombie");

    @GameTest(template = EMPTY, batch = "timing_live", timeoutTicks = 100)
    public void a_hopper_and_a_zombie_are_timed_by_what_they_are_and_by_chunk(GameTestHelper h) {
        BlockPos rel = new BlockPos(3, 2, 3);
        h.setBlock(rel, Blocks.HOPPER);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 8));
        zombie.setNoAi(true);
        Measurement m = Meter.start(h.getLevel().getServer(), "test timers");
        h.runAfterDelay(20, () -> {
            Measurement.Result r = m.finish(h.getLevel().getServer());
            h.setBlock(rel, Blocks.AIR);
            zombie.discard();
            TimingReport t = r.timings();
            TimingReport.Thing hopper = find(t, HOPPER);
            TimingReport.Thing zomb = find(t, ZOMBIE);
            if (hopper == null || !hopper.blockEntity() || hopper.calls() < 15 || hopper.nanos() <= 0) {
                h.fail("the hopper was not timed: " + hopper);
                return;
            }
            if (zomb == null || zomb.blockEntity() || zomb.calls() < 15) {
                h.fail("the zombie was not timed: " + zomb);
                return;
            }
            if (!t.blockEntityHookSeen() || !t.entityHookSeen()) {
                h.fail("a hook never ran");
                return;
            }
            BlockPos abs = h.absolutePos(rel);
            boolean chunkFound = t.chunks().stream().anyMatch(c -> c.x() == abs.getX() >> 4 && c.z() == abs.getZ() >> 4
                    && c.blockEntityCalls() >= 15);
            if (!chunkFound) {
                h.fail("the hopper's chunk is not among the timed chunks");
                return;
            }
            long calls = t.things().stream().mapToLong(TimingReport.Thing::calls).sum();
            if (t.overheadNanos() != calls * Timings.clockPairNanos() || Timings.clockPairNanos() <= 0) {
                h.fail("the timers' own cost is not counted: " + t.overheadNanos() + " for " + calls + " calls");
                return;
            }
            List<String> lines = StressCommands.summary(r);
            // the hopper is in the full list (checked above); the five busiest depend on what else the test world holds
            if (lines.stream().noneMatch(l -> l.startsWith("Busiest: "))
                    || lines.stream().noneMatch(l -> l.startsWith("Busiest chunks: the overworld at x "))
                    || lines.stream().noneMatch(l -> l.startsWith("The timers themselves took about "))) {
                h.fail("summary reads " + lines);
                return;
            }
            h.succeed();
        });
    }

    private static TimingReport.Thing find(TimingReport t, ResourceLocation id) {
        return t.things().stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
    }

    @GameTest(template = EMPTY)
    public void a_report_adds_up_by_mod_and_puts_the_busiest_first(GameTestHelper h) {
        ResourceLocation a = ResourceLocation.tryBuild("moda", "x");
        ResourceLocation b = ResourceLocation.tryBuild("modb", "y");
        ResourceLocation c = ResourceLocation.tryBuild("moda", "z");
        TimingReport t = new TimingReport("t", 10, 0.5, List.of(
                new TimingReport.Thing(a, true, 4_000_000, 40, 200_000),
                new TimingReport.Thing(b, false, 9_000_000, 10, 2_000_000),
                new TimingReport.Thing(c, true, 6_000_000, 20, 400_000)),
                List.of(), 1000, true, true);
        if (!t.things().get(0).id().equals(b) || !t.things().get(2).id().equals(a)) {
            h.fail("not busiest first: " + t.things());
            return;
        }
        if (Math.abs(t.msPerTick(10_000_000) - 1.0) > 1e-9 || Math.abs(t.perTick(40) - 4.0) > 1e-9) {
            h.fail("per tick: " + t.msPerTick(10_000_000) + ", " + t.perTick(40));
            return;
        }
        List<TimingReport.Mod> mods = t.mods();
        if (mods.size() != 2 || !mods.get(0).modId().equals("moda") || mods.get(0).nanos() != 10_000_000
                || mods.get(0).calls() != 60 || mods.get(1).nanos() != 9_000_000) {
            h.fail("mods: " + mods);
            return;
        }
        if (t.totalNanos() != 19_000_000) {
            h.fail("total " + t.totalNanos());
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "timing_off")
    public void the_timers_are_off_unless_a_session_is_open(GameTestHelper h) {
        if (Timings.on) {
            h.fail("the timers were on with nothing measuring");
            return;
        }
        TimingSession one = Timings.open(h.getLevel().getServer(), "one");
        TimingSession two = Timings.open(h.getLevel().getServer(), "two");
        one.close();
        if (!Timings.on) {
            h.fail("closing one of two sessions turned the timers off");
            return;
        }
        TimingReport r = two.close();
        if (Timings.on || two.close() != r) {
            h.fail("after the last session closed: on " + Timings.on);
            return;
        }
        h.succeed();
    }

    /** Bytes the server thread has allocated so far (HotSpot's per-thread count). */
    static long allocated() {
        return ((com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes();
    }

    /**
     * With no timing session open, the timers must cost nothing per tick, above all no allocation: on a server with tens
     * of thousands of ticking blocks, an object per tick made the garbage collector grow the heap by gigabytes while idle.
     * Measured as what 2,000 idle furnaces add to the server thread's allocation over 100 ticks, after a warm-up.
     */
    @GameTest(template = EMPTY, batch = "timing_idle_cost", timeoutTicks = 1200)
    public void idle_timers_allocate_nothing_per_tick(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var furnaces = com.mrgregles.bsp_server_stress_test.run.Options.byId("machines.minecraft.furnace");
        int n = 2000;
        furnaces.apply(server, n);
        int placed = com.mrgregles.bsp_server_stress_test.machines.Placements.count("machines.minecraft.furnace");
        long[] mark = new long[4];
        h.runAfterDelay(400, () -> mark[0] = allocated()); // after a warm-up, so the code is compiled as it runs for hours
        h.runAfterDelay(500, () -> {
            mark[1] = allocated();
            furnaces.release(server);
        });
        h.runAfterDelay(600, () -> mark[2] = allocated());
        h.runAfterDelay(700, () -> {
            mark[3] = allocated();
            double perTick = ((mark[1] - mark[0]) - (mark[3] - mark[2])) / (double) (placed * 100L);
            BSPServerStressTest.LOGGER.info("Idle timers: {} bytes allocated per block-entity tick ({} furnaces, with {} and without {} bytes over 100 ticks)",
                    String.format(java.util.Locale.ROOT, "%.1f", perTick), placed, mark[1] - mark[0], mark[3] - mark[2]);
            if (placed < n / 2) {
                h.fail("only " + placed + " furnaces placed");
            } else if (perTick > 8) {
                h.fail("idle block-entity ticks allocate " + String.format(java.util.Locale.ROOT, "%.1f", perTick) + " bytes each");
            } else {
                h.succeed();
            }
        });
    }
}
