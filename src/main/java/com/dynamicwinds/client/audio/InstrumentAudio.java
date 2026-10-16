package com.dynamicwinds.client.audio;

import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Som dos instrumentos do mod: o anemometro e a manga de vento.
 *
 * <p>Os dois sons ja existiam no pacote sem nenhum lugar que os tocasse. Aqui
 * eles ganham a causa fisica que faltava:</p>
 * <ul>
 *   <li><b>anemometro</b> - um clique por volta completa do rotor, que e como um
 *       anemometro de conchas de verdade funciona (o ima passa pelo sensor uma
 *       vez por volta). O intervalo minimo existe porque, acima de ~4 voltas por
 *       segundo, os cliques reais ja se fundem num zumbido continuo, e um motor
 *       de som nao faz essa fusao;</li>
 *   <li><b>manga de vento</b> - estalo quando a manga da um giro brusco, que e
 *       quando o escoamento se desprende e volta a grudar no tecido.</li>
 * </ul>
 *
 * <p>Nada aqui e chamado sem {@code worldObj.isRemote}: quem decide e o
 * tile entity, e esta classe so e carregada de fato no cliente, onde
 * {@code Minecraft} existe.</p>
 */
public final class InstrumentAudio {

    private static final ResourceLocation TICK = new ResourceLocation("dynamicwinds:anemometer_tick");
    private static final ResourceLocation FLAP = new ResourceLocation("dynamicwinds:windsock_flap");

    /** Intervalo minimo entre cliques, em ticks (evita metralhadora em ventania). */
    private static final int MIN_CLICK_TICKS = 5;
    /** Intervalo minimo entre estalos da manga, em ticks. */
    private static final int MIN_FLAP_TICKS = 18;
    /** Abaixo disso o rotor esta praticamente parado e nao ha o que clicar. */
    private static final float MIN_SPEED = 0.6F;
    /** Vento em que o clique ja esta no volume maximo. */
    private static final float FULL_SPEED = 15.0F;
    /** Giro brusco que faz o tecido estalar, em radianos por tick. */
    private static final float FLAP_SWING = 0.35F;

    private static final Random RND = new Random();
    private static long lastClick = -10000L;
    private static long lastFlap = -10000L;

    private InstrumentAudio() {}

    /** Clique de uma volta completa do rotor. */
    public static void anemometerPulse(World world, double x, double y, double z, float speed) {
        if (!WindConfig.enableAudio || world == null || speed < MIN_SPEED) {
            return;
        }
        long time = world.getTotalWorldTime();
        if (time - lastClick < MIN_CLICK_TICKS) {
            return;
        }
        lastClick = time;
        float s = speed > FULL_SPEED ? FULL_SPEED : speed;
        float volume = (0.22F + 0.55F * (s / FULL_SPEED)) * AudioBeds.shelter();
        play(world, TICK, volume, 0.95F + RND.nextFloat() * 0.12F, x, y, z);
    }

    /** Estalo do tecido da manga num giro brusco. */
    public static void windsockFlap(World world, double x, double y, double z, float swing) {
        if (!WindConfig.enableAudio || world == null || swing < FLAP_SWING) {
            return;
        }
        long time = world.getTotalWorldTime();
        if (time - lastFlap < MIN_FLAP_TICKS) {
            return;
        }
        lastFlap = time;
        float over = swing > FLAP_SWING * 3.0F ? 1.0F : swing / (FLAP_SWING * 3.0F);
        float volume = (0.25F + 0.45F * over) * AudioBeds.shelter();
        play(world, FLAP, volume, 0.92F + RND.nextFloat() * 0.18F, x, y, z);
    }

    private static void play(World world, ResourceLocation location, float volume, float pitch,
                             double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getSoundHandler() == null) {
            return;
        }
        // canal posicional: o instrumento tem lugar no mundo, entao o som sai de
        // onde ele esta e cai com a distancia
        mc.getSoundHandler().playSound(new PositionedSoundRecord(
                location, volume, pitch, (float) x, (float) y, (float) z));
    }
}
