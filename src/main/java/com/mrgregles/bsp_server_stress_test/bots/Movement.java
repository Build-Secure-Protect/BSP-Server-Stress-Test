package com.mrgregles.bsp_server_stress_test.bots;

import com.mrgregles.bsp_server_stress_test.chunks.Spiral;
import net.minecraft.world.level.ChunkPos;

/** The arithmetic of how bots spread and move (owner decision D8), kept apart so it can be tested on its own. */
public final class Movement {
    /** The golden angle: headings that never line up, however many bots fly out. */
    static final double GOLDEN = Math.PI * (3 - Math.sqrt(5));

    private Movement() {
    }

    /** Where the {@code index}th standing bot goes: a square spiral out from the place, {@code spacing} blocks apart. */
    public static double[] spread(double centreX, double centreZ, int index, int spacing) {
        ChunkPos cell = Spiral.around(new ChunkPos(0, 0), index + 1).get(index);
        return new double[]{centreX + cell.x * spacing, centreZ + cell.z * spacing};
    }

    /** One step from {@code (x, z)} toward {@code (tx, tz)}, at most {@code step} blocks; lands on the target when close. */
    public static double[] toward(double x, double z, double tx, double tz, double step) {
        double dx = tx - x;
        double dz = tz - z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= step || d == 0) {
            return new double[]{tx, tz};
        }
        return new double[]{x + dx / d * step, z + dz / d * step};
    }

    /** The heading (radians) of the {@code index}th flying bot. */
    public static double heading(int index) {
        return (index * GOLDEN) % (Math.PI * 2);
    }

    /** One step along a heading. */
    public static double[] along(double x, double z, double heading, double step) {
        return new double[]{x + Math.cos(heading) * step, z + Math.sin(heading) * step};
    }

    /** The yaw a player shows when heading that way (Minecraft's yaw 0 faces +z). */
    public static float yaw(double heading) {
        return (float) (Math.toDegrees(heading) - 90.0);
    }
}
