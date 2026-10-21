package com.mrgregles.bsp_server_stress_test.report;

import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.measure.Counts;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import com.mrgregles.bsp_server_stress_test.timing.TimingLines;
import com.mrgregles.bsp_server_stress_test.timing.TimingReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The plain-English report (owner decision D17): what hurt most and why in a few sentences at the top, then each step,
 * the worst places, the worst things with why each costs, each mod's share, and a note on the figures. Every sentence
 * with a number takes it from a measurement; the "why" comes from {@link Causes}. Written as a text file beside the
 * CSVs; its headline is also what the chat shows when a run ends.
 */
public final class PlainReport {
    static final int TOP = 10;
    /** Memory above this share of the most the server may use gets a warning line. */
    static final double MEMORY_WARN = 0.85;

    private final List<String> headline;
    private final List<String> lines;

    private PlainReport(List<String> headline, List<String> lines) {
        this.headline = headline;
        this.lines = lines;
    }

    /** The few sentences of what hurt most. */
    public List<String> headline() {
        return headline;
    }

    /** The whole report, line by line. */
    public List<String> lines() {
        return lines;
    }

    /**
     * Builds the report. {@code title} names the run or measurement, {@code ended} says how it ended ("" for none);
     * {@code results} are its measured steps in order (step 0 the baseline for a run, one result for a measurement).
     */
    public static PlainReport build(String title, String ended, List<Measurement.Result> results, boolean isRun) {
        return build(title, ended, results, isRun, List.of(), List.of());
    }

    /**
     * As above, with headline sentences and sections of the caller's own (the analyser's crowds, piles and what keeps
     * chunks loaded): the sentences follow the report's own, the sections come before ABOUT THESE FIGURES.
     */
    public static PlainReport build(String title, String ended, List<Measurement.Result> results, boolean isRun,
                                    List<String> extraHead, List<String> extraSections) {
        List<String> head = new ArrayList<>();
        if (results.isEmpty()) {
            head.add(Text.raw("report.nothing_measured"));
            return new PlainReport(head, assemble(title, ended, head, List.of(), extraSections));
        }
        Measurement.Result last = results.get(results.size() - 1);
        TimingReport t = last.timings();

        // 1. the biggest jump between steps
        if (isRun && results.size() > 1) {
            int at = 1;
            double jump = Double.NEGATIVE_INFINITY;
            for (int i = 1; i < results.size(); i++) {
                double d = results.get(i).averageMs() - results.get(i - 1).averageMs();
                if (d > jump) {
                    jump = d;
                    at = i;
                }
            }
            head.add(Text.raw("report.head.jump", stepName(results.get(at)), Fmt.one(results.get(at - 1).averageMs()),
                    Fmt.one(results.get(at).averageMs()), signed(jump)));
        }
        // 2. the thing that cost most, and why
        if (t != null && !t.things().isEmpty()) {
            TimingReport.Thing top = t.things().get(0);
            head.add(Text.raw("report.head.thing", Catalogue.nameOf(top.id(), top.blockEntity()),
                    Fmt.count(Math.round(t.perTick(top.calls()))), Fmt.two(t.msPerTick(top.nanos())), Fmt.one(last.averageMs()),
                    percent(t.msPerTick(top.nanos()), last.averageMs())) + " " + Causes.forThing(top));
        }
        // 3. the busiest place
        if (t != null && !t.chunks().isEmpty()) {
            TimingReport.Chunk c = t.chunks().get(0);
            head.add(Text.raw("report.head.place", TimingLines.place(c), Fmt.two(t.msPerTick(c.nanos())),
                    c.top() == null ? "?" : Catalogue.nameOf(c.top(), isBlock(t, c.top()))));
        }
        // 4. keeping up with 20 ticks a second
        head.add(keptUp(results, isRun));
        // 5. the time the timers do not see
        if (t != null) {
            double timed = t.msPerTick(t.totalNanos());
            double rest = Math.max(0, last.averageMs() - timed);
            head.add(Text.raw("report.head.untimed", Fmt.one(rest), percent(rest, last.averageMs())));
        }
        // new land, if any was made
        int generated = results.stream().mapToInt(Measurement.Result::chunksGenerated).sum();
        if (generated > 0) {
            String mods = Causes.worldgenMods();
            head.add(Text.raw(mods.isEmpty() ? "report.head.generated" : "report.head.generated_mods", Fmt.count(generated), mods));
        }
        // memory close to full
        Counts c = last.counts();
        if (c.memoryMaxMb() > 0 && c.memoryUsedMb() >= c.memoryMaxMb() * MEMORY_WARN) {
            head.add(Text.raw("report.head.memory", Fmt.count(c.memoryUsedMb()), Fmt.count(c.memoryMaxMb())));
        }
        head.addAll(extraHead);
        return new PlainReport(head, assemble(title, ended, head, results, extraSections));
    }

