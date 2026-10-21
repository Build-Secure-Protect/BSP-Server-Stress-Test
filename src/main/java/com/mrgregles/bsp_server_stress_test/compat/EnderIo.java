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
 * The only class that touches EnderIO (owner decisions D36 to D41, D50): its working setup, two SAG mills and an alloy
 * smelter around its own creative power block, each with a basic capacitor fitted (an EnderIO machine takes no power
 * without one). Found by id only.
 */
public final class EnderIo {
    public static final String MODID = "enderio";
    public static final String MACHINES = "setups.enderio.machines";
    /** Where the machines stand around the power source in the middle, for tests. */
    public static final BlockPos WEST = new BlockPos(1, 1, 2);
    public static final BlockPos EAST = new BlockPos(3, 1, 2);
    public static final BlockPos NORTH = new BlockPos(2, 1, 1);
    static final BlockPos SOURCE = new BlockPos(2, 1, 2);

    private EnderIo() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    public static List<SetupOption> setups() {
        Block source = ModBlocks.block(MODID, "creative_power");
        Block west = ModBlocks.block(MODID, "sag_mill");
        Block east = ModBlocks.block(MODID, "sag_mill");
        Block north = ModBlocks.block(MODID, "alloy_smelter");
        net.minecraft.world.item.Item capacitor = ModBlocks.item(MODID, "basic_capacitor");
        if (!ModBlocks.all(source, west, east, north, capacitor)) {
            BSPServerStressTest.LOGGER.warn("EnderIO is loaded but a block its setups need is missing; no EnderIO setups are offered");
            return List.of();
        }
        Rig.Parts p = new Rig.Parts()
                .add(SOURCE.getX(), SOURCE.getY(), SOURCE.getZ(), source.defaultBlockState())
                .add(WEST.getX(), WEST.getY(), WEST.getZ(), ModBlocks.state(west, "facing", "west"))
                .add(EAST.getX(), EAST.getY(), EAST.getZ(), ModBlocks.state(east, "facing", "east"))
                .add(NORTH.getX(), NORTH.getY(), NORTH.getZ(), ModBlocks.state(north, "facing", "north"));
        Rig rig = new Rig(p.list, List.of(new Rig.Fed(WEST, Items.COBBLESTONE, 16, capacitor), new Rig.Fed(EAST, Items.COBBLESTONE, 16, capacitor),
                new Rig.Fed(NORTH, Items.BEEF, 16, capacitor)), List.of(), List.of(), List.of(SOURCE));
        return List.of(new SetupOption(MACHINES, MODID, "setup.enderio.machines", rig));
    }
}
