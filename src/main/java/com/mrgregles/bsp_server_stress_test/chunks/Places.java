package com.mrgregles.bsp_server_stress_test.chunks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The places chunks are held around (owner decision D24), kept in {@code config/bsp_server_stress_test/places.json}.
 * The name {@code spawn} always means the world spawn, wherever it is now. With no places, chunks are held at spawn.
 */
public final class Places {
    public static final String SPAWN = "spawn";
    public static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "places.json";

    private Places() {
    }

    /** One line of the file. */
    static final class Saved {
        String name;
        String dimension;
        int chunkX;
        int chunkZ;
    }

    static final class SavedList {
        List<Saved> places = new ArrayList<>();
    }

    public static Path file() {
        return StressFiles.file(FILE);
    }

    /** The saved places, spawn resolved to where spawn is now. */
    public static List<Place> load(MinecraftServer server) {
        List<Place> out = new ArrayList<>();
        Path file = file();
        if (Files.exists(file)) {
            try {
                SavedList list = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), SavedList.class);
                if (list != null && list.places != null) {
                    for (Saved s : list.places) {
                        if (s == null || s.name == null) {
                            continue;
                        }
                        if (s.name.equals(SPAWN)) {
                            out.add(spawn(server));
                        } else if (s.dimension != null && ResourceLocation.tryParse(s.dimension) != null) {
                            out.add(new Place(s.name, ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(s.dimension)),
                                    new ChunkPos(s.chunkX, s.chunkZ)));
                        }
                    }
                }
            } catch (Exception e) {
                BSPServerStressTest.LOGGER.error("Could not read {}", file, e);
            }
        }
        return out;
    }

    /** The places to hold chunks at: the saved ones, or spawn when there are none. */
    public static List<Place> inUse(MinecraftServer server) {
        List<Place> saved = load(server);
        return saved.isEmpty() ? List.of(spawn(server)) : saved;
    }

    public static Place spawn(MinecraftServer server) {
        return new Place(SPAWN, Level.OVERWORLD, new ChunkPos(server.overworld().getSharedSpawnPos()));
    }

    public static void save(List<Place> places) throws IOException {
        SavedList list = new SavedList();
        for (Place p : places) {
            Saved s = new Saved();
            s.name = p.name();
            s.dimension = p.dimension().location().toString();
            s.chunkX = p.centre().x;
            s.chunkZ = p.centre().z;
            list.places.add(s);
        }
        Path file = file();
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(list) + "\n", StandardCharsets.UTF_8);
    }

    /** Adds or moves a place; returns the new list. */
    public static List<Place> put(MinecraftServer server, Place place) throws IOException {
        List<Place> list = new ArrayList<>(load(server));
        list.removeIf(p -> p.name().equals(place.name()));
        list.add(place);
        save(list);
        return list;
    }

    /** Removes a place by name; false when there was none. */
    public static boolean remove(MinecraftServer server, String name) throws IOException {
        List<Place> list = new ArrayList<>(load(server));
        boolean removed = list.removeIf(p -> p.name().equals(name));
        if (removed) {
            save(list);
        }
        return removed;
    }
}
