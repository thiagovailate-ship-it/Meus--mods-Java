package com.dynamicwinds.core;

/**
 * Minimal, Minecraft-free description of the world state needed by the wind model.
 * Both client and server build an identical WorldContext for a given (world, position),
 * which is what makes the model deterministic and packet-free.
 */
public final class WorldContext {
    public final long seed;
    public final long timeTicks;       // world time in ticks
    public final int surfaceY;        // cached heightmap at the cell
    public final double storm;        // 0..1 storm intensity
    public final int surfaceClass;    // index into SurfaceClass roughness table

    public WorldContext(long seed, long timeTicks, int surfaceY, double storm, int surfaceClass) {
        this.seed = seed;
        this.timeTicks = timeTicks;
        this.surfaceY = surfaceY;
        this.storm = clamp01(storm);
        this.surfaceClass = surfaceClass;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
