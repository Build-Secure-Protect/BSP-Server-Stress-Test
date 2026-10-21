package com.mrgregles.bsp_server_stress_test.report;

import com.mrgregles.bsp_server_stress_test.StressFiles;
import com.mrgregles.bsp_server_stress_test.measure.Counts;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;

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
 * The figures of one or more measurements as a CSV file in {@code config/bsp_server_stress_test/reports/}: a row for
 * the whole server ("all") and one per dimension, for each measurement. Opens in any spreadsheet.
 */
public final class CsvReport {
    public static final String HEADER = "label,scope,ticks,seconds,tps,average_ms,worst_ms,over_50ms_percent,"
            + "loaded_chunks,entities,ticking_block_entities,players,chunks_generated,memory_used_mb,memory_max_mb";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private CsvReport() {
    }

    /**
     * A new file named by the time and {@code name}, and beside it the same name ending {@code timings.csv} with where
     * the tick time went ({@link TimingCsv}); returns the first.
     */
    public static Path write(String name, List<Measurement.Result> results) throws IOException {
        String base = "reports/" + LocalDateTime.now().format(STAMP) + " " + safe(name);
        Path file = StressFiles.file(base + ".csv");
        Files.createDirectories(file.getParent());
        Files.write(file, lines(results), StandardCharsets.UTF_8);
        Files.write(timingsOf(file), TimingCsv.lines(results), StandardCharsets.UTF_8);
        return file;
    }

    /** The timings file written beside a figures file. */
    public static Path timingsOf(Path figures) {
        String n = figures.getFileName().toString();
        return figures.resolveSibling(n.substring(0, n.length() - 4) + " timings.csv");
    }

    static List<String> lines(List<Measurement.Result> results) {
        List<String> out = new ArrayList<>();
        out.add(HEADER);
        for (Measurement.Result r : results) {
            Counts c = r.counts();
            out.add(row(r.label(), "all", r.ticks(), r.seconds(), r.tps(), r.averageMs(), r.worstMs(), r.slowShare() * 100,
                    c.loadedChunks(), c.entities(), c.tickingBlockEntities(), c.players(), r.chunksGenerated(),
                    String.valueOf(c.memoryUsedMb()), String.valueOf(c.memoryMaxMb())));
            for (var e : r.dimensions().entrySet()) {
                Counts.Dimension d = c.dimension(e.getKey());
                var t = e.getValue();
                out.add(row(r.label(), e.getKey(), r.ticks(), r.seconds(), r.tps(), t.averageMs(), t.worstMs(), Double.NaN,
                        d == null ? 0 : d.loadedChunks(), d == null ? 0 : d.entities(),
                        d == null ? 0 : d.tickingBlockEntities(), d == null ? 0 : d.players(), t.chunksGenerated(), "", ""));
            }
        }
        return out;
    }

    private static String row(String label, String scope, long ticks, double seconds, double tps, double avg, double worst,
                              double slowPercent, int chunks, int entities, int blockEntities, int players, int generated,
                              String memUsed, String memMax) {
        return String.join(",", quote(label), quote(scope), String.valueOf(ticks), num(seconds), num(tps), num(avg),
                num(worst), Double.isNaN(slowPercent) ? "" : num(slowPercent), String.valueOf(chunks),
                String.valueOf(entities), String.valueOf(blockEntities), String.valueOf(players), String.valueOf(generated),
                memUsed, memMax);
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String quote(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String safe(String name) {
        String s = name.replaceAll("[^A-Za-z0-9 _-]", "_").trim();
        return s.isEmpty() ? "measure" : s;
    }
}
