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
     * Um modelo por seed/dimensao/lado. O mapa e concorrente porque em
     * single-player o servidor integrado e o cliente rodam em threads diferentes
     * e ambos consultam o vento com autoridade e snapshots distintos.
     */
    private static final java.util.concurrent.ConcurrentMap<String, WindModel> MODELS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Integer, Long> SYNCED_SEEDS =
            new java.util.concurrent.ConcurrentHashMap<Integer, Long>();

    /**
     * Lado da celula de relevo, em blocos. O vento le o solo <i>pelo centro da
     * celula</i>, e nao pela coluna exata do ponto consultado: assim o valor e
     * sempre o mesmo em qualquer ponto da celula, e o cache do servidor pode
     * guardar exatamente o numero que o modelo usaria.
     */
    public static final int SURFACE_CELL = 8;

    private Wind() {}

    /**
     * Fornecedor de relevo. A camada de integracao registra um para que as
     * consultas do servidor venham do cache de celulas em vez de irem ao mundo
     * a cada amostra - o empurrao de entidades e a varredura de destruicao
     * chamam isto centenas de vezes por tick.
     */
    public interface SurfaceProvider {
        /**
         * Altura do relevo na celula que contem a coluna dada.
         *
         * @param world mundo consultado
         * @param x coordenada X do bloco
         * @param z coordenada Z do bloco
         * @return altura considerada pelo modelo
         */
        int heightAt(World world, int x, int z);

        /**
         * Classe de superficie (agua, areia, floresta...) na celula dada.
         *
         * @param world mundo consultado
         * @param x coordenada X do bloco
         * @param z coordenada Z do bloco
         * @return identificador da classe de superficie
         */
        int surfaceClassAt(World world, int x, int z);
    }

    private static volatile SurfaceProvider surfaceProvider;

    /**
     * Registra o fornecedor de relevo do servidor.
     *
     * @param provider fornecedor, ou nulo para voltar a ler o mundo direto
     */
    public static void setSurfaceProvider(SurfaceProvider provider) {
        surfaceProvider = provider;
    }

    /**
     * Indice da celula de relevo que contem a coordenada dada.
     *
     * @param coord coordenada de bloco
     * @return indice da celula
     */
    public static int cellOf(int coord) {
        return Math.floorDiv(coord, SURFACE_CELL);
    }

    /**
     * Coordenada do centro da celula de relevo.
     *
     * @param cell indice da celula
     * @return coordenada de bloco do centro da celula
     */
    public static int cellCenter(int cell) {
        return cell * SURFACE_CELL + SURFACE_CELL / 2;
    }

    /**
     * Altura do relevo que o modelo de vento usa neste ponto.
     *
     * <p>No cliente (mundo remoto) a leitura vai direto ao mundo, pelo centro
     * da celula: o cache do servidor pertence a outra thread e nao pode ser
     * lido daqui. Como a celula e a mesma dos dois lados, servidor e cliente
     * chegam ao mesmo numero.</p>
     *
     * @param world mundo consultado
     * @param x coordenada X do bloco
     * @param z coordenada Z do bloco
     * @return altura do relevo considerada pelo modelo
     */
    public static int surfaceHeightAt(World world, int x, int z) {
        SurfaceProvider provider = surfaceProvider;
        if (provider != null && !world.isRemote) {
            return provider.heightAt(world, x, z);
        }
        return world.getHeightValue(cellCenter(cellOf(x)), cellCenter(cellOf(z)));
    }

    /**
     * Classe de superficie que o modelo de vento usa neste ponto.
     *
     * @param world mundo consultado
     * @param x coordenada X do bloco
     * @param z coordenada Z do bloco
     * @return identificador da classe de superficie
     */
    public static int surfaceClassAt(World world, int x, int z) {
        SurfaceProvider provider = surfaceProvider;
        if (provider != null && !world.isRemote) {
            return provider.surfaceClassAt(world, x, z);
        }
        return surfaceClassFor(world.getBiomeGenForCoords(cellCenter(cellOf(x)),
                cellCenter(cellOf(z))));
    }

    public static WindSample getWindAt(World world, double x, double y, double z) {
        long time = world.getTotalWorldTime();
        return getWindAtTime(world, x, y, z, time, weatherStormAt(world, time));
    }

    /** Deterministic sample at an explicit tick/weather state, used by parity diagnostics. */
    public static WindSample getWindAtTime(World world, double x, double y, double z,
                                           long time, double storm) {
        com.dynamicwinds.integration.PerfTracker.recordWindSample();
        long seed = seedFor(world);
        WindModel model = modelFor(world);
        int sx = (int) Math.floor(x);
        int sz = (int) Math.floor(z);
        int surfaceY = surfaceHeightAt(world, sx, sz);
        int surfaceClass = surfaceClassAt(world, sx, sz);
        WorldContext ctx = new WorldContext(seed, time, surfaceY, storm, surfaceClass,
                WindConfig.baseSpeedMultiplier);
        return model.compute(ctx, x, y, z, new BiomeRoughness(world));
    }

    /** Same interpolated 0..1 weather scalar consumed by the field model. */
    public static double weatherStormAt(World world, long time) {
        if (world.isThundering()) return 1.0;
        if (!world.isRaining()) return 0.0;
        float fraction = (time % 20L) / 20.0f;
        float strength = world.prevRainingStrength
                + (world.rainingStrength - world.prevRainingStrength) * fraction;
        return strength > 0.05f ? strength : 0.0;
    }

    /**
     * Modelo de vento deste mundo (seed, dimensão e lado), criado com a
     * configuração local ou autoritativa sincronizada do servidor.
     */
    public static void setSyncedSeed(int dimensionId, long seed) {
        Long previous = SYNCED_SEEDS.put(Integer.valueOf(dimensionId), Long.valueOf(seed));
        if (previous != null && previous.longValue() == seed) return;
        // Evict only this dimension. Repeated world-sync packets must not erase
        // other dimensions' dynamic storm state.
        String prefix = dimensionId + ":";
        for (String key : MODELS.keySet()) {
            if (key.startsWith(prefix)) MODELS.remove(key);
        }
    }

    public static void invalidateModels() {
        MODELS.clear();
    }

    public static void clearSyncedWorldState() {
        SYNCED_SEEDS.clear();
        MODELS.clear();
    }

    private static long seedFor(World world) {
        Long synced = SYNCED_SEEDS.get(Integer.valueOf(world.provider.dimensionId));
        return synced == null ? world.getSeed() : synced.longValue();
    }

    public static WindModel modelFor(World world) {
        long seed = seedFor(world);
        int dimension = world.provider.dimensionId;
        String key = dimension + ":" + seed + (world.isRemote ? ":client" : ":server");
        WindModel existing = MODELS.get(key);
        if (existing != null) {
            existing.storms().setAuthoritative(!world.isRemote);
            return existing;
        }
        double chance = WindConfig.stormsEnabled()
                && WindConfig.stormsAllowedInDimension(dimension)
                ? WindConfig.stormSpawnChance() : 0.0;
        WindModel created = new WindModel(seed, WindConfig.north(), chance);
        created.storms().setAuthoritative(!world.isRemote);
        WindModel raced = MODELS.putIfAbsent(key, created);
        WindModel result = raced == null ? created : raced;
        result.storms().setAuthoritative(!world.isRemote);
        return result;
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
