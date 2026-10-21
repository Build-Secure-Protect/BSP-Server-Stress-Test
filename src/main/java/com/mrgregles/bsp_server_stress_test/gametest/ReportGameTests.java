package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.measure.Counts;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.report.Causes;
import com.mrgregles.bsp_server_stress_test.report.PlainReport;
import com.mrgregles.bsp_server_stress_test.text.Text;
import com.mrgregles.bsp_server_stress_test.timing.TimingReport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * ST-5, the plain-English report, on made-up measurements whose numbers are known: the headline (the biggest rise,
 * the costliest thing and why, the busiest place, keeping up, the time the timers do not see, new land, memory), the
 * sections, the causes, and where the file goes.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class ReportGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final ResourceLocation HOPPER = ResourceLocation.tryBuild("minecraft", "hopper");
    static final ResourceLocation ZOMBIE = ResourceLocation.tryBuild("minecraft", "zombie");
    static final ResourceLocation OVERWORLD = ResourceLocation.tryBuild("minecraft", "overworld");

    /** A step: its label, average tick, new chunks, memory used of 1000 MB; 100 ticks timed with a hopper and a zombie. */
    static Measurement.Result step(String label, double averageMs, int generated, long memoryUsedMb) {
        TimingReport t = new TimingReport(label, 100, 5.0, List.of(
                new TimingReport.Thing(HOPPER, true, 300_000_000L, 500, 2_000_000L),
                new TimingReport.Thing(ZOMBIE, false, 100_000_000L, 200, 900_000L)),
                List.of(new TimingReport.Chunk(OVERWORLD, 0, 1, 350_000_000L, 500, 100, HOPPER, 300_000_000L)),
                2_000_000L, true, true);
        Counts c = new Counts(List.of(new Counts.Dimension(OVERWORLD.toString(), 400, 30, 60, 0)), memoryUsedMb, 1000);
        return new Measurement.Result(label, 100, 5.0, averageMs, averageMs * 2, averageMs > 50 ? 0.9 : 0.0,
                Map.of(OVERWORLD.toString(), new Measurement.Result.DimensionTicks(averageMs, averageMs * 2, generated)), c, t);
    }

    /** The same with {@code ticks} done in {@code seconds} of wall-clock time (fewer than 20 a second: time lost between ticks). */
    static Measurement.Result slowStep(String label, double averageMs, long ticks, double seconds, int generated) {
        Measurement.Result r = step(label, averageMs, generated, 300);
        return new Measurement.Result(label, ticks, seconds, averageMs, r.worstMs(), 0.031, r.dimensions(), r.counts(), r.timings());
    }

    private static boolean clean(GameTestHelper h, List<String> lines) {
        for (String l : lines) {
            if (l.contains("NaN") || l.contains(Text.PREFIX) || l.contains("%s")) {
                h.fail("a broken line: " + l);
                return false;
            }
        }
        return true;
    }

    @GameTest(template = EMPTY)
    public void a_run_report_says_what_hurt_most_and_why(GameTestHelper h) {
        List<Measurement.Result> steps = List.of(step("step 0 (the baseline, nothing added)", 5, 0, 300),
                step("step 1 (test load: 1 ms wasted every tick)", 6, 0, 300),
                step("step 2 (test load: 2 ms wasted every tick)", 20, 0, 300));
        PlainReport r = PlainReport.build("run of profile 'x'", "Run 'x' finished: every option reached its ceiling.", steps, true);
        List<String> head = r.headline();
        String[] want = {
                "The biggest rise in tick time came at step 2 (test load: 2 ms wasted every tick): the average tick went from 6.0 ms to 20.0 ms (+14.0 ms).",
                "At the end, Hopper (5 of them) cost 3.00 ms of the 20.0 ms average tick, 15% of it. Hoppers check every tick",
                "The busiest place was the overworld at x 8, z 24: 3.50 ms a tick, mostly Hopper.",
                "The server kept up with 20 ticks a second throughout, ending at an average tick of 20.0 ms in step 2 (",
                "16.0 ms of the average tick (80%) went to work the timers do not see",
        };
        if (head.size() != want.length) {
            h.fail(head.size() + " headline lines: " + head);
            return;
        }
        for (int i = 0; i < want.length; i++) {
            if (!head.get(i).startsWith(want[i])) {
                h.fail("headline " + (i + 1) + " reads: " + head.get(i));
                return;
            }
        }
        List<String> all = r.lines();
        for (String needed : List.of("BSP-Server Stress Test: run of profile 'x' (", "Run 'x' finished: every option reached its ceiling.",
                "WHAT HURT MOST", "1. The biggest rise", "STEP BY STEP",
                "Step 0 (the baseline, nothing added): 20.0 ticks a second, average tick 5.0 ms, worst 10.0 ms; 400 chunks, 30 entities, 60 ticking blocks.",
                "Step 1 (test load: 1 ms wasted every tick): 20.0 ticks a second, average tick 6.0 ms, worst 12.0 ms; 400 chunks, 30 entities, 60 ticking blocks. (+1.0 ms on the step before)",
                "   Busiest: Hopper, 3.00 ms a tick.",
                "WORST PLACES (during step 2 (test load: 2 ms wasted every tick))",
                "1. the overworld at x 8, z 24: 3.50 ms a tick, mostly Hopper (ticking blocks there: 5, entities: 1).",
                "1. Hopper (minecraft:hopper, ticking block): 3.00 ms a tick for 5 of them, 0.600 ms each, longest single tick 2.00 ms.",
                "2. Zombie (minecraft:zombie, entity): 1.00 ms a tick for 2 of them, 0.500 ms each, longest single tick 0.90 ms.",
                "- Minecraft: 4.00 ms a tick, 20% of the average tick.",
                "ABOUT THESE FIGURES", "The timers took about 0.02 ms a tick themselves, included in the figures above.")) {
            if (all.stream().noneMatch(l -> l.startsWith(needed))) {
                h.fail("missing line: " + needed + "\nreport:\n" + String.join("\n", all));
                return;
            }
        }
        if (clean(h, all)) {
            h.succeed();
        }
    }

    @GameTest(template = EMPTY)
    public void a_report_says_when_the_server_fell_behind(GameTestHelper h) {
        PlainReport fell = PlainReport.build("r", "", List.of(step("step 0 (the baseline, nothing added)", 30, 0, 300),
                step("step 1 (a)", 55, 0, 300), step("step 2 (b)", 80, 0, 300)), true);
        if (fell.headline().stream().noneMatch(l -> l.startsWith("The server fell behind 20 ticks a second at step 1 (a): the average tick passed 50 ms (55.0 ms, 20.0 ticks a second)."))) {
            h.fail("fell behind: " + fell.headline());
            return;
        }
        PlainReport already = PlainReport.build("r", "", List.of(step("step 0 (the baseline, nothing added)", 60, 0, 300),
                step("step 1 (a)", 70, 0, 300)), true);
        if (already.headline().stream().noneMatch(l -> l.startsWith("The server was already behind 20 ticks a second before anything was added: the average tick was 60.0 ms."))) {
            h.fail("already behind: " + already.headline());
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_measurement_report_has_no_steps_and_says_how_the_server_is_doing(GameTestHelper h) {
        PlainReport r = PlainReport.build("measurement of the server as it is", "", List.of(step("measure", 5, 0, 300)), false);
        if (r.lines().contains("STEP BY STEP") || r.headline().stream().anyMatch(l -> l.startsWith("The biggest rise"))) {
            h.fail("a single measurement has steps");
            return;
        }
        if (r.headline().stream().noneMatch(l -> l.equals("The server kept up with 20 ticks a second: average tick 5.0 ms, 20.0 ticks a second, 0.0% of ticks over 50 ms."))
                || !r.lines().contains("WORST THINGS (during the measurement)")) {
            h.fail("measurement report: " + r.lines());
            return;
        }
        if (clean(h, r.lines())) {
            h.succeed();
        }
    }

    @GameTest(template = EMPTY)
    public void new_land_and_full_memory_get_their_own_lines(GameTestHelper h) {
        PlainReport r = PlainReport.build("r", "", List.of(step("step 0 (the baseline, nothing added)", 5, 0, 300),
                step("step 1 (a)", 9, 120, 900)), true);
        String worldgen = Causes.worldgenMods();
        String land = worldgen.isEmpty()
                ? "120 new chunks were generated. Making new land is some of the costliest work a server does, and it falls in the time the timers do not see."
                : "120 new chunks were generated. Making new land is some of the costliest work a server does; on this server it runs through " + worldgen;
        if (r.headline().stream().noneMatch(l -> l.startsWith(land))) {
            h.fail("no new-land line: " + r.headline());
            return;
        }
        if (r.headline().stream().noneMatch(l -> l.startsWith("Memory was at 900 of 1,000 MB."))) {
            h.fail("no memory line: " + r.headline());
            return;
        }
        PlainReport calm = PlainReport.build("r", "", List.of(step("measure", 5, 0, 300)), false);
        if (calm.headline().stream().anyMatch(l -> l.startsWith("Memory") || l.contains("new chunks"))) {
            h.fail("lines without cause: " + calm.headline());
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void each_thing_gets_its_own_reason_or_its_kinds(GameTestHelper h) {
        String hopper = Causes.forThing(new TimingReport.Thing(HOPPER, true, 1, 1, 1));
        String zombie = Causes.forThing(new TimingReport.Thing(ZOMBIE, false, 1, 1, 1));
        String item = Causes.forThing(new TimingReport.Thing(ResourceLocation.tryBuild("minecraft", "item"), false, 1, 1, 1));
        String arrow = Causes.forThing(new TimingReport.Thing(ResourceLocation.tryBuild("minecraft", "arrow"), false, 1, 1, 1));
        String crucible = Causes.forThing(new TimingReport.Thing(ResourceLocation.tryBuild("bsp_core", "tetrium_crucible"), true, 1, 1, 1));
        if (!hopper.startsWith("Hoppers check every tick") || !zombie.startsWith("Mobs think every tick")
                || !item.startsWith("Dropped items look every tick") || !arrow.startsWith("It is an entity from Minecraft")
                || !crucible.startsWith("It is a ticking block from ")) {
            h.fail("causes: " + List.of(hopper, zombie, item, arrow, crucible));
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void the_report_is_a_text_file_beside_the_csvs(GameTestHelper h) {
        PlainReport r = PlainReport.build("r", "", List.of(step("measure", 5, 0, 300)), false);
        Path alone = null;
        Path beside = null;
        try {
            alone = r.write(null, "test report");
            beside = r.write(StressFiles.file("reports/2000-01-01_00-00-00 test beside.csv"), "unused");
            if (!alone.startsWith(StressFiles.root().resolve("reports")) || !alone.getFileName().toString().endsWith(" test report.txt")
                    || !beside.getFileName().toString().equals("2000-01-01_00-00-00 test beside.txt")) {
                h.fail("written to " + alone + " and " + beside);
                return;
            }
            if (!Files.readAllLines(alone).equals(r.lines())) {
                h.fail("the file does not hold the report");
                return;
            }
        } catch (Exception e) {
            h.fail("writing: " + e);
            return;
        } finally {
            for (Path p : new Path[]{alone, beside}) {
                try {
                    if (p != null) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ignored) {
                    // a test file left in run/ harms nothing
                }
            }
        }
        h.succeed();
    }

    /**
     * A server doing too few ticks a second is behind even when each tick is short (the owner's measurement of 2026-10-21:
     * 11.7 ticks a second at a 16 ms average tick was reported as keeping up): the report says so, and how much of each
     * second went to work between ticks, with the new land made.
     */
    @GameTest(template = EMPTY)
    public void a_report_counts_ticks_a_second_not_only_tick_length(GameTestHelper h) {
        PlainReport measured = PlainReport.build("m", "", List.of(slowStep("measure", 16, 117, 10, 645)), false);
        String want = "The server was behind 20 ticks a second: 11.7 ticks a second, though the average tick took only 16.0 ms. About 813 ms of each second went to work done between ticks, which neither the tick times nor the timers see: chunks loading and being generated (645 new chunks were generated), Java pausing to clear memory, and the server catching up on waiting work. 3.1% of ticks were over 50 ms.";
        if (measured.headline().stream().noneMatch(l -> l.equals(want)) || measured.headline().stream().anyMatch(l -> l.startsWith("The server kept up"))) {
            h.fail("measurement at 11.7 ticks a second: " + measured.headline());
            return;
        }
        PlainReport run = PlainReport.build("r", "", List.of(step("step 0 (the baseline, nothing added)", 10, 0, 300),
                slowStep("step 1 (a)", 12, 120, 10, 0)), true);
        if (run.headline().stream().noneMatch(l -> l.startsWith("The server fell behind 20 ticks a second at step 1 (a): 12.0 ticks a second, though the average tick took only 12.0 ms. About 856 ms of each second"))) {
            h.fail("run falling behind on ticks a second: " + run.headline());
            return;
        }
        PlainReport from = PlainReport.build("r", "", List.of(slowStep("step 0 (the baseline, nothing added)", 10, 150, 10, 0)), true);
        if (from.headline().stream().noneMatch(l -> l.startsWith("The server was already behind 20 ticks a second before anything was added: 15.0 ticks a second"))) {
            h.fail("behind from the start on ticks a second: " + from.headline());
            return;
        }
        if (clean(h, measured.lines()) && clean(h, run.lines()) && clean(h, from.lines())) {
            h.succeed();
        }
    }
}
