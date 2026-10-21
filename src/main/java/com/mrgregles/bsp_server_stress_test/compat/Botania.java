package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.setups.Rig;
import com.mrgregles.bsp_server_stress_test.setups.SetupOption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import java.util.List;

import static com.mrgregles.bsp_server_stress_test.compat.ModBlocks.state;

/**
 * The only class that touches Botania (owner decisions D36 to D41, D51): its working setup, an Exoflame drawing mana
 * from a creative mana pool to heat three furnaces of raw beef. Blocks are found by id; the one call into Botania's own
 * code (binding the flower to its pool, as a player's wand does) is reflective.
 */
public final class Botania {
    public static final String MODID = "botania";
    public static final String EXOFLAME_FURNACES = "setups.botania.exoflame_furnaces";
    /** Where the furnaces stand around the flower, for tests. */
    public static final BlockPos WEST = new BlockPos(0, 1, 2);
    public static final BlockPos EAST = new BlockPos(4, 1, 2);
    public static final BlockPos SOUTH = new BlockPos(2, 1, 4);
    static final BlockPos FLOWER = new BlockPos(2, 1, 2);
    static final BlockPos POOL = new BlockPos(2, 1, 0);

    private Botania() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    public static List<SetupOption> setups() {
        Block pool = ModBlocks.block(MODID, "creative_pool");
        Block exoflame = ModBlocks.block(MODID, "exoflame");
        if (!ModBlocks.all(pool, exoflame)) {
            BSPServerStressTest.LOGGER.warn("Botania is loaded but a block its setups need is missing; no Botania setups are offered");
            return List.of();
        }
        return List.of(new SetupOption(EXOFLAME_FURNACES, MODID, "setup.botania.exoflame_furnaces", exoflameFurnaces(pool, exoflame)));
    }

    /**
     * An Exoflame on a block of dirt with a creative mana pool beside it, bound to it when placed (a flower placed as a
     * block, not by a player, binds to nothing), and three unlit furnaces of raw beef around it two blocks away, which the
     * flower keeps burning with mana.
     */
    static Rig exoflameFurnaces(Block pool, Block exoflame) {
        Rig.Parts p = new Rig.Parts()
                .add(2, 0, 2, Blocks.DIRT.defaultBlockState())
                .add(FLOWER.getX(), FLOWER.getY(), FLOWER.getZ(), exoflame.defaultBlockState())
                .add(POOL.getX(), POOL.getY(), POOL.getZ(), pool.defaultBlockState())
                .add(WEST.getX(), WEST.getY(), WEST.getZ(), state(Blocks.FURNACE, "facing", "west"))
                .add(EAST.getX(), EAST.getY(), EAST.getZ(), state(Blocks.FURNACE, "facing", "east"))
                .add(SOUTH.getX(), SOUTH.getY(), SOUTH.getZ(), state(Blocks.FURNACE, "facing", "south"));
        return new Rig(p.list, List.of(new Rig.Fed(WEST, Items.BEEF, 16), new Rig.Fed(EAST, Items.BEEF, 16),
                new Rig.Fed(SOUTH, Items.BEEF, 16)), List.of(), List.of()) {
            @Override
            public void placed(ServerLevel level, BlockPos origin) {
                super.placed(level, origin);
                Calls.bind(level.getBlockEntity(origin.offset(FLOWER)), origin.offset(POOL));
            }
        };
    }

    /** The calls into Botania's own code, by reflection, so nothing of Botania loads unless it is there. */
    static final class Calls {
        private Calls() {
        }

        /** Binds a flower to a pool (or a generating flower to a spreader), as a player's wand does. */
        static void bind(BlockEntity flower, BlockPos to) {
            if (flower == null) {
                return;
            }
            try {
                flower.getClass().getMethod("setBindingPos", BlockPos.class).invoke(flower, to);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not bind the Botania flower at {}: {}", flower.getBlockPos(), e.toString());
            }
        }
    }
}
