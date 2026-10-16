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

    // tempo severo (vortices, tempestades tropicais e furacões)
    public static boolean enableStorms = true;
    public static double stormSpawnChance = 0.35;
    public static boolean northernHemisphere = true;
    public static boolean stormForceWeather = true;
    public static boolean stormLightning = true;
    /** Distancia maxima de renderizacao das nuvens da tempestade, em blocos. */
    public static double stormMaxViewDistance = 420.0;
    /** Chuva inclinada pelo vento, em particulas por tick. */
    public static int stormRainRate = 16;

    // destruicao causada por tempestades
    public static boolean enableDestruction = true;
    public static int destructionBlocksPerTick = 24;
    public static double destructionRadiusScale = 1.0;
    public static boolean destructionDropItems = false;
    public static boolean destructionProtectSpawn = true;

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

        enableStorms = cfg.getBoolean("enableStorms", "storms",
                true, "Gerar vórtices/tempestades/furacões naturalmente pelo mundo");
        Property pChance = cfg.get("storms", "stormSpawnChance", 0.35,
                "Chance de um sistema nascer por janela de ~20 min (0 = nenhum)");
        double chance = pChance.getDouble();
        if (chance < 0.0 || chance > 1.0) pChance.set(0.35);
        stormSpawnChance = pChance.getDouble();
        northernHemisphere = cfg.getBoolean("northernHemisphere", "storms", true,
                "Hemisferio norte: define o sentido de rotação dos ciclones");
        stormForceWeather = cfg.getBoolean("stormForceWeather", "storms", true,
                "Forçar chuva/trovoada enquanto um ciclone estiver ativo por perto");
        Property pView = cfg.get("storms", "stormMaxViewDistance", 420.0,
                "Distancia maxima, em blocos, para desenhar as nuvens da tempestade");
        if (pView.getDouble() < 64.0 || pView.getDouble() > 2048.0) pView.set(420.0);
        stormMaxViewDistance = pView.getDouble();
        stormRainRate = cfg.getInt("stormRainRate", "storms", 16, 0, 64,
                "Particulas de chuva inclinada por tick dentro de uma tempestade");
        stormLightning = cfg.getBoolean("stormLightning", "storms", true,
                "Relâmpagos na parede do olho dos furacões");

        enableDestruction = cfg.getBoolean("enableDestruction", "destruction", true,
                "Permitir que tempestades destruam blocos");
        destructionBlocksPerTick = cfg.getInt("destructionBlocksPerTick", "destruction",
                24, 0, 256, "Teto de blocos quebrados por tick (protege o servidor)");
        Property pScale = cfg.get("destruction", "destructionRadiusScale", 1.0,
                "Multiplicador do raio de destruição (0.5 = metade)");
        double scale = pScale.getDouble();
        if (scale < 0.1 || scale > 3.0) pScale.set(1.0);
        destructionRadiusScale = pScale.getDouble();
        destructionDropItems = cfg.getBoolean("destructionDropItems", "destruction", false,
                "Soltar itens dos blocos destruídos (desligado por padrão: evita sobrecarga)");
        destructionProtectSpawn = cfg.getBoolean("destructionProtectSpawn", "destruction", true,
                "Proteger a área de spawn do mundo contra destruição");
        cfg.save();
    }

    public static boolean stormsEnabled() { return enableStorms; }
    public static double stormSpawnChance() { return stormSpawnChance; }
    public static boolean north() { return northernHemisphere; }

    public static int tornadoParticleRate() { return tornadoParticleRate; }
    public static int tornadoDebrisRate() { return tornadoDebrisRate; }
    public static double tornadoMaxViewDistance() { return tornadoMaxViewDistance; }

    /** Distancia de renderizacao das nuvens de tempestade, em blocos. */
    public static double stormMaxViewDistance() { return stormMaxViewDistance; }

    /** Chuva inclinada pelo vento, particulas por tick. */
    public static int stormRainRate() { return stormRainRate; }
}
