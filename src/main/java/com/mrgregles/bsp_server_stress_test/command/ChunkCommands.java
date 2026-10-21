package com.mrgregles.bsp_server_stress_test.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkBar;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkHold;
import com.mrgregles.bsp_server_stress_test.chunks.ChunkOptions;
import com.mrgregles.bsp_server_stress_test.chunks.Place;
import com.mrgregles.bsp_server_stress_test.chunks.Places;
import com.mrgregles.bsp_server_stress_test.run.RunEngine;
import com.mrgregles.bsp_server_stress_test.run.StressRun;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import com.mrgregles.bsp_server_stress_test.timing.TimingLines;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /stress chunks}: the chunks the mod holds at the saved places (owner decisions D10, D23, D24): set a number,
 * step it with the clickable keys, let them all go, choose ticking or only loaded, and keep the places.
 */
final class ChunkCommands {
    private ChunkCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("chunks")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.argument("number", IntegerArgumentType.integer(0, 4096))
                        .executes(ctx -> set(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "number"))))
                .then(Commands.literal("add").then(Commands.argument("change", IntegerArgumentType.integer(-4096, 4096))
                        .executes(ctx -> set(ctx.getSource(), ChunkHold.held(ChunkHold.PLACES) + IntegerArgumentType.getInteger(ctx, "change")))))
                .then(Commands.literal("release").executes(ctx -> release(ctx.getSource())))
                .then(Commands.literal("mode")
                        .then(Commands.literal("ticking").executes(ctx -> mode(ctx.getSource(), true)))
                        .then(Commands.literal("loaded").executes(ctx -> mode(ctx.getSource(), false))))
                .then(Commands.literal("place")
                        .then(Commands.literal("list").executes(ctx -> listPlaces(ctx.getSource())))
                        .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> addPlace(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        Places.load(ctx.getSource().getServer()).stream().map(Place::name), b))
                                .executes(ctx -> removePlace(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))));
    }

    /** True (and says so) when a run is setting the chunks, so a command must not. */
    private static boolean runOwns(CommandSourceStack source) {
        StressRun run = RunEngine.current();
        if (run != null && (run.level(ChunkOptions.Hold.ID) >= 0 || run.level(ChunkOptions.Fresh.ID) >= 0)) {
            source.sendFailure(Text.t("chunks.run_owns"));
            return true;
        }
        return false;
    }

    private static int set(CommandSourceStack source, int wanted) {
        if (runOwns(source)) {
            return 0;
        }
        int number = Math.max(0, wanted);
        int got = ChunkHold.set(source.getServer(), ChunkHold.PLACES, Places.inUse(source.getServer()), number);
        if (got < number) {
            source.sendFailure(Text.t("chunks.capped", Fmt.count(got), Fmt.count(ChunkHold.total() - got),
                    Fmt.count(StressConfig.MAX_CHUNKS.get())));
        }
        ChunkBar.update(source.getServer());
        return status(source);
    }

    private static int release(CommandSourceStack source) {
        if (runOwns(source)) {
            return 0;
        }
        ChunkHold.release(source.getServer(), ChunkHold.PLACES);
        ChunkBar.update(source.getServer());
        source.sendSuccess(() -> Text.t("chunks.released"), false);
        return 1;
    }

    private static int mode(CommandSourceStack source, boolean ticking) {
        if (runOwns(source)) {
            return 0;
        }
        StressConfig.CHUNKS_TICKING.set(ticking);
        StressConfig.CHUNKS_TICKING.save();
        int held = ChunkHold.held(ChunkHold.PLACES);
        if (held > 0) {
            ChunkHold.set(source.getServer(), ChunkHold.PLACES, Places.inUse(source.getServer()), held);
        }
        source.sendSuccess(() -> Text.t("chunks.mode_set", Text.raw(ticking ? "chunks.mode.ticking" : "chunks.mode.loaded")), false);
        return status(source);
    }

    static int status(CommandSourceStack source) {
        var server = source.getServer();
        int loaded = 0;
        for (ServerLevel level : server.getAllLevels()) {
            loaded += level.getChunkSource().getLoadedChunksCount();
        }
        String where = Places.inUse(server).stream().map(ChunkCommands::describe).collect(Collectors.joining("; "));
        int total = loaded;
        source.sendSuccess(() -> Text.t("chunks.status", Fmt.count(ChunkHold.held(ChunkHold.PLACES)), Fmt.count(StressConfig.MAX_CHUNKS.get()),
                Text.raw(StressConfig.CHUNKS_TICKING.get() ? "chunks.mode.ticking" : "chunks.mode.loaded"), where, Fmt.count(total)), false);
        source.sendSuccess(ChunkBar::keys, false);
        return 1;
    }

    static String describe(Place p) {
        return Text.raw("chunks.place", p.name(), TimingLines.dimension(p.dimension().location()), p.centre().getMiddleBlockX(),
                p.centre().getMiddleBlockZ());
    }

    private static int listPlaces(CommandSourceStack source) {
        List<Place> saved = Places.load(source.getServer());
        if (saved.isEmpty()) {
            source.sendSuccess(() -> Text.t("chunks.place.only_spawn", describe(Places.spawn(source.getServer()))), false);
        } else {
            source.sendSuccess(() -> Text.t("chunks.place.list", saved.stream().map(ChunkCommands::describe).collect(Collectors.joining("; "))), false);
        }
        return 1;
    }

    private static int addPlace(CommandSourceStack source, String name) {
        if (!Places.NAME.matcher(name).matches()) {
            source.sendFailure(Text.t("chunks.place.bad_name"));
            return 0;
        }
        Place place = name.equals(Places.SPAWN) ? Places.spawn(source.getServer())
                : new Place(name, source.getLevel().dimension(), new ChunkPos(net.minecraft.core.BlockPos.containing(source.getPosition())));
        try {
            List<Place> all = Places.put(source.getServer(), place);
            source.sendSuccess(() -> Text.t("chunks.place.added", describe(place), all.stream().map(Place::name).collect(Collectors.joining(", "))), false);
        } catch (IOException e) {
            source.sendFailure(Text.t("chunks.place.write_failed", String.valueOf(e.getMessage())));
            return 0;
        }
        return reshare(source);
    }

    private static int removePlace(CommandSourceStack source, String name) {
        try {
            if (!Places.remove(source.getServer(), name)) {
                source.sendFailure(Text.t("chunks.place.none", name));
                return 0;
            }
        } catch (IOException e) {
            source.sendFailure(Text.t("chunks.place.write_failed", String.valueOf(e.getMessage())));
            return 0;
        }
        source.sendSuccess(() -> Text.t("chunks.place.removed", name), false);
        return reshare(source);
    }

    /** After the places change, the chunks held are shared between the new places at once. */
    private static int reshare(CommandSourceStack source) {
        int held = ChunkHold.held(ChunkHold.PLACES);
        if (held > 0 && !runOwnsQuietly()) {
            ChunkHold.set(source.getServer(), ChunkHold.PLACES, Places.inUse(source.getServer()), held);
            return status(source);
        }
        return 1;
    }

    private static boolean runOwnsQuietly() {
        StressRun run = RunEngine.current();
        return run != null && (run.level(ChunkOptions.Hold.ID) >= 0 || run.level(ChunkOptions.Fresh.ID) >= 0);
    }
}
