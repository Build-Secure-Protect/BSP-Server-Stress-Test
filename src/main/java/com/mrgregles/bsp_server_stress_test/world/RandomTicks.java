package com.mrgregles.bsp_server_stress_test.world;

import com.google.gson.Gson;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.run.Limit;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code world.random_ticks} (owner decision D31): the level is the {@code randomTickSpeed} game rule, set at each step
 * and put back at the end. The world's own value is written to {@code random_ticks.json} in the mod's folder before the
 * first change, so a server that stopped mid-run gets it back at its next start or from {@code /stress cleanup}. The
 * game keeps game rules in the world's {@code level.dat}: the one setting a run changes there (D31).
 */
public final class RandomTicks implements StressOption {
    public static final String ID = "world.random_ticks";
    public static final String FILE = "random_ticks.json";
    private static final Gson GSON = new Gson();

    /** The world's own value while a run has changed it; null when nothing is changed. */
    private static Integer original;

    static final class Saved {
        int randomTickSpeed;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String describe(int level) {
        return Text.raw("option.random_ticks", Fmt.count(level));
    }

    @Override
    public int defaultStep() {
        return 3;
    }

    @Override
    public int defaultCeiling() {
        return 30;
    }

    @Override
    public Limit limit() {
        return Limit.RANDOM_TICK_SPEED;
    }

    @Override
    public void apply(MinecraftServer server, int level) {
        if (level <= 0) {
            release(server);
            return;
        }
        synchronized (RandomTicks.class) {
            if (original == null) {
                original = speed(server);
                write(original);
            }
        }
        set(server, level);
    }

    @Override
    public void release(MinecraftServer server) {
        synchronized (RandomTicks.class) {
            if (original != null) {
                set(server, original);
                original = null;
                delete();
            }
        }
    }

    public static int speed(MinecraftServer server) {
        return server.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
    }

    static void set(MinecraftServer server, int value) {
        server.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(value, server);
    }

    public static Path file() {
        return StressFiles.file(FILE);
    }

    /**
     * Puts back the value an earlier run left in the file (the server stopped before it could), and the value of a run
     * going now is not touched. Returns the value put back, or -1 when there was nothing to do.
     */
    public static synchronized int restoreLeftOver(MinecraftServer server) {
        if (original != null || !Files.exists(file())) {
            return -1;
        }
        try {
            Saved s = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Saved.class);
            delete();
            if (s == null) {
                return -1;
            }
            set(server, s.randomTickSpeed);
            return s.randomTickSpeed;
        } catch (Exception e) {
            BSPServerStressTest.LOGGER.error("Could not read {}", file(), e);
            return -1;
        }
    }

    /** Forgets the value in memory without touching the game rule (tests of a server that stopped mid-run). */
    public static synchronized void forgetForTest() {
        original = null;
    }

    private static void write(int value) {
        Saved s = new Saved();
        s.randomTickSpeed = value;
        try {
            Files.writeString(file(), GSON.toJson(s) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            BSPServerStressTest.LOGGER.error("Could not write {}", file(), e);
        }
    }

    private static void delete() {
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            BSPServerStressTest.LOGGER.error("Could not delete {}", file(), e);
        }
    }
}
