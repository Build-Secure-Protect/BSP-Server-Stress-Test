package com.mrgregles.bsp_server_stress_test.text;

import java.util.Locale;

/** Numbers as the mod writes them for people: one decimal for times and rates, thousands separated for counts. */
public final class Fmt {
    private Fmt() {
    }

    public static String one(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Two decimals, for the small times of single blocks and entities. */
    public static String two(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** Three decimals, for the time of one block's or entity's single tick. */
    public static String three(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    public static String count(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }
}
