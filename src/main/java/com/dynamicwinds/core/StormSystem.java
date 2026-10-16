package com.dynamicwinds.core;

/**
 * Um sistema de tempo severo: vortice, tempestade tropical, furacao ou furacao
 * maior.
 *
 * <p>Todo o estado do sistema e derivado de parametros <b>imutaveis</b> de
 * nascimento (tipo, tick de nascimento, posicao inicial, direcao, curvatura e
 * hemisferio) mais o relogio do mundo. Nao ha estado mutavel: duas chamadas com
 * o mesmo tick devolvem exatamente o mesmo resultado, e cliente e servidor
 * chegam ao mesmo cenario sem trafegar posicao a cada tick - o mesmo principio
 * que o mod ja usa para o campo de vento. Na pratica, sincronizar um sistema
 * custa um pacote pequeno no nascimento e nada mais.</p>
 *
 * <h3>Estrutura do campo</h3>
 * <ul>
 *   <li><b>Tangencial</b>: {@link VortexBalance#tangential} (Rankine
 *       modificado), com sentido ciclonico conforme o hemisferio.</li>
 *   <li><b>Radial</b>: convergencia para o centro, com angulo de inflow que
 *       cresce de ~6 graus junto a parede do olho ate ~26 graus na periferia -
 *       e a espiral de Ekman na camada limite.</li>
 *   <li><b>Vertical</b>: corrente ascendente na parede do olho e subsidiacao
 *       (descendencia) no olho, que e o motivo de o olho ser calmo.</li>
 *   <li><b>Deslocamento</b>: o sistema se move e a massa de ar vai junto, entao
 *       o vetor de translacao entra somado ao campo.</li>
 *   <li><b>Altura</b>: o vento cresce dentro da camada limite e decai
 *       exponencialmente acima do topo da coluna.</li>
 *   <li><b>Landfall</b>: sobre continente o sistema perde energia, informada
 *       pelo fator {@link SurfaceRoughness}.</li>
 * </ul>
 *
 * <p>A trajetoria e um arco de circulo exato (direcao girando a taxa constante
 * com velocidade constante), integrado analiticamente - sem acumular erro de
 * passo e sem guardar historico.</p>
 */
public final class StormSystem {

    /** Parte da translacao que realmente e transmitida ao ar proximo ao solo. */
    private static final double TRANSLATION_SHARE = 0.6;

    public final int kind;
    public final long id;
    public final long spawnTick;
    public final double spawnX;
    public final double spawnZ;
    /** Direcao inicial de deslocamento, em radianos. */
    public final double heading0;
    /** Curvatura da trajetoria, em radianos por tick. */
    public final double turnRate;
    public final boolean north;
    /** Multiplicador de intensidade (1.0 = natural, o Cajado usa > 1). */
    public final double intensityMul;
    /** Marca se foi criado por jogador, para permitir remocao em lote. */
    public final boolean playerSpawned;

    public StormSystem(int kind, long id, long spawnTick, double spawnX, double spawnZ,
                       double heading0, double turnRate, boolean north,
                       double intensityMul, boolean playerSpawned) {
        this.kind = kind;
        this.id = id;
        this.spawnTick = spawnTick;
        this.spawnX = spawnX;
        this.spawnZ = spawnZ;
        this.heading0 = heading0;
        this.turnRate = turnRate;
        this.north = north;
        this.intensityMul = intensityMul <= 0.0 ? 1.0 : intensityMul;
        this.playerSpawned = playerSpawned;
    }

    // ------------------------------------------------------------------
    // ciclo de vida
    // ------------------------------------------------------------------

    public long durationTicks() {
        return StormKind.durationTicks(kind);
    }

    public long age(long time) {
        return time - spawnTick;
    }

    public boolean isAlive(long time) {
        long ageTicks = age(time);
        return ageTicks >= 0L && ageTicks < durationTicks();
    }

    /** Fracao do ciclo de vida ja percorrida, de 0 a 1. */
    public double progress(long time) {
        double d = durationTicks();
        if (d <= 0.0) return 1.0;
        double p = age(time) / d;
        return clamp01(p);
    }

