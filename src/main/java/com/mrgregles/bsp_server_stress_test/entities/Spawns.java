package com.mrgregles.bsp_server_stress_test.entities;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.bots.Movement;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mobs, dropped items and experience orbs the mod spawns for a run (owner decision D13): on the ground around the chunk
 * places (or spawn), one per block on a square spiral, so they crowd as farms do; the chunks under them held ticking.
 * Mobs are kept from despawning; items never despawn and nobody can pick them up. Every one carries the tag
 * {@link #TAG}, so one left in the world by a server that stopped mid-run is removed the moment its chunk loads, and
 * {@code /stress cleanup} removes every one loaded. When a step adds more, those killed or merged since are replaced.
 */
public final class Spawns {
    public static final String TAG = BSPServerStressTest.MODID;
    public static final String CHUNK_GROUP = "entities";

    public enum Kind { MOB, ITEM, XP }

    record Spawned(Entity entity, int cell) {
    }

    private static final Map<String, List<Spawned>> BY_OPTION = new LinkedHashMap<>();
    private static final Set<UUID> OURS = new HashSet<>();
    private static final Deque<Integer> FREE = new ArrayDeque<>();
    private static int nextCell;

    private Spawns() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(Spawns::onJoin);
    }

    /** One of ours from an earlier run (the server stopped first): it does not come back. */
    private static void onJoin(EntityJoinLevelEvent e) {
        Entity entity = e.getEntity();
        if (!e.getLevel().isClientSide() && entity.getTags().contains(TAG) && !isOurs(entity.getUUID())) {
            e.setCanceled(true);
        }
    }

    /**
     * Makes an entity the mod made some other way one of ours (a working setup's moving contraption, D45): it carries the
     * tag, so a stopped server's copy is refused when its chunk loads, and {@code /stress cleanup} takes it.
     */
    public static synchronized void adopt(Entity e) {
        e.addTag(TAG);
        OURS.add(e.getUUID());
    }

    public static synchronized boolean isOurs(UUID uuid) {
        return OURS.contains(uuid);
    }

    public static synchronized int total() {
        prune();
        return BY_OPTION.values().stream().mapToInt(List::size).sum();
    }

    public static synchronized int count(String option) {
        prune();
        List<Spawned> l = BY_OPTION.get(option);
        return l == null ? 0 : l.size();
    }

    public static synchronized List<Entity> entities(String option) {
        prune();
        return BY_OPTION.getOrDefault(option, List.of()).stream().map(Spawned::entity).toList();
    }

    /** Spawns or removes until {@code n} of this option are alive (within the ceilings); returns how many are. */
    public static synchronized int set(MinecraftServer server, String option, Kind kind, EntityType<?> type, int n) {
        prune();
        List<Spawned> list = BY_OPTION.computeIfAbsent(option, k -> new ArrayList<>());
        int target = Math.max(0, Math.min(n, StressConfig.MAX_SPAWNED_TOTAL.get() - (total() - list.size())));
        List<Place> places = Places.inUse(server);
        int tries = 0;
        while (list.size() < target && tries++ < target * 2 + 32) {
            int cell = FREE.isEmpty() ? nextCell++ : FREE.pollFirst();
            Place place = places.get(cell % places.size());
            ServerLevel level = server.getLevel(place.dimension());
            if (level == null) {
                continue;
            }
            double[] xz = Movement.spread(place.centre().getMiddleBlockX() + 0.5, place.centre().getMiddleBlockZ() + 0.5, cell / places.size(), 1);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(xz[0]), (int) Math.floor(xz[1]));
            Entity e = spawn(level, kind, type, xz[0], y, xz[1]);
            if (e == null) {
                FREE.addLast(cell);
                continue;
            }
            e.addTag(TAG);
            OURS.add(e.getUUID());
            list.add(new Spawned(e, cell));
        }
        while (list.size() > target) {
            Spawned s = list.remove(list.size() - 1);
            OURS.remove(s.entity.getUUID());
            s.entity.discard();
            FREE.addLast(s.cell);
        }
        if (list.isEmpty()) {
            BY_OPTION.remove(option);
        }
        afterChange(server);
        return list.size();
    }

    private static Entity spawn(ServerLevel level, Kind kind, EntityType<?> type, double x, int y, double z) {
        return switch (kind) {
            case ITEM -> {
                ItemEntity item = new ItemEntity(level, x, y + 0.1, z, new ItemStack(Items.COBBLESTONE));
                item.setDeltaMovement(0, 0, 0);
                item.setUnlimitedLifetime();
                item.setNeverPickUp();
                yield level.addFreshEntity(item) ? item : null;
            }
            case XP -> {
                ExperienceOrb orb = new ExperienceOrb(level, x, y + 0.1, z, 1);
                orb.setDeltaMovement(0, 0, 0);
                yield level.addFreshEntity(orb) ? orb : null;
            }
            case MOB -> {
                Entity e = type.spawn(level, BlockPos.containing(x, y, z), MobSpawnType.COMMAND);
                if (e instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }
                yield e;
            }
        };
    }

    /** Forgets the ones killed, merged or gone for good, and frees their cells. */
    private static void prune() {
        for (List<Spawned> l : BY_OPTION.values()) {
            l.removeIf(s -> {
                if (s.entity.isRemoved() && s.entity.getRemovalReason() != null && s.entity.getRemovalReason().shouldDestroy()) {
                    OURS.remove(s.entity.getUUID());
                    FREE.addLast(s.cell);
                    return true;
                }
                return false;
            });
        }
    }

    private static void afterChange(MinecraftServer server) {
        Map<ResourceKey<Level>, Set<Long>> chunks = new LinkedHashMap<>();
        for (List<Spawned> l : BY_OPTION.values()) {
            for (Spawned s : l) {
                chunks.computeIfAbsent(s.entity.level().dimension(), k -> new LinkedHashSet<>()).add(s.entity.chunkPosition().toLong());
            }
        }
        ChunkHold.setExact(server, CHUNK_GROUP, chunks);
        if (BY_OPTION.isEmpty()) {
            FREE.clear();
            nextCell = 0;
        }
    }

    /** Removes everything spawned for every option. */
    public static synchronized void releaseAll(MinecraftServer server) {
        for (List<Spawned> l : BY_OPTION.values()) {
            for (Spawned s : l) {
                s.entity.discard();
            }
        }
        BY_OPTION.clear();
        OURS.clear();
        afterChange(server);
    }

    /** {@code /stress cleanup}: everything spawned now, and every loaded entity with the tag; returns how many went. */
    public static synchronized int cleanup(MinecraftServer server) {
        int n = 0;
        for (List<Spawned> l : BY_OPTION.values()) {
            n += (int) l.stream().filter(s -> !s.entity.isRemoved()).count();
        }
        releaseAll(server);
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> tagged = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e.getTags().contains(TAG)) {
                    tagged.add(e);
                }
            }
            for (Entity e : tagged) {
                e.discard();
                n++;
            }
        }
        return n;
    }

    /** Whether a chunk holds any of ours (tests). */
    public static synchronized boolean heldUnder(Entity e) {
        return ChunkHold.holds(e.level().dimension(), new ChunkPos(e.blockPosition()));
    }
}
