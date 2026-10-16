package com.dynamicwinds.client.audio;

import com.dynamicwinds.client.storm.StormClientState;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * Som da tempestade: o ronco continuo, os trovões e o assobio do vento na
 * parede do olho.
 *
 * <p>O volume e a frequencia saem do mesmo estado que alimenta a nevoa e as
 * nuvens ({@link StormClientState}), entao o que se ve e o que se ouve andam
 * juntos: entrar no olho silencia de verdade, e aproximar-se da parede aumenta o
 * ruido. O ronco e re-disparado periodicamente em vez de ser um loop, porque no
 * 1.7.10 o canal de som posicional nao repete sozinho - e o periodo e curto o
 * bastante para as repeticoes se sobreporem sem buraco audivel.</p>
 */
public final class StormAudio {

    /** Periodo entre re-disparos do ronco de fundo, em ticks. */
    private static final int ROAR_PERIOD = 100;
    /** Periodo minimo entre trovões, em ticks. */
    private static final int THUNDER_PERIOD = 90;

    private static final Random RND = new Random();
    private static long lastRoarTick = -1000L;
    private static long lastThunderTick = -1000L;

    private StormAudio() {}

    public static void update() {
        if (!WindConfig.enableAudio) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        StormSystem s = StormClientState.current();
        if (s == null) return;

        long time = mc.theWorld.getTotalWorldTime();
        float influence = (float) StormClientState.influence();
        if (influence <= 0.02F) return;

        float x = (float) mc.thePlayer.posX;
        float y = (float) mc.thePlayer.posY;
        float z = (float) mc.thePlayer.posZ;

        // ronco continuo: a chuva da parede do olho, em volume proporcional
        if (time - lastRoarTick >= ROAR_PERIOD) {
            lastRoarTick = time;
            float volume = 0.35F + 1.35F * influence;
            mc.getSoundHandler().playSound(new PositionedSoundRecord(
                    new ResourceLocation("dynamicwinds:rain_wind"),
                    volume, 0.85F + RND.nextFloat() * 0.12F, x, y, z));
        }

        // trovão: mais provável perto da parede do olho e em sistema forte
        double deficit = s.centralDeficitHpa(time);
        float thunderChance = 0.35F * influence * (float) Math.min(1.6, deficit / 30.0);
        if (time - lastThunderTick >= THUNDER_PERIOD && RND.nextFloat() < thunderChance) {
            lastThunderTick = time;
            mc.getSoundHandler().playSound(new PositionedSoundRecord(
                    new ResourceLocation("dynamicwinds:thunder_rumble"),
                    0.6F + 1.6F * influence, 0.8F + RND.nextFloat() * 0.15F, x, y, z));
        }
    }
}
