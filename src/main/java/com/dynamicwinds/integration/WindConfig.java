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

    private WindConfig() {}

    public static void load(File file) {
        cfg = new Configuration(file);
        Configuration cfg = WindConfig.cfg;
        cfg.load();
        enableParticles = cfg.getBoolean("enableParticles", "client", true, "Enable wind particles");
        enablePush = cfg.getBoolean("enablePush", "gameplay", true, "Enable entity/item push");
        enableAudio = cfg.getBoolean("enableAudio", "client", true, "Enable wind audio");
        baseSpeedMultiplier = cfg.getFloat("baseSpeedMultiplier", "general", 1.0f, 0.0f, 5.0f, "Base speed multiplier");
        debugHud = cfg.getBoolean("debugHud", "client", false, "Show debug HUD");
        cfg.save();
    }
}
