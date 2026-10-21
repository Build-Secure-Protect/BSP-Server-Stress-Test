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
 * The only class that touches Industrial Foregoing (owner decisions D36 to D41, D50): its working setup, three
 * resourceful furnaces whose energy the mod tops up every tick (D36: Industrial Foregoing has no creative power block,
 * and its library Titanium's creative generator is only registered in development). Found by id only.
 */
public final class IndustrialForegoing {
    public static final String MODID = "industrialforegoing";
    public static final String MACHINES = "setups.industrialforegoing.machines";
    /** Where the machines stand around the middle, for tests. */
    public static final BlockPos WEST = new BlockPos(1, 1, 2);
    public static final BlockPos EAST = new BlockPos(3, 1, 2);
    public static final BlockPos NORTH = new BlockPos(2, 1, 1);

    private IndustrialForegoing() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    public static List<SetupOption> setups() {
        Block west = ModBlocks.block(MODID, "resourceful_furnace");
        Block east = ModBlocks.block(MODID, "resourceful_furnace");
        Block north = ModBlocks.block(MODID, "resourceful_furnace");
        if (!ModBlocks.all(west, east, north)) {
            BSPServerStressTest.LOGGER.warn("Industrial Foregoing is loaded but a block its setups need is missing; no Industrial Foregoing setups are offered");
            return List.of();
        }
        Rig.Parts p = new Rig.Parts()
                .add(WEST.getX(), WEST.getY(), WEST.getZ(), ModBlocks.state(west, "facing", "west"))
                .add(EAST.getX(), EAST.getY(), EAST.getZ(), ModBlocks.state(east, "facing", "east"))
                .add(NORTH.getX(), NORTH.getY(), NORTH.getZ(), ModBlocks.state(north, "facing", "north"));
        Rig rig = new Rig(p.list, List.of(new Rig.Fed(WEST, Items.BEEF, 16), new Rig.Fed(EAST, Items.BEEF, 16), new Rig.Fed(NORTH, Items.BEEF, 16)), List.of(), List.of(WEST, EAST, NORTH));
        return List.of(new SetupOption(MACHINES, MODID, "setup.industrialforegoing.machines", rig));
    }
}
