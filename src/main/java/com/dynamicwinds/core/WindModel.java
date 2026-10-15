package com.dynamicwinds.core;

/**
 * Pure, deterministic wind model: wind = f(seed, time, position).
 * Refinements: curl-based divergence-free field, log-law profile, rotating pressure
 * direction, diurnal cycle, storm factor, OU gusts.
 */
public final class WindModel {
    private final long seed;
    private final CurlNoise curl;
    private final PressureField pressure;
    private final OrnsteinUhlenbeck ou;
    private final TornadoField tornado;

    public WindModel(long seed) {
        this.seed = seed;
        this.curl = new CurlNoise(seed);
        this.pressure = new PressureField(seed);
        this.ou = new OrnsteinUhlenbeck(0.12, 0.0, 1.0, seed);
        this.tornado = new TornadoField(seed);
    }

    public WindSample compute(WorldContext ctx, double x, double y, double z) {
        Vec3 c = curl.sample(seed, x, y, z, ctx.timeTicks);

        // slowly rotating dominant horizontal direction from pressure field
        Vec3 pdir = pressure.direction(x, z, ctx.timeTicks);

        // blend: 70% curl (divergence-free) + 30% pressure direction (horizontal)
        Vec3 blended = new Vec3(
            c.x * 0.7 + pdir.x * 0.3,
            c.y * 0.7,                 // keep vertical from curl
            c.z * 0.7 + pdir.z * 0.3
        );
        // normalize to direction
        Vec3 dir = blended.normalize();
        if (dir == Vec3.ZERO) dir = new Vec3(1, 0, 0);

        // friction velocity scales with storm
        double uStar = 0.5 + 0.5 * ctx.storm * 2.0;
        double heightAbove = y - ctx.surfaceY;
        if (heightAbove < 1.0) heightAbove = 1.0;
        double z0 = SurfaceClass.z0(ctx.surfaceClass);
        double profileSpeed = LogProfile.speed(uStar, heightAbove, z0);
        if (profileSpeed < 0) profileSpeed = 0;

        double diurnal = DiurnalCycle.factor(ctx.timeTicks);
        double gust = ou.gust(seed, x, z, ctx.timeTicks);
        double stormMul = 1.0 + ctx.storm * 2.5;

        double speed = profileSpeed * diurnal * gust * stormMul * ctx.baseSpeedMul;
        // dampen near ground already handled by log profile (z0)
        Vec3 vector = dir.mul(speed);

        // tornado contribution
        Vec3 t = tornado.sample(seed, x, y, z, ctx.timeTicks);
        vector = vector.add(t);

        return new WindSample(vector, vector.length(), gust);
    }
}
