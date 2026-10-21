package com.mrgregles.bsp_server_stress_test;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * The mod's settings, in {@code config/bsp_server_stress_test/settings.toml} (owner decision D4). Registered as a
 * COMMON config with a path inside the mod's folder, so it is never written into the world folder. What a run includes,
 * its step sizes and its timing are in the profiles beside it ({@code profiles/}), not here.
 */
public final class StressConfig {
    public static final String FILE_NAME = StressFiles.FOLDER + "/settings.toml";
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_RUNS_WITH_PLAYERS;
    public static final ForgeConfigSpec.IntValue EMERGENCY_TICK_SECONDS;
    public static final ForgeConfigSpec.IntValue STOP_WINDOW_SECONDS;

    public static final ForgeConfigSpec.IntValue MAX_BOTS;
    public static final ForgeConfigSpec.IntValue MAX_CHUNKS;
    public static final ForgeConfigSpec.IntValue MAX_EXPLOSION_POWER;
    public static final ForgeConfigSpec.IntValue MAX_MACHINES;
    public static final ForgeConfigSpec.IntValue MAX_TEST_MILLISECONDS;
    public static final ForgeConfigSpec.IntValue MAX_PLACED_TOTAL;
    public static final ForgeConfigSpec.IntValue MAX_ENTITIES;
    public static final ForgeConfigSpec.IntValue MAX_SPAWNED_TOTAL;
    public static final ForgeConfigSpec.IntValue MAX_RANDOM_TICK_SPEED;
    public static final ForgeConfigSpec.IntValue MAX_SAVES_PER_MINUTE;

    public static final ForgeConfigSpec.IntValue MACHINES_GRID_SIDE;
    public static final ForgeConfigSpec.IntValue MACHINES_SPACING;
    public static final ForgeConfigSpec.IntValue MACHINES_HEIGHT;

    public static final ForgeConfigSpec.IntValue WORLD_GRID_SIDE;
    public static final ForgeConfigSpec.IntValue WORLD_SPACING;
    public static final ForgeConfigSpec.IntValue WORLD_ABOVE_MACHINES;

    public static final ForgeConfigSpec.IntValue SETUPS_GRID_SIDE;
    public static final ForgeConfigSpec.IntValue SETUPS_SPACING;
    public static final ForgeConfigSpec.IntValue SETUPS_ABOVE_WORLD;
    public static final ForgeConfigSpec.IntValue SETUPS_ROTOR_SIDE;
    public static final ForgeConfigSpec.IntValue SETUPS_PISTON_SIDE;

    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> LEAVE_OUT;

    public static final ForgeConfigSpec.BooleanValue CHUNKS_TICKING;

    public static final ForgeConfigSpec.IntValue BLAST_EVERY_SECONDS;
    public static final ForgeConfigSpec.IntValue BLAST_DISTANCE;
    public static final ForgeConfigSpec.IntValue BLAST_SPACING;

    public static final ForgeConfigSpec.IntValue BOTS_SPACING;
    public static final ForgeConfigSpec.IntValue BOTS_WANDER_SQUARE;
    public static final ForgeConfigSpec.DoubleValue BOTS_WALK_SPEED;
    public static final ForgeConfigSpec.DoubleValue BOTS_FLY_SPEED;
    public static final ForgeConfigSpec.IntValue BOTS_FLY_Y;

    public static final ForgeConfigSpec.BooleanValue WATCH_ENABLED;
    public static final ForgeConfigSpec.IntValue WATCH_LIMIT_MS;
    public static final ForgeConfigSpec.IntValue WATCH_OVER_SECONDS;
    public static final ForgeConfigSpec.IntValue WATCH_ANALYSE_SECONDS;
    public static final ForgeConfigSpec.IntValue WATCH_QUIET_MINUTES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("What keeps a stress run away from real players and from hanging the server.").push("safety");
        ALLOW_RUNS_WITH_PLAYERS = b
                .comment("When false, a run refuses to start while a real player is online (bots, and people allowed to use /stress, do not count).",
                        "When true, a run with real players online starts only when /stress start is given twice within 3 seconds.")
                .define("allowRunsWithPlayers", false);
        EMERGENCY_TICK_SECONDS = b
                .comment("A run is stopped and everything it made is released at once when one tick takes longer than this.",
                        "The server's own watchdog (max-tick-time in server.properties) may wait far longer.")
                .defineInRange("emergencyTickSeconds", 10, 1, 600);
        STOP_WINDOW_SECONDS = b
                .comment("A run stops when the average tick over this many seconds passes the profile's limit (stopAverageMs).")
                .defineInRange("stopWindowSeconds", 20, 1, 600);
        b.pop();

