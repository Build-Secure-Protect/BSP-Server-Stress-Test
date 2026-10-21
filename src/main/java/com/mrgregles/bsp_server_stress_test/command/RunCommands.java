package com.mrgregles.bsp_server_stress_test.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.run.Options;
import com.mrgregles.bsp_server_stress_test.run.Profile;
import com.mrgregles.bsp_server_stress_test.run.Profiles;
import com.mrgregles.bsp_server_stress_test.run.RunEngine;
import com.mrgregles.bsp_server_stress_test.run.RunGuard;
import com.mrgregles.bsp_server_stress_test.run.StressOption;
import com.mrgregles.bsp_server_stress_test.run.StressRun;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * The run commands under {@code /stress}: {@code start <profile>}, {@code stop}, {@code status},
 * {@code ramp up|down|hold|auto}, and {@code profile ...} to list, show, make and edit profiles (owner decisions D14,
 * D15).
 */
final class RunCommands {
    private static final SuggestionProvider<CommandSourceStack> PROFILES =
            (ctx, b) -> SharedSuggestionProvider.suggest(Profiles.names(), b);
    private static final SuggestionProvider<CommandSourceStack> OPTIONS =
            (ctx, b) -> SharedSuggestionProvider.suggest(Options.all().stream().map(StressOption::id), b);

