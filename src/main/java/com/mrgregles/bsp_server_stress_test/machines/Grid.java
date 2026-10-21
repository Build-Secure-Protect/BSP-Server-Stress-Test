package com.mrgregles.bsp_server_stress_test.machines;

/**
 * Where the {@code index}th placed block of a place goes, from the grid's corner: rows across one layer of
 * {@code side} by {@code side}, {@code spacing} blocks apart, then the next layer {@link #LAYER_GAP} higher.
 */
public final class Grid {
    public static final int LAYER_GAP = 3;

    private Grid() {
    }

    /** {dx, dy, dz} from the grid's corner. */
    public static int[] cell(int index, int side, int spacing) {
        return cell(index, side, spacing, LAYER_GAP);
    }

    /** The same with layers {@code layerGap} apart (contraptions taller than one block). */
    public static int[] cell(int index, int side, int spacing, int layerGap) {
        int perLayer = side * side;
        int layer = index / perLayer;
        int r = index % perLayer;
        return new int[]{(r % side) * spacing, layer * layerGap, (r / side) * spacing};
    }
}
