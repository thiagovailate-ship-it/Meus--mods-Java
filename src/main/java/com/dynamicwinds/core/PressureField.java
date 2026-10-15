package com.dynamicwinds.core;

/**
 * Advected pressure field producing a slowly rotating dominant wind direction.
 */
public final class PressureField {
    private final PerlinNoise noise;

    public PressureField(long seed) {
        this.noise = new PerlinNoise(seed + 7777L);
    }

    /** Returns a horizontal unit vector (y=0) for the dominant direction. */
    public Vec3 direction(double x, double z, long timeTicks) {
        double slowTime = timeTicks * 0.00005;
        double n = noise.noise(x * 0.002, 0, z * 0.002 + slowTime);
        double angle = slowTime * 0.5 + 2.0 * Math.PI * n;
        return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
    }
}
