package com.mrgregles.bsp_server_stress_test.analyse;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the world holds at one moment, for the analyser (owner decision D18): the chunks with the most entities (and
 * what most of them are), the biggest piles of dropped items, and what keeps chunks loaded besides players (the tickets:
 * the spawn area, /forceload, portals, and chunk loaders by the mod that placed them). Read on the server thread; it
 * changes nothing.
 */
public record WorldScan(List<Crowd> crowds, List<Pile> piles, List<Holder> holders, int players) {
    public static final int TOP = 10;
    static final int SPOTS = 5;

    /** The entities in one chunk, and the type there is most of. */
    public record Crowd(ResourceLocation dimension, int cx, int cz, int entities, ResourceLocation topType, int topCount) {
    }

    /** Dropped items in one chunk: how many item entities (stacks) and how many items in them. */
    public record Pile(ResourceLocation dimension, int cx, int cz, int stacks, long items) {
    }

    /** One kind of ticket (and the mod behind it, when the ticket says): how many, and where the first few are. */
    public record Holder(String type, String modId, int tickets, List<Spot> spots) {
    }

    public record Spot(ResourceLocation dimension, int cx, int cz) {
    }

    public static WorldScan scan(MinecraftServer server) {
        List<Crowd> crowds = new ArrayList<>();
        List<Pile> piles = new ArrayList<>();
        Map<String, HolderBuilder> holders = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            ResourceLocation dim = level.dimension().location();
            Map<Long, Map<EntityType<?>, int[]>> perChunk = new HashMap<>();
            Map<Long, long[]> items = new HashMap<>();
            for (Entity e : level.getAllEntities()) {
                long key = e.chunkPosition().toLong();
                perChunk.computeIfAbsent(key, k -> new IdentityHashMap<>()).computeIfAbsent(e.getType(), k -> new int[1])[0]++;
                if (e instanceof ItemEntity item) {
                    long[] p = items.computeIfAbsent(key, k -> new long[2]);
                    p[0]++;
                    p[1] += item.getItem().getCount();
                }
            }
            perChunk.forEach((key, types) -> {
                int total = 0;
                EntityType<?> top = null;
                int topCount = 0;
                for (Map.Entry<EntityType<?>, int[]> t : types.entrySet()) {
                    total += t.getValue()[0];
                    if (t.getValue()[0] > topCount) {
                        top = t.getKey();
                        topCount = t.getValue()[0];
                    }
                }
                ChunkPos pos = new ChunkPos(key);
                crowds.add(new Crowd(dim, pos.x, pos.z, total, BuiltInRegistries.ENTITY_TYPE.getKey(top), topCount));
            });
            items.forEach((key, p) -> {
                ChunkPos pos = new ChunkPos(key);
                piles.add(new Pile(dim, pos.x, pos.z, (int) p[0], p[1]));
            });
            readTickets(level, dim, holders);
        }
        crowds.sort(Comparator.comparingInt(Crowd::entities).reversed());
        piles.sort(Comparator.comparingLong(Pile::items).reversed());
        List<Holder> held = new ArrayList<>();
        holders.values().forEach(b -> held.add(new Holder(b.type, b.modId, b.tickets, List.copyOf(b.spots))));
        held.sort(Comparator.comparingInt(Holder::tickets).reversed());
        return new WorldScan(List.copyOf(crowds.subList(0, Math.min(TOP, crowds.size()))),
                List.copyOf(piles.subList(0, Math.min(TOP, piles.size()))), List.copyOf(held), server.getPlayerCount());
    }

    private static final class HolderBuilder {
        final String type;
        final String modId;
        int tickets;
        final List<Spot> spots = new ArrayList<>();

        HolderBuilder(String type, String modId) {
            this.type = type;
            this.modId = modId;
        }
    }

    private static void readTickets(ServerLevel level, ResourceLocation dim, Map<String, HolderBuilder> holders) {
        var tickets = level.getChunkSource().chunkMap.distanceManager.tickets;
        for (var entry : tickets.long2ObjectEntrySet()) {
            ChunkPos pos = new ChunkPos(entry.getLongKey());
            SortedArraySet<Ticket<?>> set = entry.getValue();
            for (Ticket<?> t : set) {
                String type = t.getType().toString();
                String modId = modOf(t.key);
                HolderBuilder b = holders.computeIfAbsent(type + "|" + modId, k -> new HolderBuilder(type, modId));
                b.tickets++;
                if (b.spots.size() < SPOTS) {
                    b.spots.add(new Spot(dim, pos.x, pos.z));
                }
            }
        }
    }

    private static Field ownerModId;
    private static boolean ownerModIdTried;

    /** The mod behind a Forge chunk-loading ticket (its key is a {@code ForgeChunkManager.TicketOwner}), or "". */
    static String modOf(Object key) {
        if (key == null || !key.getClass().getName().equals("net.minecraftforge.common.world.ForgeChunkManager$TicketOwner")) {
            return "";
        }
        try {
            if (!ownerModIdTried) {
                ownerModIdTried = true;
                ownerModId = key.getClass().getDeclaredField("modId");
                ownerModId.setAccessible(true);
            }
            return ownerModId == null ? "" : String.valueOf(ownerModId.get(key));
        } catch (ReflectiveOperationException | RuntimeException e) {
            BSPServerStressTest.LOGGER.debug("Cannot read the mod of a forced-chunk ticket: {}", e.toString());
            ownerModId = null;
            return "";
        }
    }
}
