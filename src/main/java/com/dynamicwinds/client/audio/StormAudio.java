package com.dynamicwinds.client.audio;

import com.dynamicwinds.client.storm.StormClientState;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Som da tempestade: chuva em tres regimes, trovao com distancia real e detritos.
 *
 * <p>Cada efeito e amarrado a uma grandeza do modelo, e nao a um tempo:</p>
 * <ul>
 *   <li><b>chuva</b> - a intensidade no ponto da camera
 *       ({@link StormClientState#rainStrength()}), que cresce para dentro e
 *       seca no olho, onde o ar desce;</li>
 *   <li><b>trovao</b> - a distancia ate a parede do olho, que e a banda de
 *       conveccao onde nascem as descargas. Perto estala (banda larga), a
 *       duzentos blocos so rola e acima disso so chega o grave: e a absorcao do
 *       ar, que come o agudo muito antes do grave;</li>
 *   <li><b>detritos</b> - a velocidade do vento na camera, porque e ela que
 *       arranca e arremessa o que ja esta solto.</li>
 * </ul>
 *
 * <p>O trovao tambem chega <b>atrasado</b> e vem do lado certo. A luz e
 * instantanea, o som anda a 343 m/s: a descarga a trezentos blocos e vista agora
 * e ouvida quase um segundo depois, do ponto onde ela caiu, com o nivel caindo
 * conforme a distancia. Por isso o evento e agendado e nao tocado na hora.</p>
 */
public final class StormAudio {

    private static final ResourceLocation RAIN_LIGHT = new ResourceLocation("dynamicwinds:rain_light");
    private static final ResourceLocation RAIN_HEAVY = new ResourceLocation("dynamicwinds:rain_heavy");
    private static final ResourceLocation RAIN_WALL = new ResourceLocation("dynamicwinds:rain_wind");
    private static final ResourceLocation THUNDER_CLOSE = new ResourceLocation("dynamicwinds:thunder_close");
    private static final ResourceLocation THUNDER_MID = new ResourceLocation("dynamicwinds:thunder_rumble");
    private static final ResourceLocation THUNDER_FAR = new ResourceLocation("dynamicwinds:thunder_far");
    private static final ResourceLocation SND_DEBRIS = new ResourceLocation("dynamicwinds:debris_rattle");

    private static final ResourceLocation[] RAIN_TIERS = { RAIN_LIGHT, RAIN_HEAVY, RAIN_WALL };
    private static final float[] RAIN_GAIN = { 0.80F, 0.90F, 1.00F };

    /** Intervalo minimo entre descargas consideradas pelo audio, em ticks. */
    private static final int THUNDER_PERIOD = 70;
    /** Velocidade do som, em blocos por segundo. */
    private static final double SOUND_SPEED = 343.0D;
    /** Alcance audivel do trovao, em blocos (volume acima de 1 estica o canal). */
    private static final float THUNDER_RANGE = 620.0F;
    /** Blocos cobertos por unidade de volume do canal posicional do 1.7.10. */
    private static final float RANGE_PER_VOLUME = 16.0F;
    /** Distancias que definem o timbre: perto estala, longe so rola. */
    private static final double CLOSE_DISTANCE = 90.0D;
    private static final double MID_DISTANCE = 300.0D;
    /** Limite de descargas agendadas ao mesmo tempo. */
    private static final int MAX_PENDING = 12;

    private static final double DEBRIS_MIN_WIND = 20.0D;
    private static final int DEBRIS_COOLDOWN = 40;

    private static final Random RND = new Random();
    private static final List<Pending> PENDING = new ArrayList<Pending>();
    private static long lastThunderTick = -10000L;
    private static long lastDebrisTick = -10000L;

    private StormAudio() {}

    /** Descarga agendada: o clarão ja aconteceu, o som ainda esta viajando. */
    private static final class Pending {
        long tick;
        ResourceLocation sound;
        float x;
        float y;
        float z;
        float volume;
        float pitch;
    }

    public static void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.getSoundHandler() == null) {
            PENDING.clear();
            return;
        }
        if (!WindConfig.enableAudio) {
            PENDING.clear();
            return;
        }

        long time = mc.theWorld.getTotalWorldTime();
        playPending(mc, time);

        float rain = StormClientState.rainStrength();
        if (rain > 0.02F) {
            // dois saltos: chuva fina, chuva forte e a cortina da parede do olho
            float pos = clamp01(rain * 1.35F) * (RAIN_TIERS.length - 1);
            float loud = clamp(rain / 0.55F, 0.15F, 1.0F);
            for (int i = 0; i < RAIN_TIERS.length; i++) {
                double w = 1.0D - Math.abs(pos - i);
                if (w <= 0.0D) {
                    continue;
                }
                AudioBeds.request("rain_tier_" + i, RAIN_TIERS[i],
                        (float) (w * loud * RAIN_GAIN[i]), 1.0F);
            }
        }

        StormSystem s = StormClientState.current();
        if (s == null) {
            return;
        }
        float influence = (float) StormClientState.influence();

        if (WindConfig.stormLightning && influence > 0.05F && time - lastThunderTick >= THUNDER_PERIOD) {
            double deficit = s.centralDeficitHpa(time);
            // a taxa de descargas acompanha a intensidade e o deficit central
            double chance = 0.30D * influence * Math.min(1.6D, deficit / 30.0D);
            if (RND.nextDouble() < chance) {
                lastThunderTick = time;
                scheduleThunder(mc, s, time, influence);
            }
        }

        debris(mc, time);
    }

    private static void scheduleThunder(Minecraft mc, StormSystem s, long time, float influence) {
        EntityPlayer p = mc.thePlayer;
        double dx = p.posX - s.centerX(time);
        double dz = p.posZ - s.centerZ(time);
        double distance = Math.sqrt(dx * dx + dz * dz);
        double wall = s.rmaxAt(time);
        double d = distance - wall;
        if (d < 0.0D) {
            d = 0.0D;
        }

        ResourceLocation sound = d < CLOSE_DISTANCE ? THUNDER_CLOSE
                : (d < MID_DISTANCE ? THUNDER_MID : THUNDER_FAR);
        // O alcance do canal e o maximo entre o alcance do sistema (que cresce
        // com a intensidade) e a propria distancia da descarga: sem esse piso,
        // um relampago na borda externa cairia fora do raio audivel e ficaria
        // mudo, quando na verdade trovão distante e fraco - nao inexistente.
        float range = Math.max(THUNDER_RANGE * (0.55F + 0.45F * influence),
                (float) d + 80.0F);
        // o grave atravessa o solo melhor que o agudo: sob abrigo o trovao abafa
        // menos que o vento e a chuva, por isso a raiz
        float volume = range / RANGE_PER_VOLUME * (float) Math.sqrt(AudioBeds.shelter());

        // ponto da descarga: mesma distancia, azimute sorteado
        double angle = RND.nextDouble() * Math.PI * 2.0D;
        Pending pend = new Pending();
        pend.sound = sound;
        pend.x = (float) (p.posX + Math.cos(angle) * d);
        pend.y = (float) (p.posY + 10.0D);
        pend.z = (float) (p.posZ + Math.sin(angle) * d);
        pend.volume = volume;
        pend.pitch = 0.94F + RND.nextFloat() * 0.12F;
        pend.tick = time + (long) Math.round(d / SOUND_SPEED * 20.0D);
        if (PENDING.size() < MAX_PENDING) {
            PENDING.add(pend);
        }

        // o clarão e instantaneo: quem esta no olho ve a parede acender e so
        // depois ouve o trovao
        StormClientState.flash((float) (0.35D + 0.65D * Math.min(1.0D, influence + 0.2D)));
    }

    private static void playPending(Minecraft mc, long time) {
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (p.tick > time) {
                continue;
            }
            mc.getSoundHandler().playSound(new PositionedSoundRecord(
                    p.sound, p.volume, p.pitch, p.x, p.y, p.z));
            PENDING.remove(i);
        }
    }

    /**
     * Detritos: acima de 20 m/s (categoria 1) o que estava solto comeca a voar.
     */
    private static void debris(Minecraft mc, long time) {
        double v = StormClientState.localWind();
        if (v < DEBRIS_MIN_WIND || time - lastDebrisTick < DEBRIS_COOLDOWN) {
            return;
        }
        double over = Math.min(1.0D, (v - DEBRIS_MIN_WIND) / 30.0D);
        if (RND.nextDouble() > 0.15D + 0.5D * over) {
            return;
        }
        lastDebrisTick = time;
        EntityPlayer p = mc.thePlayer;
        double angle = RND.nextDouble() * Math.PI * 2.0D;
        double r = 6.0D + RND.nextDouble() * 12.0D;
        float volume = (0.45F + 0.5F * (float) over) * AudioBeds.shelter();
        mc.getSoundHandler().playSound(new PositionedSoundRecord(
                SND_DEBRIS, volume, 0.90F + RND.nextFloat() * 0.25F,
                (float) (p.posX + Math.cos(angle) * r),
                (float) (p.posY + 1.0D),
                (float) (p.posZ + Math.sin(angle) * r)));
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
