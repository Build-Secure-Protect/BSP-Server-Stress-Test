package com.mrgregles.bsp_server_stress_test.setups;

import com.mrgregles.bsp_server_stress_test.machines.Placements.Part;
import com.mrgregles.bsp_server_stress_test.machines.Placements.Shape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * A working setup made of plain parts (owner decisions D36 to D38): machines fed their input every
 * {@link Feed#EVERY} ticks with their output thrown away, spots drained, and, for a family with no creative power
 * source, machines whose energy the mod tops up every tick through Forge's energy hookup (D36). Built by each family's
 * adapter in {@code compat/}.
 */
public class Rig implements Shape {
    /**
     * A machine fed {@code count} of {@code input} (relative to the setup's corner), with {@code fitted} (a capacitor an
     * EnderIO machine needs before it takes power; null for none) put in when missing and never thrown away.
     */
    public record Fed(BlockPos at, Item input, int count, Item fitted) {
        public Fed(BlockPos at, Item input, int count) {
            this(at, input, count, null);
        }
    }

    private final List<Part> parts;
    private final List<Fed> fed;
    private final List<BlockPos> drained;
    private final List<BlockPos> powered;
    private final List<BlockPos> charged;

    public Rig(List<Part> parts, List<Fed> fed, List<BlockPos> drained, List<BlockPos> powered) {
        this(parts, fed, drained, powered, List.of());
    }

    /**
     * {@code charged}: creative power sources filled when placed and at every feed (a creative block placed as a block,
     * not from its creative item, starts empty).
     */
    public Rig(List<Part> parts, List<Fed> fed, List<BlockPos> drained, List<BlockPos> powered, List<BlockPos> charged) {
        this.parts = List.copyOf(parts);
        this.fed = List.copyOf(fed);
        this.drained = List.copyOf(drained);
        this.powered = List.copyOf(powered);
        this.charged = List.copyOf(charged);
    }

    @Override
    public void placed(ServerLevel level, BlockPos origin) {
        for (BlockPos p : charged) {
            topUp(level.getBlockEntity(origin.offset(p)));
        }
        fit(level, origin);
    }

    private void fit(ServerLevel level, BlockPos origin) {
        for (Fed f : fed) {
            BlockEntity be = level.getBlockEntity(origin.offset(f.at()));
            if (f.fitted() == null || be == null || has(be, f.fitted())) {
                continue;
            }
            if (!Feed.fitDirect(be, f.fitted())) {
                for (net.minecraftforge.items.IItemHandler h : Feed.handlers(be)) {
                    if (net.minecraftforge.items.ItemHandlerHelper.insertItem(h, new net.minecraft.world.item.ItemStack(f.fitted()), false).isEmpty()) {
                        break;
                    }
                }
            }
        }
    }

    private static boolean has(BlockEntity be, Item item) {
        for (net.minecraftforge.items.ItemStackHandler h : Feed.stores(be)) {
            for (int i = 0; i < h.getSlots(); i++) {
                if (h.getStackInSlot(i).is(item)) {
                    return true;
                }
            }
        }
        for (net.minecraftforge.items.IItemHandler h : Feed.handlers(be)) {
            for (int i = 0; i < h.getSlots(); i++) {
                if (h.getStackInSlot(i).is(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whatever the fed machines hold is taken out first, so nothing drops when they go. */
    @Override
    public void removing(ServerLevel level, BlockPos origin) {
        for (Fed f : fed) {
            Feed.drain(level, origin.offset(f.at()));
            Feed.clearStores(level.getBlockEntity(origin.offset(f.at())));
        }
    }

    public List<Fed> fed() {
        return fed;
    }

    @Override
    public List<Part> parts() {
        return parts;
    }

    @Override
    public int every() {
        return powered.isEmpty() ? Feed.EVERY : 1;
    }

    @Override
    public void cycle(ServerLevel level, BlockPos origin) {
        for (BlockPos p : powered) {
            topUp(level.getBlockEntity(origin.offset(p)));
        }
        // with power topped up every tick, the feeding still goes every Feed.EVERY ticks, spread by where the setup is
        if (!powered.isEmpty() && Math.floorMod(level.getServer().getTickCount() + origin.hashCode(), Feed.EVERY) != 0) {
            return;
        }
        for (BlockPos p : charged) {
            topUp(level.getBlockEntity(origin.offset(p)));
        }
        for (BlockPos p : drained) {
            Feed.drain(level, origin.offset(p));
        }
        fit(level, origin);
        for (Fed f : fed) {
            Feed.work(level, origin.offset(f.at()), f.input(), f.count(), f.fitted());
        }
    }

    /** Fills a block entity's energy as far as it takes in one go, from no side or else the first side that takes it. */
    public static void topUp(BlockEntity be) {
        if (be == null) {
            return;
        }
        for (Direction d : SIDES) {
            IEnergyStorage e = be.getCapability(ForgeCapabilities.ENERGY, d).orElse(null);
            if (e != null && e.receiveEnergy(Integer.MAX_VALUE, false) > 0) {
                return;
            }
        }
    }

    private static final Direction[] SIDES = {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** A list builder for parts, for the adapters. */
    public static final class Parts {
        public final List<Part> list = new ArrayList<>();

        public Parts add(int x, int y, int z, net.minecraft.world.level.block.state.BlockState state) {
            list.add(Part.of(x, y, z, state));
            return this;
        }
    }
}
