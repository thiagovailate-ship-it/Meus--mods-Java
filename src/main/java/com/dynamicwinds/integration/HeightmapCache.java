package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches surface height and surface class per 8x8 cell.
 * Persists across ticks for a given world/dimension; cleared when world is unloaded.
 *
 * <p>Uma celula e sempre representada pelo seu <i>centro</i>, tanto aqui quanto
 * no caminho direto do cliente em {@link Wind}, e o cache guarda exatamente o
 * numero que o modelo usaria - por isso o valor nao depende de qual ponto da
 * celula foi consultado primeiro, e cliente e servidor chegam ao mesmo
 * resultado.</p>
 *
 * <p>Somente o servidor toca neste cache. Ele pertence a thread do servidor, e
 * os mapas sao simples de proposito: um cliente lendo daqui seria uma corrida
 * entre threads, entao o mundo remoto le o mundo direto (ver
 * {@link Wind#surfaceHeightAt}).</p>
 */
public final class HeightmapCache {
    /** Teto de celulas guardadas, so como seguro contra crescimento sem fim. */
    private static final int MAX_CELLS = 16384;

    /** Altura de referencia usada para perguntar ao mundo se o chunk esta carregado. */
    private static final int LOADED_CHECK_Y = 64;

    private final World world;
    // key = (cx << 32) | (cz & 0xffffffffL)
    private final Map<Long, Integer> heights = new HashMap<>();
    private final Map<Long, Integer> surfaceClasses = new HashMap<>();

    public HeightmapCache(World world) { this.world = world; }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public int heightAt(int x, int z) {
        int cx = Wind.cellOf(x);
        int cz = Wind.cellOf(z);
        long k = key(cx, cz);
        Integer h = heights.get(k);
        if (h == null) {
            h = computeHeight(Wind.cellCenter(cx), Wind.cellCenter(cz));
            heights.put(k, h);
        } else {
            PerfTracker.recordHeightHit();
        }
        return h;
    }

    public int surfaceClassAt(int x, int z) {
        int cx = Wind.cellOf(x);
        int cz = Wind.cellOf(z);
        long k = key(cx, cz);
        Integer s = surfaceClasses.get(k);
        if (s == null) {
            s = Wind.surfaceClassFor(world.getBiomeGenForCoords(Wind.cellCenter(cx),
                    Wind.cellCenter(cz)));
            surfaceClasses.put(k, s);
        }
        return s;
    }

    private int computeHeight(int x, int z) {
        PerfTracker.recordHeightMiss();
        // nao forca carregamento de chunk: getHeightValue e a altura pelo mapa
        // de alturas do chunk, nao uma varredura de blocos
        return world.getHeightValue(x, z);
    }

    /**
     * Recalcula as celulas de relevo dentro do raio e devolve quantas foram
     * tocadas.
     *
     * <p>O refresh e <i>incremental</i>: ele substitui so as celulas do
     * retangulo. A versao anterior calculava os limites do retangulo e em
     * seguida limpava o cache inteiro - os limites eram codigo morto e todo o
     * trabalho feito no resto do mundo era descartado a cada dois segundos, o
     * que fazia cada amostra seguinte pagar a leitura do mundo de novo.</p>
     *
     * <p>Celulas de chunks nao carregados sao puladas: recalcular ali custaria
     * carregar chunk, e quem consultar a celula depois recebe o valor calculado
     * na hora.</p>
     *
     * @param px coordenada X do centro do refresh
     * @param pz coordenada Z do centro do refresh
     * @param radius raio do refresh, em blocos
     * @return numero de celulas efetivamente recalculadas
     */
    public int refreshNear(double px, double pz, int radius) {
        int minCx = Wind.cellOf((int) Math.floor(px - radius));
        int maxCx = Wind.cellOf((int) Math.floor(px + radius));
        int minCz = Wind.cellOf((int) Math.floor(pz - radius));
        int maxCz = Wind.cellOf((int) Math.floor(pz + radius));
        int touched = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                int x = Wind.cellCenter(cx);
                int z = Wind.cellCenter(cz);
                if (!world.blockExists(x, LOADED_CHECK_Y, z)) continue;
                long k = key(cx, cz);
                heights.put(k, computeHeight(x, z));
                surfaceClasses.put(k, Wind.surfaceClassFor(world.getBiomeGenForCoords(x, z)));
                touched++;
            }
        }
        if (heights.size() > MAX_CELLS) {
            heights.clear();
            surfaceClasses.clear();
        }
        return touched;
    }
}
