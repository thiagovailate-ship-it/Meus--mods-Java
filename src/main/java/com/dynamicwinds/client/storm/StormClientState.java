package com.dynamicwinds.client.storm;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import java.util.List;

/**
 * Estado do sistema de tempestade visto do cliente, recalculado uma vez por tick.
 *
 * <p>O renderizador e o audio rodam varias vezes por tick (o render, a cada
 * quadro); recalcular o sistema dominante em cada um deles seria desperdicio e
 * ainda poderia pegar instantes diferentes, fazendo a nevoa, a nuvem e o som
 * tremerem um em relacao ao outro. Aqui o calculo acontece uma vez e todos
 * consomem o mesmo resultado.</p>
 */
public final class StormClientState {

    /** Sistema que mais domina a posicao da camera, ou null. */
    private static StormSystem current;
    /** Dominancia do sistema no ponto: 0 na borda externa, 1 no centro. */
    private static double influence;
    /** Distancia ao centro dividida pelo raio externo. */
    private static double normalizedDistance = 1.0;
    /** Velocidade do vento na camera, em m/s. */
    private static double localWind;
    /** Intensidade do sistema dominante no instante do calculo. */
    private static double intensity;
    /** Raio do vento maximo dividido pelo raio externo (o "tamanho do olho"). */
    private static double eyeRatio;

    private StormClientState() {}

    public static void tick() {
        current = null;
        influence = 0.0;
        normalizedDistance = 1.0;
        localWind = 0.0;
        intensity = 0.0;
        eyeRatio = 0.0;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        EntityPlayer p = mc.thePlayer;
        if (world == null || p == null) return;

        long time = world.getTotalWorldTime();
        StormField field = Wind.stormsFor(world);
        if (field == null) return;

        localWind = Wind.getWindAt(world, p.posX, p.posY, p.posZ).speed;

        List<StormSystem> active = field.activeAt(time);
        StormSystem best = null;
        double bestScore = 0.0;
        double bestNorm = 1.0;
        for (int i = 0; i < active.size(); i++) {
            StormSystem s = active.get(i);
            double outer = s.outerRadius();
            double dist = s.distanceTo(time, p.posX, p.posZ);
            if (dist >= outer) continue;
            double norm = dist / outer;
            double score = s.intensityAt(time) * (1.0 - norm);
            if (score > bestScore) {
                bestScore = score;
                bestNorm = norm;
                best = s;
            }
        }
        if (best == null) return;

        current = best;
        influence = bestScore;
        normalizedDistance = bestNorm;
        intensity = best.intensityAt(time);
        eyeRatio = best.rmaxAt(time) / Math.max(1.0, best.outerRadius());
    }

    /** Sistema dominante, ou null se nenhum alcanca a camera. */
    public static StormSystem current() { return current; }

    /** 0 na borda externa, ate 1 no centro (ja multiplicado pela intensidade). */
    public static double influence() { return influence; }

    /** Distancia ao centro dividida pelo raio externo. */
    public static double normalizedDistance() { return normalizedDistance; }

    /** Velocidade do vento na camera, em m/s. */
    public static double localWind() { return localWind; }

    /** Intensidade do sistema dominante. */
    public static double intensity() { return intensity; }

    /**
     * Densidade da nevoa dentro da tempestade, de 0 a 1.
     *
     * <p>Cresce para o centro e satura: dentro de um furacao a visibilidade cai
     * muito, mas nao a zero, senao o jogo fica injogavel. O olho abre uma
     * clareira de novo, porque la o ar esta descendo e a chuva para - e esse
     * contraste entre parede e olho e justamente o que identifica um ciclone.</p>
     */
    public static float fogStrength() {
        if (current == null) return 0.0F;
        double d = normalizedDistance;
        double band = 1.0 - d;
        double eyeClear = d < eyeRatio ? (d / Math.max(1.0e-4, eyeRatio)) * 0.55 : 1.0;
        double f = band * intensity * 1.35 * eyeClear;
        if (f <= 0.0) return 0.0F;
        if (f >= 1.0) return 1.0F;
        return (float) f;
    }
}