    private RunCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("start")
                        .then(Commands.argument("profile", StringArgumentType.word()).suggests(PROFILES)
                                .executes(ctx -> start(ctx.getSource(), StringArgumentType.getString(ctx, "profile")))))
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("ramp")
                        .then(Commands.literal("up").executes(ctx -> ramp(ctx.getSource(), "up")))
                        .then(Commands.literal("down").executes(ctx -> ramp(ctx.getSource(), "down")))
                        .then(Commands.literal("hold").executes(ctx -> ramp(ctx.getSource(), "hold")))
                        .then(Commands.literal("auto").executes(ctx -> ramp(ctx.getSource(), "auto"))))
                .then(Commands.literal("profile")
                        .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("show").then(name().executes(ctx -> show(ctx.getSource(), name(ctx)))))
                        .then(Commands.literal("new").then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx.getSource(), name(ctx), null))
                                .then(Commands.argument("from", StringArgumentType.word()).suggests(PROFILES)
                                        .executes(ctx -> create(ctx.getSource(), name(ctx), StringArgumentType.getString(ctx, "from"))))))
                        .then(Commands.literal("include").then(name().then(pattern()
                                .executes(ctx -> edit(ctx, p -> { add(p.include, pat(ctx)); p.exclude.remove(pat(ctx)); return p; })))))
                        .then(Commands.literal("exclude").then(name().then(pattern()
                                .executes(ctx -> edit(ctx, p -> { add(p.exclude, pat(ctx)); p.include.remove(pat(ctx)); return p; })))))
                        .then(Commands.literal("forget").then(name().then(pattern()
                                .executes(ctx -> edit(ctx, p -> { p.include.remove(pat(ctx)); p.exclude.remove(pat(ctx)); return p; })))))
                        .then(Commands.literal("step").then(name().then(option().then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> editOption(ctx, (t, n) -> t.step = n))))))
                        .then(Commands.literal("ceiling").then(name().then(option().then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> editOption(ctx, (t, n) -> t.ceiling = n))))))
                        .then(Commands.literal("timing").then(name()
                                .then(Commands.argument("stepSeconds", IntegerArgumentType.integer(5, 3600))
                                        .then(Commands.argument("settleSeconds", IntegerArgumentType.integer(0, 3599))
                                                .executes(ctx -> edit(ctx, p -> {
                                                    p.stepSeconds = IntegerArgumentType.getInteger(ctx, "stepSeconds");
                                                    p.settleSeconds = Math.min(IntegerArgumentType.getInteger(ctx, "settleSeconds"), p.stepSeconds - 1);
                                                    return p;
                                                }))))))
                        .then(Commands.literal("limit").then(name()
                                .then(Commands.argument("ms", IntegerArgumentType.integer(1, 10000))
                                        .executes(ctx -> edit(ctx, p -> { p.stopAverageMs = IntegerArgumentType.getInteger(ctx, "ms"); return p; }))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("name", StringArgumentType.word()).suggests(PROFILES);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> pattern() {
        return Commands.argument("pattern", StringArgumentType.greedyString());
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> option() {
        return Commands.argument("option", StringArgumentType.word()).suggests(OPTIONS);
    }

    private static String name(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "name");
    }

    private static String pat(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "pattern").trim();
    }

    private static void add(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    // ---- runs

    private static int start(CommandSourceStack source, String name) {
        StressRun running = RunEngine.current();
        if (running != null) {
            source.sendFailure(Text.t("run.busy", running.profile().name, running.step()));
            return 0;
        }
        Profile profile = loadOrSay(source, name);
        if (profile == null) {
            return 0;
        }
        List<StressOption> options = Options.chosenBy(profile);
        if (options.isEmpty()) {
            source.sendFailure(Text.t("run.no_options", name));
            return 0;
        }
        List<ServerPlayer> real = RunGuard.realPlayers(source.getServer().getPlayerList().getPlayers());
        String names = real.stream().map(p -> p.getGameProfile().getName()).collect(Collectors.joining(", "));
        switch (RunGuard.decide(real.size(), StressConfig.ALLOW_RUNS_WITH_PLAYERS.get(), source.getTextName() + "|" + name, System.nanoTime())) {
            case REFUSED -> {
                source.sendFailure(Text.t("run.refused_players", real.size(), names));
                return 0;
            }
            case CONFIRM -> {
                source.sendFailure(Text.t("run.confirm_players", real.size(), names));
                return 0;
            }
            case GO -> {
            }
        }
        RunEngine.start(source.getServer(), profile, options, sayTo(source));
        return 1;
    }

    /** Lines about a run go to whoever started it and to the server log. */
    static Consumer<String> sayTo(CommandSourceStack source) {
        return line -> {
            source.sendSuccess(() -> Text.of(line), false);
            if (source.getPlayer() != null) {
                BSPServerStressTest.LOGGER.info("[stress] {}", line);
            }
        };
    }

    private static int stop(CommandSourceStack source) {
        StressRun run = RunEngine.current();
        if (run == null) {
            source.sendFailure(Text.t("run.none"));
            return 0;
        }
        run.stop(source.getServer(), source.getTextName());
        if (RunEngine.current() == run) {
            return 0;
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        StressRun run = RunEngine.current();
        if (run == null) {
            source.sendSuccess(() -> Text.t("run.none"), false);
            return 1;
        }
        long now = System.nanoTime();
        String mode = Text.raw(run.mode() == StressRun.Mode.AUTO ? "run.mode.auto" : "run.mode.hold");
        source.sendSuccess(() -> Text.t("run.status", run.profile().name, run.step(), mode, run.secondsIntoStep(now),
                run.profile().stepSeconds), false);
        source.sendSuccess(() -> Text.t("run.status.levels", run.describeLevels()), false);
        List<Measurement.Result> results = run.results();
        if (!results.isEmpty()) {
            Measurement.Result r = results.get(results.size() - 1);
            source.sendSuccess(() -> Text.t("run.status.last", Fmt.one(r.tps()), Fmt.one(r.averageMs()), Fmt.one(r.worstMs())), false);
        }
        return 1;
    }

    private static int ramp(CommandSourceStack source, String how) {
        StressRun run = RunEngine.current();
        if (run == null) {
            source.sendFailure(Text.t("run.none"));
            return 0;
        }
        long now = System.nanoTime();
        switch (how) {
            case "up" -> run.up(source.getServer(), now);
            case "down" -> {
                if (!run.down(source.getServer(), now)) {
                    source.sendFailure(Text.t("run.down_at_zero"));
                    return 0;
                }
            }
            case "hold" -> {
                run.hold();
                source.sendSuccess(() -> Text.t("run.hold", run.step()), false);
            }
            default -> {
                run.auto();
                source.sendSuccess(() -> Text.t("run.auto", run.profile().stepSeconds), false);
            }
        }
        return 1;
    }

    // ---- profiles

    private static Profile loadOrSay(CommandSourceStack source, String name) {
        if (!Profiles.validName(name)) {
            source.sendFailure(Text.t("profile.bad_name"));
            return null;
        }
        try {
            Profile p = Profiles.load(name);
            if (p == null) {
                source.sendFailure(Text.t("run.no_profile", name));
            }
            return p;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Text.t("run.bad_profile", name, String.valueOf(e.getMessage())));
            return null;
        }
    }

    private static int list(CommandSourceStack source) {
        List<String> names = Profiles.names();
        source.sendSuccess(() -> names.isEmpty() ? Text.t("profile.none") : Text.t("profile.list", String.join(", ", names)), false);
        return 1;
    }

    private static int show(CommandSourceStack source, String name) {
        Profile p = loadOrSay(source, name);
        if (p == null) {
            return 0;
        }
        for (String line : describe(p)) {
            source.sendSuccess(() -> Text.of(line), false);
        }
        return 1;
    }

    /** A profile in chat lines: its description, its patterns, its timing, and each option it includes here. */
    static List<String> describe(Profile p) {
        List<String> lines = new java.util.ArrayList<>();
        String nothing = Text.raw("word.nothing");
        lines.add(Text.raw("profile.show.head", p.name, p.description));
        lines.add(Text.raw("profile.show.patterns", p.include.isEmpty() ? nothing : String.join(" ", p.include),
                p.exclude.isEmpty() ? nothing : String.join(" ", p.exclude)));
        lines.add(Text.raw("profile.show.timing", p.stepSeconds, p.settleSeconds, p.stopAverageMs));
        List<com.mrgregles.bsp_server_stress_test.catalogue.Catalogue.Entry> covered =
                com.mrgregles.bsp_server_stress_test.catalogue.Catalogue.chosenBy(p);
        lines.add(Text.raw("profile.show.catalogue", covered.size(), covered.stream().filter(e -> e.ready()).count()));
        List<StressOption> chosen = Options.chosenBy(p);
        if (chosen.isEmpty()) {
            lines.add(Text.raw("profile.show.no_options"));
        }
        for (StressOption o : chosen) {
            lines.add(Text.raw("profile.show.option", o.id(), p.stepOf(o), p.ceilingOf(o)));
        }
        return lines;
    }

    private static int create(CommandSourceStack source, String name, String from) {
        if (!Profiles.validName(name)) {
            source.sendFailure(Text.t("profile.bad_name"));
            return 0;
        }
        try {
            if (Profiles.load(name) != null) {
                source.sendFailure(Text.t("profile.exists", name));
                return 0;
            }
            Profile p;
            if (from != null) {
                p = loadOrSay(source, from);
                if (p == null) {
                    return 0;
                }
                p.name = name;
            } else {
                p = Profile.of(name, "", List.of(), List.of());
            }
            Profiles.save(p);
            source.sendSuccess(() -> from == null ? Text.t("profile.created_empty", name, name) : Text.t("profile.created_from", name, from), false);
            return 1;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Text.t("profile.write_failed", String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int edit(CommandContext<CommandSourceStack> ctx, UnaryOperator<Profile> change) {
        CommandSourceStack source = ctx.getSource();
        Profile p = loadOrSay(source, name(ctx));
        if (p == null) {
            return 0;
        }
        try {
            Profiles.save(change.apply(p));
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Text.t("profile.write_failed", String.valueOf(e.getMessage())));
            return 0;
        }
        source.sendSuccess(() -> Text.t("profile.saved", p.name), false);
        for (String line : describe(p)) {
            source.sendSuccess(() -> Text.of(line), false);
        }
        return 1;
    }

    private interface TuningChange {
        void apply(Profile.Tuning tuning, int amount);
    }

    private static int editOption(CommandContext<CommandSourceStack> ctx, TuningChange change) {
        String id = StringArgumentType.getString(ctx, "option");
        if (Options.byId(id) == null) {
            ctx.getSource().sendFailure(Text.t("profile.no_option", id));
            return 0;
        }
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        return edit(ctx, p -> {
            change.apply(p.tuning(id), amount);
            return p;
        });
    }
}