    /**
     * Intensidade relativa (0 a 1) ao longo do ciclo de vida:
     * genese, intensificacao, maturidade com ciclo de substituicao da parede do
     * olho, e enfraquecimento. Todas as transicoes usam smoothstep, portanto a
     * curva e continua - nao ha salto de vento quando a fase muda.
     */
    public double intensityAt(long time) {
        if (!isAlive(time)) return 0.0;
        double t = progress(time);
        if (t < 0.15) {
            return smoothstep(0.0, 0.15, t) * 0.55;
        }
        if (t < 0.45) {
            return 0.55 + smoothstep(0.15, 0.45, t) * 0.45;
        }
        if (t < 0.75) {
            // ciclo de substituicao da parede do olho: a intensidade cai um pouco
            // e se recupera, como observado em furacões intensos
            double dip = 0.10 * Math.exp(-sq((t - 0.60) / 0.05));
            return 1.0 - dip;
        }
        return 1.0 - smoothstep(0.75, 1.0, t);
    }

    /** Vento maximo sustentado no instante dado, em m/s. */
    public double vmaxAt(long time) {
        return StormKind.vmax(kind) * intensityAt(time) * intensityMul;
    }

    /**
     * Raio do vento maximo no instante dado. O campo de vento <i>se expande</i>
     * conforme o sistema enfraquece, que e o comportamento real: um furacao em
     * dissipacao cobre uma area maior com vento mais fraco.
     */
    public double rmaxAt(long time) {
        double intensity = intensityAt(time);
        return StormKind.rmax(kind) * (1.0 + 0.55 * (1.0 - intensity));
    }

    public double outerRadius() {
        return StormKind.outerRadius(kind);
    }

    // ------------------------------------------------------------------
    // trajetoria
    // ------------------------------------------------------------------

    /** Direcao de deslocamento no instante dado, em radianos. */
    public double headingAt(long time) {
        return headingForAge(age(time));
    }

    private double headingForAge(double age) {
        return heading0 + turnRate * age;
    }

    public double centerX(long time) {
        return spawnX + arcX(age(time));
    }

    public double centerZ(long time) {
        return spawnZ + arcZ(age(time));
    }

    /**
     * Integral exata do deslocamento com direcao girando a taxa constante e
     * velocidade constante. No limite de curvatura nula degenera em linha reta.
     */
    private double arcX(double age) {
        double s = StormKind.translationSpeed(kind);
        if (Math.abs(turnRate) < 1.0e-9) {
            return s * age * Math.cos(heading0);
        }
        return (s / turnRate) * (Math.sin(headingForAge(age)) - Math.sin(heading0));
    }

    private double arcZ(double age) {
        double s = StormKind.translationSpeed(kind);
        if (Math.abs(turnRate) < 1.0e-9) {
            return s * age * Math.sin(heading0);
        }
        return (s / turnRate) * (Math.cos(heading0) - Math.cos(headingForAge(age)));
    }

    /** Componente X da velocidade de translacao (blocos por tick). */
    public double translationX(long time) {
        return StormKind.translationSpeed(kind) * Math.cos(headingAt(time));
    }

    /** Componente Z da velocidade de translacao (blocos por tick). */
    public double translationZ(long time) {
        return StormKind.translationSpeed(kind) * Math.sin(headingAt(time));
    }

