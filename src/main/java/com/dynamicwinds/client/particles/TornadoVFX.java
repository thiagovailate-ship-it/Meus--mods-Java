package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.WindSample;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import java.util.List;
import java.util.Random;

/**
 * Emissao de particulas dos sistemas de tempo severo.
 *
 * <p>Nada aqui tem numero fixo de raio ou altura: tudo vem do sistema (raio do
 * vento maximo, intensidade, tipo, centro), de modo que a particula nasce onde o
 * modelo diz que o vento e forte. Quando o sistema se intensifica, se expande ou
 * perde forca em terra, o efeito acompanha sozinho.</p>
 *
 * <p>Tres camadas de efeito:</p>
 * <ul>
 *   <li><b>Poeira e destrocos</b> - giram na parede do olho (no vortice, formam
 *       o funil estreito; no ciclone, o anel largo).</li>
 *   <li><b>Chuva inclinada</b> - a velocidade vem do campo de vento no ponto de
 *       nascimento, entao a chuva inclina e gira junto com o sistema; no olho,
 *       onde o vento e fraco, ela sai quase vertical e some.</li>
 *   <li><b>Respingo</b> - a agua levantada onde a parede do olho toca o chao.</li>
 * </ul>
 */
public final class TornadoVFX {

    private static final Random RND = new Random();
    /** Distancia, em blocos, em que a chuva e emitida em volta da camera. */
    private static final double RAIN_RADIUS = 46.0;
    /** Distancia maxima para emitir respingo. */
    private static final double SPRAY_RADIUS = 64.0;

    private TornadoVFX() {}

    public static void tick() {
        if (!WindConfig.enableParticles) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        long time = mc.theWorld.getTotalWorldTime();
        StormField field = Wind.stormsFor(mc.theWorld);
        if (field == null) return;
        List<StormSystem> active = field.activeAt(time);
        if (active.isEmpty()) return;

        double maxDist = WindConfig.stormMaxViewDistance();
        for (int i = 0; i < active.size(); i++) {
            StormSystem system = active.get(i);
            double cx = system.centerX(time);
            double cz = system.centerZ(time);
            double dist = Math.hypot(cx - mc.thePlayer.posX, cz - mc.thePlayer.posZ);
            if (dist > maxDist + system.outerRadius()) continue;
            emit(mc, system, time, cx, cz, dist, maxDist);
            emitRain(mc, system, time, cx, cz);
        }
    }

    // ------------------------------------------------------------------
    // parede do olho / funil
    // ------------------------------------------------------------------

