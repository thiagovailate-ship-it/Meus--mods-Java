package com.dynamicwinds.core;

/**
 * Logarithmic wind profile: u(z) = (u-star/kappa) * ln(z / z0).
 */
public final class LogProfile {
    public static final double KAPPA = 0.41;

    private LogProfile() {}

    /**
     * Returns the speed multiplier for a given height above surface.
     * z is height above ground in blocks; z0 roughness length.
     */
    public static double speed(double uStar, double z, double z0) {
        double zz = z;
        if (zz < z0 + 0.01) zz = z0 + 0.01;
        if (z0 <= 0) z0 = 0.001;
        return (uStar / KAPPA) * Math.log(zz / z0);
    }
}
