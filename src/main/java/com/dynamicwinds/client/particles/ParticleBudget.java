package com.dynamicwinds.client.particles;

import com.dynamicwinds.integration.PerfTracker;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;

/** Per-client-tick budget with reserved capacity for storm rain. */
public final class ParticleBudget {
    private static long tick = Long.MIN_VALUE;
    private static int accepted;
    private static int lowPriority;

    private ParticleBudget() {}

    public static void beginTick() {
        Minecraft mc = Minecraft.getMinecraft();
        long current = mc.theWorld == null ? tick + 1L : mc.theWorld.getTotalWorldTime();
        if (current != tick) {
            tick = current;
            accepted = 0;
            lowPriority = 0;
        }
    }

    /** priority: 0 decorative, 1 storm debris/tornado, 2 spray, 3 rain (reserved). */
    public static void submit(Minecraft mc, EntityFX particle, int priority) {
        if (mc == null || mc.effectRenderer == null || particle == null) return;
        beginTick();
        int max = WindConfig.maxParticles;
        if (max <= 0) return;
        int lowLimit = Math.max(1, max / 5);
        int normalLimit = Math.max(lowLimit, max * 7 / 10);
        int debrisLimit = Math.max(normalLimit, max * 9 / 10);
        if (priority <= 0 && lowPriority >= lowLimit) return;
        if (priority <= 1 && accepted >= normalLimit) return;
        if (priority == 2 && accepted >= debrisLimit) return;
        if (accepted >= max) return;
        mc.effectRenderer.addEffect(particle);
        accepted++;
        if (priority <= 0) lowPriority++;
        PerfTracker.recordParticle();
    }
}