        b.comment("Hard ceilings no run goes past, whatever a profile asks for.").push("limits");
        MAX_BOTS = b.comment("Bots online at once.").defineInRange("maxBots", 200, 0, 2000);
        MAX_CHUNKS = b.comment("Chunks the mod holds loaded, across every place (64 x 64 at most).").defineInRange("maxChunks", 4096, 0, 4096);
        MAX_EXPLOSION_POWER = b.comment("The power of one explosion (TNT is 4).").defineInRange("maxExplosionPower", 128, 0, 1000);
        MAX_MACHINES = b.comment("Machines and other blocks placed by one option.").defineInRange("maxMachines", 2000, 0, 100000);
        MAX_TEST_MILLISECONDS = b.comment("The test load: milliseconds wasted every tick.").defineInRange("maxTestMilliseconds", 200, 0, 5000);
        MAX_PLACED_TOTAL = b.comment("Blocks placed by every option together.").defineInRange("maxPlacedTotal", 20000, 0, 1000000);
        MAX_ENTITIES = b.comment("Mobs, dropped items or experience orbs spawned by one option.").defineInRange("maxEntities", 2000, 0, 100000);
        MAX_SPAWNED_TOTAL = b.comment("Entities spawned by every option together.").defineInRange("maxSpawnedTotal", 10000, 0, 1000000);
        MAX_RANDOM_TICK_SPEED = b.comment("The randomTickSpeed game rule a run may set (the game's own is 3).").defineInRange("maxRandomTickSpeed", 30, 0, 1000);
        MAX_SAVES_PER_MINUTE = b.comment("Whole-world saves a minute (world.save).").defineInRange("maxSavesPerMinute", 12, 0, 60);
        b.pop();

        b.comment("Where placed machines go: a grid in the air above each chunk place (or spawn), layer on layer.").push("machines");
        MACHINES_GRID_SIDE = b.comment("Machines along each side of one layer.").defineInRange("gridSide", 64, 4, 512);
        MACHINES_SPACING = b.comment("Blocks from one machine to the next (2 leaves a gap, so machines do not join up).").defineInRange("spacing", 2, 1, 16);
        MACHINES_HEIGHT = b.comment("How far above the ground at the place the first layer is.").defineInRange("heightAboveGround", 20, 1, 200);
        b.pop();

        b.comment("Where the world options' contraptions go (redstone clocks, hopper rings, fluid basins, lights, falling sand):",
                "their own grid above the machine grid at each place, so they never touch machines.").push("world");
        WORLD_GRID_SIDE = b.comment("Contraptions along each side of one layer.").defineInRange("gridSide", 16, 2, 256);
        WORLD_SPACING = b.comment("Blocks from one contraption's corner to the next (8 fits the 7-wide fluid basin with a gap).").defineInRange("spacing", 8, 8, 64);
        WORLD_ABOVE_MACHINES = b.comment("How far above the machine grid's first layer the contraptions' first layer is.").defineInRange("aboveMachines", 40, 1, 300);
        b.pop();

        b.comment("Where the working setups go (setups.*: mods' machines powered and fed so they really work):",
                "their own grid above the world options' contraptions at each place.").push("setups");
        SETUPS_GRID_SIDE = b.comment("Setups along each side of one layer.").defineInRange("gridSide", 8, 2, 128);
        SETUPS_SPACING = b.comment("Blocks from one setup's corner to the next (16 fits the biggest setup with a gap).").defineInRange("spacing", 16, 16, 64);
        SETUPS_ABOVE_WORLD = b.comment("How far above the contraptions' first layer the setups' first layer is.").defineInRange("aboveWorld", 40, 1, 300);
        SETUPS_ROTOR_SIDE = b.comment("The side of the plate a Create bearing rotor spins (5: 25 blocks). Read at server start.").defineInRange("rotorSide", 5, 1, 9);
        SETUPS_PISTON_SIDE = b.comment("The side of the face a Create piston line pushes (3: 9 blocks). Read at server start.").defineInRange("pistonSide", 3, 1, 5);
        b.pop();

