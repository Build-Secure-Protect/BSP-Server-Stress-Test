package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.machines.Grid;
import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ST-11, idle machines: the grid; one option per catalogue machine; real hoppers placed with their block entities,
 * their chunks held, the record written, stepping down and letting go; a filled cell is never overwritten; what a
 * stopped server left is cleaned from the record.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class MachineGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final String HOPPERS = "machines.minecraft.hopper";

    @GameTest(template = EMPTY)
    public void the_grid_fills_rows_then_layers_without_sharing_a_cell(GameTestHelper h) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4 * 4 * 3; i++) {
            int[] c = Grid.cell(i, 4, 2);
            if (!seen.add(c[0] + "," + c[1] + "," + c[2]) || c[0] % 2 != 0 || c[2] % 2 != 0 || c[0] > 6 || c[2] > 6) {
                h.fail("cell " + i + " at " + c[0] + "," + c[1] + "," + c[2]);
                return;
            }
        }
        if (Grid.cell(4, 4, 2)[2] != 2 || Grid.cell(16, 4, 2)[1] != Grid.LAYER_GAP || Grid.cell(16, 4, 2)[0] != 0) {
            h.fail("rows or layers are out of order");
            return;
        }
        for (String id : List.of(HOPPERS, "machines.minecraft.furnace", "machines.bsp_core.tetrium_crucible")) {
            if (Options.byId(id) == null || !Catalogue.byId(id).ready()) {
                h.fail(id + " has no option");
                return;
            }
        }
        if (Options.byId("machines.bsp_core.shatter_totem") != null || Options.byId("machines.minecraft.moving_piston") != null) {
            h.fail("a left-out machine has an option");
            return;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "machines_live")
    public void hoppers_are_placed_held_recorded_and_removed(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption hoppers = Options.byId(HOPPERS);
        try {
            hoppers.apply(server, 5);
            List<Placements.Placed> placed = Placements.placed(HOPPERS);
            if (placed.size() != 5) {
                h.fail("placed " + placed.size());
                return;
            }
            for (Placements.Placed p : placed) {
                ServerLevel level = server.getLevel(p.dimension());
                if (!level.getBlockState(p.pos()).is(Blocks.HOPPER) || level.getBlockEntity(p.pos()) == null) {
                    h.fail("no hopper with its block entity at " + p.pos());
                    return;
                }
                if (!ChunkHold.holds(p.dimension(), new net.minecraft.world.level.ChunkPos(p.pos()))) {
                    h.fail("the chunk under " + p.pos() + " is not held");
                    return;
                }
            }
            String record = Files.readString(Placements.file());
            if (record.split("minecraft:hopper", -1).length - 1 != 5) {
                h.fail("the record does not list 5 hoppers: " + record);
                return;
            }
            hoppers.apply(server, 2);
            ServerLevel level = server.getLevel(placed.get(4).dimension());
            if (Placements.count(HOPPERS) != 2 || !level.getBlockState(placed.get(4).pos()).isAir()
                    || !level.getBlockState(placed.get(0).pos()).is(Blocks.HOPPER)) {
                h.fail("stepping down to 2 left " + Placements.count(HOPPERS));
                return;
            }
            hoppers.release(server);
            for (Placements.Placed p : placed) {
                if (!level.getBlockState(p.pos()).isAir()) {
                    h.fail("a hopper stayed at " + p.pos());
                    return;
                }
            }
            if (Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("letting go left the record or held chunks");
                return;
            }
        } catch (Exception e) {
            h.fail("placing: " + e);
            return;
        } finally {
            Placements.releaseAll(server);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "machines_never_overwrite")
    public void a_filled_cell_is_never_overwritten(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption hoppers = Options.byId(HOPPERS);
        BlockPos first = null;
        ServerLevel level = null;
        try {
            hoppers.apply(server, 1);
            Placements.Placed p = Placements.placed(HOPPERS).get(0);
            first = p.pos();
            level = server.getLevel(p.dimension());
            hoppers.release(server);
            level.setBlock(first, Blocks.STONE.defaultBlockState(), 2);
            hoppers.apply(server, 1);
            Placements.Placed again = Placements.placed(HOPPERS).get(0);
            if (again.pos().equals(first) || !level.getBlockState(first).is(Blocks.STONE)) {
                h.fail("the stone in the first cell was replaced");
                return;
            }
        } finally {
            Placements.releaseAll(server);
            if (level != null && first != null && level.getBlockState(first).is(Blocks.STONE)) {
                level.setBlock(first, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "machines_cleanup")
    public void what_a_stopped_server_left_is_cleaned_from_the_record(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption hoppers = Options.byId(HOPPERS);
        try {
            hoppers.apply(server, 3);
            List<Placements.Placed> placed = Placements.placed(HOPPERS);
            Placements.forgetForTest(); // as if the server had stopped mid-run: the blocks and the record stay
            if (Placements.leftOver() != 3) {
                h.fail("left over: " + Placements.leftOver());
                return;
            }
            int removed = Placements.cleanup(server);
            ServerLevel level = server.getLevel(placed.get(0).dimension());
            if (removed != 3 || placed.stream().anyMatch(p -> !level.getBlockState(p.pos()).isAir()) || Files.exists(Placements.file())
                    || ChunkHold.held(Placements.CHUNK_GROUP) != 0 || Placements.leftOver() != 0) {
                h.fail("cleanup removed " + removed);
                return;
            }
        } finally {
            Placements.releaseAll(server);
        }
        h.succeed();
    }
}
