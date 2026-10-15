package com.dynamicwinds.client.particles;

import com.dynamicwinds.core.TornadoField;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class TornadoVFX {
    private static final Random RND = new Random();
    // cached fields per world seed to avoid per-tick allocation
    private static final Map<Long, TornadoField> FIELDS = new HashMap<>();

    private TornadoVFX() {}

    public static void tick() {
        if (!WindConfig.enableParticles) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        long seed = mc.theWorld.getSeed();
        long time = mc.theWorld.getTotalWorldTime();

        TornadoField tf = FIELDS.computeIfAbsent(seed, TornadoField::new);
        double[] c = tf.activeCenter(seed, time);
        if (c == null) return;

        double dist = Math.hypot(c[0] - mc.thePlayer.posX, c[2] - mc.thePlayer.posZ);
        double maxDist = WindConfig.tornadoMaxViewDistance();
        if (dist > maxDist) return;

        int baseRate = WindConfig.tornadoParticleRate();
        int rate = (int) (baseRate * Math.max(0.2, 1.0 - dist / maxDist));
        rate = Math.max(1, Math.min(rate, 24));

        for (int i = 0; i < rate; i++) {
            double ang = (2 * Math.PI * i) / rate + RND.nextDouble() * 0.3;
            double rad = 2 + RND.nextDouble() * 6;
            double x = c[0] + Math.cos(ang) * rad;
            double z = c[2] + Math.sin(ang) * rad;
            double y = c[1] + RND.nextDouble() * 20;
            TornadoParticle p = new TornadoParticle(mc.theWorld, x, y, z, rad, ang);
            mc.effectRenderer.addEffect(p);
        }

        if (RND.nextDouble() < 0.15 * WindConfig.tornadoDebrisRate()) {
            double ang = RND.nextDouble() * 2 * Math.PI;
            double rad = RND.nextDouble() * 15;
            double x = c[0] + Math.cos(ang) * rad;
            double z = c[2] + Math.sin(ang) * rad;
            DebrisParticle dp = new DebrisParticle(mc.theWorld, x, c[1] + RND.nextDouble() * 10, z);
            mc.effectRenderer.addEffect(dp);
        }
    }
}
