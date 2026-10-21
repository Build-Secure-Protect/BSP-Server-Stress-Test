package com.mrgregles.bsp_server_stress_test.chunks;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/** A named spot chunks are held around: a dimension and the chunk at the middle (owner decision D24). */
public record Place(String name, ResourceKey<Level> dimension, ChunkPos centre) {
}
