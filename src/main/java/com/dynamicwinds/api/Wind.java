package com.dynamicwinds.api;

import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.SurfaceClass;
import com.dynamicwinds.core.SurfaceRoughness;
import com.dynamicwinds.core.WindModel;
import com.dynamicwinds.core.WindSample;
import com.dynamicwinds.core.WorldContext;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Public facade for other mods and internal features.
 */
public final class Wind {
    /**
     * Um modelo por seed de mundo. O mapa e concorrente porque em single-player
     * o servidor integrado e o cliente rodam em threads diferentes e ambos
     * consultam o vento.
     */
    private static final java.util.Map<Long, WindModel> MODELS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Wind() {}

    public static WindSample getWindAt(World world, double x, double y, double z) {
        long seed = world.getSeed();
        WindModel model = modelFor(world);
        int sx = (int) Math.floor(x);
        int sz = (int) Math.floor(z);
        int surfaceY = world.getHeightValue(sx, sz);
        int surfaceClass = surfaceClassFor(world.getBiomeGenForCoords(sx, sz));
        // storm intensity from world weather state (0..1)
        double storm = 0.0;
        if (world.isThundering()) {
            storm = 1.0;
        } else if (world.isRaining()) {
            // interpolate rain strength for smoothness
            float rs = world.rainingStrength;
            float prs = world.prevRainingStrength;
            float frac = (world.getTotalWorldTime() % 20) / 20.0f;
            float cur = prs + (rs - prs) * frac;
            storm = (cur > 0.05) ? (cur / 1.0f) : 0.0;
        }
        WorldContext ctx = new WorldContext(seed, world.getTotalWorldTime(), surfaceY, storm, surfaceClass, WindConfig.baseSpeedMultiplier);
        return model.compute(ctx, x, y, z, new BiomeRoughness(world));
    }

    /**
     * Modelo de vento deste mundo (um por seed), criado com a configuracao atual
     * de hemisferio e chance de geracao de tempestades.
     */
    public static WindModel modelFor(World world) {
        long seed = world.getSeed();
        WindModel existing = MODELS.get(Long.valueOf(seed));
        if (existing != null) return existing;
        double chance = WindConfig.stormsEnabled() ? WindConfig.stormSpawnChance() : 0.0;
        WindModel created = new WindModel(seed, WindConfig.north(), chance);
        MODELS.put(Long.valueOf(seed), created);
        return created;
    }

    /**
     * Sistemas de tempo severo do mundo (naturais + invocados). O VFX e os
     * comandos usam esta mesma instancia que o modelo de vento, para que o
     * desenho e a fisica nunca divirjam.
     */
    public static StormField stormsFor(World world) {
        return modelFor(world).storms();
    }

    /**
     * Fator de energia da superficie deste mundo, derivado do bioma.
     *
     * <p>Pedido pela camada de integracao (arrasto de entidades, destruicao,
     * clima) para consultar o campo de tempestade exatamente com a mesma
     * energia que o modelo principal usa - se cada chamador passasse o seu
     * proprio fator, o vento "da fisica" e o vento "do dano" divergiriam.</p>
     *
     * @param world mundo consultado
     * @return fator de energia baseado em bioma (1.0 sobre oceano)
     */
    public static SurfaceRoughness surfaceEnergyFor(World world) {
        return new BiomeRoughness(world);
    }

    public static int surfaceClassFor(BiomeGenBase biome) {
        if (biome == null) return SurfaceClass.GRASS;
        String name = biome.biomeName.toLowerCase();
        if (name.contains("ocean") || name.contains("river")) return SurfaceClass.WATER;
        if (name.contains("desert")) return SurfaceClass.SAND;
        if (name.contains("forest") || name.contains("jungle") || name.contains("taiga")) return SurfaceClass.FOREST;
        if (name.contains("mountain") || name.contains("hills")) return SurfaceClass.STONE;
        return SurfaceClass.GRASS;
    }

    /**
     * Energia que a superficie fornece a um sistema de tempestade.
     *
     * <p>Um ciclone tropical e uma maquina termica que retira calor da agua
     * quente: sobre o oceano ele se mantem, sobre continente ele perde forca por
     * atrito e por falta de umidade. Este e o unico ponto do modelo que depende
     * do bioma, e por isso ele vive na camada de integracao e nao no nucleo de
     * fisica.</p>
     *
     * <p>Oceano aberto devolve 1.0; continente devolve de 0.35 a 0.85 conforme o
     * tipo de superficie.</p>
     */
    private static final class BiomeRoughness implements SurfaceRoughness {
        private final World world;

        BiomeRoughness(World world) {
            this.world = world;
        }

        @Override
        public double energyAt(double x, double z) {
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            BiomeGenBase biome;
            try {
                biome = world.getBiomeGenForCoords(bx, bz);
            } catch (Throwable t) {
                return 0.65;
            }
            if (biome == null) return 0.65;
            String name = biome.biomeName.toLowerCase();
            if (name.contains("ocean") || name.contains("river") || name.contains("beach")
                    || name.contains("water")) {
                return 1.0;
            }
            if (name.contains("ice") || name.contains("frozen") || name.contains("tundra")
                    || name.contains("cold")) {
                return 0.35;   // agua fria nao sustenta ciclone tropical
            }
            if (name.contains("desert") || name.contains("savanna") || name.contains("mesa")) {
                return 0.85;   // quente, mas sem umidade
            }
            if (name.contains("forest") || name.contains("jungle") || name.contains("taiga")) {
                return 0.5;    // atrito alto da vegetacao
            }
            if (name.contains("mountain") || name.contains("hills") || name.contains("stone")) {
                return 0.4;    // atrito de terreno alto
            }
            return 0.65;
        }
    }
}
