package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.SurfaceRoughness;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Acopla os sistemas de tempo severo ao clima do Minecraft: chuva, trovoes e
 * relampagos deixam de ser um estado aleatorio do mundo e passam a ser
 * consequencia da fisica.
 *
 * <p>A chuva e do jogo, mas quem manda nela e o sistema: perto do olho chove
 * forte, na periferia chove fraco. Onde o sistema nao alcanca ninguem, o mod nao
 * interfere - a chuva volta a ser o ciclo normal do mundo (nunca forcamos "seco
 * para sempre", apenas deixamos de renovar o forcar).</p>
 *
 * <p>Os relampagos sao sorteados na parede do olho, que e onde a conveccao e
 * mais forte em um ciclone real, e nao em qualquer ponto do ceu.</p>
 */
public final class StormWeather {

    /** Ticks de chuva renovados a cada passagem (o vanilla decrementa). */
    private static final int RAIN_REFRESH = 1400;
    /** Ticks de trovoada renovados a cada passagem. */
    private static final int THUNDER_REFRESH = 900;
    /** Distancia maxima, em blocos, para sortear um relampago em volta do jogador. */
    private static final double STRIKE_RANGE = 72.0;

    /** Dimensoes em que o mod esta forcando clima agora. */
    private static final Map<Long, Boolean> FORCED = new HashMap<Long, Boolean>();

    private StormWeather() {}

    public static void tick(World world) {
        if (world.isRemote) return;
        if (world.provider.dimensionId != 0) return;
        if (!WindConfig.stormsEnabled()) return;

        long time = world.getTotalWorldTime();
        StormField field = Wind.stormsFor(world);
        if (field == null) return;

        SurfaceRoughness energy = Wind.surfaceEnergyFor(world);
        List<StormSystem> active = field.activeAt(time, energy);

        double reach = 0.0;
        StormSystem closest = null;
        if (!active.isEmpty()) {
            List players = world.playerEntities;
            for (int i = 0; i < players.size(); i++) {
                EntityPlayer p = (EntityPlayer) players.get(i);
                for (int j = 0; j < active.size(); j++) {
                    StormSystem s = active.get(j);
                    double outer = s.outerRadius();
                    double dist = s.distanceTo(time, p.posX, p.posZ);
                    if (dist >= outer) continue;
                    // 1 no centro, 0 na borda externa: a chuva cresce ao entrar
                    double local = 1.0 - dist / outer;
                    if (local > reach) {
                        reach = local;
                        closest = s;
                    }
                }
            }
        }

        WorldInfo info = world.getWorldInfo();
        long key = world.provider.dimensionId;
        if (closest == null) {
            // deixamos de forcar: o clima volta ao ciclo natural do mundo
            if (Boolean.TRUE.equals(FORCED.remove(Long.valueOf(key)))) {
                info.setRaining(false);
                info.setThundering(false);
            }
            return;
        }

        FORCED.put(Long.valueOf(key), Boolean.TRUE);

        if (WindConfig.stormForceWeather) {
            boolean heavy = reach > 0.35;
            int rain = (int) (RAIN_REFRESH * (0.4 + 0.6 * reach));
            info.setRaining(true);
            info.setRainTime(Math.max(info.getRainTime(), rain));
            info.setThundering(heavy);
            if (heavy) {
                int thunder = (int) (THUNDER_REFRESH * (0.4 + 0.6 * reach));
                info.setThunderTime(Math.max(info.getThunderTime(), thunder));
            }
        }

        if (WindConfig.stormLightning && info.isThundering()) {
            strikeNear(world, active, time, closest, reach);
        }
    }

    /**
     * Sorteia relampagos na parede do olho.
     *
     * <p>A probabilidade cresce com a intensidade do sistema e com a proximidade
     * do jogador, e o ponto sorteado e rejeitado se cair dentro do olho (onde ha
     * subsidencia, ou seja, ar descendo: nao ha relampago) ou fora da banda de
     * conveccao.</p>
     */
    private static void strikeNear(World world, List<StormSystem> active, long time,
                                   StormSystem main, double reach) {
        Random rand = world.rand;
        double deficit = main.centralDeficitHpa(time);
        // sistemas fracos mal produzem descargas; um furacao maior produz muitas
        double chance = 0.012 * reach * (deficit / 30.0);
        if (rand.nextDouble() >= chance) return;

        List players = world.playerEntities;
        if (players.isEmpty()) return;
        EntityPlayer p = (EntityPlayer) players.get(rand.nextInt(players.size()));

        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            double dist = 18.0 + rand.nextDouble() * (STRIKE_RANGE - 18.0);
            int x = (int) Math.floor(p.posX + Math.cos(angle) * dist);
            int z = (int) Math.floor(p.posZ + Math.sin(angle) * dist);
            if (!world.blockExists(x, 64, z)) continue;

            // o raio so cai na banda convectiva, nunca no olho calmo
            boolean inBand = false;
            for (int i = 0; i < active.size(); i++) {
                StormSystem s = active.get(i);
                double r = s.distanceTo(time, x, z);
                double rmax = Math.max(1.0, s.rmaxAt(time));
                if (r > 0.55 * rmax && r < 2.4 * rmax) {
                    inBand = true;
                    break;
                }
            }
            if (!inBand) continue;

            int y = world.getPrecipitationHeight(x, z);
            world.addWeatherEffect(new EntityLightningBolt(world, x + 0.5, y, z + 0.5));
            return;
        }
        // nenhum ponto valido: nada acontece neste tick
    }

    /** Esquece o estado forcado de uma dimensao (chamado ao descarregar o mundo). */
    public static void forget(World world) {
        FORCED.remove(Long.valueOf(world.provider.dimensionId));
    }
}
