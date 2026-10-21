package com.mrgregles.bsp_server_stress_test.machines;

import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

/**
 * {@code machines.<mod>.<block>}: that many of a ticking block placed and left idle (not powered or fed), one option
 * for every machine in the catalogue (owner decision D6). Working setups come in later tasks.
 */
public final class MachineOption implements StressOption {
    private final String id;
    private final Block block;
    private final String name;

    public MachineOption(String id, Block block, String name) {
        this.id = id;
        this.block = block;
        this.name = name;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String describe(int level) {
        return Text.raw("option.machine", Fmt.count(level), name);
    }

    @Override
    public int defaultStep() {
        return 5;
    }

    @Override
    public int defaultCeiling() {
        return 100;
    }

    @Override
    public Limit limit() {
        return Limit.MACHINES;
    }

    @Override
    public void apply(MinecraftServer server, int level) {
        Placements.set(server, id, block, level);
    }

    @Override
    public void release(MinecraftServer server) {
        Placements.set(server, id, block, 0);
    }
}
