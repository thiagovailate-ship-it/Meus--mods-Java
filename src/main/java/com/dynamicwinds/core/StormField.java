package com.dynamicwinds.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conjunto de sistemas de tempo severo ativos em um mundo.
 *
 * <p>Duas origens coexistem:</p>
 * <ul>
 *   <li><b>Naturais</b> - derivados apenas da seed do mundo e do relogio. O
 *       tempo e dividido em janelas ({@link #SLOT_TICKS}) e cada janela pode
 *       gerar um sistema, com tipo, posicao, rumo e curvatura sorteados de forma
 *       determinista. Como a derivacao e pura, cliente e servidor concordam sem
 *       trocar pacotes.</li>
 *   <li><b>Invocados</b> - criados em tempo de execucao pelo Cajado da
 *       Catastrofe ou por comando. Estes <i>nao</i> podem ser derivados da seed,
 *       entao precisam ser transmitidos aos clientes (veja
 *       {@code StormNetwork}); o pacote leva apenas os parametros de nascimento,
 *       porque a evolucao e determinista a partir deles.</li>
 * </ul>
 *
 * <p>Os sistemas nascem distribuidos em uma regiao ao redor da origem do mundo
 * (a area onde o jogador costuma construir), e nao em cima do jogador: uma
 * tempestade e um evento do mundo, nao um efeito preso a camera. Para testar em
 * qualquer lugar existe o Cajado.</p>
 */
public final class StormField {

    /** Duracao de cada janela de geracao natural, em ticks (~20 min de jogo). */
    public static final long SLOT_TICKS = 24000L;
    /** Raio da regiao onde sistemas naturais nascem. */
    public static final double REGION_RADIUS = 1500.0;

    private static final int CHECK_BACK = 2;
    private static final int CHECK_AHEAD = 1;
    private static final int CACHE_LIMIT = 24;

    private static final double TWO_PI = Math.PI * 2.0;

    private final long seed;
    private final boolean north;
    private final double spawnChance;
    private final PerlinNoise noise;
    // Em single-player o servidor integrado e o cliente rodam em threads
    // diferentes e compartilham esta mesma instancia (o modelo e indexado pela
    // seed do mundo). As estruturas abaixo sao seguras para leitura concorrente:
    // o servidor escreve raramente (nasce/termina um sistema), mas o VFX le a
    // cada quadro, e uma lista comum daria ConcurrentModificationException.
    private final Map<Long, StormSystem> naturalCache = new ConcurrentHashMap<Long, StormSystem>();
    private final List<StormSystem> dynamic = new CopyOnWriteArrayList<StormSystem>();
    /**
     * IDs dinâmicos ocupam o espaço negativo; os IDs naturais são slots de
     * tempo (não-negativos para o tempo normal de jogo). Assim um sistema
     * invocado nunca colide com uma tempestade natural.
     */
    private final AtomicLong nextDynamicId = new AtomicLong(-1L);
    private volatile boolean authoritative = true;

    /** Hemisferio norte, com chance padrao de um sistema por janela. */
    public StormField(long seed) {
        this(seed, true, 0.35);
    }

    public StormField(long seed, boolean north, double spawnChance) {
        this.seed = seed;
        this.north = north;
        this.spawnChance = clamp01(spawnChance);
        this.noise = new PerlinNoise(seed + 424243L);
    }

    public long seed() { return seed; }

    public boolean north() { return north; }

    public double spawnChance() { return spawnChance; }

    /** Only the server derives natural systems; clients consume server snapshots. */
    public void setAuthoritative(boolean authoritative) {
        this.authoritative = authoritative;
    }

    public boolean isAuthoritative() { return authoritative; }

    // ------------------------------------------------------------------
    // consulta
    // ------------------------------------------------------------------

    /**
     * Sistemas ativos no instante dado, tratando a superficie como oceano.
     * Usado por VFX e por comandos, onde a precisao do landfall nao importa.
     */
    public List<StormSystem> activeAt(long time) {
        return activeAt(time, SurfaceRoughness.OPEN_WATER);
    }

    /** Sistemas ativos no instante dado (lista nova a cada chamada). */
    public List<StormSystem> activeAt(long time, SurfaceRoughness roughness) {
        List<StormSystem> out = new ArrayList<StormSystem>(2);
        long slot = Math.floorDiv(time, SLOT_TICKS);
        for (long i = slot - CHECK_BACK; i <= slot + CHECK_AHEAD; i++) {
            StormSystem s = natural(i);
            if (s != null && s.isAlive(time) && !hasDynamicId(s.id)) out.add(s);
        }
        for (int i = 0; i < dynamic.size(); i++) {
            StormSystem s = dynamic.get(i);
            if (s.isAlive(time)) out.add(s);
        }
        return out;
    }

    /**
     * Soma o vento de todos os sistemas ativos no ponto dado.
     *
     * <p>Nao aloca lista: e o caminho quente (chamado por entidade, por tick).</p>
     */
    public Vec3 sampleWind(long time, double x, double y, double z, int surfaceY,
                           SurfaceRoughness roughness) {
        Vec3 sum = Vec3.ZERO;
        long slot = Math.floorDiv(time, SLOT_TICKS);
        for (long i = slot - CHECK_BACK; i <= slot + CHECK_AHEAD; i++) {
            StormSystem s = natural(i);
            if (s == null || !s.isAlive(time) || hasDynamicId(s.id)) continue;
            sum = sum.add(s.wind(time, x, y, z, surfaceY, centerEnergy(s, time, roughness)));
        }
        for (int i = 0; i < dynamic.size(); i++) {
            StormSystem s = dynamic.get(i);
            if (!s.isAlive(time)) continue;
            sum = sum.add(s.wind(time, x, y, z, surfaceY, centerEnergy(s, time, roughness)));
        }
        return sum;
    }

    /** Maior deficit de pressao entre os sistemas ativos, em hPa. */
    public double sampleDeficitHpa(long time, double x, double z) {
        double worst = 0.0;
        List<StormSystem> active = activeAt(time);
        for (int i = 0; i < active.size(); i++) {
            double d = active.get(i).deficitHpa(time, x, z);
            if (d > worst) worst = d;
        }
        return worst;
    }

    /** O sistema ativo mais proximo do ponto, ou null. */
    public StormSystem nearest(long time, double x, double z) {
        StormSystem best = null;
        double bestDist = Double.MAX_VALUE;
        List<StormSystem> active = activeAt(time);
        for (int i = 0; i < active.size(); i++) {
            StormSystem s = active.get(i);
            double d = s.distanceTo(time, x, z);
            if (d < bestDist) {
                bestDist = d;
                best = s;
            }
        }
        return best;
    }

    /**
     * O sistema ativo mais forte que alcanca o ponto, ou null. Usado pelo VFX
     * para decidir qual tempestade desenhar quando duas se sobrepoem.
     */
    public StormSystem strongestAt(long time, double x, double z) {
        StormSystem best = null;
        double bestVmax = 0.0;
        List<StormSystem> active = activeAt(time);
        for (int i = 0; i < active.size(); i++) {
            StormSystem s = active.get(i);
            if (s.distanceTo(time, x, z) > s.outerRadius()) continue;
            double v = s.vmaxAt(time);
            if (v > bestVmax) {
                bestVmax = v;
                best = s;
            }
        }
        return best;
    }

    private static double centerEnergy(StormSystem s, long time, SurfaceRoughness roughness) {
        if (roughness == null) return 1.0;
        return clamp01(roughness.energyAt(s.centerX(time), s.centerZ(time)));
    }

    // ------------------------------------------------------------------
    // geracao natural
    // ------------------------------------------------------------------

    private StormSystem natural(long slot) {
        if (!authoritative || spawnChance <= 0.0) return null;
        Long key = Long.valueOf(slot);
        StormSystem cached = naturalCache.get(key);
        if (cached != null) return cached;
        StormSystem built = buildNatural(slot);
        if (built != null) {
            naturalCache.put(key, built);
            pruneCache(slot);
        }
        return built;
    }

    private StormSystem buildNatural(long slot) {
        if (rand(slot, 0) > spawnChance) return null;

        int kind = pickKind(rand(slot, 1));
        long spawn = slot * SLOT_TICKS + (long) (rand(slot, 2) * (SLOT_TICKS * 0.6));
        double angle = rand(slot, 3) * TWO_PI;
        double distance = 200.0 + rand(slot, 4) * (REGION_RADIUS - 200.0);
        double x0 = Math.cos(angle) * distance;
        double z0 = Math.sin(angle) * distance;
        double heading = rand(slot, 5) * TWO_PI;
        // ciclones curvam para o polo no proprio hemisferio; a curvatura e
        // pequena (raio de giro de centenas de blocos) para a trajetoria
        // continuar legivel em uma area jogavel
        double turn = (rand(slot, 6) - 0.5) * 2.0 * 6.0e-6 * (north ? 1.0 : -1.0);

        return new StormSystem(kind, slot, spawn, x0, z0, heading, turn, north, 1.0, false);
    }

    private static int pickKind(double r) {
        if (r < 0.55) return StormKind.TROPICAL_STORM;
        if (r < 0.85) return StormKind.HURRICANE;
        return StormKind.MAJOR_HURRICANE;
    }

    /**
     * Sorteio determinista e independente por (janela, componente). Cada
     * componente usa um plano diferente do ruido para nao correlacionar tipo,
     * posicao e rumo.
     */
    private double rand(long slot, int component) {
        double n = noise.noise(slot * 5.13 + 0.5, component * 7.31 + 0.5, 0.0);
        return clamp01(n * 0.5 + 0.5);
    }

    private void pruneCache(long currentSlot) {
        if (naturalCache.size() <= CACHE_LIMIT) return;
        Iterator<Map.Entry<Long, StormSystem>> it = naturalCache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, StormSystem> entry = it.next();
            if (entry.getKey().longValue() < currentSlot - CHECK_BACK - 2) {
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    // sistemas invocados
    // ------------------------------------------------------------------

    /**
     * Cria um sistema em tempo de execucao (Cajado da Catastrofe / comando).
     *
     * @param kind         tipo do sistema
     * @param time         tick atual do mundo (nascimento)
     * @param x            posicao X do centro
     * @param z            posicao Z do centro
     * @param headingRad   rumo inicial em radianos
     * @param intensityMul multiplicador de intensidade (1.0 = padrao)
     * @return o sistema criado
     */
    public StormSystem spawn(int kind, long time, double x, double z, double headingRad,
                             double intensityMul) {
        return spawnAtProgress(kind, time, x, z, headingRad, intensityMul, 0.0);
    }

    /** Spawn with a chosen normalized life-cycle progress (0..1). */
    public StormSystem spawnAtProgress(int kind, long time, double x, double z,
                                       double headingRad, double intensityMul,
                                       double progress) {
        double bounded = Math.max(0.0, Math.min(0.999, progress));
        long birth = time - Math.round(bounded * StormKind.durationTicks(kind));
        StormSystem system = new StormSystem(kind, nextDynamicId.getAndDecrement(), birth,
                x, z, headingRad, 0.0, north, intensityMul, true);
        dynamic.add(system);
        return system;
    }

    /** Registra um sistema vindo da rede (o id ja vem definido pelo servidor). */
    public void addDynamic(StormSystem s) {
        if (s == null) return;
        for (int i = 0; i < dynamic.size(); i++) {
            if (dynamic.get(i).id == s.id) {
                dynamic.set(i, s);
                return;
            }
        }
        dynamic.add(s);
        // Pacotes/estado carregado podem trazer IDs dinâmicos atribuídos por
        // outra instância. Mantém o contador no próximo valor negativo livre.
        if (s.id < 0L) {
            long candidate = s.id - 1L;
            long current;
            do {
                current = nextDynamicId.get();
                if (candidate >= current) break;
            } while (!nextDynamicId.compareAndSet(current, candidate));
        }
    }

    public void removeDynamic(long id) {
        for (int i = 0; i < dynamic.size(); i++) {
            if (dynamic.get(i).id == id) {
                dynamic.remove(i);
                return;
            }
        }
    }

    public void clearDynamic() {
        for (int i = dynamic.size() - 1; i >= 0; i--) {
            if (dynamic.get(i).playerSpawned) dynamic.remove(i);
        }
    }

    /** Replaces every replicated system, including server-generated naturals. */
    public void replaceSystems() {
        dynamic.clear();
    }

    private boolean hasDynamicId(long id) {
        for (int i = 0; i < dynamic.size(); i++) {
            if (dynamic.get(i).id == id) return true;
        }
        return false;
    }

    public int dynamicCount() {
        int count = 0;
        for (int i = 0; i < dynamic.size(); i++) {
            if (dynamic.get(i).playerSpawned) count++;
        }
        return count;
    }

    public List<StormSystem> dynamicSystems() {
        List<StormSystem> invoked = new ArrayList<StormSystem>();
        for (int i = 0; i < dynamic.size(); i++) {
            StormSystem system = dynamic.get(i);
            if (system.playerSpawned) invoked.add(system);
        }
        return Collections.unmodifiableList(invoked);
    }

    // ------------------------------------------------------------------

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
