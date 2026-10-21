package com.mrgregles.bsp_server_stress_test.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_server_stress_test.StressConfig;
import com.mrgregles.bsp_server_stress_test.bots.BotOptions;
import com.mrgregles.bsp_server_stress_test.bots.Bots;
import com.mrgregles.bsp_server_stress_test.run.RunEngine;
import com.mrgregles.bsp_server_stress_test.run.StressRun;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** {@code /stress bots [number] [stand|wander|fly]} and {@code /stress bots release}: bots by hand, outside a run (D7, D8). */
final class BotCommands {
    private BotCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        var bots = Commands.literal("bots")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("release").executes(ctx -> release(ctx.getSource())));
        var number = Commands.argument("number", IntegerArgumentType.integer(0, 2000))
                .executes(ctx -> set(ctx.getSource(), Bots.Behaviour.STAND, IntegerArgumentType.getInteger(ctx, "number")));
        for (Bots.Behaviour b : Bots.Behaviour.values()) {
            number.then(Commands.literal(b.name().toLowerCase(java.util.Locale.ROOT))
                    .executes(ctx -> set(ctx.getSource(), b, IntegerArgumentType.getInteger(ctx, "number"))));
        }
        root.then(bots.then(number));
    }

    /** True (and says so) when a run is setting bots, so a command must not. */
    private static boolean runOwns(CommandSourceStack source) {
        StressRun run = RunEngine.current();
        if (run != null && (run.level(BotOptions.STAND) >= 0 || run.level(BotOptions.WANDER) >= 0 || run.level(BotOptions.FLY) >= 0)) {
            source.sendFailure(Text.t("bots.run_owns"));
            return true;
        }
        return false;
    }

    private static int set(CommandSourceStack source, Bots.Behaviour behaviour, int wanted) {
        if (runOwns(source)) {
            return 0;
        }
        int got = Bots.setCount(source.getServer(), behaviour, wanted);
        if (got < wanted) {
            source.sendFailure(Text.t("bots.capped", Fmt.count(StressConfig.MAX_BOTS.get())));
        }
        return status(source);
    }

    private static int release(CommandSourceStack source) {
        if (runOwns(source)) {
            return 0;
        }
        Bots.releaseAll(source.getServer());
        return status(source);
    }

    private static int status(CommandSourceStack source) {
        source.sendSuccess(() -> Text.t("bots.status", Fmt.count(Bots.count()), Fmt.count(StressConfig.MAX_BOTS.get()),
                Fmt.count(Bots.count(Bots.Behaviour.STAND)), Fmt.count(Bots.count(Bots.Behaviour.WANDER)),
                Fmt.count(Bots.count(Bots.Behaviour.FLY)), Fmt.count(source.getServer().getPlayerCount())), false);
        return 1;
    }
}
