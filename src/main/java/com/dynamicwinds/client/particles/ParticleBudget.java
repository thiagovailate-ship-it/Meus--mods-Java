package com.dynamicwinds.client.particles;

import com.dynamicwinds.integration.PerfTracker;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Per-client-tick budget with reserved capacity for storm rain. */
public final class ParticleBudget {
    private static long tick = Long.MIN_VALUE;
    private static int accepted;
    private static int lowPriority;
    private static World lastWorld;
    private static final Set<EntityFX> ACTIVE = new HashSet<EntityFX>();

    private ParticleBudget() {}

    public static void beginTick() {
        Minecraft mc = Minecraft.getMinecraft();
        World currentWorld = mc.theWorld;
        if (currentWorld != lastWorld) {
            ACTIVE.clear();
            accepted = 0;
            lowPriority = 0;
            lastWorld = currentWorld;
            tick = Long.MIN_VALUE;
            PerfTracker.recordLiveParticles(0);
        }
        long current = currentWorld == null ? tick + 1L : currentWorld.getTotalWorldTime();
        if (current != tick) {
            tick = current;
            accepted = 0;
            lowPriority = 0;
            Iterator<EntityFX> it = ACTIVE.iterator();
            while (it.hasNext()) if (it.next().isDead) it.remove();
            PerfTracker.recordLiveParticles(ACTIVE.size());
        }
    }

    public static int activeCount() { return ACTIVE.size(); }

    public static void reset() {
        tick = Long.MIN_VALUE;
        accepted = 0;
        lowPriority = 0;
        lastWorld = null;
        ACTIVE.clear();
        PerfTracker.recordLiveParticles(0);
    }

    /** priority: 0 decorative, 1 storm debris/tornado, 2 spray, 3 rain (reserved). */
    public static void submit(Minecraft mc, EntityFX particle, int priority) {
        if (mc == null || mc.effectRenderer == null || particle == null) return;
        beginTick();
        int max = WindConfig.maxParticles;
        if (max <= 0) return;
        if (!allowed(priority, accepted, lowPriority, max)) return;
        mc.effectRenderer.addEffect(particle);
        ACTIVE.add(particle);
        accepted++;
        PerfTracker.recordLiveParticles(ACTIVE.size());
        if (priority <= 0) lowPriority++;
        PerfTracker.recordParticle();
    }

    static boolean allowed(int priority, int accepted, int lowPriority, int max) {
        if (max <= 0 || accepted >= max) return false;
        int lowLimit = Math.max(1, max / 5);
        int normalLimit = Math.max(lowLimit, max * 7 / 10);
        int debrisLimit = Math.max(normalLimit, max * 9 / 10);
        if (priority <= 0 && lowPriority >= lowLimit) return false;
        if (priority <= 1 && accepted >= normalLimit) return false;
        if (priority == 2 && accepted >= debrisLimit) return false;
        return true;
    }
}
