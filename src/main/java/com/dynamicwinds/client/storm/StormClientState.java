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
    /** Intensidade da chuva no ponto da camera, de 0 a 1. */
    private static double rain;
    /** Duracao do clarao de um relampago, em ticks. */
    private static final int FLASH_TICKS = 8;
    private static int flashTicks;
    private static float flashPower;

    private StormClientState() {}

    public static void tick() {
        if (flashTicks > 0) {
            flashTicks--;
        }
        current = null;
        influence = 0.0;
        normalizedDistance = 1.0;
        localWind = 0.0;
        intensity = 0.0;
        eyeRatio = 0.0;
        rain = 0.0;

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

        // chuva: cresce para dentro, mais rapido perto da parede, e seca no olho
        // - la o ar esta descendo e nao ha nuvem carregada em cima. O expoente
        // 0,8 deixa a chuva entrar cedo nos primeiros aneis do sistema, que e o
        // comportamento observado num ciclone real.
        double band = 1.0D - bestNorm;
        if (band < 0.0D) band = 0.0D;
        double eyeDry = bestNorm < eyeRatio
                ? 0.15D + 0.85D * (bestNorm / Math.max(1.0e-4D, eyeRatio))
                : 1.0D;
        rain = Math.pow(band, 0.8D) * intensity * eyeDry;
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
     * Intensidade da chuva no ponto da camera, de 0 a 1.
     *
     * <p>A chuva do sistema e a protagonista; a chuva do proprio jogo entra como
     * camada secundaria e menor, porque quando a tempestade e quem esta forcando
     * o clima as duas sao o mesmo evento - somar as duas em igual peso seria
     * contar a mesma chuva duas vezes.</p>
     */
    public static float rainStrength() {
        double r = rain;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null) {
            double vanilla = mc.theWorld.getRainStrength(1.0F);
            if (vanilla > 0.0D) {
                r = Math.min(1.0D, r + vanilla * 0.45D);
            }
        }
        if (r <= 0.0D) return 0.0F;
        if (r >= 1.0D) return 1.0F;
        return (float) r;
    }

    /**
     * Acende o clarao de um relampago.
     *
     * <p>A luz nao espera: ela chega no mesmo instante da descarga, enquanto o
     * som fica preso na velocidade do som. Por isso o clarao e disparado na hora
     * e o trovao e agendado.</p>
     *
     * @param power forca do clarao, de 0 a 1 (proximidade da descarga)
     */
    public static void flash(float power) {
        flashTicks = FLASH_TICKS;
        flashPower = power < 0.0F ? 0.0F : (power > 1.0F ? 1.0F : power);
    }

    /** Forca do clarao, de 0 a 1, com queda rapida depois do pico. */
    public static float flashStrength() {
        if (flashTicks <= 0) {
            return 0.0F;
        }
        float t = flashTicks / (float) FLASH_TICKS;
        return flashPower * t * t;
    }

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
