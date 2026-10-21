package com.mrgregles.bsp_server_stress_test.chunks;

import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** The two chunk options a run can raise (owner decision D10). */
public final class ChunkOptions {
    private ChunkOptions() {
    }

    static String mode() {
        return Text.raw(com.mrgregles.bsp_server_stress_test.StressConfig.CHUNKS_TICKING.get() ? "chunks.mode.ticking" : "chunks.mode.loaded");
    }

    /** {@code chunks.hold}: chunks held at the saved places (or spawn), in land that already exists there. */
    public static final class Hold implements StressOption {
        public static final String ID = "chunks.hold";
        private List<Place> places = List.of();

        @Override
        public String id() {
            return ID;
        }

        @Override
        public String describe(int level) {
            return Text.raw("option.chunks_hold", Fmt.count(level),
                    places.stream().map(Place::name).collect(Collectors.joining(", ")), mode());
        }

        @Override
        public int defaultStep() {
            return 256;
        }

        @Override
        public int defaultCeiling() {
            return 4096;
        }

        @Override
        public Limit limit() {
            return Limit.CHUNKS;
        }

        @Override
        public void apply(MinecraftServer server, int level) {
            places = Places.inUse(server);
            ChunkHold.set(server, ChunkHold.PLACES, places, level);
        }

        @Override
        public void release(MinecraftServer server) {
            ChunkHold.release(server, ChunkHold.PLACES);
        }
    }

    /**
     * {@code chunks.generate_fresh}: chunks held in land nobody has visited, far out in the overworld, so every step
     * makes the server generate new land. A new spot is picked for each run.
     */
    public static final class Fresh implements StressOption {
        public static final String ID = "chunks.generate_fresh";
        static final int MIN_BLOCKS = 30_000;
        static final int MAX_BLOCKS = 200_000;
        private Place place;

        @Override
        public String id() {
            return ID;
        }

        @Override
        public String describe(int level) {
            return place == null ? Text.raw("option.chunks_fresh_none", Fmt.count(level))
                    : Text.raw("option.chunks_fresh", Fmt.count(level), place.centre().getMiddleBlockX(), place.centre().getMiddleBlockZ(), mode());
        }

        @Override
        public int defaultStep() {
            return 64;
        }

        @Override
        public int defaultCeiling() {
            return 1024;
        }

        @Override
        public Limit limit() {
            return Limit.CHUNKS;
        }

        @Override
        public void apply(MinecraftServer server, int level) {
            if (place == null) {
                place = pick(server.overworld());
            }
            ChunkHold.set(server, ChunkHold.FRESH, List.of(place), level);
        }

        @Override
        public void release(MinecraftServer server) {
            ChunkHold.release(server, ChunkHold.FRESH);
            place = null;
        }

        public Place place() {
            return place;
        }

        /** A spot 30,000 to 200,000 blocks from spawn inside the world border, or near the border's edge when it is smaller. */
        public static Place pick(ServerLevel level) {
            ChunkPos spawn = new ChunkPos(level.getSharedSpawnPos());
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int tries = 0; tries < 50; tries++) {
                double angle = r.nextDouble(Math.PI * 2);
                int blocks = r.nextInt(MIN_BLOCKS, MAX_BLOCKS);
                int x = spawn.getMiddleBlockX() + (int) (Math.cos(angle) * blocks);
                int z = spawn.getMiddleBlockZ() + (int) (Math.sin(angle) * blocks);
                if (level.getWorldBorder().isWithinBounds(x, z)) {
                    return new Place("fresh", Level.OVERWORLD, new ChunkPos(x >> 4, z >> 4));
                }
            }
            // a small world border: as far from spawn as the border allows
            var border = level.getWorldBorder();
            int x = (int) (border.getCenterX() + border.getSize() * 0.45);
            return new Place("fresh", Level.OVERWORLD, new ChunkPos(x >> 4, ((int) border.getCenterZ()) >> 4));
        }
    }
}
