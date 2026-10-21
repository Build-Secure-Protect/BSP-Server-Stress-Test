package com.mrgregles.bsp_server_stress_test.timing;

import com.mrgregles.bsp_server_stress_test.catalogue.Catalogue;
import com.mrgregles.bsp_server_stress_test.measure.Counts;
import com.mrgregles.bsp_server_stress_test.text.Fmt;
import com.mrgregles.bsp_server_stress_test.text.Text;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** A {@link TimingReport} as short chat lines: what cost most, which chunks, what the timers cost, and a broken hook. */
public final class TimingLines {
    public static final int THINGS = 5;
    public static final int CHUNKS = 3;

    private TimingLines() {
    }

    public static List<String> lines(TimingReport t, Counts counts) {
        List<String> out = new ArrayList<>();
        if (!t.things().isEmpty()) {
            out.add(Text.raw("timing.busiest", t.things().stream().limit(THINGS)
                    .map(x -> Text.raw("timing.thing", Catalogue.nameOf(x.id(), x.blockEntity()), Fmt.two(t.msPerTick(x.nanos())),
                            Fmt.count(Math.round(t.perTick(x.calls())))))
                    .collect(Collectors.joining("; "))));
        }
        if (!t.chunks().isEmpty()) {
            out.add(Text.raw("timing.busiest_chunks", t.chunks().stream().limit(CHUNKS)
                    .map(c -> Text.raw("timing.chunk", place(c), Fmt.two(t.msPerTick(c.nanos())),
                            c.top() == null ? "?" : Catalogue.nameOf(c.top(), isBlock(t, c.top()))))
                    .collect(Collectors.joining("; "))));
        }
        if (t.ticks() > 0) {
            out.add(Text.raw("timing.overhead", Fmt.two(t.msPerTick(t.overheadNanos())),
                    Fmt.count(Math.round(t.perTick(t.things().stream().mapToLong(TimingReport.Thing::calls).sum())))));
        }
        if (!t.blockEntityHookSeen() && counts != null && counts.tickingBlockEntities() > 0) {
            out.add(Text.raw("timing.no_block_hook"));
        }
        if (!t.entityHookSeen() && counts != null && counts.entities() > 0) {
            out.add(Text.raw("timing.no_entity_hook"));
        }
        return out;
    }

    /** "the overworld at x 200, z -632": the middle of the chunk in block coordinates, ready for /tp. */
    public static String place(TimingReport.Chunk c) {
        return Text.raw("timing.place", dimension(c.dimension()), c.x() * 16 + 8, c.z() * 16 + 8);
    }

    public static String dimension(ResourceLocation id) {
        return id.getNamespace().equals("minecraft") ? id.getPath().replace('_', ' ') : id.toString();
    }

    static boolean isBlock(TimingReport t, ResourceLocation id) {
        for (TimingReport.Thing x : t.things()) {
            if (x.id().equals(id)) {
                return x.blockEntity();
            }
        }
        return true;
    }
}
