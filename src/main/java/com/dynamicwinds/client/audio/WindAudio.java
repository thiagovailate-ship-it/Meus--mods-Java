package com.dynamicwinds.client.audio;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.client.storm.StormClientState;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.WindSample;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

/**
 * Vento ambiente: tres leitos que se cruzam conforme a velocidade real do vento.
 *
 * <p>A escolha do leito nao e um degrau ("vento forte passa a tocar o arquivo
 * forte"), e sim uma interpolacao: a mesma velocidade normalizada entre a brisa
 * e a categoria 1 vira uma posicao continua entre as tres faixas, e os pesos sao
 * triangulares (no maximo duas faixas ativas por vez, somando 1). O resultado
 * audivel e o que a fisica pede - o vento medio nao "muda de som", ele passa de
 * um registro para outro.</p>
 *
 * <p>A altura da reproducao tambem sai do modelo: sobe um pouco com a
 * velocidade (turbulencia mais rapida) e cai quando ha uma tempestade de verdade
 * em cima, porque o vortice dela tem centenas de blocos de diametro e a
 * turbulencia se desloca para frequencias mais baixas. O rugido de um furacao e
 * grave pelo mesmo motivo que o de um jato e agudo: a escala do escoamento.</p>
 */
public final class WindAudio {

    private static final ResourceLocation BED_LIGHT = new ResourceLocation("dynamicwinds:wind_ambience");
    private static final ResourceLocation BED_MEDIUM = new ResourceLocation("dynamicwinds:wind_strong");
    private static final ResourceLocation BED_STRONG = new ResourceLocation("dynamicwinds:wind_storm");
    private static final ResourceLocation SND_GUST = new ResourceLocation("dynamicwinds:wind_gust");
    private static final ResourceLocation SND_RUSTLE = new ResourceLocation("dynamicwinds:leaf_rustle");

    private static final ResourceLocation[] TIERS = { BED_LIGHT, BED_MEDIUM, BED_STRONG };
    /** Ganho de cada faixa: o material de cada uma ja traz parte da diferenca. */
    private static final float[] LEVEL_GAIN = { 0.70F, 0.85F, 1.00F };

    /** Abaixo disso o vento nao se ouve (brisa fraca, ~3,5 m/s). */
    private static final double V_CALM = 3.5D;
    /** Categoria 1: 26 m/s. Dali para cima o leito ja esta no maximo. */
    private static final double V_FULL = 26.0D;

    private static final float GUST_TRIGGER = 1.30F;
    private static final int GUST_COOLDOWN = 50;
    private static final double RUSTLE_MIN_SPEED = 5.0D;
    private static final int RUSTLE_COOLDOWN = 30;

    private static final Random RND = new Random();
    private static long lastGustTick = -10000L;
    private static long lastRustleTick = -10000L;
    private static long leafScanTick = -10000L;
    private static boolean leavesNearby;

    private WindAudio() {}

    public static void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (!WindConfig.enableAudio) {
            return;   // sem pedido, os leitos somem sozinhos
        }

        WindSample s = Wind.getWindAt(mc.theWorld, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        long time = mc.theWorld.getTotalWorldTime();

        double q = (s.speed - V_CALM) / (V_FULL - V_CALM);
        if (q < 0.0D) {
            q = 0.0D;
        } else if (q > 1.0D) {
            q = 1.0D;
        }

        float depth = stormDepth(time);
        double pos = q * (TIERS.length - 1);
        for (int i = 0; i < TIERS.length; i++) {
            double w = 1.0D - Math.abs(pos - i);
            if (w <= 0.0D) {
                continue;
            }
            float pitch = (float) ((0.97D + 0.06D * q) * (1.0D - 0.16D * depth));
            AudioBeds.request("wind_tier_" + i, TIERS[i], (float) (w * LEVEL_GAIN[i]), pitch);
        }

        if (s.gust >= GUST_TRIGGER && time - lastGustTick >= GUST_COOLDOWN) {
            lastGustTick = time;
            float over = (float) Math.min(1.0D, (s.gust - GUST_TRIGGER) * 1.2D);
            play(mc, SND_GUST, (0.35F + 0.5F * over) * AudioBeds.shelter(),
                    0.94F + RND.nextFloat() * 0.12F);
        }

        if (s.speed > RUSTLE_MIN_SPEED && leavesNearby(mc, time)
                && time - lastRustleTick >= RUSTLE_COOLDOWN && RND.nextFloat() < 0.45F) {
            lastRustleTick = time;
            float v = (float) Math.min(1.0D, s.speed / 18.0D);
            play(mc, SND_RUSTLE, (0.25F + 0.45F * v) * AudioBeds.shelter(),
                    0.95F + RND.nextFloat() * 0.10F);
        }
    }

    /**
     * Profundidade do sistema de tempestade dominante, de 0 a 1, pelo deficit de
     * pressao central (72 hPa e o de um sistema maior).
     */
    private static float stormDepth(long time) {
        StormSystem s = StormClientState.current();
        if (s == null) {
            return 0.0F;
        }
        double d = s.centralDeficitHpa(time) / 72.0D;
        if (d > 1.0D) {
            d = 1.0D;
        }
        return (float) d;
    }

    /**
     * Ha folhagem por perto?
     *
     * <p>Folha balancando e o unico som de vento que depende do que esta em volta,
     * e nao do ar: sem arvore, o vento nao tem o que fazer farfalhar. A varredura
     * roda uma vez por segundo e cobre so a vizinhanca imediata - e o suficiente,
     * porque a folhagem que se ouve e a que esta a poucos metros.</p>
     */
    private static boolean leavesNearby(Minecraft mc, long time) {
        if (time - leafScanTick < 20L) {
            return leavesNearby;
        }
        leafScanTick = time;
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        leavesNearby = false;
        for (int dx = -3; dx <= 3 && !leavesNearby; dx++) {
            for (int dy = -1; dy <= 2 && !leavesNearby; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Block b = mc.theWorld.getBlock(px + dx, py + dy, pz + dz);
                    if (b != null && b.getMaterial() == Material.leaves) {
                        leavesNearby = true;
                        break;
                    }
                }
            }
        }
        return leavesNearby;
    }

    private static void play(Minecraft mc, ResourceLocation location, float volume, float pitch) {
        mc.getSoundHandler().playSound(new PositionedSoundRecord(
                location, volume, pitch,
                (float) mc.thePlayer.posX,
                (float) (mc.thePlayer.posY + 1.0D),
                (float) mc.thePlayer.posZ));
    }
}
