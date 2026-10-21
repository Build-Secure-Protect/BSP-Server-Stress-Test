package com.mrgregles.bsp_server_stress_test.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Optional;

/**
 * Finding another mod's blocks and items by id and setting their states by property name, for the adapters' working
 * setups (owner decision D41: no adapter is compiled against the mod it sets up).
 */
public final class ModBlocks {
    private ModBlocks() {
    }

    /** The block, or null when this server has none of that id. */
    public static Block block(String modId, String path) {
        ResourceLocation id = ResourceLocation.tryParse(modId + ":" + path);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.get(id) : null;
    }

    /** The item, or null when this server has none of that id. */
    public static Item item(String modId, String path) {
        ResourceLocation id = ResourceLocation.tryParse(modId + ":" + path);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
    }

    /** Whether every one is there. */
    public static boolean all(Object... found) {
        for (Object o : found) {
            if (o == null) {
                return false;
            }
        }
        return true;
    }

    /** The state with a property set by name ("facing", "east"); unchanged when the block has no such property or value. */
    public static BlockState with(BlockState state, String name, String value) {
        for (Property<?> p : state.getProperties()) {
            if (p.getName().equals(name)) {
                return set(state, p, value);
            }
        }
        return state;
    }

    /** The block's default state with properties set by name, in pairs ("facing", "east", "axis", "y"). */
    public static BlockState state(Block block, String... props) {
        BlockState s = block.defaultBlockState();
        for (int i = 0; i + 1 < props.length; i += 2) {
            s = with(s, props[i], props[i + 1]);
        }
        return s;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> p, String value) {
        Optional<T> v = p.getValue(value);
        return v.map(t -> state.setValue(p, t)).orElse(state);
    }
}