    /** Below this many measured ticks a second the server is behind, however short its ticks are. */
    static final double KEEP_UP_TPS = 19.0;

    /** Behind 20 ticks a second: ticks over 50 ms on average, or too few ticks done each second (time lost between ticks). */
    static boolean behind(Measurement.Result r) {
        return r.averageMs() > 50 || r.tps() < KEEP_UP_TPS;
    }

    /**
     * Milliseconds of each second that went to work between ticks: what is left of the second after the ticks done in it,
     * at their average length. A server behind has no time to wait between ticks, so this is work, not idling.
     */
    static double betweenTicksMs(Measurement.Result r) {
        return Math.max(0, 1000 - r.tps() * r.averageMs());
    }

    /** ", with N new chunks generated" when the step made new land, for the between-ticks lines; "" otherwise. */
    private static String newLand(Measurement.Result r) {
        return r.chunksGenerated() > 0 ? Text.raw("report.head.between_new_land", Fmt.count(r.chunksGenerated())) : "";
    }

    private static String keptUp(List<Measurement.Result> results, boolean isRun) {
        int firstSlow = -1;
        for (int i = 0; i < results.size(); i++) {
            if (behind(results.get(i))) {
                firstSlow = i;
                break;
            }
        }
        if (!isRun) {
            Measurement.Result r = results.get(0);
            if (!behind(r)) {
                return Text.raw("report.head.keeping_up_now", Fmt.one(r.averageMs()), Fmt.one(r.tps()), Fmt.one(r.slowShare() * 100));
            }
            return r.averageMs() > 50
                    ? Text.raw("report.head.behind_now", Fmt.one(r.averageMs()), Fmt.one(r.tps()), Fmt.one(r.slowShare() * 100))
                    : Text.raw("report.head.behind_now_between", Fmt.one(r.tps()), Fmt.one(r.averageMs()),
                    Fmt.count(Math.round(betweenTicksMs(r))), newLand(r), Fmt.one(r.slowShare() * 100));
        }
        if (firstSlow < 0) {
            Measurement.Result last = results.get(results.size() - 1);
            return Text.raw("report.head.kept_up", Fmt.one(last.averageMs()), stepName(last));
        }
        Measurement.Result slow = results.get(firstSlow);
        if (slow.averageMs() <= 50) {
            // behind on ticks a second while each tick was short: the time went between the ticks
            return firstSlow == 0
                    ? Text.raw("report.head.behind_from_start_between", Fmt.one(slow.tps()), Fmt.one(slow.averageMs()),
                    Fmt.count(Math.round(betweenTicksMs(slow))), newLand(slow))
                    : Text.raw("report.head.fell_behind_between", stepName(slow), Fmt.one(slow.tps()), Fmt.one(slow.averageMs()),
                    Fmt.count(Math.round(betweenTicksMs(slow))), newLand(slow));
        }
        return firstSlow == 0
                ? Text.raw("report.head.behind_from_start", Fmt.one(slow.averageMs()))
                : Text.raw("report.head.fell_behind", stepName(slow), Fmt.one(slow.averageMs()), Fmt.one(slow.tps()));
    }

