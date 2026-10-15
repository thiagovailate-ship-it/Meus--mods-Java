package com.dynamicwinds.core;

/**
 * Roughness classes used by the logarithmic wind profile. z0 in blocks.
 */
public final class SurfaceClass {
    public static final int WATER = 0;
    public static final int SAND = 1;
    public static final int GRASS = 2;
    public static final int FOREST = 3;
    public static final int STONE = 4;
    public static final int URBAN = 5;

    public static final double[] Z0 = { 0.0002, 0.001, 0.03, 0.5, 0.01, 1.0 };

    private SurfaceClass() {}

    public static double z0(int cls) {
        if (cls < 0 || cls >= Z0.length) return Z0[GRASS];
        return Z0[cls];
    }
}
