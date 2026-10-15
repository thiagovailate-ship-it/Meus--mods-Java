package com.dynamicwinds.core;

/**
 * Deterministic tornado (Rankine vortex) field.
 * Lifecycle: spawn then form then mature then dissipate then gone.
 * Spatial distribution: tornadoes are seeded across the world using per-cycle
 * pseudo-random positions derived from seed + cycle index. Both client and server
 * compute identical positions.
 */
public final class TornadoField {
    private final PerlinNoise spawnNoise;

    public TornadoField(long seed) {
        // separate noise stream for spawn positions
        this.spawnNoise = new PerlinNoise(seed + 99991L);
    }

    /**
     * Tornado lifecycle parameters (in ticks):
     * - spawn window: 0..400 (formation)
     * - mature window: 400..3600 (full intensity)
     * - dissipate: 3600..4000 (fading)
     * - total life: 4000 ticks (~200 seconds = 3.3 minutes)
     * - spawn interval: ~15 minutes (30000 ticks) between tornado cycles
     */
    private static final long CYCLE = 30000L;
    private static final long FORMATION_END = 400L;
    private static final long DISSIPATE_START = 3600L;
    private static final long LIFETIME = 4000L;
    private static final double MAX_RADIUS = 120.0;  // world distribution radius
    private static final double CORE_R = 8.0;
    private static final double VMAX = 28.0;

    /**
     * Returns the wind contribution of any active tornado at (x,y,z).
     * Deterministic: same seed, time, position produces same result.
     */
    public Vec3 sample(long seed, double x, double y, double z, long timeTicks) {
        long phase = timeTicks % CYCLE;
        if (phase >= LIFETIME) return Vec3.ZERO;

        // Determine which tornado cycle we're in
        long cycleIndex = timeTicks / CYCLE;
        // spawn position deterministic from seed + cycle
        double cx = spawnPosition(seed, cycleIndex, 0) * MAX_RADIUS;
        double cz = spawnPosition(seed, cycleIndex, 1) * MAX_RADIUS;
        // center y varies with terrain height, but we approximate at 64 for now
        // (real implementation would query world heightmap)
        double cy = 64.0;

        double dx = x - cx;
        double dz = z - cz;
        double r = Math.sqrt(dx*dx + dz*dz);
        if (r > 100.0) return Vec3.ZERO;

        double lifeFactor = lifecycleFactor(phase);
        if (lifeFactor <= 0.0) return Vec3.ZERO;

        // Rankine vortex: solid body rotation inside core, 1/r outside
        double vtheta;
        if (r < CORE_R) {
            vtheta = VMAX * (r / CORE_R);
        } else {
            vtheta = VMAX * (CORE_R / r);
        }

        // tangential direction (perpendicular to radial, CCW)
        double tx = -dz / (r + 1e-6);
        double tz = dx / (r + 1e-6);

        // updraft: strongest in core, decreases with radius
        double up = (r < CORE_R) ? 8.0 * (1.0 - r / CORE_R) : 0.0;
        // also updraft in outer region during mature phase
        if (phase > FORMATION_END && phase < DISSIPATE_START && r < 40.0) {
            up += 2.0 * (1.0 - r / 40.0);
        }

        // radial inflow near ground (converges toward center)
        double radial = 0.0;
        if (r < 50.0) {
            radial = -2.0 * (1.0 - r / 50.0);
        }

        // Intensity varies with lifecycle: ramps up during formation, steady mature, fades
        double intensity = lifeFactor;
        if (phase < FORMATION_END) {
            intensity = phase / (double) FORMATION_END; // 0..1 ramp
        } else if (phase > DISSIPATE_START) {
            intensity = (LIFETIME - phase) / (double) (LIFETIME - DISSIPATE_START); // 1..0 fade
        }

        double vx = tx * vtheta * intensity + dx / (r + 1e-6) * radial * intensity;
        double vy = up * intensity;
        double vz = tz * vtheta * intensity + dz / (r + 1e-6) * radial * intensity;

        return new Vec3(vx, vy, vz);
    }

    /**
     * Returns active tornado center (x,y,z) or null if none active.
     */
    public double[] activeCenter(long seed, long timeTicks) {
        long phase = timeTicks % CYCLE;
        if (phase >= LIFETIME) return null;

        long cycleIndex = timeTicks / CYCLE;
        double cx = spawnPosition(seed, cycleIndex, 0) * MAX_RADIUS;
        double cz = spawnPosition(seed, cycleIndex, 1) * MAX_RADIUS;
        double cy = 64.0;

        double lifeFactor = lifecycleFactor(phase);
        if (lifeFactor <= 0.0) return null;

        return new double[]{cx, cy, cz};
    }

    /** Deterministic random position from seed + component. */
    private double spawnPosition(long seed, long cycleIndex, int component) {
        return spawnNoise.noise(cycleIndex * 0.7, component, 0) * 0.5 + 0.5;
    }

    /** Lifecycle factor: 0 outside active window, 0..1 during lifecycle. */
    private double lifecycleFactor(long phase) {
        if (phase < 100) return phase / 100.0; // formation ramp 0..1
        if (phase > DISSIPATE_START && phase < LIFETIME) {
            return (LIFETIME - phase) / (double) (LIFETIME - DISSIPATE_START);
        }
        if (phase >= LIFETIME) return 0.0;
        return 1.0; // mature phase
    }
}
