package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkBar;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkOptions;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import com.mrgregles.bsp_server_stress_test.chunks.Spiral;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ST-7, chunks: the square grows outward and is shared between places, the ceiling holds, real chunks are loaded and
 * ticking with the mod's own ticket and let go again, the places file, the two options, the bar and the keys.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class ChunkGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;

    @GameTest(template = EMPTY)
    public void the_square_grows_outward_and_is_shared_between_places(GameTestHelper h) {
        ChunkPos c = new ChunkPos(10, -4);
        for (int n : new int[]{1, 9, 25, 49}) {
            List<ChunkPos> s = Spiral.around(c, n);
            int r = ((int) Math.sqrt(n) - 1) / 2;
            Set<ChunkPos> unique = new HashSet<>(s);
            if (s.size() != n || unique.size() != n || s.stream().anyMatch(p -> Math.abs(p.x - c.x) > r || Math.abs(p.z - c.z) > r)) {
                h.fail(n + " chunks are not a full square of radius " + r + ": " + s);
                return;
            }
        }
        List<ChunkPos> ten = Spiral.around(c, 10);
        if (!ten.get(0).equals(c) || ten.subList(0, 9).stream().anyMatch(p -> Math.abs(p.x - c.x) > 1 || Math.abs(p.z - c.z) > 1)
                || Math.max(Math.abs(ten.get(9).x - c.x), Math.abs(ten.get(9).z - c.z)) != 2) {
            h.fail("the tenth chunk does not start the next ring: " + ten);
            return;
        }
        if (!java.util.Arrays.equals(Spiral.share(10, 3), new int[]{4, 3, 3}) || !java.util.Arrays.equals(Spiral.share(0, 2), new int[]{0, 0})
                || !java.util.Arrays.equals(Spiral.share(4096, 1), new int[]{4096})) {
            h.fail("sharing is wrong");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void the_ceiling_counts_every_chunk_the_mod_holds(GameTestHelper h) {
        if (ChunkHold.allowed(100, 4000, 4096) != 96 || ChunkHold.allowed(10, 0, 4096) != 10
                || ChunkHold.allowed(5, 5000, 4096) != 0 || ChunkHold.allowed(-3, 0, 4096) != 0) {
            h.fail("the ceiling is not shared");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "chunks_live", timeoutTicks = 10000) // waits for chunks to load
    public void held_chunks_load_and_tick_with_the_mods_ticket_and_are_let_go(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        ChunkPos centre = new ChunkPos((at.getX() >> 4) + 40, at.getZ() >> 4);
        Place place = new Place("test", Level.OVERWORLD, centre);
        int got = ChunkHold.set(level.getServer(), "test", List.of(place), 9);
        if (got != 9 || ChunkHold.held("test") != 9 || !ChunkHold.holds(Level.OVERWORLD, centre)) {
            ChunkHold.release(level.getServer(), "test");
            h.fail("held " + got);
            return;
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    for (ChunkPos p : Spiral.around(centre, 9)) {
                        if (!level.getChunkSource().hasChunk(p.x, p.z)) {
                            throw new GameTestAssertException("still loading " + p);
                        }
                    }
                    if (!level.shouldTickBlocksAt(centre.toLong())) {
                        throw new GameTestAssertException("loaded but not ticking yet");
                    }
                })
                .thenExecute(() -> {
                    try {
                        if (ourTickets(level, centre) != 1) {
                            throw new GameTestAssertException("the centre has " + ourTickets(level, centre) + " of the mod's tickets");
                        }
                        ChunkBar.update(level.getServer());
                        if (ChunkBar.bar() == null || !ChunkBar.bar().getName().getString().startsWith("Chunks held ")) {
                            throw new GameTestAssertException("no bar while chunks are held");
                        }
                        ChunkHold.set(level.getServer(), "test", List.of(place), 1);
                        ChunkPos edge = Spiral.around(centre, 9).get(8);
                        if (ourTickets(level, edge) != 0 || ourTickets(level, centre) != 1 || ChunkHold.held("test") != 1) {
                            throw new GameTestAssertException("stepping down to 1 left " + ChunkHold.held("test"));
                        }
                    } finally {
                        ChunkHold.release(level.getServer(), "test");
                    }
                    if (ourTickets(level, centre) != 0 || ChunkHold.held("test") != 0 || ChunkHold.holds(Level.OVERWORLD, centre)) {
                        throw new GameTestAssertException("letting go left a ticket");
                    }
                    ChunkBar.update(level.getServer());
                    if (ChunkHold.total() == 0 && ChunkBar.bar() != null) {
                        throw new GameTestAssertException("the bar stayed with nothing held");
                    }
                })
                .thenSucceed();
    }

    private static int ourTickets(ServerLevel level, ChunkPos pos) {
        var set = level.getChunkSource().chunkMap.distanceManager.tickets.get(pos.toLong());
        if (set == null) {
            return 0;
        }
        int n = 0;
        for (Ticket<?> t : set) {
            if (t.getType() == ChunkHold.TICKET) {
                n++;
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = "chunks_places")
    public void places_are_kept_in_the_mods_folder_and_spawn_is_the_default(GameTestHelper h) {
        var server = h.getLevel().getServer();
        Path file = Places.file();
        String before = null;
        try {
            before = Files.exists(file) ? Files.readString(file) : null;
            Places.save(List.of());
            List<Place> use = Places.inUse(server);
            if (use.size() != 1 || !use.get(0).name().equals("spawn") || !use.get(0).centre().equals(new ChunkPos(server.overworld().getSharedSpawnPos()))) {
                h.fail("with no places, chunks are not held at spawn: " + use);
                return;
            }
            Places.put(server, new Place("base", Level.NETHER, new ChunkPos(3, -7)));
            Places.put(server, new Place("spawn", Level.OVERWORLD, new ChunkPos(999, 999)));
            List<Place> saved = Places.load(server);
            if (saved.size() != 2 || !saved.get(0).equals(new Place("base", Level.NETHER, new ChunkPos(3, -7)))
                    || !saved.get(1).centre().equals(new ChunkPos(server.overworld().getSharedSpawnPos()))) {
                h.fail("places read back as " + saved + " (spawn must follow the world spawn)");
                return;
            }
            if (!Places.remove(server, "base") || Places.remove(server, "base") || Places.load(server).size() != 1) {
                h.fail("removing a place");
                return;
            }
            for (String bad : new String[]{"Base", "a b", "../x", ""}) {
                if (Places.NAME.matcher(bad).matches()) {
                    h.fail("a bad name passed: " + bad);
                    return;
                }
            }
        } catch (Exception e) {
            h.fail("places file: " + e);
            return;
        } finally {
            try {
                if (before == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.writeString(file, before);
                }
            } catch (Exception ignored) {
                // the owner's places file is only touched on a test server
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void the_chunk_options_are_ready_and_fresh_land_is_far_out(GameTestHelper h) {
        for (String id : List.of(ChunkOptions.Hold.ID, ChunkOptions.Fresh.ID)) {
            Catalogue.Entry e = Catalogue.byId(id);
            if (e == null || !e.ready()) {
                h.fail(id + " is not ready in the catalogue");
                return;
            }
        }
        ServerLevel overworld = h.getLevel().getServer().overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        for (int i = 0; i < 20; i++) {
            Place p = ChunkOptions.Fresh.pick(overworld);
            double d = Math.hypot(p.centre().getMiddleBlockX() - spawn.getX(), p.centre().getMiddleBlockZ() - spawn.getZ());
            if (d < 29_000 || !overworld.getWorldBorder().isWithinBounds(p.centre().getMiddleBlockX(), p.centre().getMiddleBlockZ())) {
                h.fail("fresh land at " + p.centre() + ", " + (int) d + " blocks from spawn");
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void the_bar_reads_the_count_and_the_keys_run_their_commands(GameTestHelper h) {
        String title = ChunkBar.title(1200, 4096, 3870);
        if (!title.equals("Chunks held 1,200 / 4,096 · loaded on the server 3,870")) {
            h.fail("bar reads " + title);
            return;
        }
        Component keys = ChunkBar.keys();
        List<String> commands = new java.util.ArrayList<>();
        keys.visit((style, text) -> {
            ClickEvent click = style.getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND) {
                commands.add(click.getValue());
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        if (!commands.equals(List.of("/stress chunks add -100", "/stress chunks add -10", "/stress chunks add 10",
                "/stress chunks add 100", "/stress chunks release")) || !keys.getString().startsWith("Change it: [-100] [-10] [+10] [+100] [let go]")) {
            h.fail("keys: " + keys.getString() + " " + commands);
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "chunks_run")
    public void a_run_raises_held_chunks_step_by_step_and_lets_them_all_go(GameTestHelper h) {
        var server = h.getLevel().getServer();
        com.mrgregles.bsp_server_stress_test.run.Profile p = com.mrgregles.bsp_server_stress_test.run.Profile.of("chunk_test", "", List.of("chunks.hold"), List.of());
        p.stepSeconds = 3600;
        p.settleSeconds = 0;
        p.tuning(ChunkOptions.Hold.ID).step = 4;
        p.tuning(ChunkOptions.Hold.ID).ceiling = 8;
        List<String> said = new java.util.ArrayList<>();
        var run = new com.mrgregles.bsp_server_stress_test.run.StressRun(p,
                List.of(com.mrgregles.bsp_server_stress_test.run.Options.byId(ChunkOptions.Hold.ID)), said::add, 20_000_000_000L, 10_000_000_000L);
        long now = System.nanoTime();
        try {
            run.begin(server, now);
            run.up(server, now);
            if (ChunkHold.held(ChunkHold.PLACES) != 4) {
                h.fail("step 1 holds " + ChunkHold.held(ChunkHold.PLACES));
                return;
            }
            run.up(server, now);
            if (ChunkHold.held(ChunkHold.PLACES) != 8 || said.stream().noneMatch(l -> l.startsWith("Step 2: 8 chunks held at "))) {
                h.fail("step 2 holds " + ChunkHold.held(ChunkHold.PLACES) + "; said " + said);
                return;
            }
        } finally {
            run.stop(server, "the test");
            for (var f : new java.nio.file.Path[]{run.csv(), run.csv() == null ? null : com.mrgregles.bsp_server_stress_test.report.CsvReport.timingsOf(run.csv()), run.report()}) {
                try {
                    if (f != null) {
                        Files.deleteIfExists(f);
                    }
                } catch (Exception ignored) {
                    // a test file left in run/ harms nothing
                }
            }
        }
        if (ChunkHold.held(ChunkHold.PLACES) != 0) {
            h.fail("the run's end left " + ChunkHold.held(ChunkHold.PLACES) + " chunks held");
            return;
        }
        h.succeed();
    }
}
