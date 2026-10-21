package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.setups.Rig;
import com.mrgregles.bsp_server_stress_test.setups.SetupOption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;

import java.util.List;

import static com.mrgregles.bsp_server_stress_test.compat.ModBlocks.state;

/**
 * The only class that touches Refined Storage (owner decisions D36 to D41, D47): its working setup, a network on a
 * creative controller moving items from one chest to another. Found by id only; nothing of Refined Storage is called.
 */
public final class RefinedStorage {
    public static final String MODID = "refinedstorage";
    public static final String ITEM_NETWORK = "setups.refinedstorage.item_network";
    /** The chest the importer empties (fed) and the one the external storage fills (drained), for tests. */
    public static final BlockPos FROM_CHEST = new BlockPos(2, 1, 0);
    public static final BlockPos TO_CHEST = new BlockPos(5, 1, 0);

    private RefinedStorage() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    public static List<SetupOption> setups() {
        Block controller = ModBlocks.block(MODID, "creative_controller");
        Block cable = ModBlocks.block(MODID, "cable");
        Block importer = ModBlocks.block(MODID, "importer");
        Block external = ModBlocks.block(MODID, "external_storage");
        if (!ModBlocks.all(controller, cable, importer, external)) {
            BSPServerStressTest.LOGGER.warn("Refined Storage is loaded but a block its setups need is missing; no Refined Storage setups are offered");
            return List.of();
        }
        return List.of(new SetupOption(ITEM_NETWORK, MODID, "setup.refinedstorage.item_network", itemNetwork(controller, cable, importer, external)));
    }

    /**
     * A creative controller and a 6-block cable; an importer pulls cobblestone from a chest the mod keeps filled, and an
     * external storage on a second chest takes it in, which the mod keeps empty. Every item crosses the network, and
     * none is kept in Refined Storage's own storage (which it saves in the world).
     */
    static Rig itemNetwork(Block controller, Block cable, Block importer, Block external) {
        Rig.Parts p = new Rig.Parts().add(0, 1, 2, controller.defaultBlockState());
        for (int x = 1; x <= 6; x++) {
            p.add(x, 1, 2, cable.defaultBlockState());
        }
        p.add(FROM_CHEST.getX(), 1, 1, state(importer, "direction", "north"));
        p.add(TO_CHEST.getX(), 1, 1, state(external, "direction", "north"));
        p.add(FROM_CHEST.getX(), FROM_CHEST.getY(), FROM_CHEST.getZ(), Blocks.CHEST.defaultBlockState());
        p.add(TO_CHEST.getX(), TO_CHEST.getY(), TO_CHEST.getZ(), Blocks.CHEST.defaultBlockState());
        return new Rig(p.list, List.of(new Rig.Fed(FROM_CHEST, Items.COBBLESTONE, 64)), List.of(TO_CHEST), List.of());
    }
}
