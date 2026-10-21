package com.mrgregles.bsp_server_stress_test.setups;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * What working setups work on (owner decision D37): every few seconds an input gets a cheap item its machine can
 * process, and whatever else it holds (the output) is taken out and thrown away, so it never backs up or stops. Done
 * through Forge's item hookup (and any fluid it made is thrown away too), from no side (the whole inventory, where a mod offers it) or else from above as a hopper
 * would, so it works for any mod's machine.
 */
public final class Feed {
    /** Ticks between two feeds (D37: every few seconds). */
    public static final int EVERY = 40;

    private Feed() {
    }

    public static IItemHandler handler(ServerLevel level, BlockPos pos) {
        List<IItemHandler> all = handlers(level.getBlockEntity(pos));
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Every item handler a block entity offers: from no side first, then from each side (a Mekanism machine takes its
     * input only from some sides and nothing from no side), each one once.
     */
    public static List<IItemHandler> handlers(BlockEntity be) {
        List<IItemHandler> out = new ArrayList<>();
        if (be == null) {
            return out;
        }
        List<Direction> sides = new ArrayList<>();
        sides.add(null);
        sides.addAll(List.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST));
        for (Direction d : sides) {
            be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).ifPresent(h -> {
                if (out.stream().noneMatch(o -> o == h)) {
                    out.add(h);
                }
            });
        }
        return out;
    }

    /**
     * Throws away everything at {@code pos} that is not {@code input}, then puts in {@code count} of it, through the
     * first handler that takes it, if none is there.
     */
    public static void work(ServerLevel level, BlockPos pos, Item input, int count) {
        work(level, pos, input, count, null);
    }

    /** The same, never throwing away {@code keep} (a capacitor fitted in the machine); null keeps nothing. */
    public static void work(ServerLevel level, BlockPos pos, Item input, int count, Item keep) {
        drainFluids(level.getBlockEntity(pos));
        List<IItemHandler> all = handlers(level.getBlockEntity(pos));
        boolean has = false;
        for (IItemHandler h : all) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.isEmpty()) {
                    continue;
                }
                if (s.is(input)) {
                    has = true;
                } else if (keep == null || !s.is(keep)) {
                    h.extractItem(i, s.getCount(), false);
                }
            }
        }
        if (has) {
            return;
        }
        ItemStack stack = new ItemStack(input, count);
        for (IItemHandler h : all) {
            if (ItemHandlerHelper.insertItem(h, stack.copy(), true).getCount() < count) {
                ItemHandlerHelper.insertItem(h, stack, false);
                return;
            }
        }
    }

    /** Throws away every fluid a machine holds (a resourceful furnace's essence), so its tank never fills and stops it. */
    public static void drainFluids(BlockEntity be) {
        if (be == null) {
            return;
        }
        for (Direction d : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            be.getCapability(ForgeCapabilities.FLUID_HANDLER, d).ifPresent(f -> f.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE));
        }
    }

    /** Throws away everything at {@code pos} (the end of a belt). Returns how many items went. */
    public static int drain(ServerLevel level, BlockPos pos) {
        int out = 0;
        for (IItemHandler h : handlers(level.getBlockEntity(pos))) {
            for (int i = 0; i < h.getSlots(); i++) {
                out += h.extractItem(i, h.getStackInSlot(i).getCount(), false).getCount();
            }
        }
        return out;
    }

    /**
     * Empties every item store a block entity keeps in a field (Forge's {@code ItemStackHandler}, which Titanium's and
     * many mods' inventories are), inputs and fitted items alike: a machine that will not give up its input from outside
     * (Create's millstone) or drops its inventory when removed drops nothing then.
     */
    public static void clearStores(BlockEntity be) {
        for (net.minecraftforge.items.ItemStackHandler h : stores(be)) {
            for (int i = 0; i < h.getSlots(); i++) {
                h.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Puts {@code item} straight into the first empty slot of a block entity's item stores that takes it and refuses an
     * ordinary block (a slot made for it and closed to outside hookups, like an EnderIO machine's capacitor slot, not an
     * output slot that takes anything). False when none does.
     */
    public static boolean fitDirect(BlockEntity be, Item item) {
        for (net.minecraftforge.items.ItemStackHandler h : stores(be)) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack stack = new ItemStack(item);
                if (h.getStackInSlot(i).isEmpty() && h.isItemValid(i, stack) && !h.isItemValid(i, new ItemStack(net.minecraft.world.item.Items.DIRT))) {
                    h.setStackInSlot(i, stack);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The item stores a block entity keeps in its fields. A class whose fields cannot be read (one naming a client-only
     * class, as some of Mekanism's do, which a server may not load) is passed over.
     */
    static List<net.minecraftforge.items.ItemStackHandler> stores(BlockEntity be) {
        List<net.minecraftforge.items.ItemStackHandler> out = new ArrayList<>();
        for (Class<?> c = be == null ? null : be.getClass(); c != null && c != BlockEntity.class; c = c.getSuperclass()) {
            java.lang.reflect.Field[] fields;
            try {
                fields = c.getDeclaredFields();
            } catch (LinkageError | RuntimeException e) {
                continue;
            }
            for (java.lang.reflect.Field f : fields) {
                if (!net.minecraftforge.items.ItemStackHandler.class.isAssignableFrom(f.getType())
                        || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    if (f.get(be) instanceof net.minecraftforge.items.ItemStackHandler h && !out.contains(h)) {
                        out.add(h);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    com.mrgregles.bsp_server_stress_test.BSPServerStressTest.LOGGER.debug("Could not read {} of {}: {}", f.getName(), be.getBlockPos(), e.toString());
                }
            }
        }
        return out;
    }

    /** How many of {@code item} are at {@code pos}, for tests. */
    public static int count(ServerLevel level, BlockPos pos, Item item) {
        int most = 0;
        for (IItemHandler h : handlers(level.getBlockEntity(pos))) {
            int n = 0;
            for (int i = 0; i < h.getSlots(); i++) {
                if (h.getStackInSlot(i).is(item)) {
                    n += h.getStackInSlot(i).getCount();
                }
            }
            most = Math.max(most, n);
        }
        return most;
    }
}