    private static void emit(Minecraft mc, StormSystem system, long time,
                             double cx, double cz, double dist, double maxDist) {
        double intensity = system.intensityAt(time);
        if (intensity <= 0.01) return;

        double rmax = system.rmaxAt(time);
        int groundY = mc.theWorld.getHeightValue((int) Math.floor(cx), (int) Math.floor(cz));

        // taxa cai com a distancia, mas nunca chega a zero dentro do alcance
        double falloff = maxDist <= 0.0 ? 1.0 : Math.max(0.15, 1.0 - dist / maxDist);
        int baseRate = WindConfig.tornadoParticleRate();
        int rate = (int) Math.round(baseRate * falloff * (0.4 + 0.6 * intensity));
        if (rate < 1) rate = 1;
        if (rate > 24) rate = 24;

        // O vortice e estreito (particulas na parede); o ciclone tem olho grande
        // e bandas, entao as particulas vao para a parede do olho e para fora.
        boolean narrow = system.kind == StormKind.VORTEX;
        double wallRadius = narrow ? rmax * 1.6 : rmax;
        double spread = narrow ? 6.0 : rmax * 0.45;
        double height = narrow ? 24.0 : StormKind.columnTop(system.kind) * 0.35;
        double spin = system.north ? 1.0 : -1.0;

        for (int i = 0; i < rate; i++) {
            // espalhamento em espiral: as particulas nao ficam num anel perfeito,
            // seguem a mesma espiral das bandas de chuva
            double t = RND.nextDouble();
            double angle = (2.0 * Math.PI * i) / rate + RND.nextDouble() * 0.3;
            double radius = wallRadius + RND.nextDouble() * spread;
            if (!narrow) {
                angle += spin * Math.log(1.0 + t) * 1.2;
            }
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double y = groundY + RND.nextDouble() * height;
            TornadoParticle p = new TornadoParticle(mc.theWorld, x, y, z, radius, angle);
            ParticleBudget.submit(mc, p, 1);
        }

        if (RND.nextDouble() < 0.15 * WindConfig.tornadoDebrisRate() * intensity) {
            double angle = RND.nextDouble() * 2.0 * Math.PI;
            double radius = RND.nextDouble() * wallRadius * 1.2;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double y = groundY + RND.nextDouble() * 10.0;
            ParticleBudget.submit(mc, new DebrisParticle(mc.theWorld, x, y, z), 1);
        }

        // respingo na parede do olho, perto o bastante para ser visto
        EntityPlayer player = mc.thePlayer;
        double playerDist = Math.hypot(cx - player.posX, cz - player.posZ);
        if (playerDist < SPRAY_RADIUS && intensity > 0.25) {
            int sprayRate = (int) Math.round(6.0 * intensity);
            for (int i = 0; i < sprayRate; i++) {
                double angle = RND.nextDouble() * 2.0 * Math.PI;
                double radius = rmax * (0.85 + RND.nextDouble() * 0.5);
                double x = cx + Math.cos(angle) * radius;
                double z = cz + Math.sin(angle) * radius;
                if (Math.hypot(x - player.posX, z - player.posZ) > SPRAY_RADIUS) continue;
                int ground = mc.theWorld.getHeightValue((int) Math.floor(x), (int) Math.floor(z));
                ParticleBudget.submit(mc,
                        new SprayParticle(mc.theWorld, x, ground + 0.4, z), 2);
            }
        }
    }

    // ------------------------------------------------------------------
    // chuva inclinada pelo campo de vento
    // ------------------------------------------------------------------

    /**
     * Emite chuva em volta da camera, dentro do sistema.
     *
     * <p>So emite quando o jogador esta dentro do alcance do sistema: chuva a
     * centenas de blocos de distancia nao seria vista e custaria tick de
     * renderizacao de particula sem motivo.</p>
     */
    private static void emitRain(Minecraft mc, StormSystem system, long time,
                                 double cx, double cz) {
        int baseRate = WindConfig.stormRainRate();
        if (baseRate <= 0) return;

        EntityPlayer player = mc.thePlayer;
        double intensity = system.intensityAt(time);
        if (intensity <= 0.05) return;
        double outer = system.outerRadius();
        double distToCenter = Math.hypot(cx - player.posX, cz - player.posZ);
        if (distToCenter > outer) return;

        // dentro do olho a chuva para: o ar desce e as nuvens abrem
        double rmax = system.rmaxAt(time);
        if (distToCenter < rmax * 0.55) return;

        double band = 1.0 - distToCenter / outer;      // 1 no centro, 0 na borda
        int rate = (int) Math.round(baseRate * intensity * (0.35 + 0.65 * band));
        if (rate <= 0) return;

        for (int i = 0; i < rate; i++) {
            double angle = RND.nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(RND.nextDouble()) * RAIN_RADIUS;
            double x = player.posX + Math.cos(angle) * radius;
            double y = player.posY + 8.0 + RND.nextDouble() * 14.0;
            double z = player.posZ + Math.sin(angle) * radius;
            WindSample s = Wind.getWindAt(mc.theWorld, x, y, z);
            ParticleBudget.submit(mc, new RainStreakParticle(mc.theWorld, x, y, z,
                    s.vector.x, s.vector.y, s.vector.z), 3);
        }
    }
}
