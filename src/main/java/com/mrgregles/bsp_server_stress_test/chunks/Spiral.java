package com.mrgregles.bsp_server_stress_test.chunks;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/** The first {@code n} chunks of a square growing outward from a centre, ring by ring (D24). */
public final class Spiral {
    private Spiral() {
    }

    public static List<ChunkPos> around(ChunkPos centre, int n) {
        List<ChunkPos> out = new ArrayList<>(Math.max(0, n));
        if (n <= 0) {
            return out;
        }
        out.add(centre);
        for (int r = 1; out.size() < n; r++) {
            // the ring at distance r: top and bottom rows, then the sides between them
            for (int dx = -r; dx <= r && out.size() < n; dx++) {
                out.add(new ChunkPos(centre.x + dx, centre.z - r));
            }
            for (int dz = -r + 1; dz <= r - 1 && out.size() < n; dz++) {
                out.add(new ChunkPos(centre.x + r, centre.z + dz));
            }
            for (int dx = r; dx >= -r && out.size() < n; dx--) {
                out.add(new ChunkPos(centre.x + dx, centre.z + r));
            }
            for (int dz = r - 1; dz >= -r + 1 && out.size() < n; dz--) {
                out.add(new ChunkPos(centre.x - r, centre.z + dz));
            }
        }
        return out;
    }

    /** {@code total} shared evenly between {@code places}, the first ones taking one more when it does not divide. */
    public static int[] share(int total, int places) {
        int[] out = new int[Math.max(0, places)];
        for (int i = 0; i < out.length; i++) {
            out[i] = total / places + (i < total % places ? 1 : 0);
        }
        return out;
    }
}
