package com.dynamicwinds.core;

/**
 * Tipos de sistema de tempo severo e seus parametros de escala.
 *
 * <p>Sao tres niveis de ciclone, na linha da escala Saffir-Simpson, mais o
 * vortice (tornado / tromba d'agua):</p>
 *
 * <pre>
 * nivel  tipo                Vmax    Rmax    raio externo   duracao
 *   0    vortice            28.0     9 m      150 m         ~3 min
 *   1    tempestade tropical 24.0    55 m      260 m         ~10 min
 *   2    furacao            46.0    85 m      400 m         ~15 min
 *   3    furacao maior      72.0   130 m      560 m         ~20 min
 * </pre>
 *
 * <p><b>Escala adotada:</b> 1 bloco = 1 metro, entao Vmax esta em m/s (72 m/s
 * ~ 260 km/h, categoria 5). Os <i>raios</i> sao comprimidos cerca de 300x em
 * relacao a um ciclone real (um furacao verdadeiro tem Rmax de 30 a 60 km), de
 * modo que a tempestade caiba em uma area jogavel. O que importa e que a
 * relacao vento-pressao continua fisicamente consistente: o deficit central
 * calculado por {@link VortexBalance} cai na faixa observada (7 a 72 hPa).</p>
 */
public final class StormKind {

    public static final int VORTEX = 0;
    public static final int TROPICAL_STORM = 1;
    public static final int HURRICANE = 2;
    public static final int MAJOR_HURRICANE = 3;
    public static final int COUNT = 4;

    /** Vento maximo sustentado na maturidade, em m/s. */
    private static final double[] VMAX = { 28.0, 24.0, 46.0, 72.0 };
    /** Raio do vento maximo (parede do olho), em blocos. */
    private static final double[] RMAX = { 9.0, 55.0, 85.0, 130.0 };
    /** Raio externo: acima disso o sistema nao contribui mais. */
    private static final double[] ROUT = { 150.0, 260.0, 400.0, 560.0 };
    /** Topo da coluna de vento forte (nao a altura total da nuvem). */
    private static final double[] TOP = { 110.0, 150.0, 180.0, 210.0 };
    /** Expoente de decaimento do vortice fora do nucleo. */
    private static final double[] ALPHA = { 0.75, 0.62, 0.60, 0.58 };
    /** Duracao total do ciclo de vida, em ticks. */
    private static final long[] DURATION = { 4000L, 12000L, 18000L, 24000L };
    /** Velocidade de deslocamento (steering), em blocos por tick. */
    private static final double[] SPEED = { 0.030, 0.035, 0.050, 0.065 };

    private static final String[] NAME_PT = {
        "Vortice", "Tempestade Tropical", "Furacao", "Furacao Maior"
    };
    private static final String[] NAME_EN = {
        "Vortex", "Tropical Storm", "Hurricane", "Major Hurricane"
    };

    private StormKind() {}

    private static double get(double[] table, int kind) {
        if (kind < 0 || kind >= table.length) return table[VORTEX];
        return table[kind];
    }

    public static double vmax(int kind) { return get(VMAX, kind); }

    public static double rmax(int kind) { return get(RMAX, kind); }

    public static double outerRadius(int kind) { return get(ROUT, kind); }

    public static double columnTop(int kind) { return get(TOP, kind); }

    public static double alpha(int kind) { return get(ALPHA, kind); }

    public static double translationSpeed(int kind) { return get(SPEED, kind); }

    public static long durationTicks(int kind) {
        if (kind < 0 || kind >= DURATION.length) return DURATION[VORTEX];
        return DURATION[kind];
    }

    public static String name(int kind, boolean english) {
        String[] table = english ? NAME_EN : NAME_PT;
        if (kind < 0 || kind >= table.length) return table[VORTEX];
        return table[kind];
    }

    public static String name(int kind) { return name(kind, false); }

    /** Ciclones tropicais tem olho e bandas espirais; o vortice nao. */
    public static boolean isCyclone(int kind) {
        return kind >= TROPICAL_STORM && kind <= MAJOR_HURRICANE;
    }

    /** Rotulo no estilo Saffir-Simpson, para exibir em comando/HUD. */
    public static String category(int kind) {
        switch (kind) {
            case TROPICAL_STORM: return "TS";
            case HURRICANE: return "Cat 1-2";
            case MAJOR_HURRICANE: return "Cat 3+";
            default: return "EF2-5";
        }
    }
}
