package com.mrgregles.bsp_server_stress_test.explosions;

import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

/**
 * {@code explosions.vanilla} (the level is the power: TNT is 4), {@code explosions.tnt_chain} (lit TNT per blast) and
 * {@code explosions.<mod>.<block>} for each explosive in the catalogue (how many set off per blast).
 */
public final class ExplosionOption implements StressOption {
    public static final String VANILLA = "explosions.vanilla";
    public static final String TNT_CHAIN = "explosions.tnt_chain";

    private final String id;
    private final Explosions.Kind kind;
    private final Block block;
    private final String name;

    public ExplosionOption(String id, Explosions.Kind kind, Block block, String name) {
        this.id = id;
        this.kind = kind;
        this.block = block;
        this.name = name;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String describe(int level) {
        return switch (kind) {
            case VANILLA -> Text.raw("option.explosion", Fmt.count(level));
            case TNT_CHAIN -> Text.raw("option.tnt_chain", Fmt.count(level));
            case BLOCK -> Text.raw("option.explosive", Fmt.count(level), name);
        };
    }

    @Override
    public int defaultStep() {
        return switch (kind) {
            case VANILLA -> 8;
            case TNT_CHAIN -> 20;
            case BLOCK -> 1;
        };
    }

    @Override
    public int defaultCeiling() {
        return switch (kind) {
            case VANILLA -> 128;
            case TNT_CHAIN -> 400;
            case BLOCK -> 10;
        };
    }

    @Override
    public Limit limit() {
        return switch (kind) {
            case VANILLA -> Limit.EXPLOSION_POWER;
            case TNT_CHAIN -> Limit.ENTITIES;
            case BLOCK -> Limit.MACHINES;
        };
    }

    @Override
    public void apply(MinecraftServer server, int level) {
        Explosions.set(server, id, kind, block, level);
    }

    @Override
    public void release(MinecraftServer server) {
        Explosions.set(server, id, kind, block, 0);
    }
}
