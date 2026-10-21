package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.analyse.Analysis;
import com.mrgregles.bsp_server_stress_test.analyse.Watch;
import com.mrgregles.bsp_server_stress_test.analyse.WorldScan;
import com.mrgregles.bsp_server_stress_test.report.CsvReport;
import com.mrgregles.bsp_server_stress_test.report.PlainReport;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ST-6, the analyser: a crowd and a pile of items are found where they are, forced chunks are traced to /forceload and
 * to the mod whose chunk loader holds them, the report adds the world's sentences and sections, a whole analysis runs
 * and writes its files, and the watch starts only when it should.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class AnalyseGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final ResourceLocation OVERWORLD = ResourceLocation.tryBuild("minecraft", "overworld");

    @GameTest(template = EMPTY, batch = "analyse_scan")
    public void a_crowd_and_a_pile_of_items_are_found_where_they_are(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(4, 2, 4));
        List<Entity> made = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                Entity stand = EntityType.ARMOR_STAND.create(level);
                stand.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                level.addFreshEntity(stand);
                made.add(stand);
            }
            for (int i = 0; i < 15; i++) {
                ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, new ItemStack(Items.COBBLESTONE, 3));
                level.addFreshEntity(item);
                made.add(item);
            }
            // scanned in the same tick, before the items can merge
            WorldScan scan = WorldScan.scan(level.getServer());
            int cx = at.getX() >> 4;
            int cz = at.getZ() >> 4;
            WorldScan.Crowd crowd = scan.crowds().stream().filter(c -> c.dimension().equals(OVERWORLD) && c.cx() == cx && c.cz() == cz)
                    .findFirst().orElse(null);
            if (crowd == null || crowd.entities() < 35 || !crowd.topType().getPath().equals("armor_stand") || crowd.topCount() < 20) {
                h.fail("crowd: " + crowd + " among " + scan.crowds());
                return;
            }
            WorldScan.Pile pile = scan.piles().stream().filter(p -> p.dimension().equals(OVERWORLD) && p.cx() == cx && p.cz() == cz)
                    .findFirst().orElse(null);
            if (pile == null || pile.stacks() < 15 || pile.items() < 45) {
                h.fail("pile: " + pile);
                return;
            }
        } finally {
            made.forEach(Entity::discard);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "analyse_tickets")
    public void forced_chunks_are_traced_to_forceload_and_to_the_chunk_loaders_mod(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(4, 2, 4));
        // the test server force-loads its own test areas, so use chunks well away from them
        int cx = (at.getX() >> 4) + 60;
        int cz = at.getZ() >> 4;
        if (level.getForcedChunks().contains(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) {
            h.fail("the far chunk was already forced; pick another");
            return;
        }
        int before = tickets(WorldScan.scan(level.getServer()), "forced", "");
        int loadersBefore = tickets(WorldScan.scan(level.getServer()), "forge:block", BSPServerStressTest.MODID);
        try {
            level.setChunkForced(cx, cz, true);
            ForgeChunkManager.forceChunk(level, BSPServerStressTest.MODID, at, cx + 1, cz, true, false);
            WorldScan scan = WorldScan.scan(level.getServer());
            if (tickets(scan, "forced", "") != before + 1) {
                h.fail("/forceload of one chunk: " + before + " forced tickets before, " + tickets(scan, "forced", "") + " after");
                return;
            }
            WorldScan.Holder loader = scan.holders().stream()
                    .filter(x -> x.type().equals("forge:block") && x.modId().equals(BSPServerStressTest.MODID)).findFirst().orElse(null);
            if (loader == null || loader.tickets() != loadersBefore + 1 || loader.spots().stream().noneMatch(s -> s.cx() == cx + 1 && s.cz() == cz)) {
                h.fail("the chunk loader's mod not traced: " + scan.holders());
                return;
            }
            if (scan.holders().stream().noneMatch(x -> x.type().equals("start"))) {
                h.fail("the spawn area is not among the holders: " + scan.holders());
                return;
            }
            PlainReport r = Analysis.report(ReportGameTests.step("analyse", 5, 0, 300), scan, "");
            if (r.lines().stream().noneMatch(l -> l.startsWith("- chunk loaders placed as blocks (BSP-Server Stress Test): "))
                    || r.lines().stream().noneMatch(l -> l.startsWith("- chunks loaded with /forceload: "))
                    || r.lines().stream().noneMatch(l -> l.startsWith("- the spawn area, always loaded: "))) {
                h.fail("the report's holders read " + r.lines());
                return;
            }
        } finally {
            level.setChunkForced(cx, cz, false);
            ForgeChunkManager.forceChunk(level, BSPServerStressTest.MODID, at, cx + 1, cz, false, false);
        }
        h.succeed();
    }

    private static int tickets(WorldScan scan, String type, String modId) {
        return scan.holders().stream().filter(x -> x.type().equals(type) && x.modId().equals(modId)).mapToInt(WorldScan.Holder::tickets).sum();
    }

    @GameTest(template = EMPTY)
    public void the_analysis_report_adds_the_worlds_sentences_and_sections(GameTestHelper h) {
        WorldScan scan = new WorldScan(
                List.of(new WorldScan.Crowd(OVERWORLD, 2, -3, 84, ResourceLocation.tryBuild("minecraft", "cow"), 70)),
                List.of(new WorldScan.Pile(OVERWORLD, 5, 5, 120, 312)),
                List.of(new WorldScan.Holder("start", "", 1, List.of(new WorldScan.Spot(OVERWORLD, 0, 0))),
                        new WorldScan.Holder("forced", "", 12, List.of(new WorldScan.Spot(OVERWORLD, 10, 10)))),
                3);
        PlainReport r = Analysis.report(ReportGameTests.step("analyse", 8, 0, 300), scan, "Started by the watch: test.");
        List<String> head = r.headline();
        for (String want : List.of("The biggest crowd is 84 entities in the overworld at x 40, z -40, mostly Cow (70 of them).",
                "The biggest pile of dropped items is 312 items in 120 stacks, in the overworld at x 88, z 88.",
                "Besides players, chunks are held loaded by chunks loaded with /forceload (12).")) {
            if (!head.contains(want)) {
                h.fail("headline lacks: " + want + "\n" + head);
                return;
            }
        }
        for (String want : List.of("Started by the watch: test.", "CROWDS (the chunks with the most entities)",
                "1. the overworld at x 40, z -40: 84 entities, mostly Cow (70).", "ITEM PILES (the chunks with the most dropped items)",
                "1. the overworld at x 88, z 88: 312 items in 120 stacks.", "WHAT KEEPS CHUNKS LOADED",
                "- players: 3 online, each keeping the chunks around them loaded",
                "- the spawn area, always loaded: 1 ticket(s); the first at the overworld at x 8, z 8",
                "DIMENSIONS", "- the overworld: average tick 8.0 ms, worst 16.0 ms; 400 chunks loaded, 30 entities, 60 ticking blocks, 0 players.",
                "ABOUT THESE FIGURES")) {
            if (!r.lines().contains(want)) {
                h.fail("report lacks: " + want + "\n" + String.join("\n", r.lines()));
                return;
            }
        }
        if (r.lines().indexOf("WHAT KEEPS CHUNKS LOADED") > r.lines().indexOf("ABOUT THESE FIGURES")) {
            h.fail("the analyser's sections come after ABOUT THESE FIGURES");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "analyse_run", timeoutTicks = 10000) // waits on wall-clock seconds
    public void an_analysis_runs_and_writes_its_report(GameTestHelper h) {
        AtomicReference<Analysis.Done> got = new AtomicReference<>();
        Analysis.start(h.getLevel().getServer(), 1, "", got::set);
        if (!Analysis.running()) {
            h.fail("the analysis is not running");
            return;
        }
        h.startSequence()
                .thenWaitUntil(() -> {
                    if (got.get() == null) {
                        throw new GameTestAssertException("still analysing");
                    }
                })
                .thenExecute(() -> {
                    Analysis.Done d = got.get();
                    try {
                        if (Analysis.running() || d.txt() == null || d.csv() == null) {
                            throw new GameTestAssertException("files: " + d.txt() + ", " + d.csv());
                        }
                        List<String> text = Files.readAllLines(d.txt());
                        if (!text.contains("WHAT KEEPS CHUNKS LOADED") || text.stream().noneMatch(l -> l.startsWith("- the spawn area, always loaded: "))
                                || text.stream().noneMatch(l -> l.startsWith("BSP-Server Stress Test: analysis of the server as it is ("))) {
                            throw new GameTestAssertException("report reads " + text);
                        }
                    } catch (java.io.IOException e) {
                        throw new GameTestAssertException("reading the report: " + e);
                    } finally {
                        for (Path p : new Path[]{d.txt(), d.csv(), d.csv() == null ? null : CsvReport.timingsOf(d.csv())}) {
                            try {
                                if (p != null) {
                                    Files.deleteIfExists(p);
                                }
                            } catch (Exception ignored) {
                                // a test file left in run/ harms nothing
                            }
                        }
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public void the_watch_starts_an_analysis_only_when_it_should(GameTestHelper h) {
        long s = 1_000_000_000L;
        long now = 10_000 * s;
        long over = 60 * s;
        long quiet = 1800 * s;
        long never = Long.MIN_VALUE / 2;
        // on, idle, watched for long enough, quiet long enough, slow: start
        if (!Watch.shouldStart(true, false, now, now - 100 * s, never, over, quiet, 55, 50)) {
            h.fail("did not start when it should");
            return;
        }
        Object[][] no = {
                {false, false, now - 100 * s, never, 55.0, "off"},
                {true, true, now - 100 * s, never, 55.0, "a run or analysis going"},
                {true, false, now - 30 * s, never, 55.0, "not watched for the whole window yet"},
                {true, false, now - 100 * s, now - 600 * s, 55.0, "still quiet after the last one"},
                {true, false, now - 100 * s, never, 50.0, "not past the limit"},
                {true, false, now - 100 * s, never, -1.0, "no ticks read"},
        };
        for (Object[] c : no) {
            if (Watch.shouldStart((boolean) c[0], (boolean) c[1], now, (long) c[2], (long) c[3], over, quiet, (double) c[4], 50)) {
                h.fail("started although " + c[5]);
                return;
            }
        }
        h.succeed();
    }
}
