package com.dynamicwinds.core;

/**
 * Divergence-free-ish 3D wind field built as the curl of a Perlin potential.
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

    /** Sample the curl at (x,y,z) with time evolution baked into z-offset. */
    public Vec3 sample(long seed, double x, double y, double z, double time) {
        // time enters as slow drift of the sampling point
        double tx = x * 0.05 + time * 0.001;
        double ty = y * 0.05;
        double tz = z * 0.05 + time * 0.0007;
        double eps = 0.01;
        // potential components
        // curl = (dPz/dy - dPy/dz, dPx/dz - dPz/dx, dPy/dx - dPx/dy)
        double dPzDy = (pz.noise(tx, (ty + eps), tz) - pz.noise(tx, (ty - eps), tz)) / (2 * eps);
        double dPyDz = (py.noise(tx, ty, (tz + eps)) - py.noise(tx, ty, (tz - eps))) / (2 * eps);
        double dPxDz = (px.noise(tx, ty, (tz + eps)) - px.noise(tx, ty, (tz - eps))) / (2 * eps);
        double dPzDx = (pz.noise((tx + eps), ty, tz) - pz.noise((tx - eps), ty, tz)) / (2 * eps);
        double dPyDx = (py.noise((tx + eps), ty, tz) - py.noise((tx - eps), ty, tz)) / (2 * eps);
        double dPxDy = (px.noise(tx, (ty + eps), tz) - px.noise(tx, (ty - eps), tz)) / (2 * eps);
        double cx = dPzDy - dPyDz;
        double cy = dPxDz - dPzDx;
        double cz = dPyDx - dPxDy;
        // fractal octaves for richer turbulence
        double amp = 0.5;
        double fx = x*0.1, fy = y*0.1, fz = z*0.1;
        for (int o = 1; o < 3; o++) {
            double e = 0.01 * (o+1);
            double a = (px.noise(fx*(o+1), fy*(o+1), fz*(o+1)) - px.noise(fx*(o+1)-e, fy*(o+1), fz*(o+1)))/e;
            cx += a * amp;
            amp *= 0.5;
        }
        double m = Math.sqrt(cx*cx+cy*cy+cz*cz);
        if (m > 6.0) { cx*=6.0/m; cy*=6.0/m; cz*=6.0/m; }
        return new Vec3(cx, cy, cz);
    }

    /** Convenience using an internal instance. */
    public static Vec3 sampleStatic(long seed, double x, double y, double z, double time) {
        return new CurlNoise(seed).sample(seed, x, y, z, time);
    }
}
