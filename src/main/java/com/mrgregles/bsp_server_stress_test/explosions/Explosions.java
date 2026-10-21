package com.mrgregles.bsp_server_stress_test.explosions;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.bots.Movement;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import com.mrgregles.bsp_server_stress_test.chunks.Spiral;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Explosions for a run (owner decisions D11, D12): while an explosion option is in a run, it goes off every
 * {@code explosions.everySeconds} in the blast area {@code explosions.distance} blocks east of each place, at the next
 * spot of a square spiral. Vanilla explosions alternate between keeping the blocks (only the calculation of what would
 * break, nothing broken) and breaking them; TNT chains drop a cluster of lit TNT; a mod's explosive is placed with a
 * redstone block on top, which sets off any that answer to redstone (one that does not is removed after ten seconds and
 * counted as a dud). The blast area's chunks are held ticking while any explosion option is on.
 */
public final class Explosions {
    public static final String CHUNK_GROUP = "explosions";
    static final int SPOTS = 25;
    static final int DUD_TICKS = 200;

    public enum Kind { VANILLA, TNT_CHAIN, BLOCK }

    static final class Active {
        final Kind kind;
        final Block block;
        int level;
        int ticks;
        int blasts;

        Active(Kind kind, Block block) {
            this.kind = kind;
            this.block = block;
        }
    }

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, Block block, int dueTick, boolean redstone) {
    }

    private static final Map<String, Active> ACTIVE = new LinkedHashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    private static int spot;
    private static int duds;

    private Explosions() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(Explosions::onServerTick);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> {
            ACTIVE.clear();
            PENDING.clear();
        });
    }

    /** Sets an option's level (power, TNT count or explosives per blast); 0 stops it. */
    public static synchronized void set(MinecraftServer server, String option, Kind kind, Block block, int level) {
        if (level <= 0) {
            ACTIVE.remove(option);
            if (ACTIVE.isEmpty()) {
                releaseAll(server);
                return;
            }
        } else {
            ACTIVE.computeIfAbsent(option, k -> new Active(kind, block)).level = level;
        }
        holdChunks(server);
    }

    public static synchronized int level(String option) {
        Active a = ACTIVE.get(option);
        return a == null ? 0 : a.level;
    }

    public static synchronized int blasts(String option) {
        Active a = ACTIVE.get(option);
        return a == null ? 0 : a.blasts;
    }

    public static synchronized int duds() {
        return duds;
    }

    /** Whether an option that has waited {@code ticks} should go off now. */
    public static boolean due(int ticks, int everyTicks) {
        return ticks >= everyTicks;
    }

    /** The blast spot's centre for a place: {@code distance} blocks east of it. */
    static BlockPos blastCentre(Place p) {
        return new BlockPos(p.centre().getMiddleBlockX() + StressConfig.BLAST_DISTANCE.get(), 0, p.centre().getMiddleBlockZ());
    }

    private static void holdChunks(MinecraftServer server) {
        Map<ResourceKey<Level>, Set<Long>> want = new LinkedHashMap<>();
        if (!ACTIVE.isEmpty()) {
            for (Place p : Places.inUse(server)) {
                BlockPos c = blastCentre(p);
                Set<Long> set = want.computeIfAbsent(p.dimension(), k -> new LinkedHashSet<>());
                for (int i = 0; i < SPOTS; i++) {
                    double[] xz = Movement.spread(c.getX(), c.getZ(), i, StressConfig.BLAST_SPACING.get());
                    for (ChunkPos cp : Spiral.around(new ChunkPos(BlockPos.containing(xz[0], 0, xz[1])), 9)) {
                        set.add(cp.toLong());
                    }
                }
            }
        }
        ChunkHold.setExact(server, CHUNK_GROUP, want);
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = e.getServer();
        synchronized (Explosions.class) {
            settlePending(server);
            if (ACTIVE.isEmpty()) {
                return;
            }
            int every = StressConfig.BLAST_EVERY_SECONDS.get() * 20;
            List<Place> places = Places.inUse(server);
            for (Active a : ACTIVE.values()) {
                if (!due(++a.ticks, every)) {
                    continue;
                }
                a.ticks = 0;
                Place p = places.get(a.blasts % places.size());
                ServerLevel level = server.getLevel(p.dimension());
                if (level == null) {
                    continue;
                }
                BlockPos c = blastCentre(p);
                double[] xz = Movement.spread(c.getX(), c.getZ(), spot++ % SPOTS, StressConfig.BLAST_SPACING.get());
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) xz[0], (int) xz[1]);
                BlockPos at = new BlockPos((int) xz[0], y, (int) xz[1]);
                try {
                    switch (a.kind) {
                        case VANILLA -> explodeAt(level, at, a.level, a.blasts % 2 == 1);
                        case TNT_CHAIN -> primeCluster(level, at, a.level);
                        case BLOCK -> {
                            for (int i = 0; i < a.level; i++) {
                                placeAndFire(level, at.offset((i % 5) * 4, 0, (i / 5) * 4), a.block);
                            }
                        }
                    }
                } catch (RuntimeException ex) {
                    BSPServerStressTest.LOGGER.error("An explosion failed at {}", at, ex);
                }
                a.blasts++;
            }
        }
    }

    /** A vanilla explosion: with {@code breakBlocks} false the blocks stay (only the work of finding them is done). */
    public static void explodeAt(ServerLevel level, BlockPos at, float power, boolean breakBlocks) {
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, power,
                breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
    }

    /** {@code count} lit TNT in a tight cluster, with fuses a little apart so they set each other off. */
    public static List<PrimedTnt> primeCluster(ServerLevel level, BlockPos at, int count) {
        List<PrimedTnt> out = new ArrayList<>();
        int side = Math.max(1, (int) Math.ceil(Math.cbrt(count)));
        for (int i = 0; i < count; i++) {
            int dx = i % side;
            int dz = (i / side) % side;
            int dy = i / (side * side);
            PrimedTnt tnt = new PrimedTnt(level, at.getX() + dx + 0.5, at.getY() + dy, at.getZ() + dz + 0.5, null);
            tnt.setFuse(40 + (i % 20));
            level.addFreshEntity(tnt);
            out.add(tnt);
        }
        return out;
    }

    /** Places a mod's explosive with a redstone block on top (which sets off what answers to redstone). */
    public static void placeAndFire(ServerLevel level, BlockPos at, Block explosive) {
        if (!level.getBlockState(at).isAir() && !level.getBlockState(at).canBeReplaced()) {
            at = at.above();
        }
        level.setBlock(at, explosive.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(at.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        int now = level.getServer().getTickCount();
        PENDING.add(new Pending(level.dimension(), at.above(), Blocks.REDSTONE_BLOCK, now + 2, true));
        PENDING.add(new Pending(level.dimension(), at, explosive, now + DUD_TICKS, false));
    }

    /** Takes away the redstone blocks, and explosives that did not go off (duds), when their time comes. */
    private static void settlePending(MinecraftServer server) {
        int now = server.getTickCount();
        PENDING.removeIf(p -> {
            if (now < p.dueTick) {
                return false;
            }
            ServerLevel level = server.getLevel(p.dimension);
            if (level != null && level.getBlockState(p.pos).is(p.block)) {
                level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (!p.redstone) {
                    duds++;
                }
            }
            return true;
        });
    }

    /** Stops every explosion option and lets the blast area go; what is still waiting is settled at once. */
    public static synchronized void releaseAll(MinecraftServer server) {
        ACTIVE.clear();
        if (duds > 0) {
            BSPServerStressTest.LOGGER.info(com.mrgregles.bsp_server_stress_test.text.Text.raw("explosions.duds", duds));
            duds = 0;
        }
        for (Pending p : PENDING) {
            ServerLevel level = server.getLevel(p.dimension);
            if (level != null && level.getBlockState(p.pos).is(p.block)) {
                level.setBlock(p.pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        PENDING.clear();
        holdChunks(server);
    }
}
