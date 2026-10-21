package com.mrgregles.bsp_server_stress_test.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /stress options}: rebuilds the catalogue from the loaded mods, says what the server can be tested with, and
 * writes {@code options.txt}; {@code /stress options <mod>} lists one mod's options with their ids (owner decision D5).
 */
final class OptionCommands {
    /** Lines of one mod's options shown in chat; the rest are in the file. */
    static final int LINES_IN_CHAT = 40;
    static final int TOP_MODS = 8;

    private OptionCommands() {
    }

    static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("options")
                .executes(ctx -> summary(ctx.getSource()))
                .then(Commands.argument("mod", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                Catalogue.entries().stream().map(Catalogue.Entry::modId).filter(m -> !m.isEmpty()).distinct(), b))
                        .executes(ctx -> mod(ctx.getSource(), StringArgumentType.getString(ctx, "mod")))));
    }

    private static int summary(CommandSourceStack source) {
        Catalogue.build(source.getServer());
        for (String line : summaryLines()) {
            source.sendSuccess(() -> Text.of(line), false);
        }
        return 1;
    }

    /** The chat summary of the catalogue: counts by kind, by state, the mods with the most options, and the file. */
    static List<String> summaryLines() {
        List<Catalogue.Entry> all = Catalogue.entries();
        List<String> lines = new ArrayList<>();
        long mods = all.stream().map(Catalogue.Entry::modId).filter(m -> !m.isEmpty()).distinct().count();
        lines.add(Text.raw("options.summary", count(all, Catalogue.Kind.MACHINE), count(all, Catalogue.Kind.MOB),
                count(all, Catalogue.Kind.EXPLOSIVE), count(all, Catalogue.Kind.SETUP), Fmt.count(mods), count(all, Catalogue.Kind.GENERAL)));
        long ready = all.stream().filter(Catalogue.Entry::ready).count();
        long left = all.stream().filter(Catalogue.Entry::leftOut).count();
        lines.add(Text.raw("options.states", Fmt.count(ready), Fmt.count(all.size() - ready - left), Fmt.count(left)));
        Map<String, Long> perMod = all.stream().filter(e -> !e.modId().isEmpty() && !e.leftOut())
                .collect(Collectors.groupingBy(Catalogue.Entry::modId, Collectors.counting()));
        String top = perMod.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_MODS)
                .map(e -> Catalogue.modName(e.getKey()) + " " + Fmt.count(e.getValue()))
                .collect(Collectors.joining(", "));
        if (!top.isEmpty()) {
            lines.add(Text.raw("options.top", top));
        }
        lines.add(Text.raw("options.file", StressFiles.shown() + Catalogue.FILE));
        return lines;
    }

    private static String count(List<Catalogue.Entry> all, Catalogue.Kind kind) {
        return Fmt.count(all.stream().filter(e -> e.kind() == kind && !e.leftOut()).count());
    }

    private static int mod(CommandSourceStack source, String modId) {
        List<Catalogue.Entry> list = Catalogue.byMod().get(modId);
        if (list == null || list.isEmpty()) {
            source.sendFailure(Text.t("options.no_mod", modId));
            return 0;
        }
        source.sendSuccess(() -> Text.t("options.mod.head", Catalogue.modName(modId), modId, list.size()), false);
        for (Catalogue.Entry e : list.subList(0, Math.min(LINES_IN_CHAT, list.size()))) {
            source.sendSuccess(() -> Text.t("options.mod.line", e.id(), e.name(), e.kind().text(), e.state()), false);
        }
        if (list.size() > LINES_IN_CHAT) {
            source.sendSuccess(() -> Text.t("options.mod.more", list.size() - LINES_IN_CHAT, StressFiles.shown() + Catalogue.FILE), false);
        }
        return 1;
    }
}
