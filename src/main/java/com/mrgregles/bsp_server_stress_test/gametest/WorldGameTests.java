package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.world.Contraptions;
import com.mrgregles.bsp_server_stress_test.world.RandomTicks;
import com.mrgregles.bsp_server_stress_test.world.WorldOption;
import com.mrgregles.bsp_server_stress_test.world.WorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ST-14, the world options (owner decisions D27 to D34): every one is a ready option; a redstone clock pulses and a
 * hopper ring passes its items round, and both go without a drop; basins, lights and sand do their repeated work and
 * go without leaving water, sand or falling blocks; what a stopped run left (contraptions and the random tick speed) is
 * put back; world saving marks the held chunks and saves them.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class WorldGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final List<String> IDS = List.of(WorldOption.REDSTONE_CLOCKS, WorldOption.HOPPERS, WorldOption.FLUIDS,
            RandomTicks.ID, WorldOption.LIGHTING, WorldOption.FALLING_BLOCKS, WorldSave.ID);

    @GameTest(template = EMPTY)
    public void the_world_options_are_ready_with_their_shapes(GameTestHelper h) {
        for (String id : IDS) {
            if (Options.byId(id) == null || Catalogue.byId(id) == null || !Catalogue.byId(id).ready()) {
                h.fail(id + " is not a ready option");
                return;
            }
        }
        if (Contraptions.REDSTONE_CLOCK.parts().size() != 12 || Contraptions.HOPPER_RING.parts().size() != 4
                || Contraptions.basinFor(0).parts().size() != 98 || Contraptions.LIGHT.parts().size() != 1
                || Contraptions.FALLING_SAND.parts().size() != Contraptions.SAND_DROP + 2) {
            h.fail("a contraption has the wrong number of blocks");
            return;
        }
        if (Contraptions.basinFor(3).parts().get(73).place().getBlock() != Blocks.LAVA
                || Contraptions.basinFor(2).parts().get(73).place().getBlock() != Blocks.WATER) {
            h.fail("every fourth basin is not the lava one");
            return;
        }
        if (WorldSave.everyTicks(12) != 100 || WorldSave.everyTicks(1) != 1200 || WorldSave.everyTicks(0) != 0) {
            h.fail("saves a minute do not turn into the right ticks");
            return;
        }
        h.succeed();
    }

    private static boolean allGone(MinecraftServer server, List<Placements.Unit> units) {
        for (Placements.Unit u : units) {
            ServerLevel level = server.getLevel(u.dimension());
            for (Placements.Placed p : u.parts()) {
                if (!level.getBlockState(p.pos()).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean dropsNear(MinecraftServer server, List<Placements.Unit> units) {
        for (Placements.Unit u : units) {
            AABB box = new AABB(u.origin()).inflate(10, 16, 10);
            ServerLevel level = server.getLevel(u.dimension());
            if (!level.getEntitiesOfClass(ItemEntity.class, box).isEmpty() || !level.getEntitiesOfClass(FallingBlockEntity.class, box).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = EMPTY, batch = "world_clock_hoppers", timeoutTicks = 600)
    public void a_clock_pulses_and_a_hopper_ring_turns_and_both_go_without_a_drop(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption clocks = Options.byId(WorldOption.REDSTONE_CLOCKS);
        StressOption hoppers = Options.byId(WorldOption.HOPPERS);
        clocks.apply(server, 1);
        hoppers.apply(server, 1);
        List<Placements.Unit> placed = new java.util.ArrayList<>(Placements.units(WorldOption.REDSTONE_CLOCKS));
        placed.addAll(Placements.units(WorldOption.HOPPERS));
        if (placed.size() != 2) {
            Placements.releaseAll(server);
            h.fail("placed " + placed.size() + " contraptions");
            return;
        }
        ServerLevel level = server.getLevel(placed.get(0).dimension());
        BlockPos lamp = placed.get(0).origin().offset(0, 1, 0);
        BlockPos firstHopper = placed.get(1).origin();
        Set<Boolean> lampSeen = new HashSet<>();
        boolean[] moved = {false};
        h.onEachTick(() -> {
            if (level.getBlockState(lamp).is(Blocks.REDSTONE_LAMP)) {
                lampSeen.add(level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT));
            }
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}) {
                if (level.getBlockEntity(firstHopper.offset(o[0], 0, o[1])) instanceof HopperBlockEntity hb
                        && (hb.getItem(0).getCount() != 64 || !hb.getItem(1).isEmpty())) {
                    moved[0] = true;
                }
            }
        });
        h.runAfterDelay(300, () -> {
            int items = 0;
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}}) {
                if (level.getBlockEntity(firstHopper.offset(o[0], 0, o[1])) instanceof HopperBlockEntity hb) {
                    for (int i = 0; i < hb.getContainerSize(); i++) {
                        items += hb.getItem(i).getCount();
                    }
                }
            }
            clocks.release(server);
            hoppers.release(server);
            boolean gone = allGone(server, placed);
            boolean drops = dropsNear(server, placed);
            Placements.releaseAll(server);
            if (lampSeen.size() != 2) {
                h.fail("the lamp never switched: seen " + lampSeen);
            } else if (!moved[0] || items != 256) {
                h.fail("the ring did not pass its items round (moved " + moved[0] + ", " + items + " items)");
            } else if (!gone || drops) {
                h.fail("removing them left blocks (" + !gone + ") or drops (" + drops + ")");
            } else if (Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("the record or held chunks stayed");
            } else {
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = "world_cycles", timeoutTicks = 600)
    public void basins_lights_and_sand_do_their_work_and_leave_nothing(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption fluids = Options.byId(WorldOption.FLUIDS);
        StressOption lighting = Options.byId(WorldOption.LIGHTING);
        StressOption sand = Options.byId(WorldOption.FALLING_BLOCKS);
        fluids.apply(server, 4);
        lighting.apply(server, 1);
        sand.apply(server, 1);
        List<Placements.Unit> basins = Placements.units(WorldOption.FLUIDS);
        Placements.Unit light = Placements.units(WorldOption.LIGHTING).isEmpty() ? null : Placements.units(WorldOption.LIGHTING).get(0);
        Placements.Unit column = Placements.units(WorldOption.FALLING_BLOCKS).isEmpty() ? null : Placements.units(WorldOption.FALLING_BLOCKS).get(0);
        if (basins.size() != 4 || light == null || column == null) {
            Placements.releaseAll(server);
            h.fail("placed " + basins.size() + " basins, light " + light + ", sand " + column);
            return;
        }
        ServerLevel level = server.getLevel(light.dimension());
        String problem = null;
        // a basin's source goes and comes back; the fourth is lava
        BlockPos centre = basins.get(0).origin().offset(3, 1, 3);
        if (!level.getBlockState(basins.get(3).origin().offset(3, 1, 3)).is(Blocks.LAVA)) {
            problem = "the fourth basin holds no lava";
        } else {
            boolean source = level.getFluidState(centre).isSource();
            basins.get(0).shape().cycle(level, basins.get(0).origin());
            boolean after = level.getFluidState(centre).isSource();
            basins.get(0).shape().cycle(level, basins.get(0).origin());
            if (source == after || level.getFluidState(centre).isSource() != source) {
                problem = "the source did not go and come back";
            }
        }
        // a light swaps between glowstone and stone
        if (problem == null) {
            boolean glow = level.getBlockState(light.origin()).is(Blocks.GLOWSTONE);
            light.shape().cycle(level, light.origin());
            if (!level.getBlockState(light.origin()).is(glow ? Blocks.STONE : Blocks.GLOWSTONE)) {
                problem = "the light did not swap";
            }
        }
        String found = problem;
        boolean[] landed = {false};
        boolean[] spread = {false};
        BlockPos corner = basins.get(0).origin().offset(1, 1, 1);
        h.onEachTick(() -> {
            if (level.getBlockState(column.origin().above()).is(Blocks.SAND)) {
                landed[0] = true;
            }
            if (level.getFluidState(corner).is(net.minecraft.world.level.material.Fluids.FLOWING_WATER)) {
                spread[0] = true;
            }
        });
        List<Placements.Unit> all = new java.util.ArrayList<>(basins);
        all.add(light);
        all.add(column);
        h.runAfterDelay(200, () -> {
            fluids.release(server);
            lighting.release(server);
            sand.release(server);
            boolean gone = allGone(server, all);
            boolean drops = dropsNear(server, all);
            Placements.releaseAll(server);
            if (found != null) {
                h.fail(found);
            } else if (!landed[0]) {
                h.fail("the sand never landed");
            } else if (!spread[0]) {
                h.fail("the water never reached the basin's corners");
            } else if (!gone || drops) {
                h.fail("removing them left blocks or fluid (" + !gone + ") or falling blocks and drops (" + drops + ")");
            } else if (Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("the record or held chunks stayed");
            } else {
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = "world_cleanup")
    public void what_a_stopped_run_left_is_put_back(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        int speed = RandomTicks.speed(server);
        StressOption ticks = Options.byId(RandomTicks.ID);
        try {
            Options.byId(WorldOption.REDSTONE_CLOCKS).apply(server, 1);
            Options.byId(WorldOption.FLUIDS).apply(server, 1);
            ticks.apply(server, speed + 6);
            List<Placements.Unit> units = new java.util.ArrayList<>(Placements.units(WorldOption.REDSTONE_CLOCKS));
            units.addAll(Placements.units(WorldOption.FLUIDS));
            if (RandomTicks.speed(server) != speed + 6 || !Files.exists(RandomTicks.file())) {
                h.fail("the tick speed is " + RandomTicks.speed(server) + " and the file " + Files.exists(RandomTicks.file()));
                return;
            }
            Placements.forgetForTest(); // as if the server had stopped mid-run: the blocks, the rule and the files stay
            RandomTicks.forgetForTest();
            if (Placements.leftOver() != 12 + 98) {
                h.fail("left over: " + Placements.leftOver());
                return;
            }
            int removed = Placements.cleanup(server);
            int back = RandomTicks.restoreLeftOver(server);
            if (removed < 12 + 74 || !allGone(server, units) || Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("cleanup removed " + removed + "; all gone " + allGone(server, units));
                return;
            }
            if (back != speed || RandomTicks.speed(server) != speed || Files.exists(RandomTicks.file()) || RandomTicks.restoreLeftOver(server) != -1) {
                h.fail("the tick speed came back as " + back + " (now " + RandomTicks.speed(server) + ")");
                return;
            }
        } finally {
            Placements.releaseAll(server);
            ticks.release(server);
            if (RandomTicks.restoreLeftOver(server) < 0 && RandomTicks.speed(server) != speed) {
                server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(speed, server);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "world_save", timeoutTicks = 400)
    public void saving_marks_the_held_chunks_and_writes_them(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption save = Options.byId(WorldSave.ID);
        StressOption lights = Options.byId(WorldOption.LIGHTING);
        lights.apply(server, 1);
        List<Placements.Unit> units = Placements.units(WorldOption.LIGHTING);
        if (units.isEmpty()) {
            h.fail("no light placed");
            return;
        }
        ServerLevel level = server.getLevel(units.get(0).dimension());
        ChunkPos at = new ChunkPos(units.get(0).origin());
        int marked = WorldSave.markHeld(server);
        LevelChunk chunk = level.getChunkSource().getChunkNow(at.x, at.z);
        if (marked < 1 || chunk == null || !chunk.isUnsaved()) {
            lights.release(server);
            h.fail("marked " + marked + " held chunks");
            return;
        }
        int before = WorldSave.saves();
        save.apply(server, 12);
        h.runAfterDelay(130, () -> {
            int saves = WorldSave.saves() - before;
            save.release(server);
            lights.release(server);
            if (saves < 1) {
                h.fail("no save in 130 ticks at 12 a minute");
            } else if (ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("the light's chunk is still held");
            } else {
                h.succeed();
            }
        });
    }
}
