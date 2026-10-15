package com.dynamicwinds.core;

/**
 * Result of the wind model at a single point in space/time.
 */
public final class WindSample {
    public final Vec3 vector;   // wind vector (m/s-ish units)
    public final double speed;  // magnitude of vector
    public final double gust;   // gust multiplier (>=0)

    public WindSample(Vec3 vector, double speed, double gust) {
        this.vector = vector;
        this.speed = speed;
        this.gust = gust;
    }

    public static final WindSample ZERO = new WindSample(Vec3.ZERO, 0.0, 1.0);
}
