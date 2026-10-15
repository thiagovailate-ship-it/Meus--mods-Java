package com.dynamicwinds.core;

/**
 * Diurnal (day/night) modulation factor. timeTicks is world time.
 */
public final class DiurnalCycle {
    private DiurnalCycle() {}

    public static double factor(long timeTicks) {
        // Minecraft day = 24000 ticks
        double phase = (timeTicks % 24000L) / 24000.0 * 2.0 * Math.PI;
        // peak midday, calmer at night
        double f = 1.0 + 0.25 * Math.sin(phase - Math.PI / 2.0);
        if (f < 0.5) f = 0.5;
        return f;
    }
}
