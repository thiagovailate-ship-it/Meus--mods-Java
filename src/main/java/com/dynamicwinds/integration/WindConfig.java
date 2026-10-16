package com.dynamicwinds.integration;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public final class WindConfig {
    public static Configuration cfg;
    private static File configFile;
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
    /** Desenhar nuvem, parede do olho e bandas de chuva das tempestades. */
    public static boolean stormRenderSky = true;
    /** Distancia maxima de renderizacao das nuvens da tempestade, em blocos. */
    public static double stormMaxViewDistance = 420.0;
    /** Chuva inclinada pelo vento, em particulas por tick. */
    public static int stormRainRate = 16;
    public static int maxParticles = 400;
    public static int[] stormsInDimensions = new int[]{0};

    // destruicao causada por tempestades
    public static boolean enableDestruction = true;
    public static int destructionBlocksPerTick = 24;
    public static double destructionRadiusScale = 1.0;
    public static boolean destructionDropItems = false;
    public static boolean destructionProtectSpawn = true;
    public static int destructionMaxPerStorm = 60;
    public static boolean destructionWarnOnly = false;
    public static String[] destructionProtectedBlocks = new String[]{"minecraft:bedrock", "minecraft:obsidian"};
    public static int[] destructionProtectedDimensions = new int[0];

    private WindConfig() {}

    public static void load(File file) {
        configFile = file;
        cfg = new Configuration(file);
        Configuration cfg = WindConfig.cfg;
        cfg.load();
        cfg.addCustomCategoryComment("client", "Client rendering and ambient effects; cosmetic options unless noted.");
        cfg.addCustomCategoryComment("gameplay", "Wind interactions with entities and the world.");
        cfg.addCustomCategoryComment("general", "Global wind simulation parameters.");
        cfg.addCustomCategoryComment("tornado", "Tornado particle and visibility budgets.");
        cfg.addCustomCategoryComment("storms", "Severe storm simulation, weather, and rendering.");
        cfg.addCustomCategoryComment("destruction", "Server-side block damage controls; review before enabling on multiplayer servers.");
        cfg.addCustomCategoryComment("perf", "Global performance budgets.");
        enableParticles = cfg.getBoolean("enableParticles", "client", true, "Enable wind particles");
        enablePush = cfg.getBoolean("enablePush", "gameplay", true, "Enable entity/item push");
        enableAudio = cfg.getBoolean("enableAudio", "client", true, "Enable wind audio");
        Property pSpeed = cfg.get("general", "baseSpeedMultiplier", 1.0, "Base speed multiplier");
        baseSpeedMultiplier = boundedDouble("general.baseSpeedMultiplier", pSpeed, 0.0, 5.0, 1.0);
        debugHud = cfg.getBoolean("debugHud", "client", false, "Show debug HUD");
        tornadoParticleRate = boundedInt("tornado.tornadoParticleRate", cfg.get("tornado", "tornadoParticleRate", 8,
                "Particles per tick near tornado"), 1, 32, 8);
        tornadoDebrisRate = boundedInt("tornado.tornadoDebrisRate", cfg.get("tornado", "tornadoDebrisRate", 1,
                "Debris spawn chance multiplier"), 0, 4, 1);
        Property pDist = cfg.get("tornado", "tornadoMaxViewDistance", 120.0, "Max distance to render tornado");
        tornadoMaxViewDistance = boundedDouble("tornado.tornadoMaxViewDistance", pDist, 20.0, 300.0, 120.0);

        enableStorms = cfg.getBoolean("enableStorms", "storms",
                true, "Gerar vórtices/tempestades/furacões naturalmente pelo mundo");
        Property pChance = cfg.get("storms", "stormSpawnChance", 0.35,
                "Chance de um sistema nascer por janela de ~20 min (0 = nenhum)");
        stormSpawnChance = boundedDouble("storms.stormSpawnChance", pChance, 0.0, 1.0, 0.35);
        stormsInDimensions = parseDimensions(cfg.getStringList("stormsInDimensions", "storms",
                new String[]{"0"}, "Dimension IDs where natural storms may occur; default is Overworld only."));
        northernHemisphere = cfg.getBoolean("northernHemisphere", "storms", true,
                "Hemisferio norte: define o sentido de rotação dos ciclones");
        stormForceWeather = cfg.getBoolean("stormForceWeather", "storms", true,
                "Forçar chuva/trovoada enquanto um ciclone estiver ativo por perto");
        Property pView = cfg.get("storms", "stormMaxViewDistance", 420.0,
                "Distancia maxima, em blocos, para desenhar as nuvens da tempestade");
        stormMaxViewDistance = boundedDouble("storms.stormMaxViewDistance", pView, 64.0, 2048.0, 420.0);
        stormRainRate = boundedInt("storms.stormRainRate", cfg.get("storms", "stormRainRate", 16,
                "Particles of wind-driven rain per tick"), 0, 64, 16);
        stormRenderSky = cfg.getBoolean("stormRenderSky", "storms", true,
                "Desenhar a nuvem, a parede do olho e as bandas de chuva das tempestades");
        stormLightning = cfg.getBoolean("stormLightning", "storms", true,
                "Relâmpagos na parede do olho dos furacões");

        enableDestruction = cfg.getBoolean("enableDestruction", "destruction", true,
                "Permitir que tempestades destruam blocos");
        destructionBlocksPerTick = boundedInt("destruction.destructionBlocksPerTick", cfg.get("destruction", "destructionBlocksPerTick", 24,
                "Maximum blocks broken per tick"), 0, 256, 24);
        Property pScale = cfg.get("destruction", "destructionRadiusScale", 1.0,
                "Multiplicador do raio de destruição (0.5 = metade)");
        destructionRadiusScale = boundedDouble("destruction.destructionRadiusScale", pScale, 0.1, 3.0, 1.0);
        destructionDropItems = cfg.getBoolean("destructionDropItems", "destruction", false,
                "Soltar itens dos blocos destruídos (desligado por padrão: evita sobrecarga)");
        destructionProtectSpawn = cfg.getBoolean("destructionProtectSpawn", "destruction", true,
                "Proteger a área de spawn do mundo contra destruição");
        destructionMaxPerStorm = boundedInt("destruction.destructionMaxPerStorm",
                cfg.get("destruction", "destructionMaxPerStorm", 60, "Maximum blocks broken by one storm"),
                0, 10000, 60);
        destructionWarnOnly = cfg.getBoolean("destructionWarnOnly", "destruction", false,
                "Log blocks that would break without modifying them");
        destructionProtectedBlocks = cfg.getStringList("destructionProtectedBlocks", "destruction",
                destructionProtectedBlocks, "Registry names never broken, e.g. minecraft:obsidian");
        destructionProtectedDimensions = parseDimensions(cfg.getStringList(
                "destructionProtectedDimensions", "destruction", new String[0],
                "Dimension IDs where destruction is completely disabled."));
        maxParticles = boundedInt("perf.maxParticles", cfg.get("perf", "maxParticles", 400,
                "Maximum DynamicWinds particles accepted per client tick"), 50, 5000, 400);
        sanitize();
        cfg.save();
    }

    /** Revalidates numeric options after config load or GUI edits. */
    public static void sanitize() {
        if (cfg == null) return;
        baseSpeedMultiplier = boundedDouble("general.baseSpeedMultiplier",
                cfg.get("general", "baseSpeedMultiplier", 1.0), 0.0, 5.0, 1.0);
        tornadoParticleRate = boundedInt("tornado.tornadoParticleRate",
                cfg.get("tornado", "tornadoParticleRate", 8), 1, 32, 8);
        tornadoDebrisRate = boundedInt("tornado.tornadoDebrisRate",
                cfg.get("tornado", "tornadoDebrisRate", 1), 0, 4, 1);
        tornadoMaxViewDistance = boundedDouble("tornado.tornadoMaxViewDistance",
                cfg.get("tornado", "tornadoMaxViewDistance", 120.0), 20.0, 300.0, 120.0);
        stormSpawnChance = boundedDouble("storms.stormSpawnChance",
                cfg.get("storms", "stormSpawnChance", 0.35), 0.0, 1.0, 0.35);
        stormMaxViewDistance = boundedDouble("storms.stormMaxViewDistance",
                cfg.get("storms", "stormMaxViewDistance", 420.0), 64.0, 2048.0, 420.0);
        stormRainRate = boundedInt("storms.stormRainRate",
                cfg.get("storms", "stormRainRate", 16), 0, 64, 16);
        destructionBlocksPerTick = boundedInt("destruction.destructionBlocksPerTick",
                cfg.get("destruction", "destructionBlocksPerTick", 24), 0, 256, 24);
        destructionRadiusScale = boundedDouble("destruction.destructionRadiusScale",
                cfg.get("destruction", "destructionRadiusScale", 1.0), 0.1, 3.0, 1.0);
        destructionMaxPerStorm = boundedInt("destruction.destructionMaxPerStorm",
                cfg.get("destruction", "destructionMaxPerStorm", 60), 0, 10000, 60);
        maxParticles = boundedInt("perf.maxParticles", cfg.get("perf", "maxParticles", 400),
                50, 5000, 400);
    }

    public static void saveAndReload() {
        if (cfg == null || configFile == null) return;
        sanitize();
        cfg.save();
        load(configFile);
    }

    private static double boundedDouble(String key, Property property, double min,
                                         double max, double fallback) {
        double value = property.getDouble();
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            System.err.println("DynamicWinds: config " + key + "=" + value
                    + " out of range [" + min + ", " + max + " ]; reset to " + fallback);
            property.set(fallback);
            value = fallback;
        }
        return value;
    }

    private static int boundedInt(String key, Property property, int min, int max, int fallback) {
        int value = property.getInt();
        if (value < min || value > max) {
            System.err.println("DynamicWinds: config " + key + "=" + value
                    + " out of range [" + min + ", " + max + "]; reset to " + fallback);
            property.set(fallback);
            value = fallback;
        }
        return value;
    }

    private static int[] parseDimensions(String[] values) {
        java.util.ArrayList<Integer> parsed = new java.util.ArrayList<Integer>();
        for (String value : values) {
            try {
                int id = Integer.parseInt(value.trim());
                boolean duplicate = false;
                for (Integer existing : parsed) if (existing.intValue() == id) duplicate = true;
                if (!duplicate) parsed.add(Integer.valueOf(id));
            } catch (RuntimeException ex) {
                System.err.println("DynamicWinds: ignoring invalid stormsInDimensions entry: " + value);
            }
        }
        int[] result = new int[parsed.size()];
        for (int i = 0; i < result.length; i++) result[i] = parsed.get(i).intValue();
        return result;
    }

    public static boolean stormsAllowedInDimension(int dimensionId) {
        for (int allowed : stormsInDimensions) if (allowed == dimensionId) return true;
        return false;
    }

    public static boolean destructionProtectedDimension(int dimensionId) {
        for (int protectedId : destructionProtectedDimensions) if (protectedId == dimensionId) return true;
        return false;
    }

    public static boolean isProtectedBlock(String registryName) {
        for (String protectedName : destructionProtectedBlocks) {
            if (protectedName != null && protectedName.trim().equalsIgnoreCase(registryName)) return true;
        }
        return false;
    }

    public static void setProtectedBlocks(String[] names) {
        destructionProtectedBlocks = names;
        if (cfg != null) {
            cfg.get("destruction", "destructionProtectedBlocks", names,
                    "Registry names never broken, e.g. minecraft:obsidian").set(names);
            cfg.save();
        }
    }

    /** Applies the server's simulation settings on a remote client. */
    public static void applyServerSettings(boolean north, double spawnChance,
                                           boolean stormsEnabled, boolean forceWeather,
                                           int rainRate, boolean destructionEnabled,
                                           double baseSpeed) {
        double safeChance = Math.max(0.0, Math.min(1.0, spawnChance));
        int safeRainRate = Math.max(0, Math.min(64, rainRate));
        double safeBaseSpeed = Math.max(0.0, Math.min(5.0, baseSpeed));
        boolean changed = northernHemisphere != north || stormSpawnChance != safeChance
                || baseSpeedMultiplier != safeBaseSpeed
                || enableStorms != stormsEnabled || stormForceWeather != forceWeather
                || stormRainRate != safeRainRate || enableDestruction != destructionEnabled;
        northernHemisphere = north;
        stormSpawnChance = safeChance;
        baseSpeedMultiplier = safeBaseSpeed;
        enableStorms = stormsEnabled;
        stormForceWeather = forceWeather;
        stormRainRate = safeRainRate;
        enableDestruction = destructionEnabled;
        if (changed) com.dynamicwinds.api.Wind.invalidateModels();
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

    /** Desenhar o ceu da tempestade (nuvem, olho e bandas). */
    public static boolean stormRenderSky() { return stormRenderSky; }
}
