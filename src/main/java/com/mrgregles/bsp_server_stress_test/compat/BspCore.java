package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * The only class that touches BSP-Core, which is optional (owner decision D21). Every call into BSP-Core sits in the
 * nested {@link Impl}, so nothing of BSP-Core loads when it is absent; a BSP-Core older or newer than expected (a missing
 * class or method) counts as absent.
 */
public final class BspCore {
    public static final String MODID = "bsp_core";

    private BspCore() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MODID);
    }

    /** Whether BSP-Core counts the player as an admin ({@code admin.admins}, or operators when that list is empty). */
    public static boolean isAdmin(Player player) {
        if (!isLoaded()) {
            return false;
        }
        try {
            return Impl.isAdmin(player);
        } catch (LinkageError | RuntimeException e) {
            BSPServerStressTest.LOGGER.debug("BSP-Core admin check failed: {}", e.toString());
            return false;
        }
    }

    private static final class Impl {
        static boolean isAdmin(Player player) {
            return com.mrgregles.bsp_core.api.BspPlayers.isAdmin(player);
        }
    }
}
