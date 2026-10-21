package com.mrgregles.bsp_server_stress_test.gametest;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.bots.BotOptions;
import com.mrgregles.bsp_server_stress_test.bots.Bots;
import com.mrgregles.bsp_server_stress_test.bots.Movement;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ST-10, how bots move: the spread, the steps and headings are right; real bots stand spread out, wander inside their
 * square on the ground, and fly out in a straight line at their height; the three options are ready. Live tests pause
 * mob spawning and log their bots out.
 */
@GameTestHolder(BSPServerStressTest.MODID)
@PrefixGameTestTemplate(false)
public class MovementGameTests {
    static final String EMPTY = SetupGameTests.EMPTY;

    @GameTest(template = EMPTY)
    public void spreading_stepping_and_headings_are_right(GameTestHelper h) {
        Set<String> spots = new HashSet<>();
        for (int i = 0; i < 9; i++) {
            double[] p = Movement.spread(100, -50, i, 48);
            if (Math.abs(p[0] - 100) > 48 || Math.abs(p[1] + 50) > 48 || (Math.round(p[0] - 100) % 48 != 0) || !spots.add(p[0] + "," + p[1])) {
                h.fail("spread " + i + " at " + p[0] + ", " + p[1]);
                return;
            }
        }
        double[] first = Movement.spread(100, -50, 0, 48);
        if (first[0] != 100 || first[1] != -50) {
            h.fail("the first bot is not at the place");
            return;
        }
        double[] step = Movement.toward(0, 0, 30, 40, 5);
        if (Math.abs(step[0] - 3) > 1e-9 || Math.abs(step[1] - 4) > 1e-9) {
            h.fail("a 5-block step toward (30, 40) went to " + step[0] + ", " + step[1]);
            return;
        }
        double[] arrive = Movement.toward(0, 0, 1, 1, 5);
        if (arrive[0] != 1 || arrive[1] != 1) {
            h.fail("a step past the target did not land on it");
            return;
        }
        Set<Long> headings = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            double[] a = Movement.along(0, 0, Movement.heading(i), 2);
            if (Math.abs(Math.hypot(a[0], a[1]) - 2) > 1e-9 || !headings.add(Math.round(Movement.heading(i) * 1e6))) {
                h.fail("heading " + i);
                return;
            }
        }
        if (Bots.allowed(50, 180, 200) != 20 || Bots.allowed(10, 0, 200) != 10 || Bots.allowed(5, 300, 200) != 0) {
            h.fail("the bot ceiling is not shared between the groups");
            return;
        }
        for (String id : List.of(BotOptions.STAND, BotOptions.WANDER, BotOptions.FLY)) {
            if (Catalogue.byId(id) == null || !Catalogue.byId(id).ready()) {
                h.fail(id + " is not ready");
                return;
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bots_stand_spread", timeoutTicks = 100)
    public void standing_bots_spread_out_from_the_place(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean spawning = BotGameTests.pauseSpawning(server);
        try {
            Bots.setCount(server, Bots.Behaviour.STAND, 3);
            List<ServerPlayer> bots = Bots.online().stream().map(Bots.Bot::player).toList();
            Place place = Places.inUse(server).get(0);
            int spacing = StressConfig.BOTS_SPACING.get();
            if (bots.size() != 3 || Math.abs(bots.get(0).getX() - (place.centre().getMiddleBlockX() + 0.5)) > 1
                    || Math.abs(bots.get(0).getZ() - (place.centre().getMiddleBlockZ() + 0.5)) > 1) {
                h.fail("the first bot is not at " + place.name() + ": " + bots);
                return;
            }
            for (int a = 0; a < bots.size(); a++) {
                for (int b = a + 1; b < bots.size(); b++) {
                    double d = Math.hypot(bots.get(a).getX() - bots.get(b).getX(), bots.get(a).getZ() - bots.get(b).getZ());
                    if (d < spacing * 0.9) {
                        h.fail("bots " + a + " and " + b + " stand " + (int) d + " blocks apart");
                        return;
                    }
                }
            }
        } finally {
            Bots.releaseAll(server);
            BotGameTests.restoreSpawning(server, spawning);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bots_wander", timeoutTicks = 200)
    public void wandering_bots_walk_on_the_ground_inside_their_square(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean spawning = BotGameTests.pauseSpawning(server);
        Bots.setCount(server, Bots.Behaviour.WANDER, 2);
        List<Bots.Bot> bots = Bots.online();
        double[][] start = bots.stream().map(b -> new double[]{b.player().getX(), b.player().getZ()}).toArray(double[][]::new);
        Place place = Places.inUse(server).get(0);
        double half = StressConfig.BOTS_WANDER_SQUARE.get() / 2.0;
        h.runAfterDelay(40, () -> {
            try {
                if (bots.size() != 2 || Bots.count(Bots.Behaviour.WANDER) != 2) {
                    h.fail(Bots.count(Bots.Behaviour.WANDER) + " wandering bots online");
                    return;
                }
                for (int i = 0; i < bots.size(); i++) {
                    ServerPlayer p = bots.get(i).player();
                    double moved = Math.hypot(p.getX() - start[i][0], p.getZ() - start[i][1]);
                    if (moved < 1) {
                        h.fail(p.getGameProfile().getName() + " moved " + moved + " blocks in 40 ticks");
                        return;
                    }
                    if (moved > 40 * StressConfig.BOTS_WALK_SPEED.get() / 20.0 + 0.01) {
                        h.fail(p.getGameProfile().getName() + " moved faster than it walks: " + moved);
                        return;
                    }
                    if (Math.abs(p.getX() - place.centre().getMiddleBlockX()) > half + 1 || Math.abs(p.getZ() - place.centre().getMiddleBlockZ()) > half + 1) {
                        h.fail(p.getGameProfile().getName() + " left its square: " + p.position());
                        return;
                    }
                    int ground = p.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
                    if (Math.abs(p.getY() - ground) > 2) {
                        h.fail(p.getGameProfile().getName() + " is at y " + p.getY() + " over ground at " + ground);
                        return;
                    }
                }
            } finally {
                Bots.releaseAll(server);
                BotGameTests.restoreSpawning(server, spawning);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "bots_fly", timeoutTicks = 200)
    public void flying_bots_fly_out_in_a_straight_line_at_their_height(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        boolean spawning = BotGameTests.pauseSpawning(server);
        Bots.setCount(server, Bots.Behaviour.FLY, 1);
        ServerPlayer p = Bots.online().get(0).player();
        double sx = p.getX();
        double sz = p.getZ();
        h.runAfterDelay(40, () -> {
            try {
                double moved = Math.hypot(p.getX() - sx, p.getZ() - sz);
                double expected = 40 * StressConfig.BOTS_FLY_SPEED.get() / 20.0;
                if (moved < expected * 0.9 || moved > expected * 1.1) {
                    h.fail("flew " + moved + " blocks in 40 ticks, expected about " + expected);
                    return;
                }
                if (Math.abs(p.getY() - StressConfig.BOTS_FLY_Y.get()) > 0.01 || !p.getAbilities().flying) {
                    h.fail("at y " + p.getY() + ", flying " + p.getAbilities().flying);
                    return;
                }
                if (!p.serverLevel().getChunkSource().hasChunk(p.chunkPosition().x, p.chunkPosition().z)) {
                    h.fail("the chunk under the flying bot is not loaded");
                    return;
                }
            } finally {
                Bots.releaseAll(server);
                BotGameTests.restoreSpawning(server, spawning);
            }
            h.succeed();
        });
    }
}
