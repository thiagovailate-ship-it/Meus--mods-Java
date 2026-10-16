package com.dynamicwinds.core;

/**
 * Pure, deterministic wind model: wind = f(seed, time, position).
 * Refinements: curl-based divergence-free field, log-law profile, rotating pressure
 * direction, diurnal cycle, storm factor, OU gusts, severe weather systems.
 */
public final class WindModel {
    private final long seed;
    private final CurlNoise curl;
    private final PressureField pressure;
    private final OrnsteinUhlenbeck ou;
    private final StormField storms;

    /** Hemisferio norte com a chance padrao de geracao de sistemas. */
    public WindModel(long seed) {
        this(seed, true, 0.35);
    }

    /**
     * @param seed             seed do mundo
     * @param north            hemisferio (define o sentido de rotacao dos ciclones)
     * @param stormSpawnChance chance de nascer um sistema por janela (0 desliga)
     */
    public WindModel(long seed, boolean north, double stormSpawnChance) {
        this.seed = seed;
        this.curl = new CurlNoise(seed);
        this.pressure = new PressureField(seed);
        this.ou = new OrnsteinUhlenbeck(0.12, 0.0, 1.0, seed);
        this.storms = new StormField(seed, north, stormSpawnChance);
    }

    /** Sistemas de tempo severo deste mundo (naturais e invocados). */
    public StormField storms() {
        return storms;
    }

    /**
     * Vento no ponto dado, tratando a superficie como oceano (usado em testes e
     * quando nao ha informacao de bioma disponivel).
     */
    public WindSample compute(WorldContext ctx, double x, double y, double z) {
        return compute(ctx, x, y, z, SurfaceRoughness.OPEN_WATER);
    }

    /**
     * Vento no ponto dado.
     *
     * @param ctx       estado do mundo (seed, tempo, superficie, clima)
     * @param x         posicao X
     * @param y         posicao Y
     * @param z         posicao Z
     * @param roughness fator de energia da superficie sob os sistemas ativos
     *                  (define landfall: tempestade sobre continente perde forca)
     * @return amostra de vento (vento de fundo somado aos sistemas severos)
     */
    public WindSample compute(WorldContext ctx, double x, double y, double z,
                              SurfaceRoughness roughness) {
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

        // severe weather systems: vortices, tropical storms, hurricanes
        if (roughness != null) {
            Vec3 severe = storms.sampleWind(ctx.timeTicks, x, y, z, ctx.surfaceY, roughness);
            if (severe != Vec3.ZERO) {
                vector = vector.add(severe);
            }
        }

        return new WindSample(vector, vector.length(), gust);
    }
}
