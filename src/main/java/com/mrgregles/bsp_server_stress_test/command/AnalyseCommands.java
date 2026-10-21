package com.mrgregles.bsp_server_stress_test.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.analyse.Analysis;
import com.mrgregles.bsp_server_stress_test.analyse.Watch;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /stress analyse [seconds]}: measures the server as it is and looks at the world, then says what is costing
 * most and writes the report; {@code /stress watch [on|off]}: the watch that analyses by itself when ticks stay slow
 * (owner decision D18).
 */
final class AnalyseCommands {
    static final int DEFAULT_SECONDS = 30;

    private AnalyseCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("analyse")
                        .executes(ctx -> analyse(ctx.getSource(), DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 600))
                                .executes(ctx -> analyse(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("watch")
                        .executes(ctx -> watch(ctx.getSource()))
                        .then(Commands.literal("on").executes(ctx -> { Watch.set(true); return watch(ctx.getSource()); }))
                        .then(Commands.literal("off").executes(ctx -> { Watch.set(false); return watch(ctx.getSource()); })));
    }

    private static int analyse(CommandSourceStack source, int seconds) {
        if (Analysis.running()) {
            source.sendFailure(Text.t("analyse.busy", Analysis.current().secondsLeft()));
            return 0;
        }
        Analysis.start(source.getServer(), seconds, "", done -> {
            for (String line : done.report().headline()) {
                source.sendSuccess(() -> Text.of("- " + line), false);
            }
            if (done.txt() != null) {
                source.sendSuccess(() -> Text.t("report.written", StressFiles.shown() + StressFiles.root().relativize(done.txt()).toString().replace('\\', '/')), false);
            } else {
                source.sendFailure(Text.t("analyse.write_failed"));
            }
        });
        source.sendSuccess(() -> Text.t("analyse.started", seconds), false);
        return 1;
    }

    private static int watch(CommandSourceStack source) {
        source.sendSuccess(() -> StressConfig.WATCH_ENABLED.get()
                ? Text.t("watch.on", StressConfig.WATCH_LIMIT_MS.get(), StressConfig.WATCH_OVER_SECONDS.get(),
                StressConfig.WATCH_ANALYSE_SECONDS.get(), StressConfig.WATCH_QUIET_MINUTES.get())
                : Text.t("watch.off"), false);
        return 1;
    }
}
