package com.mrgregles.bsp_server_stress_test.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.compat.BspCore;
import com.mrgregles.bsp_server_stress_test.measure.Counts;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.measure.Meter;
import com.mrgregles.bsp_server_stress_test.report.CsvReport;
import com.mrgregles.bsp_server_stress_test.report.PlainReport;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.timing.TimingLines;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * {@code /stress}: for operators at level 4 and BSP-Core admins (owner decision D3). On its own it says what the mod is
 * set to; {@code /stress measure [seconds]} measures the server as it is; the run and profile commands are in
 * {@link RunCommands}. The chunk and analysis commands join them in later tasks.
 */
public final class StressCommands {
    public static final int OPERATOR_LEVEL = 4;
    public static final String MEASURE_LABEL = "measure";
    public static final int MEASURE_DEFAULT_SECONDS = 30;
    /** At most this many dimensions are listed in chat; the CSV has them all. */
    static final int DIMENSIONS_IN_CHAT = 5;

    private StressCommands() {
    }

    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("stress")
                .requires(StressCommands::canUse)
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("measure")
                        .executes(ctx -> measure(ctx.getSource(), MEASURE_DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                .executes(ctx -> measure(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));
        RunCommands.attach(root);
        OptionCommands.attach(root);
        AnalyseCommands.attach(root);
        ChunkCommands.attach(root);
        BotCommands.attach(root);
        root.then(Commands.literal("cleanup").executes(ctx -> cleanup(ctx.getSource())));
        dispatcher.register(root);
    }

    /** Operators at level 4 and BSP-Core admins; the console passes by its permission level. */
    public static boolean canUse(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? canUse(player) : source.hasPermission(OPERATOR_LEVEL);
    }

    public static boolean canUse(ServerPlayer player) {
        return player.hasPermissions(OPERATOR_LEVEL) || BspCore.isAdmin(player);
    }

    private static int status(CommandSourceStack source) {
        String version = ModList.get().getModContainerById(BSPServerStressTest.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        source.sendSuccess(() -> Text.t("status.title", version), false);
        source.sendSuccess(() -> Text.t("status.files", StressFiles.shown()), false);
        source.sendSuccess(() -> Text.t(StressConfig.ALLOW_RUNS_WITH_PLAYERS.get() ? "status.players_allowed" : "status.players_blocked"), false);
        source.sendSuccess(() -> Text.t(BspCore.isLoaded() ? "status.bsp_core" : "status.no_bsp_core"), false);
        int left = com.mrgregles.bsp_server_stress_test.machines.Placements.leftOver();
        if (left > 0) {
            source.sendFailure(Text.t("cleanup.leftover", Fmt.count(left)));
        }
        return 1;
    }

    private static int measure(CommandSourceStack source, int seconds) {
        Measurement running = Meter.running(MEASURE_LABEL);
        if (running != null) {
            source.sendFailure(Text.t("measure.busy", running.secondsLeft()));
            return 0;
        }
        Meter.startTimed(source.getServer(), MEASURE_LABEL, seconds, result -> {
            for (String line : summary(result)) {
                source.sendSuccess(() -> Text.of(line), false);
            }
            try {
                Path file = CsvReport.write(MEASURE_LABEL, List.of(result));
                source.sendSuccess(() -> Text.t("measure.written", StressFiles.shown() + StressFiles.root().relativize(file).toString().replace('\\', '/')), false);
                Path report = PlainReport.build(Text.raw("measure.title"), "", List.of(result), false).write(file, MEASURE_LABEL);
                source.sendSuccess(() -> Text.t("report.written", StressFiles.shown() + StressFiles.root().relativize(report).toString().replace('\\', '/')), false);
            } catch (IOException | RuntimeException e) {
                BSPServerStressTest.LOGGER.error("Could not write the figures file", e);
                source.sendFailure(Text.t("measure.write_failed", e.getMessage()));
            }
        });
        source.sendSuccess(() -> Text.t("measure.started", seconds), false);
        return 1;
    }

    /**
     * {@code /stress cleanup} (owner decision D9): removes every block a run placed (also those a stopped server left in
     * {@code placed.json}), every mob, item and orb it spawned (and any loaded one with the mod's tag), logs every bot
     * out, lets every held chunk go, puts back a random tick speed a stopped run left changed, and takes out of Create's
     * records any train a train line left, and has BSP-Core (1.4.5 or newer) forget every bot name's totems. Not while a
     * run is going.
     */
    private static int cleanup(CommandSourceStack source) {
        if (com.mrgregles.bsp_server_stress_test.run.RunEngine.current() != null) {
            source.sendFailure(Text.t("cleanup.run_going"));
            return 0;
        }
        var server = source.getServer();
        int blocks = com.mrgregles.bsp_server_stress_test.machines.Placements.cleanup(server);
        int entities = com.mrgregles.bsp_server_stress_test.entities.Spawns.cleanup(server);
        com.mrgregles.bsp_server_stress_test.explosions.Explosions.releaseAll(server);
        int bots = com.mrgregles.bsp_server_stress_test.bots.Bots.count();
        com.mrgregles.bsp_server_stress_test.bots.Bots.releaseAll(server);
        int totems = com.mrgregles.bsp_server_stress_test.bots.Bots.forgetBspCoreRecords(server);
        int chunks = com.mrgregles.bsp_server_stress_test.chunks.ChunkHold.total();
        com.mrgregles.bsp_server_stress_test.chunks.ChunkHold.releaseAll(server);
        com.mrgregles.bsp_server_stress_test.chunks.ChunkBar.update(server);
        int ticks = com.mrgregles.bsp_server_stress_test.world.RandomTicks.restoreLeftOver(server);
        int trains = com.mrgregles.bsp_server_stress_test.compat.Create.isLoaded() ? com.mrgregles.bsp_server_stress_test.compat.Create.cleanupTrains() : 0;
        source.sendSuccess(() -> Text.t("cleanup.done", Fmt.count(blocks), Fmt.count(entities), Fmt.count(bots), Fmt.count(chunks)), false);
        if (ticks >= 0) {
            source.sendSuccess(() -> Text.t("cleanup.random_ticks", ticks), false);
        }
        if (trains > 0) {
            source.sendSuccess(() -> Text.t("cleanup.trains", Fmt.count(trains)), false);
        }
        if (totems >= 0) {
            source.sendSuccess(() -> Text.t("cleanup.bsp_core", com.mrgregles.bsp_server_stress_test.bots.Bots.nameOf(StressConfig.MAX_BOTS.get()),
                    Fmt.count(totems)), false);
        }
        return 1;
    }

    /** The chat lines for a measurement: the ticks, the busiest dimensions, the totals. */
    public static List<String> summary(Measurement.Result r) {
        Counts c = r.counts();
        List<String> lines = new java.util.ArrayList<>();
        lines.add(Text.raw("measure.summary", Fmt.one(r.seconds()), Fmt.count(r.ticks()), Fmt.one(r.tps()),
                Fmt.one(r.averageMs()), Fmt.one(r.worstMs()), Fmt.one(r.slowShare() * 100)));
        r.dimensions().entrySet().stream()
                .sorted(Map.Entry.<String, Measurement.Result.DimensionTicks>comparingByValue(
                        Comparator.comparingDouble(Measurement.Result.DimensionTicks::averageMs)).reversed())
                .filter(e -> c.dimension(e.getKey()) != null && c.dimension(e.getKey()).loadedChunks() > 0)
                .limit(DIMENSIONS_IN_CHAT)
                .forEach(e -> {
                    Counts.Dimension d = c.dimension(e.getKey());
                    lines.add(Text.raw("measure.dimension", e.getKey(), Fmt.one(e.getValue().averageMs()),
                            Fmt.one(e.getValue().worstMs()), Fmt.count(d.loadedChunks()), Fmt.count(d.entities()),
                            Fmt.count(d.tickingBlockEntities())));
                });
        lines.add(Text.raw("measure.totals", Fmt.count(c.loadedChunks()), Fmt.count(c.entities()),
                Fmt.count(c.tickingBlockEntities()), Fmt.count(c.players()), Fmt.count(r.chunksGenerated()),
                Fmt.count(c.memoryUsedMb()), Fmt.count(c.memoryMaxMb())));
        if (r.timings() != null) {
            lines.addAll(TimingLines.lines(r.timings(), c));
        }
        return lines;
    }
}
