package com.mrgregles.bsp_server_stress_test.bots;

import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.border.WorldBorder;

import java.util.concurrent.ThreadLocalRandom;

/**
 * How one bot behaves (owner decision D8): placed when it joins its group, then moved each tick. A move is what a
 * player's movement packet does on the server: the new position, then the chunks around it updated (loading new ones
 * and sending them, as for a real player). Bots never break or place anything.
 */
public abstract class Mover {
    protected final Place place;
    protected final int index;

    protected Mover(Place place, int index) {
        this.place = place;
        this.index = index;
    }

    /** Puts the bot where it starts. */
    abstract void start(MinecraftServer server, ServerPlayer bot);

    /** Moves the bot one tick. */
    abstract void tick(ServerPlayer bot);

    public Place place() {
        return place;
    }

    static int surface(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
    }

    /** The first placement: to the place's dimension if needed, chunks and tracking updated by the teleport. */
    static void put(MinecraftServer server, ServerPlayer bot, Place place, double x, double y, double z, float yaw) {
        ServerLevel level = server.getLevel(place.dimension());
        if (level == null) {
            level = server.overworld();
        }
        bot.teleportTo(level, x, y, z, yaw, 0);
    }

    /** A move within the bot's world, as a movement packet would make it. */
    static void move(ServerPlayer bot, double x, double y, double z, float yaw) {
        bot.absMoveTo(x, y, z, yaw, 0);
        bot.serverLevel().getChunkSource().move(bot);
    }

    /** {@code bots.stand}: on its own spot (see {@link Spots}: far apart, only on generated land), on the ground. */
    static final class Stand extends Mover {
        Stand(Place place, int index) {
            super(place, index);
        }

        @Override
        void start(MinecraftServer server, ServerPlayer bot) {
            int[] spot = Spots.spot(server, place, index);
            double x = spot[0] + 0.5;
            double z = spot[1] + 0.5;
            ServerLevel level = server.getLevel(place.dimension()) == null ? server.overworld() : server.getLevel(place.dimension());
            put(server, bot, place, x, surface(level, x, z), z, 0);
        }

        @Override
        void tick(ServerPlayer bot) {
        }
    }

    /**
     * {@code bots.wander}: walks to one random point after another inside a square around its own spot (see
     * {@link Spots}), each point one whose land the bot would see is already generated; when none is found it walks
     * back to its spot.
     */
    static final class Wander extends Mover {
        static final int TRIES = 8;
        private double targetX;
        private double targetZ;
        private int homeX;
        private int homeZ;
        private ServerLevel level;
        private int view = 10;

        Wander(Place place, int index) {
            super(place, index);
        }

        double half() {
            return StressConfig.BOTS_WANDER_SQUARE.get() / 2.0;
        }

        void pickTarget() {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < TRIES; i++) {
                double x = homeX + r.nextDouble(-half(), half());
                double z = homeZ + r.nextDouble(-half(), half());
                if (level == null || Spots.generated(level, (int) Math.floor(x), (int) Math.floor(z), view)) {
                    targetX = x;
                    targetZ = z;
                    return;
                }
            }
            targetX = homeX + 0.5;
            targetZ = homeZ + 0.5;
        }

        @Override
        void start(MinecraftServer server, ServerPlayer bot) {
            level = server.getLevel(place.dimension()) == null ? server.overworld() : server.getLevel(place.dimension());
            view = server.getPlayerList().getViewDistance();
            int[] spot = Spots.spot(server, place, index);
            homeX = spot[0];
            homeZ = spot[1];
            put(server, bot, place, homeX + 0.5, surface(level, homeX + 0.5, homeZ + 0.5), homeZ + 0.5, 0);
            pickTarget();
        }

        @Override
        void tick(ServerPlayer bot) {
            double step = StressConfig.BOTS_WALK_SPEED.get() / 20.0;
            double[] next = Movement.toward(bot.getX(), bot.getZ(), targetX, targetZ, step);
            if (next[0] == targetX && next[1] == targetZ) {
                pickTarget();
            }
            float yaw = Movement.yaw(Math.atan2(targetZ - bot.getZ(), targetX - bot.getX()));
            move(bot, next[0], surface(bot.serverLevel(), next[0], next[1]), next[1], yaw);
        }

        double targetX() {
            return targetX;
        }

        double targetZ() {
            return targetZ;
        }
    }

    /** {@code bots.fly_out}: flies from the place in a straight line at {@code bots.flyY}, turning back at the world border. */
    static final class Fly extends Mover {
        static final double BORDER_MARGIN = 64;
        private double heading;

        Fly(Place place, int index) {
            super(place, index);
            this.heading = Movement.heading(index);
        }

        @Override
        void start(MinecraftServer server, ServerPlayer bot) {
            bot.getAbilities().mayfly = true;
            bot.getAbilities().flying = true;
            bot.onUpdateAbilities();
            put(server, bot, place, place.centre().getMiddleBlockX() + 0.5, StressConfig.BOTS_FLY_Y.get(),
                    place.centre().getMiddleBlockZ() + 0.5, Movement.yaw(heading));
        }

        @Override
        void tick(ServerPlayer bot) {
            double step = StressConfig.BOTS_FLY_SPEED.get() / 20.0;
            double[] next = Movement.along(bot.getX(), bot.getZ(), heading, step);
            WorldBorder border = bot.serverLevel().getWorldBorder();
            if (border.getDistanceToBorder(next[0], next[1]) < BORDER_MARGIN) {
                heading += Math.PI;
                next = Movement.along(bot.getX(), bot.getZ(), heading, step);
            }
            move(bot, next[0], StressConfig.BOTS_FLY_Y.get(), next[1], Movement.yaw(heading));
        }

        double heading() {
            return heading;
        }
    }
}
