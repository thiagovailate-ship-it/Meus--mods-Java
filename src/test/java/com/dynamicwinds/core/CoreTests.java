package com.dynamicwinds.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreTests {

    @Test
    void determinism() {
        WindModel m = new WindModel(12345L);
        WorldContext ctx = new WorldContext(12345L, 1000L, 64, 0.3, SurfaceClass.GRASS);
        WindSample a = m.compute(ctx, 10.5, 70.0, 20.5);
        WindSample b = m.compute(ctx, 10.5, 70.0, 20.5);
        assertEquals(a.vector, b.vector);
        assertEquals(a.speed, b.speed, 1e-12);
        assertEquals(a.gust, b.gust, 1e-12);
    }

    @Test
    void divergenceApproxZero() {
        WindModel m = new WindModel(999L);
        WorldContext ctx = new WorldContext(999L, 500L, 64, 0.0, SurfaceClass.GRASS);
        double eps = 0.5;
        for (double[] pt : new double[][]{{12.3,70.0,34.5},{-5.7,65.0,10.2},{0.0,64.0,0.0},{100.5,80.0,-50.3}}) {
            double x=pt[0], y=pt[1], z=pt[2];
            WindSample xp = m.compute(ctx, x + eps, y, z);
            WindSample xm = m.compute(ctx, x - eps, y, z);
            WindSample yp = m.compute(ctx, x, y + eps, z);
            WindSample ym = m.compute(ctx, x, y - eps, z);
            WindSample zp = m.compute(ctx, x, y, z + eps);
            WindSample zm = m.compute(ctx, x, y, z - eps);
            double div = (xp.vector.x - xm.vector.x) / (2 * eps)
                       + (yp.vector.y - ym.vector.y) / (2 * eps)
                       + (zp.vector.z - zm.vector.z) / (2 * eps);
            assertTrue(Math.abs(div) < 5.0, "divergence too large at " + x + "," + y + "," + z + ": " + div);
        }
    }

    @Test
    void profileMonotonicWithHeight() {
        double uStar = 1.0;
        double z0 = SurfaceClass.z0(SurfaceClass.GRASS);
        double prev = LogProfile.speed(uStar, 1, z0);
        for (int h = 2; h <= 50; h++) {
            double cur = LogProfile.speed(uStar, h, z0);
            assertTrue(cur >= prev - 1e-9, "profile not monotonic at h=" + h);
            prev = cur;
        }
    }

    @Test
    void speedNonNegative() {
        WindModel m = new WindModel(7L);
        WorldContext ctx = new WorldContext(7L, 0L, 64, 0.0, SurfaceClass.GRASS);
        WindSample s = m.compute(ctx, 0, 65, 0);
        assertTrue(s.speed >= 0);
        assertTrue(s.gust > 0);
    }

    @Test
    void curlNoiseFinite() {
        CurlNoise cn = new CurlNoise(42L);
        Vec3 v = cn.sample(42L, 1.2, 3.4, 5.6, 100L);
        assertTrue(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z));
    }

    @Test
    void diurnalFactorRange() {
        for (long t = 0; t < 24000; t += 100) {
            double f = DiurnalCycle.factor(t);
            assertTrue(f >= 0.5 && f <= 1.25);
        }
    }

    @Test
    void surfaceClassZ0() {
        assertTrue(SurfaceClass.z0(SurfaceClass.WATER) < SurfaceClass.z0(SurfaceClass.FOREST));
    }
    @Test
    void gustRange() {
        OrnsteinUhlenbeck ou = new OrnsteinUhlenbeck(0.12, 0.0, 1.0, 1L);
        for (long t = 0; t < 1200; t += 10) {
            double g = ou.gust(1L, 5.0, 7.0, t);
            assertTrue(g >= 0.15 && g <= 3.0);
        }
    }

    @Test
    void pressureDirectionUnit() {
        PressureField pf = new PressureField(1L);
        Vec3 d = pf.direction(10, 20, 1000);
        double len = d.length();
        assertTrue(Math.abs(len - 1.0) < 1e-9);
    }

    @Test
    void stormIncreasesSpeed() {
        WindModel m = new WindModel(1L);
        WorldContext calm = new WorldContext(1L, 1000L, 64, 0.0, SurfaceClass.GRASS);
        WorldContext storm = new WorldContext(1L, 1000L, 64, 1.0, SurfaceClass.GRASS);
        WindSample a = m.compute(calm, 5, 70, 5);
        WindSample b = m.compute(storm, 5, 70, 5);
        assertTrue(b.speed >= a.speed);
    }
    @Test
    void tornadoFieldFinite() {
        TornadoField tf = new TornadoField(1L);
        Vec3 v = tf.sample(1L, 10, 70, 10, 1000L);
        assertTrue(Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z));
    }

    @Test
    void tornadoActiveCenterLifecycle() {
        TornadoField tf = new TornadoField(1L);
        // find a time with active tornado
        boolean found = false;
        for (long t = 0; t < 12000; t += 100) {
            if (tf.activeCenter(1L, t) != null) { found = true; break; }
        }
        assertTrue(found);
    }
}