    private static List<String> assemble(String title, String ended, List<String> head, List<Measurement.Result> results,
                                         List<String> extraSections) {
        List<String> out = new ArrayList<>();
        out.add(Text.raw("report.title", title, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT))));
        if (!ended.isEmpty()) {
            out.add(ended);
        }
        out.add("");
        out.add(Text.raw("report.section.what_hurt"));
        for (int i = 0; i < head.size(); i++) {
            out.add((i + 1) + ". " + head.get(i));
        }
        if (results.isEmpty()) {
            out.addAll(extraSections);
            return out;
        }
        if (results.size() > 1) {
            out.add("");
            out.add(Text.raw("report.section.steps"));
            Measurement.Result before = null;
            for (Measurement.Result r : results) {
                String line = Text.raw("report.step", capital(r.label()), Fmt.one(r.tps()), Fmt.one(r.averageMs()), Fmt.one(r.worstMs()),
                        Fmt.count(r.counts().loadedChunks()), Fmt.count(r.counts().entities()),
                        Fmt.count(r.counts().tickingBlockEntities()));
                if (before != null) {
                    line += " " + Text.raw("report.step.change", signed(r.averageMs() - before.averageMs()));
                }
                out.add(line);
                TimingReport t = r.timings();
                if (t != null && !t.things().isEmpty()) {
                    TimingReport.Thing top = t.things().get(0);
                    out.add("   " + Text.raw("report.step.busiest", Catalogue.nameOf(top.id(), top.blockEntity()),
                            Fmt.two(t.msPerTick(top.nanos()))));
                }
                before = r;
            }
        }
        Measurement.Result last = results.get(results.size() - 1);
        TimingReport t = last.timings();
        if (t != null) {
            String when = results.size() > 1 ? Text.raw("report.when_last", last.label()) : Text.raw("report.when_only");
            out.add("");
            out.add(Text.raw("report.section.places", when));
            int n = 0;
            for (TimingReport.Chunk c : t.chunks().stream().limit(TOP).toList()) {
                out.add((++n) + ". " + Text.raw("report.place", TimingLines.place(c), Fmt.two(t.msPerTick(c.nanos())),
                        c.top() == null ? "?" : Catalogue.nameOf(c.top(), isBlock(t, c.top())),
                        Fmt.count(Math.round(t.perTick(c.blockEntityCalls()))), Fmt.count(Math.round(t.perTick(c.entityCalls())))));
            }
            out.add("");
            out.add(Text.raw("report.section.things", when));
            n = 0;
            for (TimingReport.Thing x : t.things().stream().limit(TOP).toList()) {
                double each = x.calls() == 0 ? 0 : x.nanos() / (double) x.calls() / 1e6;
                out.add((++n) + ". " + Text.raw("report.thing", Catalogue.nameOf(x.id(), x.blockEntity()), x.id().toString(),
                        Text.raw(x.blockEntity() ? "report.kind.block" : "report.kind.entity"),
                        Fmt.two(t.msPerTick(x.nanos())), Fmt.count(Math.round(t.perTick(x.calls()))), Fmt.three(each),
                        Fmt.two(x.worstNanos() / 1e6)));
                out.add("   " + Causes.forThing(x));
            }
            out.add("");
            out.add(Text.raw("report.section.mods", when));
            for (TimingReport.Mod m : t.mods().stream().limit(TOP).toList()) {
                out.add("- " + Text.raw("report.mod", Catalogue.modName(m.modId()), Fmt.two(t.msPerTick(m.nanos())),
                        percent(t.msPerTick(m.nanos()), last.averageMs())));
            }
            out.addAll(extraSections);
            out.add("");
            out.add(Text.raw("report.section.about"));
            out.add(Text.raw("report.about.timers", Fmt.two(t.msPerTick(t.overheadNanos()))));
            if (!t.blockEntityHookSeen() && last.counts().tickingBlockEntities() > 0) {
                out.add(Text.raw("timing.no_block_hook"));
            }
            if (!t.entityHookSeen() && last.counts().entities() > 0) {
                out.add(Text.raw("timing.no_entity_hook"));
            }
        }
        out.add(Text.raw("report.about.files"));
        out.add(Text.raw("report.about.spark"));
        return out;
    }

    /** Writes the report as {@code reports/<stamp> <name>.txt}, or beside a CSV with the same name when one is given. */
    public Path write(Path besideCsv, String name) throws IOException {
        Path file = besideCsv != null
                ? besideCsv.resolveSibling(besideCsv.getFileName().toString().replaceFirst("\\.csv$", ".txt"))
                : StressFiles.file("reports/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT))
                + " " + name + ".txt");
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
        return file;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String stepName(Measurement.Result r) {
        return r.label();
    }

    private static String signed(double ms) {
        return (ms >= 0 ? "+" : "") + Fmt.one(ms);
    }

    private static String percent(double part, double whole) {
        return whole <= 0 ? "0" : String.valueOf(Math.round(part / whole * 100));
    }

    private static boolean isBlock(TimingReport t, net.minecraft.resources.ResourceLocation id) {
        for (TimingReport.Thing x : t.things()) {
            if (x.id().equals(id)) {
                return x.blockEntity();
            }
        }
        return true;
    }
}
