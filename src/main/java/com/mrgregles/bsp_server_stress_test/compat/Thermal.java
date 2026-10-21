package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.setups.Rig;
import com.mrgregles.bsp_server_stress_test.setups.SetupOption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * The only class that touches Thermal Expansion (owner decisions D36 to D41, D49): its working setup, a pulverizer, a
 * redstone furnace and a sawmill around Powah's creative energy cell (Thermal has no creative power block of its own,
 * and the pack has Powah). Found by id only.
 */
public final class Thermal {
    public static final String MODID = "thermal";
    public static final String MACHINES = "setups.thermal.machines";
    /** Where the machines stand around the power source in the middle, for tests. */
    public static final BlockPos WEST = new BlockPos(1, 1, 2);
    public static final BlockPos EAST = new BlockPos(3, 1, 2);
    public static final BlockPos NORTH = new BlockPos(2, 1, 1);
    static final BlockPos SOURCE = new BlockPos(2, 1, 2);

    private Thermal() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID) && ModList.get().isLoaded("thermal_expansion") && ModList.get().isLoaded(Powah.MODID);
    }

    public static List<SetupOption> setups() {
        Block source = ModBlocks.block(Powah.MODID, "energy_cell_creative");
        Block west = ModBlocks.block(MODID, "machine_pulverizer");
        Block east = ModBlocks.block(MODID, "machine_furnace");
        Block north = ModBlocks.block(MODID, "machine_sawmill");
        if (!ModBlocks.all(source, west, east, north)) {
            BSPServerStressTest.LOGGER.warn("Thermal is loaded but a block its setups need is missing; no Thermal setups are offered");
            return List.of();
        }
        Rig.Parts p = new Rig.Parts()
                .add(SOURCE.getX(), SOURCE.getY(), SOURCE.getZ(), source.defaultBlockState())
                .add(WEST.getX(), WEST.getY(), WEST.getZ(), ModBlocks.state(west, "facing", "west"))
                .add(EAST.getX(), EAST.getY(), EAST.getZ(), ModBlocks.state(east, "facing", "east"))
                .add(NORTH.getX(), NORTH.getY(), NORTH.getZ(), ModBlocks.state(north, "facing", "north"));
        Rig rig = new Rig(p.list, List.of(new Rig.Fed(WEST, Items.COBBLESTONE, 16), new Rig.Fed(EAST, Items.BEEF, 16), new Rig.Fed(NORTH, Items.OAK_LOG, 16)), List.of(), List.of(), List.of(SOURCE));
        return List.of(new SetupOption(MACHINES, MODID, "setup.thermal.machines", rig));
    }
}