        b.comment("The options catalogue: what this server can be tested with, built from the mods it loaded.").push("catalogue");
        LEAVE_OUT = b
                .comment("Blocks and mobs never offered as options, by id (\"minecraft:wither\") or a whole mod (\"somemod:*\").",
                        "The defaults: blocks that run the server itself (command and structure blocks), BSP-Core's totems",
                        "(one per player, with rules a stress test must not break), and the bosses.")
                .defineListAllowEmpty(List.of("leaveOut"), () -> List.of(
                        "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
                        "minecraft:structure_block", "minecraft:jigsaw",
                        "bsp_core:shatter_totem", "bsp_core:decoy_totem", "bsp_core:ghost_block",
                        "minecraft:ender_dragon", "minecraft:wither"), o -> o instanceof String);
        b.pop();

        b.comment("Chunks the mod holds loaded (/stress chunks, and the chunks options of a run).").push("chunks");
        CHUNKS_TICKING = b
                .comment("true: every held chunk ticks (its machines, crops and mobs work), as if a player stood there.",
                        "false: held chunks are loaded but do not tick. Set with /stress chunks mode ticking|loaded.")
                .define("ticking", true);
        b.pop();

        b.comment("Explosions (explosions.*): set off in a blast area away from each chunk place (or spawn), in the normal world (D11).").push("explosions");
        BLAST_EVERY_SECONDS = b.comment("Each explosion option goes off once every this many seconds while a run includes it.").defineInRange("everySeconds", 5, 1, 600);
        BLAST_DISTANCE = b.comment("How far east of each place the blast area is, in blocks, so the place's machines, crowds and bots are spared.").defineInRange("distance", 300, 0, 100000);
        BLAST_SPACING = b.comment("Blocks between one blast spot and the next; the spots go round a square spiral of 25 and start again.").defineInRange("spacing", 64, 1, 1024);
        b.pop();

        b.comment("How bots stand and move (bots.stand, bots.wander, bots.fly_out). They are shared between the chunk places, or spawn.").push("bots");
        BOTS_SPACING = b.comment("Blocks between the spots standing and wandering bots are given, on a square spiral out from each place.",
                "Only spots whose land the bot would see (the server's view distance around it) is already generated are used,",
                "so these bots never make new land; with fewer such spots than bots, bots share them (the log says so).")
                .defineInRange("spacing", 1000, 1, 100000);
        BOTS_WANDER_SQUARE = b.comment("The side of the square, in blocks, a wandering bot stays inside around its own spot (only on generated land).")
                .defineInRange("wanderSquare", 200, 16, 10000);
        BOTS_WALK_SPEED = b.comment("Blocks a second a wandering bot walks (a player walks 4.3).").defineInRange("walkBlocksPerSecond", 4.3, 0.1, 100.0);
        BOTS_FLY_SPEED = b.comment("Blocks a second a flying bot covers (a player sprint-flying in creative does about 21).").defineInRange("flyBlocksPerSecond", 20.0, 0.1, 200.0);
        BOTS_FLY_Y = b.comment("The height flying bots keep.").defineInRange("flyY", 160, -60, 319);
        b.pop();

        b.comment("The watch: analyses the server by itself when its ticks stay slow (it reads the tick times the server keeps anyway).").push("watch");
        WATCH_ENABLED = b.comment("Turned on and off with /stress watch on|off.").define("enabled", false);
        WATCH_LIMIT_MS = b.comment("An analysis starts when the average tick passes this many milliseconds...").defineInRange("limitMs", 50, 1, 10000);
        WATCH_OVER_SECONDS = b.comment("...over this many seconds.").defineInRange("overSeconds", 60, 5, 3600);
        WATCH_ANALYSE_SECONDS = b.comment("How long each analysis measures.").defineInRange("analyseSeconds", 30, 5, 600);
        WATCH_QUIET_MINUTES = b.comment("After an analysis, the watch waits this long before it may start another.").defineInRange("quietMinutes", 30, 1, 1440);
        b.pop();
        SPEC = b.build();
    }

    private StressConfig() {
    }
}
