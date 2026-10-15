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
        baseSpeedMultiplier = cfg.getDouble("baseSpeedMultiplier", "general", 1.0, 0.0, 5.0, "Base speed multiplier");
        debugHud = cfg.getBoolean("debugHud", "client", false, "Show debug HUD");
        tornadoParticleRate = cfg.getInt("tornadoParticleRate", "tornado", 8, 1, 32, "Particles per tick near tornado");
        tornadoDebrisRate = cfg.getInt("tornadoDebrisRate", "tornado", 1, 0, 4, "Debris spawn chance multiplier");
        tornadoMaxViewDistance = cfg.getDouble("tornadoMaxViewDistance", "tornado", 120.0, 20.0, 300.0, "Max distance to render tornado");
        cfg.save();
    }

    public static int tornadoParticleRate() { return tornadoParticleRate; }
    public static int tornadoDebrisRate() { return tornadoDebrisRate; }
    public static double tornadoMaxViewDistance() { return tornadoMaxViewDistance; }
}
