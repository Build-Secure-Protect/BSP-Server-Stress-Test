package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.command.StressCommands;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.measure.Meter;
import com.mrgregles.bsp_server_stress_test.measure.TickStats;
import com.mrgregles.bsp_server_stress_test.report.CsvReport;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ST-1, the measuring core: tick figures add up as they should, a measurement sees every tick the server completes and
 * each dimension, new chunks are counted, a timed measurement ends by itself, and the CSV lands in the mod's folder.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class MeasureGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;

    @GameTest(template = EMPTY)
    public void tick_figures_give_the_average_the_worst_and_the_slow_share(GameTestHelper h) {
        TickStats s = new TickStats();
        for (long ms : new long[]{10, 30, 50, 60, 100}) {
            s.add(ms * 1_000_000L);
        }
        if (s.count() != 5 || Math.abs(s.averageMs() - 50.0) > 1e-9 || Math.abs(s.worstMs() - 100.0) > 1e-9) {
            h.fail("count " + s.count() + ", average " + s.averageMs() + ", worst " + s.worstMs());
            return;
        }
        // 50 ms exactly still keeps 20 ticks a second; only 60 and 100 are slow
        if (Math.abs(s.slowShare() - 0.4) > 1e-9) {
            h.fail("slow share " + s.slowShare() + ", expected 0.4");
            return;
        }
        TickStats empty = new TickStats();
        if (empty.averageMs() != 0 || empty.slowShare() != 0) {
            h.fail("an empty set of ticks did not read as zero");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void a_measurement_sees_every_tick_and_each_dimension(GameTestHelper h) {
        Measurement m = Meter.start(h.getLevel().getServer(), "test ticks");
        h.runAfterDelay(40, () -> {
            Measurement.Result r = m.finish(h.getLevel().getServer());
            // 40 ticks of the test pass between start and finish; the tick that started it is not counted, nor the one
            // running the check (it has not completed)
            if (r.ticks() < 37 || r.ticks() > 41) {
                h.fail("counted " + r.ticks() + " ticks over 40");
                return;
            }
            if (r.averageMs() <= 0 || r.worstMs() < r.averageMs()) {
                h.fail("average " + r.averageMs() + " ms, worst " + r.worstMs() + " ms");
                return;
            }
            String overworld = Level.OVERWORLD.location().toString();
            if (!r.dimensions().containsKey(overworld) || r.dimensions().get(overworld).averageMs() <= 0) {
                h.fail("no overworld tick times: " + r.dimensions().keySet());
                return;
            }
            if (r.counts().dimension(overworld) == null || r.counts().dimension(overworld).loadedChunks() <= 0) {
                h.fail("the overworld shows no loaded chunks");
                return;
            }
            if (r.counts().memoryUsedMb() <= 0 || r.counts().memoryMaxMb() < r.counts().memoryUsedMb()) {
                h.fail("memory " + r.counts().memoryUsedMb() + " of " + r.counts().memoryMaxMb());
                return;
            }
            if (m.finish(h.getLevel().getServer()) != r) {
                h.fail("finishing twice gave a different result");
                return;
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void new_chunks_are_counted_and_old_ones_are_not(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Measurement m = Meter.start(level.getServer(), "test chunks");
        // a chunk far out that no earlier run generated (the test world persists), then one already loaded
        int x = 150_000 + ThreadLocalRandom.current().nextInt(100_000);
        int z = 150_000 + ThreadLocalRandom.current().nextInt(100_000);
        level.getChunk(x, z, ChunkStatus.FULL, true);
        level.getChunk(h.absolutePos(net.minecraft.core.BlockPos.ZERO));
        Measurement.Result r = m.finish(level.getServer());
        int counted = r.dimensions().get(Level.OVERWORLD.location().toString()) == null ? 0
                : r.dimensions().get(Level.OVERWORLD.location().toString()).chunksGenerated();
        if (counted < 1 || r.chunksGenerated() != counted) {
            h.fail("new chunks counted: " + counted + " in the overworld, " + r.chunksGenerated() + " in all");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 10000) // waits on wall-clock seconds; a catching-up test server can run hundreds of ticks a second
    public void a_timed_measurement_ends_by_itself_after_its_seconds(GameTestHelper h) {
        AtomicReference<Measurement.Result> got = new AtomicReference<>();
        long started = System.nanoTime();
        Meter.startTimed(h.getLevel().getServer(), "test timed", 1, got::set);
        if (Meter.running("test timed") == null) {
            h.fail("the timed measurement is not listed as running");
            return;
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (got.get() == null) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("still measuring");
                    }
                })
                .thenExecute(() -> {
                    double waited = (System.nanoTime() - started) / 1e9;
                    if (got.get().seconds() < 1.0 || waited < 1.0) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("ended after " + got.get().seconds() + " s");
                    }
                    if (Meter.running("test timed") != null) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("still listed as running after it ended");
                    }
                    List<String> lines = StressCommands.summary(got.get());
                    if (lines.size() < 2 || !lines.get(0).startsWith("Measured ") || lines.stream().noneMatch(l -> l.startsWith("Whole server: "))) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("summary reads " + lines);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public void the_csv_lands_in_reports_inside_the_mods_folder(GameTestHelper h) {
        Measurement m = Meter.start(h.getLevel().getServer(), "test, csv");
        h.runAfterDelay(5, () -> {
            Measurement.Result r = m.finish(h.getLevel().getServer());
            Path file = null;
            try {
                file = CsvReport.write("test csv", List.of(r));
                if (!file.startsWith(StressFiles.root().resolve("reports")) || !file.getFileName().toString().endsWith(" test csv.csv")) {
                    h.fail("written to " + file);
                    return;
                }
                List<String> lines = Files.readAllLines(file);
                if (!lines.get(0).equals(CsvReport.HEADER)) {
                    h.fail("header reads " + lines.get(0));
                    return;
                }
                if (lines.size() != 2 + r.dimensions().size() || !lines.get(1).startsWith("\"test, csv\",all,")) {
                    h.fail(lines.size() + " lines; second reads " + lines.get(1));
                    return;
                }
                int columns = CsvReport.HEADER.split(",").length;
                for (String line : lines.subList(1, lines.size())) {
                    if (line.replace("\"test, csv\"", "label").split(",", -1).length != columns) {
                        h.fail("a row has the wrong number of columns: " + line);
                        return;
                    }
                }
                h.succeed();
            } catch (Exception e) {
                h.fail("could not write or read the CSV: " + e);
            } finally {
                if (file != null) {
                    try {
                        Files.deleteIfExists(file);
                        Files.deleteIfExists(CsvReport.timingsOf(file));
                    } catch (Exception ignored) {
                        // a test file left behind in run/ harms nothing
                    }
                }
            }
        });
    }
}