    public double distanceTo(long time, double x, double z) {
        double dx = x - centerX(time);
        double dz = z - centerZ(time);
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ------------------------------------------------------------------
    // campo de vento
    // ------------------------------------------------------------------

    /**
     * Contribuicao deste sistema ao vento no ponto dado (somada ao vento de
     * fundo por {@link StormField}).
     *
     * @param time      tick do mundo
     * @param x         posicao X da consulta
     * @param y         posicao Y da consulta
     * @param z         posicao Z da consulta
     * @param surfaceY  altura da superficie local (nivel do solo)
     * @param energy    fator de energia da superficie (1 = oceano, veja {@link SurfaceRoughness})
     * @return vetor de vento do sistema, em m/s (zero fora do raio externo)
     */
    public Vec3 wind(long time, double x, double y, double z, int surfaceY, double energy) {
        if (energy <= 0.0) return Vec3.ZERO;
        double intensity = intensityAt(time);
        if (intensity <= 0.0) return Vec3.ZERO;

        double vmax = StormKind.vmax(kind) * intensity * intensityMul;
        double rmax = rmaxAt(time);
        double alpha = StormKind.alpha(kind);
        double rOut = outerRadius();
        double top = StormKind.columnTop(kind);

        double dx = x - centerX(time);
        double dz = z - centerZ(time);
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r > rOut) return Vec3.ZERO;
        if (r < 0.05) r = 0.05;          // evita direcao indefinida exatamente no centro
        double invR = 1.0 / r;

        double vt = VortexBalance.tangential(r, vmax, rmax, alpha);

        // sentido ciclonico: anti-horario no hemisferio norte, com o mapa
        // desenhado com +X a leste e +Z ao sul (norte para cima)
        double spin = north ? 1.0 : -1.0;
        double tx = spin * dz * invR;
        double tz = -spin * dx * invR;

        // inflow: espiral de Ekman, mais fechado na periferia
        double inflowDeg = 6.0 + 20.0 * smoothstep(0.5 * rmax, rOut, r);
        double inflow = Math.tan(Math.toRadians(inflowDeg));
        double hx = vt * (tx - inflow * dx * invR);
        double hz = vt * (tz - inflow * dz * invR);

        // a massa de ar acompanha o deslocamento do sistema
        hx += translationX(time) * TRANSLATION_SHARE;
        hz += translationZ(time) * TRANSLATION_SHARE;

        // perfil vertical: ganho dentro da camada limite, decaimento acima do topo
        double h = y - surfaceY;
        double heightFactor;
        if (h < 0.0) {
            heightFactor = 0.35;
        } else if (h < top) {
            heightFactor = 0.85 + 0.15 * (h / top);
        } else {
            heightFactor = Math.exp(-(h - top) / (0.35 * top));
        }

        // ascendencia na parede do olho e subsidencia dentro do olho
        double wall = gauss((r - rmax) / (0.30 * rmax));
        double eye = gauss(r / (0.45 * rmax));
        double vy = 0.38 * vt * wall - 0.10 * vmax * eye;

        double scale = heightFactor * clamp01(energy);
        return new Vec3(hx * scale, vy * scale, hz * scale);
    }

    /**
     * Deficit de pressao no ponto dado, em hPa (positivo = mais baixo que o
     * ambiente). Calculado pelo balanco do vento gradiente sobre o perfil
     * tangencial atual.
     */
    public double deficitHpa(long time, double x, double z) {
        if (!isAlive(time)) return 0.0;
        double vmax = vmaxAt(time);
        if (vmax <= 0.0) return 0.0;
        double pa = VortexBalance.deficitPa(distanceTo(time, x, z), vmax, rmaxAt(time),
                StormKind.alpha(kind), VortexBalance.coriolis(north), outerRadius());
        return pa / 100.0;
    }

    /** Deficit central (no olho) no instante dado, em hPa. */
    public double centralDeficitHpa(long time) {
        if (!isAlive(time)) return 0.0;
        double pa = VortexBalance.centralDeficitPa(vmaxAt(time), rmaxAt(time),
                StormKind.alpha(kind), VortexBalance.coriolis(north), outerRadius());
        return pa / 100.0;
    }

    // ------------------------------------------------------------------
    // utilitarios
    // ------------------------------------------------------------------

    private static double sq(double v) { return v * v; }

    private static double gauss(double v) { return Math.exp(-v * v); }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /** Smoothstep classico: continuo com derivada zero nas duas pontas. */
    private static double smoothstep(double a, double b, double x) {
        if (b <= a) return x >= b ? 1.0 : 0.0;
        double t = clamp01((x - a) / (b - a));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Resumo de uma linha, para comandos e HUD. */
    public String describe(long time) {
        if (!isAlive(time)) {
            return StormKind.name(kind) + " (inativo)";
        }
        return String.format("%s %s id=%d  centro=(%.0f,%.0f)  Vmax=%.1f m/s  Rmax=%.0f m  deficit=%.1f hPa  %d%%",
                StormKind.name(kind), StormKind.category(kind), id,
                centerX(time), centerZ(time), vmaxAt(time), rmaxAt(time),
                centralDeficitHpa(time), (int) Math.round(progress(time) * 100.0));
    }

    @Override
    public String toString() {
        return "StormSystem(" + StormKind.name(kind) + " id=" + id + ")";
    }
}
