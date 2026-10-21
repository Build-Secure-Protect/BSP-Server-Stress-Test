package com.mrgregles.bsp_server_stress_test.report;

import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.measure.Measurement;
import com.mrgregles.bsp_server_stress_test.timing.TimingLines;
import com.mrgregles.bsp_server_stress_test.timing.TimingReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where tick time went, beside the figures CSV: for each measurement the busiest blocks and entity types, the busiest
 * chunks and every mod, in milliseconds per tick and as a share of the average tick.
 */
public final class TimingCsv {
    public static final String HEADER = "label,what,id,name,dimension,x,z,ms_per_tick,share_of_tick_percent,per_tick,worst_ms";
    static final int ROWS = 50;

    private TimingCsv() {
    }

    static List<String> lines(List<Measurement.Result> results) {
        List<String> out = new ArrayList<>();
        out.add(HEADER);
        for (Measurement.Result r : results) {
            TimingReport t = r.timings();
            if (t == null) {
                continue;
            }
            double avg = r.averageMs();
            for (TimingReport.Thing x : t.things().stream().limit(ROWS).toList()) {
                out.add(row(r.label(), x.blockEntity() ? "block" : "entity", x.id().toString(),
                        Catalogue.nameOf(x.id(), x.blockEntity()), "", "", "", t.msPerTick(x.nanos()), avg,
                        t.perTick(x.calls()), x.worstNanos() / 1e6));
            }
            for (TimingReport.Chunk c : t.chunks().stream().limit(ROWS).toList()) {
                out.add(row(r.label(), "chunk", c.top() == null ? "" : c.top().toString(), "",
                        TimingLines.dimension(c.dimension()), String.valueOf(c.x() * 16 + 8), String.valueOf(c.z() * 16 + 8),
                        t.msPerTick(c.nanos()), avg, t.perTick(c.blockEntityCalls() + c.entityCalls()), Double.NaN));
            }
            for (TimingReport.Mod m : t.mods()) {
                out.add(row(r.label(), "mod", m.modId(), Catalogue.modName(m.modId()), "", "", "", t.msPerTick(m.nanos()), avg,
                        t.perTick(m.calls()), Double.NaN));
            }
            out.add(row(r.label(), "timers", "", "", "", "", "", t.msPerTick(t.overheadNanos()), avg, Double.NaN, Double.NaN));
        }
        return out;
    }

    private static String row(String label, String what, String id, String name, String dim, String x, String z,
                              double ms, double averageMs, double perTick, double worstMs) {
        return String.join(",", q(label), what, q(id), q(name), q(dim), x, z, n(ms),
                averageMs > 0 ? n(ms / averageMs * 100) : "", Double.isNaN(perTick) ? "" : n(perTick),
                Double.isNaN(worstMs) ? "" : n(worstMs));
    }

    private static String n(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private static String q(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
