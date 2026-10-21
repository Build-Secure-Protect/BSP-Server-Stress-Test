package com.mrgregles.bsp_server_stress_test.setups;

import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;

/**
 * {@code setups.<family>.<name>} (owner decisions D35 to D40): a mod's machines powered from the family's own creative
 * source and fed by {@link Feed}, so they really work; the level is how many setups stand, in the setups grid through
 * {@link Placements}, so the record and {@code /stress cleanup} cover them.
 */
public final class SetupOption implements StressOption {
    private final String id;
    private final String modId;
    private final String nameKey;
    private final Placements.Shape shape;

    public SetupOption(String id, String modId, String nameKey, Placements.Shape shape) {
        this.id = id;
        this.modId = modId;
        this.nameKey = nameKey;
        this.shape = shape;
    }

    public String modId() {
        return modId;
    }

    /** The setup's English name ("Press line: ..."). */
    public String name() {
        return Text.raw(nameKey);
    }

    public Placements.Shape shape() {
        return shape;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String describe(int level) {
        return Text.raw("option.setup", Fmt.count(level), name());
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
        Placements.setUnits(server, id, Placements.Area.SETUPS, i -> shape, level);
    }

    @Override
    public void release(MinecraftServer server) {
        Placements.setUnits(server, id, Placements.Area.SETUPS, i -> shape, 0);
    }
}
