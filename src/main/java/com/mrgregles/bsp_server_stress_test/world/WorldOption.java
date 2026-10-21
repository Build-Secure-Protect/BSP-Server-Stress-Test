package com.mrgregles.bsp_server_stress_test.world;

import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;

import java.util.function.IntFunction;

/**
 * {@code world.redstone_clocks}, {@code world.hoppers}, {@code world.fluids}, {@code world.lighting} and
 * {@code world.falling_blocks}: the level is how many of the option's contraptions stand (owner decisions D27 to D30,
 * D32, D33), placed in the world grid through {@link Placements}, so the record and {@code /stress cleanup} cover them.
 */
public final class WorldOption implements StressOption {
    public static final String REDSTONE_CLOCKS = "world.redstone_clocks";
    public static final String HOPPERS = "world.hoppers";
    public static final String FLUIDS = "world.fluids";
    public static final String LIGHTING = "world.lighting";
    public static final String FALLING_BLOCKS = "world.falling_blocks";

    private final String id;
    private final String key;
    private final IntFunction<Placements.Shape> shapes;
    private final int step;
    private final int ceiling;

    WorldOption(String id, String key, IntFunction<Placements.Shape> shapes, int step, int ceiling) {
        this.id = id;
        this.key = key;
        this.shapes = shapes;
        this.step = step;
        this.ceiling = ceiling;
    }

    static WorldOption redstoneClocks() {
        return new WorldOption(REDSTONE_CLOCKS, "option.redstone_clocks", i -> Contraptions.REDSTONE_CLOCK, 25, 500);
    }

    static WorldOption hoppers() {
        return new WorldOption(HOPPERS, "option.hopper_rings", i -> Contraptions.HOPPER_RING, 25, 500);
    }

    static WorldOption fluids() {
        return new WorldOption(FLUIDS, "option.fluids", Contraptions::basinFor, 10, 200);
    }

    static WorldOption lighting() {
        return new WorldOption(LIGHTING, "option.lighting", i -> Contraptions.LIGHT, 25, 500);
    }

    static WorldOption fallingBlocks() {
        return new WorldOption(FALLING_BLOCKS, "option.falling_blocks", i -> Contraptions.FALLING_SAND, 25, 500);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String describe(int level) {
        return Text.raw(key, Fmt.count(level));
    }

    @Override
    public int defaultStep() {
        return step;
    }

    @Override
    public int defaultCeiling() {
        return ceiling;
    }

    @Override
    public Limit limit() {
        return Limit.MACHINES;
    }

    @Override
    public void apply(MinecraftServer server, int level) {
        Placements.setUnits(server, id, Placements.Area.WORLD, shapes, level);
    }

    @Override
    public void release(MinecraftServer server) {
        Placements.setUnits(server, id, Placements.Area.WORLD, shapes, 0);
    }
}
