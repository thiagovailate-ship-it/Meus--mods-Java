package com.dynamicwinds.core;

/**
 * Balanco fisico do vortice: perfil de vento e campo de pressao associado.
 *
 * <p>As duas pecas sao calculadas de forma consistente entre si, que e o ponto
 * central do modelo:</p>
 *
 * <ol>
 *   <li><b>Vento</b> - vortice modificado de Rankine: rotacao de corpo solido
 *       dentro do raio de vento maximo (V cresce linearmente) e decaimento em
 *       lei de potencia fora dele (V ~ r^-alpha). O perfil e continuo em
 *       {@code Rmax}, com pico exatamente {@code Vmax}.</li>
 *   <li><b>Pressao</b> - integrada a partir do <i>mesmo</i> perfil de vento pelo
 *       balanco do vento gradiente: {@code dp/dr = rho * (v^2/r + f*v)}.
 *       A integracao e analitica, entao o deficit central e exato e nao uma
 *       curva ajustada a mao.</li>
 * </ol>
 *
 * <p>Sobre o termo de Coriolis: com a geometria comprimida do jogo, o numero de
 * Rossby local fica enorme ({@code v^2/r} ~ 40 contra {@code f*v} ~ 0.003), ou
 * seja, o escoamento e praticamente ciclostrofico. O termo e mantido na integral
 * porque e o fisicamente correto e define o sentido de rotacao por hemisferio,
 * mas ele contribui com menos de 0.1% do deficit.</p>
 */
public final class VortexBalance {

    /** Densidade do ar proxima a superficie (kg/m3). */
    public static final double RHO = 1.15;
    /** Velocidade angular da Terra (rad/s). */
    public static final double OMEGA = 7.2921e-5;
    /** Latitude adotada: faixa tropical, onde ciclones se formam. */
    public static final double LATITUDE_DEG = 18.0;

    private VortexBalance() {}

    /**
     * Parametro de Coriolis {@code f = 2*Omega*sin(lat)}, com sinal conforme o
     * hemisferio.
     *
     * @param north true para o hemisferio norte
     * @return parametro f em s^-1
     */
    public static double coriolis(boolean north) {
        double f = 2.0 * OMEGA * Math.sin(Math.toRadians(LATITUDE_DEG));
        return north ? f : -f;
    }

    /**
     * Perfil tangencial do vortice modificado de Rankine.
     *
     * @param r     distancia ao centro, em blocos
     * @param vmax  vento maximo sustentado
     * @param rmax  raio do vento maximo
     * @param alpha expoente de decaimento externo
     * @return velocidade tangencial naquele raio
     */
    public static double tangential(double r, double vmax, double rmax, double alpha) {
        if (r <= 0.0 || vmax <= 0.0 || rmax <= 0.0) return 0.0;
        if (r < rmax) return vmax * (r / rmax);
        return vmax * Math.pow(rmax / r, alpha);
    }

    /**
     * Integral de {@code rho*v^2/r} e {@code rho*f*v} no trecho interno
     * (a &lt;= r &lt;= b &lt;= Rmax), onde v cresce linearmente.
     *
     * @param a      limite inferior
     * @param b      limite superior
     * @param vmax   vento maximo
     * @param rmax   raio do vento maximo
     * @param f      parametro de Coriolis
     * @return variacao de pressao naquele trecho, em Pa
     */
    public static double innerPart(double a, double b, double vmax, double rmax, double f) {
        if (b <= a || rmax <= 0.0) return 0.0;
        double halfDiffSq = (b * b - a * a) * 0.5;
        return RHO * halfDiffSq * (vmax * vmax / (rmax * rmax) + f * vmax / rmax);
    }

    /**
     * Integral de {@code rho*v^2/r} e {@code rho*f*v} no trecho externo
     * (Rmax &lt;= a &lt;= r &lt;= b), onde v decai como {@code (rmax/r)^alpha}.
     *
     * @param a     limite inferior
     * @param b     limite superior
     * @param vmax  vento maximo
     * @param rmax  raio do vento maximo
     * @param alpha expoente de decaimento
     * @param f     parametro de Coriolis
     * @return variacao de pressao naquele trecho, em Pa
     */
    public static double outerPart(double a, double b, double vmax, double rmax,
                                   double alpha, double f) {
        if (b <= a || a <= 0.0 || rmax <= 0.0 || alpha <= 0.0) return 0.0;
        double v2 = RHO * vmax * vmax / (2.0 * alpha)
                * (Math.pow(rmax / a, 2.0 * alpha) - Math.pow(rmax / b, 2.0 * alpha));
        double v1 = 0.0;
        if (Math.abs(1.0 - alpha) > 1.0e-9) {
            v1 = RHO * f * vmax * Math.pow(rmax, alpha)
                    * (Math.pow(b, 1.0 - alpha) - Math.pow(a, 1.0 - alpha)) / (1.0 - alpha);
        }
        return v2 + v1;
    }

    /**
     * Deficit de pressao entre o raio {@code r} e a borda externa do sistema
     * ({@code p(r) - p(ambiente)}, negativo no centro).
     *
     * @param r      distancia ao centro, em blocos
     * @param vmax   vento maximo sustentado
     * @param rmax   raio do vento maximo
     * @param alpha  expoente de decaimento externo
     * @param f      parametro de Coriolis
     * @param rOut   raio externo do sistema
     * @return deficit em Pa (0 se {@code r >= rOut})
     */
    public static double deficitPa(double r, double vmax, double rmax, double alpha,
                                   double f, double rOut) {
        if (rOut <= 0.0 || rmax <= 0.0) return 0.0;
        if (r >= rOut) return 0.0;
        if (r < 0.0) r = 0.0;
        // fora do nucleo a integral vai direto; dentro dele soma as duas partes
        double outside = outerPart(Math.max(r, rmax), rOut, vmax, rmax, alpha, f);
        if (r < rmax) {
            return innerPart(r, rmax, vmax, rmax, f) + outside;
        }
        return outside;
    }

    /**
     * Deficit de pressao no centro do sistema (o numero que aparece em boletim
     * meteorologico).
     *
     * @param vmax  vento maximo sustentado
     * @param rmax  raio do vento maximo
     * @param alpha expoente de decaimento externo
     * @param f     parametro de Coriolis
     * @param rOut  raio externo do sistema
     * @return deficit central em Pa
     */
    public static double centralDeficitPa(double vmax, double rmax, double alpha,
                                          double f, double rOut) {
        return deficitPa(0.0, vmax, rmax, alpha, f, rOut);
    }
}
