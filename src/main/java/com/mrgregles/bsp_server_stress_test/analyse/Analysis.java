package com.mrgregles.bsp_server_stress_test.analyse;

import com.mrgregles.bsp_server_stress_test.BSPServerStressTest;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.measure.Counts;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.measure.Meter;
import com.mrgregles.bsp_server_stress_test.report.CsvReport;
import com.mrgregles.bsp_server_stress_test.report.PlainReport;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import com.mrgregles.bsp_server_stress_test.timing.TimingLines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The analyser (brief point 4, owner decision D18): measures the server as it is for a while, with the timers on and
 * nothing added, then looks at the world (crowds, item piles, what keeps chunks loaded) and writes the plain-English
 * report with those sections, beside its CSVs. Only reads; one analysis at a time.
 */
public final class Analysis {
    public static final String LABEL = "analyse";

    private Analysis() {
    }

    /** What an analysis left: its report and files (null when they could not be written). */
    public record Done(PlainReport report, Path csv, Path txt, Measurement.Result result, WorldScan scan) {
    }

    public static boolean running() {
        return Meter.running(LABEL) != null;
    }

    public static Measurement current() {
        return Meter.running(LABEL);
    }

    /** Starts an analysis of {@code seconds}; {@code why} heads the report ("" for one asked for). */
    public static void start(MinecraftServer server, int seconds, String why, Consumer<Done> then) {
        Meter.startTimed(server, LABEL, seconds, result -> {
            WorldScan scan = WorldScan.scan(server);
            PlainReport report = report(result, scan, why);
            Path csv = null;
            Path txt = null;
            try {
                csv = CsvReport.write(LABEL, List.of(result));
                txt = report.write(csv, LABEL);
            } catch (Exception e) {
                BSPServerStressTest.LOGGER.error("Could not write the analysis", e);
            }
            then.accept(new Done(report, csv, txt, result, scan));
        });
    }

    /** The report of an analysis: the measurement's report with the world's sentences and sections added. */
    public static PlainReport report(Measurement.Result result, WorldScan scan, String why) {
        List<String> head = new ArrayList<>();
        if (!scan.crowds().isEmpty()) {
            WorldScan.Crowd c = scan.crowds().get(0);
            head.add(Text.raw("analyse.head.crowd", Fmt.count(c.entities()), place(c.dimension(), c.cx(), c.cz()),
                    Catalogue.nameOf(c.topType(), false), Fmt.count(c.topCount())));
        }
        if (!scan.piles().isEmpty()) {
            WorldScan.Pile p = scan.piles().get(0);
            head.add(Text.raw("analyse.head.pile", Fmt.count(p.items()), Fmt.count(p.stacks()), place(p.dimension(), p.cx(), p.cz())));
        }
        List<WorldScan.Holder> notStart = scan.holders().stream().filter(h -> !h.type().equals("start")).toList();
        if (!notStart.isEmpty()) {
            head.add(Text.raw("analyse.head.holders", notStart.stream().limit(3)
                    .map(h -> Text.raw("analyse.holder_short", holderName(h), Fmt.count(h.tickets())))
                    .collect(Collectors.joining("; "))));
        }

        List<String> sections = new ArrayList<>();
        sections.add("");
        sections.add(Text.raw("analyse.section.crowds"));
        int n = 0;
        for (WorldScan.Crowd c : scan.crowds()) {
            sections.add((++n) + ". " + Text.raw("analyse.crowd", place(c.dimension(), c.cx(), c.cz()), Fmt.count(c.entities()),
                    Catalogue.nameOf(c.topType(), false), Fmt.count(c.topCount())));
        }
        if (n == 0) {
            sections.add(Text.raw("analyse.none"));
        }
        sections.add("");
        sections.add(Text.raw("analyse.section.piles"));
        n = 0;
        for (WorldScan.Pile p : scan.piles()) {
            sections.add((++n) + ". " + Text.raw("analyse.pile", place(p.dimension(), p.cx(), p.cz()), Fmt.count(p.items()),
                    Fmt.count(p.stacks())));
        }
        if (n == 0) {
            sections.add(Text.raw("analyse.none"));
        }
        sections.add("");
        sections.add(Text.raw("analyse.section.holders"));
        sections.add("- " + Text.raw("analyse.players", Fmt.count(scan.players())));
        for (WorldScan.Holder h : scan.holders()) {
            String where = h.spots().stream().map(s -> place(s.dimension(), s.cx(), s.cz())).collect(Collectors.joining("; "));
            sections.add("- " + Text.raw("analyse.holder", holderName(h), Fmt.count(h.tickets()), where));
        }
        sections.add("");
        sections.add(Text.raw("analyse.section.dimensions"));
        Counts counts = result.counts();
        result.dimensions().forEach((id, d) -> {
            Counts.Dimension cd = counts.dimension(id);
            if (cd != null && cd.loadedChunks() > 0) {
                sections.add("- " + Text.raw("analyse.dimension", TimingLines.dimension(ResourceLocation.tryParse(id)),
                        Fmt.one(d.averageMs()), Fmt.one(d.worstMs()), Fmt.count(cd.loadedChunks()), Fmt.count(cd.entities()),
                        Fmt.count(cd.tickingBlockEntities()), Fmt.count(cd.players())));
            }
        });
        String title = Text.raw("analyse.title");
        return PlainReport.build(title, why, List.of(result), false, head, sections);
    }

    /** A ticket kind in plain words: its own line in the language file, or the mod behind it, or its name. */
    static String holderName(WorldScan.Holder h) {
        String key = "ticket." + h.type().replace(':', '.');
        if (Text.has(key)) {
            return Text.raw(key, h.modId().isEmpty() ? Text.raw("analyse.some_mod") : Catalogue.modName(h.modId()));
        }
        return Text.raw("ticket.other", h.type());
    }

    static String place(ResourceLocation dimension, int cx, int cz) {
        return Text.raw("timing.place", TimingLines.dimension(dimension), cx * 16 + 8, cz * 16 + 8);
    }
}
