package com.mrgregles.bsp_server_stress_test.machines;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Blocks the mod places for a run (owner decisions D6, D11): each in its own cell of a grid in the air above the chunk
 * places (or spawn), only where the cell is air, so no build is ever overwritten. Placed without neighbour updates, so
 * nothing joins up or reacts; the chunks under them are held ticking. Every placed block is written to
 * {@code config/bsp_server_stress_test/placed.json} as it is placed, so a server that stopped mid-run can still be
 * cleaned with {@code /stress cleanup}; a block is only removed if it is still the block that was placed.
 * <p>
 * The world options' contraptions (D27) and the working setups (D39) are {@link Unit units} of several blocks, each in
 * its own grid ({@link Area}) higher up, placed with updates so they work. A unit's parts are in the same record, each
 * with every block that may stand there while it works (a lit lamp, flowing water, landed sand, a belt's shaft).
 */
public final class Placements {
    public static final String FILE = "placed.json";
    public static final String CHUNK_GROUP = "machines";
    private static final Gson GSON = new GsonBuilder().create();
    /** Placed and removed without neighbour updates (2 = tell players' games only). */
    static final int FLAGS = Block.UPDATE_CLIENTS;
    /** Contraptions are removed without telling their neighbours, so nothing reacts or flows while they go. */
    static final int UNIT_REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Layers of contraptions are this far apart (falling sand stands 12 high). */
    public static final int UNIT_LAYER_GAP = 16;

    /** One placed block; any of {@code also} standing there instead is removed with it too. */
    public record Placed(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation block, int cell, List<ResourceLocation> also) {
        public Placed(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation block, int cell) {
            this(dimension, pos, block, cell, List.of());
        }

        boolean matches(ResourceLocation id) {
            return block.equals(id) || also.contains(id);
        }
    }

    /** What a contraption is made of, and what it does when placed, removed and every {@link #every()} ticks. */
    public interface Shape {
        /** Every block from the unit's corner, in the order placed; removed in reverse. */
        List<Part> parts();

        /** Called once every part stands (a hopper ring gets its items). */
        default void placed(ServerLevel level, BlockPos origin) {
        }

        /** Called before the parts are removed (sand still falling is taken away). */
        default void removing(ServerLevel level, BlockPos origin) {
        }

        /** Ticks between two {@link #cycle} calls; 0 never. */
        default int every() {
            return 0;
        }

        /** The contraption's repeated work (put a source back, swap a light, lift the sand). */
        default void cycle(ServerLevel level, BlockPos origin) {
        }
    }

    /**
     * One block of a shape: {@code place} is set there (null keeps the spot clear for what the contraption moves
     * there), and any of {@code blocks} standing there is removed with the unit.
     */
    public record Part(BlockPos offset, BlockState place, List<Block> blocks) {
        public static Part of(int x, int y, int z, BlockState place) {
            return new Part(new BlockPos(x, y, z), place, List.of(place.getBlock()));
        }
    }

    /** One contraption that stands: in which grid, where, in which cell, and its parts as recorded. */
    public record Unit(Area area, ResourceKey<Level> dimension, BlockPos origin, int cell, Shape shape, List<Placed> parts) {
        /** The space its parts take, two blocks wider all round (what moves in it, falls out of it). */
        public AABB box() {
            AABB box = new AABB(origin);
            for (Placed p : parts) {
                box = box.minmax(new AABB(p.pos()));
            }
            return box.inflate(2);
        }
    }

    /**
     * The grids units go in: the world options' contraptions {@code world.aboveMachines} over the machine grid (D27),
     * and the working setups {@code setups.aboveWorld} over those (D39).
     */
    public enum Area {
        WORLD, SETUPS;

        final Deque<Integer> free = new ArrayDeque<>();
        final Set<Integer> blocked = new LinkedHashSet<>();
        int nextCell;

        void reset() {
            free.clear();
            blocked.clear();
            nextCell = 0;
        }

        int side() {
            return this == WORLD ? StressConfig.WORLD_GRID_SIDE.get() : StressConfig.SETUPS_GRID_SIDE.get();
        }

        int spacing() {
            return this == WORLD ? StressConfig.WORLD_SPACING.get() : StressConfig.SETUPS_SPACING.get();
        }

        /** Above the machine grid's first layer. */
        int height() {
            int world = StressConfig.WORLD_ABOVE_MACHINES.get();
            return this == WORLD ? world : world + StressConfig.SETUPS_ABOVE_WORLD.get();
        }
    }

    private static final Map<String, List<Placed>> BY_OPTION = new LinkedHashMap<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();
    private static final Set<Integer> BLOCKED = new LinkedHashSet<>();
    private static final Map<String, Integer> GROUND = new HashMap<>();
    private static int nextCell;

    private static final Map<String, List<Unit>> UNITS = new LinkedHashMap<>();

    private Placements() {
    }

    /** Blocks placed by every option together (a contraption counts every part). */
    public static synchronized int total() {
        return BY_OPTION.values().stream().mapToInt(List::size).sum()
                + UNITS.values().stream().flatMap(List::stream).mapToInt(u -> u.parts().size()).sum();
    }

    /** Blocks placed by a machine option, or contraptions standing for a world option. */
    public static synchronized int count(String option) {
        List<Placed> l = BY_OPTION.get(option);
        if (l != null) {
            return l.size();
        }
        List<Unit> u = UNITS.get(option);
        return u == null ? 0 : u.size();
    }

    public static synchronized List<Placed> placed(String option) {
        return List.copyOf(BY_OPTION.getOrDefault(option, List.of()));
    }

    public static synchronized List<Unit> units(String option) {
        return List.copyOf(UNITS.getOrDefault(option, List.of()));
    }

    /** Every contraption standing, for the ticker. */
    public static synchronized List<Unit> allUnits() {
        if (UNITS.isEmpty()) {
            return List.of();
        }
        List<Unit> out = new ArrayList<>();
        UNITS.values().forEach(out::addAll);
        return out;
    }

    /** Places or removes blocks until {@code n} of {@code block} stand for this option (within the ceilings). */
    public static synchronized int set(MinecraftServer server, String option, Block block, int n) {
        List<Placed> list = BY_OPTION.computeIfAbsent(option, k -> new ArrayList<>());
        int target = Math.max(0, Math.min(n, StressConfig.MAX_PLACED_TOTAL.get() - (total() - list.size())));
        List<Place> places = Places.inUse(server);
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        int tries = 0;
        while (list.size() < target && tries++ < target * 4 + 64) {
            int cell = FREE.isEmpty() ? nextCell++ : FREE.pollFirst();
            Place place = places.get(cell % places.size());
            ServerLevel level = server.getLevel(place.dimension());
            if (level == null) {
                continue;
            }
            BlockPos pos = position(level, place, cell / places.size());
            if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir()) {
                BLOCKED.add(cell); // never overwrite anything: this cell stays unused
                continue;
            }
            level.setBlock(pos, block.defaultBlockState(), FLAGS);
            list.add(new Placed(place.dimension(), pos, id, cell));
        }
        while (list.size() > target) {
            remove(server, list.remove(list.size() - 1));
        }
        if (list.isEmpty()) {
            BY_OPTION.remove(option);
        }
        afterChange(server);
        return list.size();
    }

    /**
     * Places or removes contraptions in a grid until {@code n} stand for this option (within the ceilings); the
     * {@code i}th is made from {@code shapes.apply(i)}. A cell where any part's spot is not air stays unused.
     */
    public static synchronized int setUnits(MinecraftServer server, String option, Area area, IntFunction<Shape> shapes, int n) {
        List<Unit> list = UNITS.computeIfAbsent(option, k -> new ArrayList<>());
        int size = Math.max(1, shapes.apply(0).parts().size());
        int mine = list.stream().mapToInt(u -> u.parts().size()).sum();
        int target = Math.max(0, Math.min(n, (StressConfig.MAX_PLACED_TOTAL.get() - (total() - mine)) / size));
        List<Place> places = Places.inUse(server);
        int tries = 0;
        while (list.size() < target && tries++ < target * 4 + 64) {
            int cell = area.free.isEmpty() ? area.nextCell++ : area.free.pollFirst();
            Place place = places.get(cell % places.size());
            ServerLevel level = server.getLevel(place.dimension());
            if (level == null) {
                continue;
            }
            BlockPos origin = unitPosition(level, place, area, cell / places.size());
            Shape shape = shapes.apply(list.size());
            if (!clear(level, origin, shape)) {
                area.blocked.add(cell);
                continue;
            }
            List<Placed> parts = new ArrayList<>();
            for (Part p : shape.parts()) {
                BlockPos pos = origin.offset(p.offset());
                if (p.place() != null) {
                    level.setBlock(pos, p.place(), Block.UPDATE_ALL);
                }
                List<ResourceLocation> ids = p.blocks().stream().map(BuiltInRegistries.BLOCK::getKey).toList();
                parts.add(new Placed(place.dimension(), pos, ids.get(0), cell, ids.subList(1, ids.size())));
            }
            shape.placed(level, origin);
            list.add(new Unit(area, place.dimension(), origin, cell, shape, List.copyOf(parts)));
        }
        while (list.size() > target) {
            removeUnit(server, list.remove(list.size() - 1));
        }
        if (list.isEmpty()) {
            UNITS.remove(option);
        }
        afterChange(server);
        return list.size();
    }

    private static boolean clear(ServerLevel level, BlockPos origin, Shape shape) {
        for (Part p : shape.parts()) {
            BlockPos pos = origin.offset(p.offset());
            if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    private static int ground(ServerLevel level, Place place) {
        String key = place.dimension().location() + "|" + place.centre().x + "," + place.centre().z;
        return GROUND.computeIfAbsent(key, k -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                place.centre().getMiddleBlockX(), place.centre().getMiddleBlockZ()));
    }

    private static BlockPos position(ServerLevel level, Place place, int index) {
        int side = StressConfig.MACHINES_GRID_SIDE.get();
        int spacing = StressConfig.MACHINES_SPACING.get();
        int[] c = Grid.cell(index, side, spacing);
        int half = side * spacing / 2;
        return new BlockPos(place.centre().getMiddleBlockX() - half + c[0], ground(level, place) + StressConfig.MACHINES_HEIGHT.get() + c[1],
                place.centre().getMiddleBlockZ() - half + c[2]);
    }

    /** A unit's corner in its grid, which is {@link Area#height()} over the machine grid's first layer. */
    private static BlockPos unitPosition(ServerLevel level, Place place, Area area, int index) {
        int side = area.side();
        int spacing = area.spacing();
        int[] c = Grid.cell(index, side, spacing, UNIT_LAYER_GAP);
        int half = side * spacing / 2;
        int y = ground(level, place) + StressConfig.MACHINES_HEIGHT.get() + area.height();
        return new BlockPos(place.centre().getMiddleBlockX() - half + c[0], y + c[1], place.centre().getMiddleBlockZ() - half + c[2]);
    }

    /**
     * Removes the placed block if it (or one it may have become) is still there; a container or anything holding items
     * (a depot, a millstone) is emptied first, so nothing drops.
     */
    private static boolean removeIfThere(ServerLevel level, BlockPos pos, ResourceLocation block, List<ResourceLocation> also, int flags) {
        if (level == null || block == null) {
            return false;
        }
        ResourceLocation now = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (!now.equals(block) && !also.contains(now)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) {
            c.clearContent();
        } else if (be != null) {
            empty(be);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
        return true;
    }

    /** Takes every item out of a block entity's item handler (from no side: all of it) and lets them go. */
    public static void empty(BlockEntity be) {
        try {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(h -> {
                for (int i = 0; i < h.getSlots(); i++) {
                    for (int guard = 0; guard < 64 && !h.getStackInSlot(i).isEmpty(); guard++) {
                        if (h.extractItem(i, h.getStackInSlot(i).getCount(), false).isEmpty()) {
                            break;
                        }
                    }
                }
            });
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.debug("Could not empty {}: {}", be.getBlockPos(), e.toString());
        }
    }

    private static void remove(MinecraftServer server, Placed p) {
        removeIfThere(server.getLevel(p.dimension()), p.pos(), p.block(), p.also(), FLAGS);
        FREE.addLast(p.cell());
    }

    private static int removeUnit(MinecraftServer server, Unit u) {
        ServerLevel level = server.getLevel(u.dimension());
        int removed = 0;
        if (level != null) {
            u.shape().removing(level, u.origin());
            for (int i = u.parts().size() - 1; i >= 0; i--) {
                Placed p = u.parts().get(i);
                if (removeIfThere(level, p.pos(), p.block(), p.also(), UNIT_REMOVE_FLAGS)) {
                    removed++;
                }
            }
        }
        u.area().free.addLast(u.cell());
        return removed;
    }

    /** Removes everything placed for every option. */
    public static synchronized void releaseAll(MinecraftServer server) {
        for (List<Placed> l : BY_OPTION.values()) {
            l.forEach(p -> remove(server, p));
        }
        BY_OPTION.clear();
        for (List<Unit> l : UNITS.values()) {
            l.forEach(u -> removeUnit(server, u));
        }
        UNITS.clear();
        afterChange(server);
    }

    private static void afterChange(MinecraftServer server) {
        Map<ResourceKey<Level>, Set<Long>> chunks = new LinkedHashMap<>();
        for (Placed p : everyPlaced()) {
            chunks.computeIfAbsent(p.dimension(), k -> new LinkedHashSet<>()).add(ChunkPos.asLong(p.pos().getX() >> 4, p.pos().getZ() >> 4));
        }
        ChunkHold.setExact(server, CHUNK_GROUP, chunks);
        if (BY_OPTION.isEmpty()) {
            FREE.clear();
            BLOCKED.clear();
            nextCell = 0;
        }
        for (Area area : Area.values()) {
            if (UNITS.values().stream().flatMap(List::stream).noneMatch(u -> u.area() == area)) {
                area.reset();
            }
        }
        if (BY_OPTION.isEmpty() && UNITS.isEmpty()) {
            GROUND.clear();
        }
        writeRecord();
    }

    private static List<Placed> everyPlaced() {
        List<Placed> out = new ArrayList<>();
        BY_OPTION.values().forEach(out::addAll);
        UNITS.values().forEach(l -> l.forEach(u -> out.addAll(u.parts())));
        return out;
    }

    // ---- the record in placed.json

    static final class Line {
        String dimension;
        int x;
        int y;
        int z;
        String block;
        /** Blocks that may stand there instead while a contraption works; absent for a machine. */
        List<String> also;
    }

    static final class Record {
        List<Line> blocks = new ArrayList<>();
    }

    public static Path file() {
        return StressFiles.file(FILE);
    }

    private static void writeRecord() {
        Record r = new Record();
        for (Placed p : everyPlaced()) {
            Line line = new Line();
            line.dimension = p.dimension().location().toString();
            line.x = p.pos().getX();
            line.y = p.pos().getY();
            line.z = p.pos().getZ();
            line.block = p.block().toString();
            line.also = p.also().isEmpty() ? null : p.also().stream().map(ResourceLocation::toString).toList();
            r.blocks.add(line);
        }
        try {
            if (r.blocks.isEmpty()) {
                Files.deleteIfExists(file());
            } else {
                Files.writeString(file(), GSON.toJson(r), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            BSPServerStressTest.LOGGER.error("Could not write {}", file(), e);
        }
    }

    /** How many blocks the record lists that this server did not place since it started (left by a run that never ended). */
    public static synchronized int leftOver() {
        if (!BY_OPTION.isEmpty() || !UNITS.isEmpty() || !Files.exists(file())) {
            return 0;
        }
        Record r = read();
        return r == null ? 0 : r.blocks.size();
    }

    private static Record read() {
        try {
            Record r = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Record.class);
            return r == null || r.blocks == null ? new Record() : r;
        } catch (Exception e) {
            BSPServerStressTest.LOGGER.error("Could not read {}", file(), e);
            return null;
        }
    }

    /**
     * Removes every block the record lists that is still the block placed there (or one it may have become), and
     * everything placed since the server started; then forgets the record. Returns how many blocks were removed.
     */
    public static synchronized int cleanup(MinecraftServer server) {
        int removed = 0;
        for (List<Placed> l : BY_OPTION.values()) {
            for (Placed p : l) {
                if (removeIfThere(server.getLevel(p.dimension()), p.pos(), p.block(), p.also(), FLAGS)) {
                    removed++;
                }
            }
        }
        BY_OPTION.clear();
        for (List<Unit> l : UNITS.values()) {
            for (Unit u : l) {
                removed += removeUnit(server, u);
            }
        }
        UNITS.clear();
        if (Files.exists(file())) {
            Record r = read();
            if (r != null) {
                // last placed first: a contraption's water and sand go before its basin and floor
                for (int i = r.blocks.size() - 1; i >= 0; i--) {
                    Line line = r.blocks.get(i);
                    ResourceLocation dim = ResourceLocation.tryParse(line.dimension == null ? "" : line.dimension);
                    ServerLevel level = dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
                    List<ResourceLocation> also = line.also == null ? List.of()
                            : line.also.stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull).toList();
                    if (removeIfThere(level, new BlockPos(line.x, line.y, line.z),
                            ResourceLocation.tryParse(line.block == null ? "" : line.block), also, UNIT_REMOVE_FLAGS)) {
                        removed++;
                    }
                }
            }
        }
        afterChange(server);
        return removed;
    }

    /** Forgets what is in memory without touching the world (tests of a server that stopped mid-run). */
    public static synchronized void forgetForTest() {
        BY_OPTION.clear();
        FREE.clear();
        BLOCKED.clear();
        GROUND.clear();
        nextCell = 0;
        UNITS.clear();
        for (Area area : Area.values()) {
            area.reset();
        }
    }
}
