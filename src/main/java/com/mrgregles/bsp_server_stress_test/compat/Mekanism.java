package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.setups.Rig;
import com.mrgregles.bsp_server_stress_test.setups.SetupOption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import java.util.List;

import static com.mrgregles.bsp_server_stress_test.compat.ModBlocks.state;

/**
 * The only class that touches Mekanism (owner decisions D36 to D41, D46): its working setup, machines powered by a
 * creative energy cube through universal cables. Found by id only; nothing of Mekanism is called.
 */
public final class Mekanism {
    public static final String MODID = "mekanism";
    public static final String MACHINE_LINE = "setups.mekanism.machine_line";
    /** Where the machine line's machines stand, and what each is fed and makes, for tests. */
    public static final BlockPos ENRICHER = new BlockPos(2, 2, 1);
    public static final BlockPos CRUSHER = new BlockPos(4, 2, 1);
    public static final BlockPos SMELTER = new BlockPos(6, 2, 1);

    private Mekanism() {
    }

    /**
     * Writes a full charge into a creative energy cube's saved data ({@code EnergyContainers}, Mekanism's own form) and
     * reads it back in. The charge is far beyond what a run uses; a creative container never runs down anyway.
     */
    static void charge(BlockEntity be) {
        if (be == null) {
            return;
        }
        try {
            CompoundTag tag = be.saveWithoutMetadata();
            ListTag containers = new ListTag();
            CompoundTag one = new CompoundTag();
            one.putByte("Container", (byte) 0);
            one.putString("stored", "1000000000000000");
            containers.add(one);
            tag.put("EnergyContainers", containers);
            be.load(tag);
            be.setChanged();
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.warn("Could not charge the Mekanism energy cube at {}: {}", be.getBlockPos(), e.toString());
        }
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    public static List<SetupOption> setups() {
        Block cube = ModBlocks.block(MODID, "creative_energy_cube");
        Block cable = ModBlocks.block(MODID, "basic_universal_cable");
        Block enricher = ModBlocks.block(MODID, "enrichment_chamber");
        Block crusher = ModBlocks.block(MODID, "crusher");
        Block smelter = ModBlocks.block(MODID, "energized_smelter");
        if (!ModBlocks.all(cube, cable, enricher, crusher, smelter)) {
            BSPServerStressTest.LOGGER.warn("Mekanism is loaded but a block its setups need is missing; no Mekanism setups are offered");
            return List.of();
        }
        return List.of(new SetupOption(MACHINE_LINE, MODID, "setup.mekanism.machine_line", machineLine(cube, cable, enricher, crusher, smelter)));
    }

    /**
     * A creative energy cube, its output face east, into a 6-block universal cable with three machines standing on it:
     * an enrichment chamber (iron ore into iron dust), a crusher (cobblestone into gravel) and an energized smelter (raw
     * beef into cooked beef). Placed as a block rather than from its creative item, the cube starts empty and takes no
     * energy put in from outside (a creative container), so its saved charge is written into it, as the item would.
     */
    static Rig machineLine(Block cube, Block cable, Block enricher, Block crusher, Block smelter) {
        Rig.Parts p = new Rig.Parts().add(0, 1, 1, state(cube, "facing", "east"));
        for (int x = 1; x <= 6; x++) {
            p.add(x, 1, 1, cable.defaultBlockState());
        }
        p.add(ENRICHER.getX(), ENRICHER.getY(), ENRICHER.getZ(), state(enricher, "facing", "north"));
        p.add(CRUSHER.getX(), CRUSHER.getY(), CRUSHER.getZ(), state(crusher, "facing", "north"));
        p.add(SMELTER.getX(), SMELTER.getY(), SMELTER.getZ(), state(smelter, "facing", "north"));
        return new Rig(p.list, List.of(new Rig.Fed(ENRICHER, Items.IRON_ORE, 16), new Rig.Fed(CRUSHER, Items.COBBLESTONE, 16),
                new Rig.Fed(SMELTER, Items.BEEF, 16)), List.of(), List.of()) {
            @Override
            public void placed(ServerLevel level, BlockPos origin) {
                charge(level.getBlockEntity(origin.offset(0, 1, 1)));
            }
        };
    }
}
