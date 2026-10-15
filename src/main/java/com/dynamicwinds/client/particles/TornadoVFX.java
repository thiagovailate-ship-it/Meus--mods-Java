package com.dynamicwinds.client.particles;

import com.dynamicwinds.core.TornadoField;
import net.minecraft.client.Minecraft;

import java.util.Random;

public final class TornadoVFX {
    private static final Random RND = new Random();
    private static final TornadoField FIELD = new TornadoField(0L);
    private TornadoVFX() {}

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        long seed = mc.theWorld.getSeed();
        long time = mc.theWorld.getTotalWorldTime();
        TornadoField tf = new TornadoField(seed);
        double[] c = tf.activeCenter(seed, time);
        if (c == null) return;
        // only spawn if near player
        double dist = Math.hypot(c[0]-mc.thePlayer.posX, c[2]-mc.thePlayer.posZ);
        if (dist > 120) return;
        int n = 8;
        for (int i = 0; i < n; i++) {
            double ang = (2*Math.PI*i)/n + RND.nextDouble()*0.3;
            double rad = 2 + RND.nextDouble()*6;
            double x = c[0] + Math.cos(ang)*rad;
            double z = c[2] + Math.sin(ang)*rad;
            double y = c[1] + RND.nextDouble()*20;
            TornadoParticle p = new TornadoParticle(mc.theWorld, x, y, z, rad, ang);
            mc.effectRenderer.addEffect(p);
        }
        // debris
        if (RND.nextDouble() < 0.4) {
            double ang = RND.nextDouble()*2*Math.PI;
            double rad = RND.nextDouble()*15;
            double x = c[0] + Math.cos(ang)*rad;
            double z = c[2] + Math.sin(ang)*rad;
            DebrisParticle dp = new DebrisParticle(mc.theWorld, x, c[1]+RND.nextDouble()*10, z);
            mc.effectRenderer.addEffect(dp);
        }
    }
}
