package com.dynamicwinds.core;

/**
 * Divergence-free 3D wind field built as the curl of a (multi-octave) Perlin potential.
 * curl(P) has divergence ~0 by construction.
 */
public final class CurlNoise {
    private final PerlinNoise px;
    private final PerlinNoise py;
    private final PerlinNoise pz;

    public CurlNoise(long seed) {
        this.px = new PerlinNoise(seed);
        this.py = new PerlinNoise(seed + 1013L);
        this.pz = new PerlinNoise(seed + 2027L);
    }

    public Vec3 sample(long seed, double x, double y, double z, double time) {
        double tx = x * 0.05 + time * 0.001;
        double ty = y * 0.05;
        double tz = z * 0.05 + time * 0.0007;
        double eps = 0.01;

        // spatial derivatives via finite differences on the multi-octave potential
        // using the same octave structure.
        double dPzDy = (potentialZ(tx, ty+eps, tz) - potentialZ(tx, ty-eps, tz)) / (2*eps);
        double dPyDz = (potentialY(tx, ty, tz+eps) - potentialY(tx, ty, tz-eps)) / (2*eps);
        double dPxDz = (potentialX(tx, ty, tz+eps) - potentialX(tx, ty, tz-eps)) / (2*eps);
        double dPzDx = (potentialZ(tx+eps, ty, tz) - potentialZ(tx-eps, ty, tz)) / (2*eps);
        double dPyDx = (potentialY(tx+eps, ty, tz) - potentialY(tx-eps, ty, tz)) / (2*eps);
        double dPxDy = (potentialX(tx, ty+eps, tz) - potentialX(tx, ty-eps, tz)) / (2*eps);

        double cx = dPzDy - dPyDz;
        double cy = dPxDz - dPzDx;
        double cz = dPyDx - dPxDy;
        double m = Math.sqrt(cx*cx+cy*cy+cz*cz);
        if (m > 6.0) { cx*=6.0/m; cy*=6.0/m; cz*=6.0/m; }
        return new Vec3(cx, cy, cz);
    }

    private double potentialX(double tx, double ty, double tz) {
        double v=0, amp=1.0, freq=1.0;
        for (int o=0;o<3;o++){ v += px.noise(tx*freq, ty*freq, tz*freq)*amp; amp*=0.5; freq*=2.0; }
        return v;
    }
    private double potentialY(double tx, double ty, double tz) {
        double v=0, amp=1.0, freq=1.0;
        for (int o=0;o<3;o++){ v += py.noise(tx*freq, ty*freq, tz*freq)*amp; amp*=0.5; freq*=2.0; }
        return v;
    }
    private double potentialZ(double tx, double ty, double tz) {
        double v=0, amp=1.0, freq=1.0;
        for (int o=0;o<3;o++){ v += pz.noise(tx*freq, ty*freq, tz*freq)*amp; amp*=0.5; freq*=2.0; }
        return v;
    }

    public static Vec3 sampleStatic(long seed, double x, double y, double z, double time) {
        return new CurlNoise(seed).sample(seed, x, y, z, time);
    }
}
