package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Spawns wind VFX particles around the player based on local wind speed.
 */
public final class WindVFX {
    private static final Random RND = new Random();
    private WindVFX() {}

    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!com.dynamicwinds.integration.WindConfig.enableParticles) return;
        World w = mc.theWorld;
        WindSample s = Wind.getWindAt(w, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (s.speed < 1.0) return;
        int count = (int) Math.min(4, s.speed / 2.0);
        for (int i = 0; i < count; i++) {
            double x = mc.thePlayer.posX + (RND.nextDouble() - 0.5) * 16;
            double y = mc.thePlayer.posY + RND.nextDouble() * 6;
            double z = mc.thePlayer.posZ + (RND.nextDouble() - 0.5) * 16;
            EntityFXLike.spawn(w, x, y, z, s);
        }
    }

    // lightweight dispatch
    static class EntityFXLike {
        static void spawn(World w, double x, double y, double z, WindSample s) {
            net.minecraft.client.particle.EntityFX fx;
            double r = Math.random();
            if (r < 0.35) fx = new LeafParticle(w, x, y, z);
            else if (r < 0.6) fx = new PollenParticle(w, x, y, z);
            else if (r < 0.85) fx = new DustParticle(w, x, y, z);
            else if (r < 0.95) fx = new SwirlParticle(w, x, y, z);
            else fx = new GustParticle(w, x, y, z);
            Minecraft.getMinecraft().effectRenderer.addEffect(fx);
        }
    }
}
