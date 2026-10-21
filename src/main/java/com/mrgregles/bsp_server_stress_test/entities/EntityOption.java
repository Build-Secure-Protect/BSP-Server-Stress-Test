package com.mrgregles.bsp_server_stress_test.entities;

import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;

/**
 * {@code entities.<mod>.<mob>} for every mob in the catalogue, {@code entities.items} (dropped items) and
 * {@code entities.xp} (experience orbs): that many spawned in a crowd at the places (owner decision D13).
 */
public final class EntityOption implements StressOption {
    public static final String ITEMS = "entities.items";
    public static final String XP = "entities.xp";

    private final String id;
    private final Spawns.Kind kind;
    private final EntityType<?> type;
    private final String name;

    public EntityOption(String id, Spawns.Kind kind, EntityType<?> type, String name) {
        this.id = id;
        this.kind = kind;
        this.type = type;
        this.name = name;
    }

    public static EntityOption items() {
        return new EntityOption(ITEMS, Spawns.Kind.ITEM, EntityType.ITEM, "");
    }

    public static EntityOption xp() {
        return new EntityOption(XP, Spawns.Kind.XP, EntityType.EXPERIENCE_ORB, "");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String describe(int level) {
        return switch (kind) {
            case ITEM -> Text.raw("option.items", Fmt.count(level));
            case XP -> Text.raw("option.xp", Fmt.count(level));
            case MOB -> Text.raw("option.mob", Fmt.count(level), name);
        };
    }

    @Override
    public int defaultStep() {
        return kind == Spawns.Kind.MOB ? 10 : 100;
    }

    @Override
    public int defaultCeiling() {
        return kind == Spawns.Kind.MOB ? 200 : 2000;
    }

    @Override
    public Limit limit() {
        return Limit.ENTITIES;
    }

    @Override
    public void apply(MinecraftServer server, int level) {
        Spawns.set(server, id, kind, type, level);
    }

    @Override
    public void release(MinecraftServer server) {
        Spawns.set(server, id, kind, type, 0);
    }
}
