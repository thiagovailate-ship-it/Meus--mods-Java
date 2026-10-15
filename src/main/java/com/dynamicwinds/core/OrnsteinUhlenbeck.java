package com.dynamicwinds.core;

/**
 * Deterministic gust factor. Uses a single stored PerlinNoise for spatial/temporal
 * continuity; no per-call allocations.
 */
public final class OrnsteinUhlenbeck {
    private final double theta;
    private final double mu;
    private final double sigma;
    private final PerlinNoise noise;

    public OrnsteinUhlenbeck(double theta, double mu, double sigma) {
        this(theta, mu, sigma, 0L);
    }

    public OrnsteinUhlenbeck(double theta, double mu, double sigma, long seed) {
        this.theta = theta;
        this.mu = mu;
        this.sigma = sigma;
        this.noise = new PerlinNoise(seed + 4242L);
    }

    public double gust(long seed, double x, double z, long timeTicks) {
        double t = timeTicks / 20.0;
        double raw = noise.noise(t * 0.3, x * 0.01, z * 0.01);
        double g = mu + (raw - mu) * Math.exp(-theta * 0.5) + sigma * raw * 0.5;
        double mult = 1.0 + 0.5 * g;
        if (mult < 0.15) mult = 0.15;
        if (mult > 3.0) mult = 3.0;
        return mult;
    }
}
