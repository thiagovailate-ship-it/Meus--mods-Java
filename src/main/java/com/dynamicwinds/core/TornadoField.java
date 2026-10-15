package com.dynamicwinds.core;

/**
 * Deterministic tornado (Rankine vortex) field. Tornadoes spawn/move/dissipate
 * based on seed and time; both client and server compute identical positions.
 */
public final class TornadoField {
    private final PerlinNoise noise;

    public TornadoField(long seed) {
        this.noise = new PerlinNoise(seed + 99991L);
    }

    /** Returns the wind contribution of any active tornado at (x,y,z). */
    public Vec3 sample(long seed, double x, double y, double z, long timeTicks) {
        // tornado lifecycle: one active every ~10 minutes (12000 ticks), lasting ~3 min
        long cycle = 12000L;
        long phase = timeTicks % cycle;
        if (phase > 3600L) return Vec3.ZERO; // inactive most of the time

        // center position derived from seed + cycle index
        long idx = timeTicks / cycle;
        PerlinNoise cnoise = new PerlinNoise(seed + idx * 31L);
        double cx = cnoise.noise(idx * 0.7, 0, 0) * 200.0;
        double cz = cnoise.noise(0, idx * 0.7, 1) * 200.0;
        double cy = 64.0;

        double dx = x - cx;
        double dz = z - cz;
        double r = Math.sqrt(dx*dx + dz*dz);
        if (r > 80.0) return Vec3.ZERO;

        double R = 8.0;          // core radius
        double vmax = 25.0;      // max tangential speed
        double vtheta;
        if (r < R) vtheta = vmax * (r / R);
        else vtheta = vmax * (R / r);

        // tangential direction (perpendicular to radial)
        double tx = -dz / (r + 1e-6);
        double tz = dx / (r + 1e-6);

        // updraft in core
        double up = (r < R) ? 6.0 * (1.0 - r / R) : 0.0;

        // radial inflow near ground
        double radial = -1.5 * (r < 40 ? (1.0 - r/40.0) : 0.0);

        double vx = tx * vtheta + dx/(r+1e-6) * radial;
        double vy = up;
        double vz = tz * vtheta + dz/(r+1e-6) * radial;

        // fade in/out by lifecycle
        double life = 1.0;
        if (phase < 200) life = phase / 200.0;
        if (phase > 3400) life = (3600 - phase) / 200.0;
        return new Vec3(vx*life, vy*life, vz*life);
    }
    /** Returns active tornado center (x,y,z) or null if none. */
    public double[] activeCenter(long seed, long timeTicks) {
        long cycle = 12000L;
        long phase = timeTicks % cycle;
        if (phase > 3600L) return null;
        long idx = timeTicks / cycle;
        PerlinNoise cnoise = new PerlinNoise(seed + idx * 31L);
        double cx = cnoise.noise(idx * 0.7, 0, 0) * 200.0;
        double cz = cnoise.noise(0, idx * 0.7, 1) * 200.0;
        return new double[]{cx, 64.0, cz};
    }
}
