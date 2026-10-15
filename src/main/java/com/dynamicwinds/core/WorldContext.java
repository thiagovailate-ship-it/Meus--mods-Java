package com.dynamicwinds.core;

/**
 * Minimal, Minecraft-free description of the world state needed by the wind model.
 * Both client and server build an identical WorldContext for a given (world, position),
 * which is what makes the model deterministic and packet-free.
 */
public final class WorldContext {
    public final long seed;
    public final long timeTicks;
    public final int surfaceY;
    public final double storm;
    public final int surfaceClass;
    public final double baseSpeedMul;

    public WorldContext(long seed, long timeTicks, int surfaceY, double storm, int surfaceClass, double baseSpeedMul) {
        this.seed = seed;
        this.timeTicks = timeTicks;
        this.surfaceY = surfaceY;
        this.storm = clamp01(storm);
        this.surfaceClass = surfaceClass;
        this.baseSpeedMul = baseSpeedMul;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
