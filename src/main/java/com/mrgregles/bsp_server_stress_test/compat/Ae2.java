package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.setups.Rig;
import com.mrgregles.bsp_server_stress_test.setups.SetupOption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * The only class that touches Applied Energistics 2 (owner decisions D36 to D41, D48): its working setup, a network on
 * a creative energy cell taking items in through an interface and storing them in an ME chest. Blocks are found by id;
 * the one call into AE2's own code (the ME chest's {@code setCell}, as a player puts a cell in) is reflective.
 */
public final class Ae2 {
    public static final String MODID = "ae2";
    public static final String ITEM_NETWORK = "setups.ae2.item_network";
    /** The interface the mod feeds and the ME chest that stores what the network takes in, for tests. */
    public static final BlockPos INTERFACE = new BlockPos(1, 1, 1);
    public static final BlockPos CHEST = new BlockPos(2, 1, 1);

    private Ae2() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    public static List<SetupOption> setups() {
        Block controller = ModBlocks.block(MODID, "controller");
        Block energy = ModBlocks.block(MODID, "creative_energy_cell");
        Block iface = ModBlocks.block(MODID, "interface");
        Block chest = ModBlocks.block(MODID, "chest");
        Item cell = ModBlocks.item(MODID, "item_storage_cell_4k");
        if (!ModBlocks.all(controller, energy, iface, chest, cell)) {
            BSPServerStressTest.LOGGER.warn("AE2 is loaded but a block its setups need is missing; no AE2 setups are offered");
            return List.of();
        }
        return List.of(new SetupOption(ITEM_NETWORK, MODID, "setup.ae2.item_network", itemNetwork(controller, energy, iface, chest, cell)));
    }

    /**
     * A controller with a creative energy cell, an ME interface and an ME chest holding a 4k item cell, side by side so
     * they join into one network. The mod feeds cobblestone into the interface, which hands it to the network, which
     * stores it in the chest's cell. The chest lets nothing out from outside, so when its cell is full it gets a fresh
     * empty one (about once an hour at this rate); before the chest goes, its cell is taken out, so nothing drops.
     */
    static Rig itemNetwork(Block controller, Block energy, Block iface, Block chest, Item cell) {
        Rig.Parts p = new Rig.Parts()
                .add(0, 1, 1, controller.defaultBlockState())
                .add(0, 1, 0, energy.defaultBlockState())
                .add(INTERFACE.getX(), INTERFACE.getY(), INTERFACE.getZ(), iface.defaultBlockState())
                .add(CHEST.getX(), CHEST.getY(), CHEST.getZ(), chest.defaultBlockState());
        return new Rig(p.list, List.of(new Rig.Fed(INTERFACE, Items.COBBLESTONE, 16)), List.of(), List.of()) {
            @Override
            public void placed(ServerLevel level, BlockPos origin) {
                Calls.setCell(level.getBlockEntity(origin.offset(CHEST)), new ItemStack(cell));
            }

            @Override
            public void cycle(ServerLevel level, BlockPos origin) {
                BlockEntity be = level.getBlockEntity(origin.offset(CHEST));
                if (Calls.cellState(be).contains("FULL")) {
                    Calls.setCell(be, new ItemStack(cell));
                }
                super.cycle(level, origin);
            }

            @Override
            public void removing(ServerLevel level, BlockPos origin) {
                Calls.setCell(level.getBlockEntity(origin.offset(CHEST)), ItemStack.EMPTY);
            }
        };
    }

    /** The calls into AE2's own code, by reflection, so nothing of AE2 loads unless it is there. */
    public static final class Calls {
        private Calls() {
        }

        static void setCell(BlockEntity be, ItemStack cell) {
            if (be == null) {
                return;
            }
            try {
                be.getClass().getMethod("setCell", ItemStack.class).invoke(be, cell);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                BSPServerStressTest.LOGGER.warn("Could not put a cell in the AE2 chest at {}: {}", be.getBlockPos(), e.toString());
            }
        }

        /** The ME chest's cell state by name (EMPTY, NOT_EMPTY, TYPES_FULL, FULL, ABSENT), or "" when it cannot tell. */
        public static String cellState(BlockEntity be) {
            if (be == null) {
                return "";
            }
            try {
                return String.valueOf(be.getClass().getMethod("getCellStatus", int.class).invoke(be, 0));
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return "";
            }
        }
    }
}
