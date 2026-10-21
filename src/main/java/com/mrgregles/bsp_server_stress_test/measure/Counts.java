package com.mrgregles.bsp_server_stress_test.measure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server holds at one moment: per dimension the loaded chunks, entities, ticking block entities and players,
 * and the memory the server uses. Read on the server thread.
 */
public record Counts(List<Dimension> dimensions, long memoryUsedMb, long memoryMaxMb) {

    public record Dimension(String id, int loadedChunks, int entities, int tickingBlockEntities, int players) {
    }

    public static Counts snapshot(MinecraftServer server) {
        List<Dimension> dims = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            int entities = 0;
            for (Entity ignored : level.getAllEntities()) {
                entities++;
            }
            dims.add(new Dimension(level.dimension().location().toString(), level.getChunkSource().getLoadedChunksCount(),
                    entities, level.blockEntityTickers.size(), level.players().size()));
        }
        Runtime rt = Runtime.getRuntime();
        return new Counts(List.copyOf(dims), (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20);
    }

    public Dimension dimension(String id) {
        return dimensions.stream().filter(d -> d.id.equals(id)).findFirst().orElse(null);
    }

    public int loadedChunks() {
        return dimensions.stream().mapToInt(Dimension::loadedChunks).sum();
    }

    public int entities() {
        return dimensions.stream().mapToInt(Dimension::entities).sum();
    }

    public int tickingBlockEntities() {
        return dimensions.stream().mapToInt(Dimension::tickingBlockEntities).sum();
    }

    public int players() {
        return dimensions.stream().mapToInt(Dimension::players).sum();
    }
}
