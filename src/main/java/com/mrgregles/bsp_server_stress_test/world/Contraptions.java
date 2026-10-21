package com.mrgregles.bsp_server_stress_test.world;

import com.mrgregles.bsp_server_stress_test.machines.Placements.Part;
import com.mrgregles.bsp_server_stress_test.machines.Placements.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The small fixed contraptions of the world options (owner decisions D28 to D30, D32, D33), each built from plain
 * vanilla blocks in its own cell of the world grid (D27) and working on its own once placed.
 */
public final class Contraptions {
    private Contraptions() {
    }

    /**
     * D28: two observers facing each other on a stone base, a redstone lamp behind the first and a line of dust behind
     * the second. Placing the second sets the first off, and from then on each sets the other off: a pulse every
     * 2 game ticks, with the lamp switching and the dust powering on every pulse.
     */
    public static final Shape REDSTONE_CLOCK = new Shape() {
        private final List<Part> parts = List.of(
                Part.of(0, 0, 0, Blocks.STONE.defaultBlockState()), Part.of(1, 0, 0, Blocks.STONE.defaultBlockState()),
                Part.of(2, 0, 0, Blocks.STONE.defaultBlockState()), Part.of(3, 0, 0, Blocks.STONE.defaultBlockState()),
                Part.of(4, 0, 0, Blocks.STONE.defaultBlockState()), Part.of(5, 0, 0, Blocks.STONE.defaultBlockState()),
                Part.of(0, 1, 0, Blocks.REDSTONE_LAMP.defaultBlockState()),
                Part.of(3, 1, 0, Blocks.REDSTONE_WIRE.defaultBlockState()), Part.of(4, 1, 0, Blocks.REDSTONE_WIRE.defaultBlockState()),
                Part.of(5, 1, 0, Blocks.REDSTONE_WIRE.defaultBlockState()),
                Part.of(1, 1, 0, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.EAST)),
                Part.of(2, 1, 0, Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, Direction.WEST)));

        @Override
        public List<Part> parts() {
            return parts;
        }

        @Override
        public void placed(ServerLevel level, BlockPos origin) {
            for (int x = 3; x <= 5; x++) { // the dust joins up into a line, as it does when a player lays it
                BlockPos pos = origin.offset(x, 1, 0);
                BlockState now = level.getBlockState(pos);
                if (now.is(Blocks.REDSTONE_WIRE)) {
                    level.setBlock(pos, Block.updateFromNeighbourShapes(now, level, pos), Block.UPDATE_ALL);
                }
            }
        }
    };

    /** D29: four hoppers in a square, each feeding the next, with a stack of cobblestone in each going round forever. */
    public static final Shape HOPPER_RING = new Shape() {
        private final List<Part> parts = List.of(
                Part.of(0, 0, 0, hopper(Direction.EAST)), Part.of(1, 0, 0, hopper(Direction.SOUTH)),
                Part.of(1, 0, 1, hopper(Direction.WEST)), Part.of(0, 0, 1, hopper(Direction.NORTH)));

        @Override
        public List<Part> parts() {
            return parts;
        }

        @Override
        public void placed(ServerLevel level, BlockPos origin) {
            for (Part p : parts) {
                if (level.getBlockEntity(origin.offset(p.offset())) instanceof HopperBlockEntity h) {
                    h.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
        }
    };

    private static BlockState hopper(Direction facing) {
        return Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing);
    }

    /** Ticks between a basin's source going and coming back (D30: 3 seconds). */
    public static final int FLUID_EVERY = 60;

    /** D30: a 7 x 7 glass basin with a source in the middle, taken away and put back every 3 seconds. */
    public static Shape basin(Block fluid) {
        List<Part> parts = new ArrayList<>();
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                parts.add(Part.of(x, 0, z, Blocks.GLASS.defaultBlockState()));
            }
        }
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                if (x == 0 || z == 0 || x == 6 || z == 6) {
                    parts.add(Part.of(x, 1, z, Blocks.GLASS.defaultBlockState()));
                }
            }
        }
        parts.add(Part.of(3, 1, 3, fluid.defaultBlockState()));
        for (int x = 1; x < 6; x++) {
            for (int z = 1; z < 6; z++) {
                if (x != 3 || z != 3) {
                    parts.add(new Part(new BlockPos(x, 1, z), null, List.of(fluid))); // kept clear for the flow
                }
            }
        }
        List<Part> fixed = List.copyOf(parts);
        return new Shape() {
            @Override
            public List<Part> parts() {
                return fixed;
            }

            @Override
            public int every() {
                return FLUID_EVERY;
            }

            @Override
            public void cycle(ServerLevel level, BlockPos origin) {
                BlockPos centre = origin.offset(3, 1, 3);
                BlockState now = level.getBlockState(centre);
                if (now.is(fluid) && now.getFluidState().isSource()) {
                    level.setBlock(centre, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                } else if (now.isAir() || now.is(fluid)) {
                    level.setBlock(centre, fluid.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        };
    }

    /** D30: every fourth basin holds lava, the rest water. */
    public static Shape basinFor(int index) {
        return index % 4 == 3 ? LAVA_BASIN : WATER_BASIN;
    }

    static final Shape WATER_BASIN = basin(Blocks.WATER);
    static final Shape LAVA_BASIN = basin(Blocks.LAVA);

    /** Ticks between two swaps of a light (D32: half a second). */
    public static final int LIGHT_EVERY = 10;

    /** D32: a glowstone block swapped with stone every half second, so the light around it is worked out again. */
    public static final Shape LIGHT = new Shape() {
        private final List<Part> parts = List.of(new Part(BlockPos.ZERO, Blocks.GLOWSTONE.defaultBlockState(),
                List.of(Blocks.GLOWSTONE, Blocks.STONE)));

        @Override
        public List<Part> parts() {
            return parts;
        }

        @Override
        public int every() {
            return LIGHT_EVERY;
        }

        @Override
        public void cycle(ServerLevel level, BlockPos origin) {
            BlockState now = level.getBlockState(origin);
            if (now.is(Blocks.GLOWSTONE)) {
                level.setBlock(origin, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            } else if (now.is(Blocks.STONE)) {
                level.setBlock(origin, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    };

    /** Ticks between two lifts of the sand (D33: 2 seconds). */
    public static final int SAND_EVERY = 40;
    /** How far the sand falls (D33). */
    public static final int SAND_DROP = 10;

    /** D33: sand dropped from 10 blocks up onto a stone floor; once it has landed it is lifted back to the top. */
    public static final Shape FALLING_SAND = new Shape() {
        private final List<Part> parts = sandParts();

        @Override
        public List<Part> parts() {
            return parts;
        }

        @Override
        public int every() {
            return SAND_EVERY;
        }

        @Override
        public void cycle(ServerLevel level, BlockPos origin) {
            BlockPos landed = origin.above();
            BlockPos top = origin.above(SAND_DROP + 1);
            if (level.getBlockState(landed).is(Blocks.SAND) && level.getBlockState(top).isAir()) {
                level.setBlock(landed, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(top, Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        @Override
        public void removing(ServerLevel level, BlockPos origin) {
            AABB column = new AABB(origin, origin.offset(1, SAND_DROP + 2, 1));
            level.getEntitiesOfClass(FallingBlockEntity.class, column).forEach(FallingBlockEntity::discard);
        }
    };

    private static List<Part> sandParts() {
        List<Part> parts = new ArrayList<>();
        parts.add(Part.of(0, 0, 0, Blocks.STONE.defaultBlockState()));
        for (int y = 1; y <= SAND_DROP; y++) {
            parts.add(new Part(new BlockPos(0, y, 0), null, List.of(Blocks.SAND))); // the way down, kept clear
        }
        parts.add(Part.of(0, SAND_DROP + 1, 0, Blocks.SAND.defaultBlockState()));
        return List.copyOf(parts);
    }
}
