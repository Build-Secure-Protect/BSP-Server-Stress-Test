package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.compat.Create;
import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.Profiles;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.setups.Feed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.util.List;

/**
 * ST-15, Create's working setups (owner decisions D35 to D41), with Create loaded in the dev run from libs/: the four
 * setups are ready options of their own kind and the setups_only profile covers them; each, placed alone, does its
 * real work within 400 ticks (iron pressed into sheets, cobblestone ground into gravel, beef smoked by the fan,
 * cobblestone carried to the belt's far end), and removing it leaves no block and no dropped item. ST-16: a bearing
 * rotor spins its plate as one moving piece with the mod's tag, and a piston line pushes its face out and back; removing
 * either leaves no block, no moving piece and no dropped item.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class WorkingSetupGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;
    static final List<String> IDS = List.of(Create.PRESS_LINE, Create.MILLSTONES, Create.FAN_SMOKING, Create.BELT_LINE,
            Create.BEARING_ROTOR, Create.PISTON_LINE, Create.TRAIN_LINE);

    @GameTest(template = EMPTY)
    public void create_setups_are_ready_options(GameTestHelper h) {
        if (!Create.isLoaded()) {
            h.fail("Create is not loaded in this dev run: run tools/prepare_libs.py");
            return;
        }
        try {
            var only = Profiles.load("setups_only");
            for (String id : IDS) {
                Catalogue.Entry e = Catalogue.byId(id);
                if (Options.byId(id) == null || e == null || !e.ready() || e.kind() != Catalogue.Kind.SETUP
                        || !e.modId().equals(Create.MODID) || only == null || !only.includes(id)) {
                    h.fail(id + " is not a ready Create setup covered by setups_only");
                    return;
                }
            }
        } catch (Exception ex) {
            h.fail("setups_only: " + ex);
            return;
        }
        h.succeed();
    }

    /**
     * Places one of the setup, watches every tick for {@code made} at one of the {@code at} spots (relative to the
     * setup's corner), then removes it and checks nothing is left.
     */
    private static void works(GameTestHelper h, String id, List<BlockPos> at, Item made) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption option = Options.byId(id);
        if (option == null) {
            h.fail(id + " has no option");
            return;
        }
        option.apply(server, 1);
        List<Placements.Unit> units = Placements.units(id);
        if (units.size() != 1) {
            Placements.releaseAll(server);
            h.fail("placed " + units.size());
            return;
        }
        Placements.Unit u = units.get(0);
        ServerLevel level = server.getLevel(u.dimension());
        boolean[] seen = {false};
        h.onEachTick(() -> {
            for (BlockPos p : at) {
                if (Feed.count(level, u.origin().offset(p), made) > 0) {
                    seen[0] = true;
                }
            }
        });
        h.runAfterDelay(400, () -> {
            option.release(server);
            boolean gone = u.parts().stream().allMatch(p -> level.getBlockState(p.pos()).isAir());
            boolean drops = !level.getEntitiesOfClass(ItemEntity.class, new AABB(u.origin()).inflate(16)).isEmpty();
            Placements.releaseAll(server);
            if (!seen[0]) {
                h.fail(id + " never made " + BuiltInRegistries.ITEM.getKey(made));
            } else if (!gone || drops) {
                h.fail("removing " + id + " left blocks (" + !gone + ") or dropped items (" + drops + ")");
            } else if (Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("the record or held chunks stayed");
            } else {
                h.succeed();
            }
        });
    }

    private static List<BlockPos> row(int y, int... xs) {
        return java.util.Arrays.stream(xs).mapToObj(x -> new BlockPos(x, y, 0)).toList();
    }

    /** Places one of the setup and waits until every machine in {@code made} has shown its product; then as {@link #works}. */
    static void makesAll(GameTestHelper h, String id, java.util.Map<BlockPos, Item> made) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption option = Options.byId(id);
        if (option == null) {
            h.fail(id + " has no option (is its mod in libs/?)");
            return;
        }
        option.apply(server, 1);
        List<Placements.Unit> units = Placements.units(id);
        if (units.size() != 1) {
            Placements.releaseAll(server);
            h.fail("placed " + units.size());
            return;
        }
        Placements.Unit u = units.get(0);
        ServerLevel level = server.getLevel(u.dimension());
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        h.onEachTick(() -> made.forEach((p, item) -> {
            if (Feed.count(level, u.origin().offset(p), item) > 0) {
                seen.add(p);
            }
        }));
        h.runAfterDelay(400, () -> {
            option.release(server);
            boolean gone = u.parts().stream().allMatch(p -> level.getBlockState(p.pos()).isAir());
            boolean drops = !level.getEntitiesOfClass(ItemEntity.class, new AABB(u.origin()).inflate(16)).isEmpty();
            Placements.releaseAll(server);
            List<String> missing = made.entrySet().stream().filter(e -> !seen.contains(e.getKey()))
                    .map(e -> BuiltInRegistries.ITEM.getKey(e.getValue()) + " at " + e.getKey().toShortString()).toList();
            if (!missing.isEmpty()) {
                h.fail(id + " never made " + missing);
            } else if (!gone || drops) {
                h.fail("removing " + id + " left blocks (" + !gone + ") or dropped items (" + drops + ")");
            } else if (Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("the record or held chunks stayed");
            } else {
                h.succeed();
            }
        });
    }

    static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
    }

    @GameTest(template = EMPTY, batch = "setups_mekanism", timeoutTicks = 600)
    public void a_mekanism_machine_line_runs_on_its_creative_cube(GameTestHelper h) {
        makesAll(h, com.mrgregles.bsp_server_stress_test.compat.Mekanism.MACHINE_LINE, java.util.Map.of(
                com.mrgregles.bsp_server_stress_test.compat.Mekanism.ENRICHER, item("mekanism:dust_iron"),
                com.mrgregles.bsp_server_stress_test.compat.Mekanism.CRUSHER, Items.GRAVEL,
                com.mrgregles.bsp_server_stress_test.compat.Mekanism.SMELTER, Items.COOKED_BEEF));
    }

    @GameTest(template = EMPTY, batch = "setups_refinedstorage", timeoutTicks = 600)
    public void a_refined_storage_network_moves_items_between_chests(GameTestHelper h) {
        // the far chest only ever holds cobblestone that went through the network
        makesAll(h, com.mrgregles.bsp_server_stress_test.compat.RefinedStorage.ITEM_NETWORK, java.util.Map.of(
                com.mrgregles.bsp_server_stress_test.compat.RefinedStorage.TO_CHEST, Items.COBBLESTONE));
    }

    @GameTest(template = EMPTY, batch = "setups_ae2", timeoutTicks = 600)
    public void an_ae2_network_stores_what_its_interface_takes_in(GameTestHelper h) {
        String id = com.mrgregles.bsp_server_stress_test.compat.Ae2.ITEM_NETWORK;
        MinecraftServer server = h.getLevel().getServer();
        StressOption option = Options.byId(id);
        if (option == null) {
            h.fail(id + " has no option (is AE2 in libs/?)");
            return;
        }
        option.apply(server, 1);
        Placements.Unit u = Placements.units(id).get(0);
        ServerLevel level = server.getLevel(u.dimension());
        String[] state = {""};
        h.onEachTick(() -> {
            String s = com.mrgregles.bsp_server_stress_test.compat.Ae2.Calls.cellState(level.getBlockEntity(u.origin().offset(com.mrgregles.bsp_server_stress_test.compat.Ae2.CHEST)));
            if (s.equals("NOT_EMPTY") || s.contains("FULL")) {
                state[0] = s;
            }
        });
        h.runAfterDelay(400, () -> {
            option.release(server);
            boolean gone = u.parts().stream().allMatch(p -> level.getBlockState(p.pos()).isAir());
            boolean drops = !level.getEntitiesOfClass(ItemEntity.class, new AABB(u.origin()).inflate(16)).isEmpty();
            Placements.releaseAll(server);
            if (state[0].isEmpty()) {
                h.fail("the ME chest's cell never held anything");
            } else if (!gone || drops) {
                h.fail("removing " + id + " left blocks (" + !gone + ") or dropped items (" + drops + ")");
            } else {
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = "setups_thermal", timeoutTicks = 600)
    public void thermal_machines_run_on_powahs_creative_cell(GameTestHelper h) {
        makesAll(h, com.mrgregles.bsp_server_stress_test.compat.Thermal.MACHINES, java.util.Map.of(
                com.mrgregles.bsp_server_stress_test.compat.Thermal.WEST, Items.GRAVEL,
                com.mrgregles.bsp_server_stress_test.compat.Thermal.EAST, Items.COOKED_BEEF,
                com.mrgregles.bsp_server_stress_test.compat.Thermal.NORTH, Items.OAK_PLANKS));
    }

    @GameTest(template = EMPTY, batch = "setups_enderio", timeoutTicks = 600)
    public void enderio_machines_run_on_its_creative_power(GameTestHelper h) {
        makesAll(h, com.mrgregles.bsp_server_stress_test.compat.EnderIo.MACHINES, java.util.Map.of(
                com.mrgregles.bsp_server_stress_test.compat.EnderIo.WEST, Items.GRAVEL,
                com.mrgregles.bsp_server_stress_test.compat.EnderIo.EAST, Items.GRAVEL,
                com.mrgregles.bsp_server_stress_test.compat.EnderIo.NORTH, Items.COOKED_BEEF));
    }

    @GameTest(template = EMPTY, batch = "setups_industrialforegoing", timeoutTicks = 600)
    public void industrial_foregoing_furnaces_run_on_titaniums_generator(GameTestHelper h) {
        makesAll(h, com.mrgregles.bsp_server_stress_test.compat.IndustrialForegoing.MACHINES, java.util.Map.of(
                com.mrgregles.bsp_server_stress_test.compat.IndustrialForegoing.WEST, Items.COOKED_BEEF,
                com.mrgregles.bsp_server_stress_test.compat.IndustrialForegoing.EAST, Items.COOKED_BEEF,
                com.mrgregles.bsp_server_stress_test.compat.IndustrialForegoing.NORTH, Items.COOKED_BEEF));
    }

    @GameTest(template = EMPTY, batch = "setups_botania", timeoutTicks = 600)
    public void a_botania_exoflame_heats_furnaces_on_creative_mana(GameTestHelper h) {
        makesAll(h, com.mrgregles.bsp_server_stress_test.compat.Botania.EXOFLAME_FURNACES, java.util.Map.of(
                com.mrgregles.bsp_server_stress_test.compat.Botania.WEST, Items.COOKED_BEEF,
                com.mrgregles.bsp_server_stress_test.compat.Botania.EAST, Items.COOKED_BEEF,
                com.mrgregles.bsp_server_stress_test.compat.Botania.SOUTH, Items.COOKED_BEEF));
    }

    @GameTest(template = EMPTY, batch = "setups_press", timeoutTicks = 600)
    public void a_press_line_presses_iron_into_sheets(GameTestHelper h) {
        works(h, Create.PRESS_LINE, row(0, 1, 2, 3), BuiltInRegistries.ITEM.get(ResourceLocation.tryParse("create:iron_sheet")));
    }

    @GameTest(template = EMPTY, batch = "setups_mill", timeoutTicks = 600)
    public void millstones_grind_cobblestone(GameTestHelper h) {
        works(h, Create.MILLSTONES, row(2, 0, 2, 4), Items.GRAVEL);
    }

    @GameTest(template = EMPTY, batch = "setups_fan", timeoutTicks = 600)
    public void a_fan_through_a_campfire_smokes_beef(GameTestHelper h) {
        works(h, Create.FAN_SMOKING, row(0, 3, 4, 5), Items.COOKED_BEEF);
    }

    @GameTest(template = EMPTY, batch = "setups_belt", timeoutTicks = 600)
    public void a_belt_carries_cobblestone_to_its_far_end(GameTestHelper h) {
        works(h, Create.BELT_LINE, List.of(Create.BELT_END), Items.COBBLESTONE);
    }

    /**
     * Places one moving setup, watches every tick with {@code watch} (given the level, the setup's corner and the moving
     * pieces in its space), then removes it and checks nothing is left. {@code watch} returns a problem, or null once
     * everything it waits for was seen.
     */
    private static void moves(GameTestHelper h, String id, Watcher watch) {
        moves(h, id, watch, 500);
    }

    private static void moves(GameTestHelper h, String id, Watcher watch, int ticks) {
        MinecraftServer server = h.getLevel().getServer();
        StressOption option = Options.byId(id);
        option.apply(server, 1);
        List<Placements.Unit> units = Placements.units(id);
        if (units.size() != 1) {
            Placements.releaseAll(server);
            h.fail("placed " + units.size());
            return;
        }
        Placements.Unit u = units.get(0);
        ServerLevel level = server.getLevel(u.dimension());
        AABB space = new AABB(u.origin()).inflate(16);
        String[] still = {"nothing seen yet"};
        h.onEachTick(() -> {
            if (still[0] != null) {
                still[0] = watch.check(level, u.origin(), level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, space, Create::isContraption));
            }
        });
        h.runAfterDelay(ticks, () -> {
            option.release(server);
            boolean gone = u.parts().stream().allMatch(p -> level.getBlockState(p.pos()).isAir());
            boolean moving = !level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, space, Create::isContraption).isEmpty();
            boolean drops = !level.getEntitiesOfClass(ItemEntity.class, space).isEmpty();
            Placements.releaseAll(server);
            if (still[0] != null) {
                h.fail(id + ": " + still[0]);
            } else if (!gone || moving || drops) {
                h.fail("removing " + id + " left blocks (" + !gone + "), a moving piece (" + moving + ") or dropped items (" + drops + ")");
            } else if (Files.exists(Placements.file()) || ChunkHold.held(Placements.CHUNK_GROUP) != 0) {
                h.fail("the record or held chunks stayed");
            } else {
                h.succeed();
            }
        });
    }

    interface Watcher {
        String check(ServerLevel level, BlockPos origin, List<net.minecraft.world.entity.Entity> moving);
    }

    @GameTest(template = EMPTY, batch = "setups_rotor", timeoutTicks = 700)
    public void a_bearing_rotor_spins_its_plate_as_one_tagged_moving_piece(GameTestHelper h) {
        moves(h, Create.BEARING_ROTOR, (level, origin, moving) -> {
            if (moving.size() != 1) {
                return moving.size() + " moving pieces";
            }
            var piece = moving.get(0);
            if (!piece.getTags().contains(com.mrgregles.bsp_server_stress_test.entities.Spawns.TAG)
                    || !com.mrgregles.bsp_server_stress_test.entities.Spawns.isOurs(piece.getUUID())) {
                return "the moving piece is not tagged as ours";
            }
            if (!level.getBlockState(origin.offset(4, 2, 4)).isAir()) {
                return "the plate is still blocks";
            }
            return null;
        });
    }

    @GameTest(template = EMPTY, batch = "setups_train", timeoutTicks = 1400)
    public void a_train_line_assembles_a_train_that_shuttles_between_its_stations(GameTestHelper h) {
        boolean[] nearB = {false};
        moves(h, Create.TRAIN_LINE, (level, origin, moving) -> {
            if (moving.isEmpty()) {
                return "no train";
            }
            var carriage = moving.get(0);
            if (!carriage.getTags().contains(com.mrgregles.bsp_server_stress_test.entities.Spawns.TAG)) {
                return "the train is not tagged as ours";
            }
            double x = carriage.getX() - origin.getX();
            if (x > Create.STOP_B.getX() - 1.5) {
                nearB[0] = true;
            }
            if (!nearB[0]) {
                return "the train never reached station B (at x " + String.format(java.util.Locale.ROOT, "%.1f", x) + ")";
            }
            // the carriage stands a block past station A's marker when it is there (where it was assembled)
            return x < Create.STOP_A.getX() + 2 ? null : "the train never came back to station A";
        }, 1200);
    }

    @GameTest(template = EMPTY, batch = "setups_piston", timeoutTicks = 700)
    public void a_piston_line_pushes_its_face_out_and_back(GameTestHelper h) {
        boolean[] out = {false};
        boolean[] movedSeen = {false};
        int far = Create.PISTON.getX() + 1 + Create.PISTON_POLES;
        moves(h, Create.PISTON_LINE, (level, origin, moving) -> {
            if (!moving.isEmpty()) {
                movedSeen[0] = true;
                if (!moving.get(0).getTags().contains(com.mrgregles.bsp_server_stress_test.entities.Spawns.TAG)) {
                    return "a moving piece is not tagged as ours";
                }
            }
            boolean farFace = level.getBlockState(origin.offset(far, Create.PISTON.getY(), Create.PISTON.getZ())).is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK);
            boolean nearFace = level.getBlockState(origin.offset(Create.PISTON.getX() + 1, Create.PISTON.getY(), Create.PISTON.getZ())).is(net.minecraft.world.level.block.Blocks.SLIME_BLOCK);
            if (farFace) {
                out[0] = true;
            }
            if (!movedSeen[0]) {
                return "never moved";
            }
            if (!out[0]) {
                return "the face never reached the far end";
            }
            return nearFace ? null : "the face never came back";
        });
    }
}
