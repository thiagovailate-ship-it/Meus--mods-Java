package com.dynamicwinds.integration;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public final class WindConfig {
    public static Configuration cfg;
    public static boolean enableParticles = true;
    public static boolean enablePush = true;
    public static boolean enableAudio = true;
    public static double baseSpeedMultiplier = 1.0;
    public static boolean debugHud = false;

    // tornado VFX
    public static int tornadoParticleRate = 8;
    public static int tornadoDebrisRate = 1;
    public static double tornadoMaxViewDistance = 120.0;

    private WindConfig() {}

    public static void load(File file) {
        cfg = new Configuration(file);
        Configuration cfg = WindConfig.cfg;
        cfg.load();
        enableParticles = cfg.getBoolean("enableParticles", "client", true, "Enable wind particles");
        enablePush = cfg.getBoolean("enablePush", "gameplay", true, "Enable entity/item push");
        enableAudio = cfg.getBoolean("enableAudio", "client", true, "Enable wind audio");
        Property pSpeed = cfg.get("general", "baseSpeedMultiplier", 1.0, "Base speed multiplier");
        if (pSpeed.getDouble() < 0.0 || pSpeed.getDouble() > 5.0) pSpeed.set(1.0);
        baseSpeedMultiplier = pSpeed.getDouble();
        debugHud = cfg.getBoolean("debugHud", "client", false, "Show debug HUD");
        tornadoParticleRate = cfg.getInt("tornadoParticleRate", "tornado", 8, 1, 32, "Particles per tick near tornado");
        tornadoDebrisRate = cfg.getInt("tornadoDebrisRate", "tornado", 1, 0, 4, "Debris spawn chance multiplier");
        Property pDist = cfg.get("tornado", "tornadoMaxViewDistance", 120.0, "Max distance to render tornado");
        double d = pDist.getDouble();
        if (d < 20.0 || d > 300.0) pDist.set(120.0);
        tornadoMaxViewDistance = pDist.getDouble();
        cfg.save();
    }

    public static int tornadoParticleRate() { return tornadoParticleRate; }
    public static int tornadoDebrisRate() { return tornadoDebrisRate; }
    public static double tornadoMaxViewDistance() { return tornadoMaxViewDistance; }
}
