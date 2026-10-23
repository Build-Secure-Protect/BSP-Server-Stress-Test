package com.mrgregles.bsp_server_stress_test.compat;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.UUID;

/**
 * The only class that touches BSP-Core, which is optional (owner decision D21). Every call into BSP-Core sits in the
 * nested {@link Impl}, so nothing of BSP-Core loads when it is absent; a BSP-Core older or newer than expected (a missing
 * class or method) counts as absent. From BSP-Core 1.4.5, a bot's totems are forgotten when it leaves and in
 * {@code /stress cleanup} (request ST-C1: taken, placed ones removed, every record dropped, nothing given back).
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

    private static int forgotten;
    private static boolean forgetMissing;

    /**
     * BSP-Core's wipe of a player's totems ({@code BspTotems.forgetPlayer}, BSP-Core 1.4.5, request ST-C1): carried and
     * placed totems taken, every record of them dropped, the first-join grant cleared, nothing given back. Returns how many
     * totems were taken, or -1 when BSP-Core is absent or too old to have it (the log says so once). Server thread only.
     */
    public static int forgetPlayer(MinecraftServer server, UUID player) {
        if (!isLoaded()) {
            return -1;
        }
        try {
            int taken = Impl.forgetPlayer(server, player);
            forgotten++;
            return taken;
        } catch (LinkageError e) {
            if (!forgetMissing) {
                forgetMissing = true;
                BSPServerStressTest.LOGGER.warn("BSP-Core is older than 1.4.5, so bots' totems are not forgotten when they leave: {}", e.toString());
            }
            return -1;
        } catch (RuntimeException e) {
            BSPServerStressTest.LOGGER.warn("BSP-Core could not forget the totems of {}: {}", player, e.toString());
            return -1;
        }
    }

    /** How many players BSP-Core was asked to forget since the server started, for tests. */
    public static int forgottenCount() {
        return forgotten;
    }

    private static final class Impl {
        static boolean isAdmin(Player player) {
            return com.mrgregles.bsp_core.api.BspPlayers.isAdmin(player);
        }

        static int forgetPlayer(MinecraftServer server, UUID player) {
            return com.mrgregles.bsp_core.api.BspTotems.forgetPlayer(server, player);
        }
    }
}
