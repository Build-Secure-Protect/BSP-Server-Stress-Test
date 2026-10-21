package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.Profile;
import com.mrgregles.bsp_server_stress_test.run.Profiles;
import com.mrgregles.bsp_server_stress_test.run.RunEngine;
import com.mrgregles.bsp_server_stress_test.run.RunGuard;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.run.StressRun;
import com.mrgregles.bsp_server_stress_test.run.TestSpinOption;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ST-2, the run engine: profiles choose options by pattern, steps and ceilings (capped by the settings), a run steps to
 * its ceilings by itself and releases everything, the stop rules (the limit, the emergency tick, a stop), stepping by
 * hand, the real-player guard, and the profile files. Tests that set the test load each run in a batch of their own, so
 * they never overlap.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class RunGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final long SECOND = 1_000_000_000L;

    private static StressOption spin() {
        StressOption o = Options.byId(TestSpinOption.ID);
        if (o == null) {
            throw new GameTestAssertException("the test load is not registered");
        }
        return o;
    }

    /** Deletes the CSV a test's run wrote, so tests leave nothing in the mod's folder. */
    private static void deleteCsv(StressRun... runs) {
        for (StressRun r : runs) {
            try {
                if (r != null && r.csv() != null) {
                    Files.deleteIfExists(r.csv());
                    Files.deleteIfExists(com.mrgregles.bsp_server_stress_test.report.CsvReport.timingsOf(r.csv()));
                }
                if (r != null && r.report() != null) {
                    Files.deleteIfExists(r.report());
                }
            } catch (Exception ignored) {
                // a test file left in run/ harms nothing
            }
        }
    }

    private static Profile testProfile(int step, int ceiling, int stepSeconds, int settleSeconds) {
        Profile p = Profile.of("test_run", "made by a test", List.of("test.*"), List.of());
        p.stepSeconds = stepSeconds;
        p.settleSeconds = settleSeconds;
        p.stopAverageMs = 50;
        p.tuning(TestSpinOption.ID).step = step;
        p.tuning(TestSpinOption.ID).ceiling = ceiling;
        return p;
    }

    @GameTest(template = EMPTY)
    public void profiles_choose_options_by_pattern_and_an_exclude_wins(GameTestHelper h) {
        Profile everything = Profile.of("a", "", List.of("*"), List.of("test.*"));
        Profile pregen = Profile.of("b", "", List.of("*"), List.of("test.*", "chunks.generate*", "bots.fly*"));
        Profile players = Profile.of("c", "", List.of("bots.*"), List.of());
        Profile both = Profile.of("d", "", List.of("bots.*", "bots.wander"), List.of("bots.wander"));
        String[][] cases = {
                // profile, id, expected
                {"a", "test.spin", "false"}, {"a", "machines.mekanism.digital_miner", "true"},
                {"b", "chunks.generate_fresh", "false"}, {"b", "chunks.hold", "true"}, {"b", "bots.fly_out", "false"},
                {"b", "bots.wander", "true"}, {"c", "bots.stand", "true"}, {"c", "machines.create.press", "false"},
                {"c", "botsx.thing", "false"}, {"d", "bots.wander", "false"}, {"d", "bots.stand", "true"},
        };
        for (String[] c : cases) {
            Profile p = switch (c[0]) {
                case "a" -> everything;
                case "b" -> pregen;
                case "c" -> players;
                default -> both;
            };
            if (p.includes(c[1]) != Boolean.parseBoolean(c[2])) {
                h.fail("profile " + c[0] + " on " + c[1] + ": expected " + c[2]);
                return;
            }
        }
        if (!Options.chosenBy(Profile.of("t", "", List.of("test.*"), List.of())).contains(spin())) {
            h.fail("a test.* profile did not choose the test load");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void steps_and_ceilings_come_from_the_profile_capped_by_the_settings(GameTestHelper h) {
        StressOption o = spin();
        Profile plain = Profile.of("p", "", List.of("test.*"), List.of());
        if (plain.stepOf(o) != o.defaultStep() || plain.ceilingOf(o) != Math.min(o.defaultCeiling(), StressConfig.MAX_TEST_MILLISECONDS.get())) {
            h.fail("without tuning: step " + plain.stepOf(o) + ", ceiling " + plain.ceilingOf(o));
            return;
        }
        Profile tuned = testProfile(3, 100_000, 60, 10);
        if (tuned.stepOf(o) != 3 || tuned.ceilingOf(o) != StressConfig.MAX_TEST_MILLISECONDS.get()) {
            h.fail("tuned: step " + tuned.stepOf(o) + ", ceiling " + tuned.ceilingOf(o) + " (the settings cap is "
                    + StressConfig.MAX_TEST_MILLISECONDS.get() + ")");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "run_auto", timeoutTicks = 10000) // waits on wall-clock seconds; a catching-up test server can run hundreds of ticks a second
    public void a_run_steps_to_its_ceilings_by_itself_and_releases_everything(GameTestHelper h) {
        if (RunEngine.current() != null) {
            h.fail("a run was already going");
            return;
        }
        List<String> said = new ArrayList<>();
        Profile p = testProfile(1, 2, 1, 0);
        p.stopAverageMs = 10_000;
        StressRun run = RunEngine.start(h.getLevel().getServer(), p, List.of(spin()), said::add);
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (RunEngine.current() != null) {
                        throw new GameTestAssertException("still running, step " + run.step());
                    }
                })
                .thenExecute(() -> {
                    try {
                        if (run.ended() != StressRun.End.CEILINGS) {
                            throw new GameTestAssertException("ended " + run.ended() + "; said " + said);
                        }
                        if (run.step() != 2 || run.results().size() != 3) {
                            throw new GameTestAssertException("step " + run.step() + ", " + run.results().size() + " measured steps; said " + said);
                        }
                        if (TestSpinOption.milliseconds() != 0 || run.level(TestSpinOption.ID) != 0) {
                            throw new GameTestAssertException("the test load was not released");
                        }
                        if (said.stream().noneMatch(l -> l.startsWith("Step 2: test load: 2 ms"))
                                || said.stream().noneMatch(l -> l.contains("finished: every option reached its ceiling"))) {
                            throw new GameTestAssertException("said " + said);
                        }
                        if (run.csv() == null || !Files.exists(run.csv())) {
                            throw new GameTestAssertException("no CSV written");
                        }
                        try {
                            List<String> report = run.report() == null ? List.of() : Files.readAllLines(run.report());
                            if (!report.contains("WHAT HURT MOST") || !report.contains("STEP BY STEP")
                                    || report.stream().noneMatch(l -> l.startsWith("1. The biggest rise in tick time came at step "))) {
                                throw new GameTestAssertException("the run's report reads " + report);
                            }
                        } catch (java.io.IOException e) {
                            throw new GameTestAssertException("could not read the run's report: " + e);
                        }
                        if (said.stream().noneMatch(l -> l.startsWith("Plain-English report: ")) || said.stream().noneMatch(l -> l.startsWith("- The biggest rise"))) {
                            throw new GameTestAssertException("the chat did not get the headline and the report's place: " + said);
                        }
                    } finally {
                        try {
                            deleteCsv(run);
                        } catch (Exception ignored) {
                            // a test file left in run/ harms nothing
                        }
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY, batch = "run_stops")
    public void a_run_stops_on_the_limit_on_a_hung_tick_and_on_stop(GameTestHelper h) {
        var server = h.getLevel().getServer();
        long now = System.nanoTime();
        List<String> said = new ArrayList<>();

        StressRun limit = new StressRun(testProfile(5, 60, 3600, 0), List.of(spin()), said::add, 20 * SECOND, 10 * SECOND);
        StressRun hung = new StressRun(testProfile(5, 60, 3600, 0), List.of(spin()), said::add, 20 * SECOND, 10 * SECOND);
        StressRun stopped = new StressRun(testProfile(5, 60, 3600, 0), List.of(spin()), said::add, 20 * SECOND, 10 * SECOND);
        try {
            stopsChecks(h, server, now, said, limit, hung, stopped);
        } finally {
            deleteCsv(limit, hung, stopped);
        }
    }

    private static void stopsChecks(GameTestHelper h, net.minecraft.server.MinecraftServer server, long now, List<String> said,
                                    StressRun limit, StressRun hung, StressRun stopped) {
        limit.begin(server, now);
        limit.up(server, now);
        limit.tick(server, now + SECOND, 30_000_000L, 49.9);
        if (limit.ended() != null) {
            h.fail("stopped under the limit: " + limit.ended());
            return;
        }
        limit.tick(server, now + 2 * SECOND, 30_000_000L, 50.5);
        if (limit.ended() != StressRun.End.LIMIT || TestSpinOption.milliseconds() != 0) {
            h.fail("over the limit: ended " + limit.ended() + ", test load " + TestSpinOption.milliseconds());
            return;
        }

        hung.begin(server, now);
        hung.up(server, now);
        hung.tick(server, now + SECOND, 10 * SECOND, -1);
        if (hung.ended() != null) {
            h.fail("a tick of exactly the emergency time stopped the run");
            return;
        }
        hung.tick(server, now + 2 * SECOND, 10 * SECOND + 1, -1);
        if (hung.ended() != StressRun.End.EMERGENCY || TestSpinOption.milliseconds() != 0) {
            h.fail("a hung tick: ended " + hung.ended() + ", test load " + TestSpinOption.milliseconds());
            return;
        }

        stopped.begin(server, now);
        stopped.up(server, now);
        stopped.stop(server, "a test");
        if (stopped.ended() != StressRun.End.STOPPED || TestSpinOption.milliseconds() != 0
                || said.stream().noneMatch(l -> l.equals("Run 'test_run' stopped by a test."))) {
            h.fail("stop: ended " + stopped.ended() + "; said " + said);
            return;
        }
        if (said.stream().noneMatch(l -> l.equals("Run 'test_run' stopped: the average tick over the last 20 s was 50.5 ms, past the limit of 50 ms."))
                || said.stream().noneMatch(l -> l.equals("Run 'test_run' stopped at once: one tick took 10.0 s."))) {
            h.fail("said " + said);
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "run_manual")
    public void a_run_steps_up_and_down_by_hand_and_holds(GameTestHelper h) {
        var server = h.getLevel().getServer();
        long now = System.nanoTime();
        List<String> said = new ArrayList<>();
        StressRun run = new StressRun(testProfile(2, 6, 3600, 0), List.of(spin()), said::add, 20 * SECOND, 10 * SECOND);
        try {
            run.begin(server, now);
            if (run.down(server, now)) {
                h.fail("stepped down from nothing");
                return;
            }
            run.up(server, now);
            run.up(server, now);
            if (run.level(TestSpinOption.ID) != 4 || TestSpinOption.milliseconds() != 4 || run.step() != 2) {
                h.fail("after two steps up: level " + run.level(TestSpinOption.ID) + ", step " + run.step());
                return;
            }
            run.down(server, now);
            if (run.level(TestSpinOption.ID) != 2 || TestSpinOption.milliseconds() != 2 || run.step() != 3) {
                h.fail("after a step down: level " + run.level(TestSpinOption.ID) + ", step " + run.step());
                return;
            }
            run.hold();
            run.tick(server, now + 7200 * SECOND, 1_000_000L, 1.0);
            if (run.step() != 3 || run.mode() != StressRun.Mode.HOLD) {
                h.fail("a held run stepped on its own");
                return;
            }
            run.auto();
            run.tick(server, now + 7200 * SECOND, 1_000_000L, 1.0);
            if (run.step() != 4 || run.level(TestSpinOption.ID) != 4) {
                h.fail("back on its own, it did not step: step " + run.step());
                return;
            }
            run.up(server, now);
            run.up(server, now);
            if (run.ended() != StressRun.End.CEILINGS || TestSpinOption.milliseconds() != 0) {
                h.fail("past the ceiling (6): ended " + run.ended() + ", level " + run.level(TestSpinOption.ID));
                return;
            }
        } finally {
            run.stop(server, "the test");
            deleteCsv(run);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void real_players_block_a_run_unless_allowed_and_confirmed(GameTestHelper h) {
        long t = System.nanoTime();
        if (RunGuard.decide(0, false, "x", t) != RunGuard.Answer.GO) {
            h.fail("no real players, yet not GO");
            return;
        }
        if (RunGuard.decide(2, false, "x", t) != RunGuard.Answer.REFUSED) {
            h.fail("real players and not allowed, yet not REFUSED");
            return;
        }
        if (RunGuard.decide(2, true, "x", t) != RunGuard.Answer.CONFIRM
                || RunGuard.decide(2, true, "y", t + SECOND) != RunGuard.Answer.CONFIRM
                || RunGuard.decide(2, true, "y", t + 2 * SECOND) != RunGuard.Answer.GO) {
            h.fail("confirming: another start in between must ask again, the same one within 3 s goes");
            return;
        }
        if (RunGuard.decide(2, true, "z", t) != RunGuard.Answer.CONFIRM
                || RunGuard.decide(2, true, "z", t + RunGuard.CONFIRM_NANOS + 1) != RunGuard.Answer.CONFIRM) {
            h.fail("a confirmation after 3 s started the run");
            return;
        }
        RunGuard.decide(0, false, "reset", t);
        ServerPlayer player = TestPlayers.mockServerPlayer(h);
        ServerPlayer admin = TestPlayers.mockServerPlayer(h);
        var ops = h.getLevel().getServer().getPlayerList().getOps();
        try {
            ops.add(new ServerOpListEntry(admin.getGameProfile(), 4, false));
            List<ServerPlayer> real = RunGuard.realPlayers(List.of(player, admin));
            if (real.size() != 1 || real.get(0) != player) {
                h.fail("real players: " + real.stream().map(p -> p.getGameProfile().getName()).toList());
                return;
            }
        } finally {
            ops.remove(admin.getGameProfile());
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void profiles_are_files_in_the_mods_folder(GameTestHelper h) {
        Profiles.writeStarters();
        for (String name : List.of("everything", "pre_generated", "players_only", "machines_only", "setups_only", "test_load")) {
            if (!Files.isRegularFile(Profiles.fileOf(name))) {
                h.fail("starter missing: " + name);
                return;
            }
        }
        String name = "zz_test_" + ThreadLocalRandom.current().nextInt(1_000_000);
        try {
            Profile p = testProfile(7, 21, 30, 5);
            p.name = name;
            Profiles.save(p);
            Profile back = Profiles.load(name);
            if (back == null || back.stepSeconds != 30 || back.settleSeconds != 5 || back.tuning(TestSpinOption.ID).step != 7
                    || back.tuning(TestSpinOption.ID).ceiling != 21 || !back.include.equals(List.of("test.*"))) {
                h.fail("the profile did not come back as saved");
                return;
            }
            if (!Profiles.names().contains(name) || Profiles.load("no_such_profile_here") != null) {
                h.fail("listing or a missing profile went wrong");
                return;
            }
            for (String bad : new String[]{"../x", "Big", "a b", "", "x".repeat(33)}) {
                if (Profiles.validName(bad)) {
                    h.fail("a bad name passed: " + bad);
                    return;
                }
            }
        } catch (Exception e) {
            h.fail("profile file: " + e);
            return;
        } finally {
            try {
                Files.deleteIfExists(Profiles.fileOf(name));
            } catch (Exception ignored) {
                // a test file left in run/ harms nothing
            }
        }
        h.succeed();
    }
}
