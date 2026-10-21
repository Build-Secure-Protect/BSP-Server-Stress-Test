package com.mrgregles.bsp_server_stress_test.world;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.machines.Placements;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/**
 * The world options of owner decision D13 (ST-14): registers them, works the contraptions that do something every few
 * ticks (spread over the ticks by their cell, so they do not all go at once), saves for {@code world.save}, and at
 * server start puts back a random tick speed a stopped run left changed.
 */
public final class WorldOptions {
    private WorldOptions() {
    }

    public static void register() {
        Options.register(WorldOption.redstoneClocks());
        Options.register(WorldOption.hoppers());
        Options.register(WorldOption.fluids());
        Options.register(new RandomTicks());
        Options.register(WorldOption.lighting());
        Options.register(WorldOption.fallingBlocks());
        Options.register(new WorldSave());
        MinecraftForge.EVENT_BUS.addListener(WorldOptions::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent e) -> {
            int back = RandomTicks.restoreLeftOver(e.getServer());
            if (back >= 0) {
                BSPServerStressTest.LOGGER.warn(Text.raw("cleanup.random_ticks", back));
            }
        });
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = e.getServer();
        cycleUnits(server, server.getTickCount());
        WorldSave.tick(server);
    }

    /** Gives every contraption whose turn it is on tick {@code now} its repeated work. */
    static void cycleUnits(MinecraftServer server, int now) {
        for (Placements.Unit u : Placements.allUnits()) {
            int every = u.shape().every();
            if (every <= 0 || (now + u.cell()) % every != 0) {
                continue;
            }
            ServerLevel level = server.getLevel(u.dimension());
            if (level == null || !level.isLoaded(u.origin())) {
                continue;
            }
            try {
                u.shape().cycle(level, u.origin());
            } catch (RuntimeException ex) {
                BSPServerStressTest.LOGGER.error("A contraption failed at {}", u.origin(), ex);
            }
        }
    }
}
