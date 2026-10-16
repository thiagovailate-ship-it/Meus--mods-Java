package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Random;

/**
 * Emissao de particulas dos sistemas de tempo severo.
 *
 * <p>Os parametros vem do proprio sistema (raio do vento maximo, intensidade,
 * posicao do olho), e nao de numeros fixos: a particula nasce onde o modelo diz
 * que o vento e forte. Assim, quando o sistema se intensifica ou se desloca, o
 * efeito acompanha sem nenhum ajuste manual.</p>
 */
public final class TornadoVFX {

    private static final Random RND = new Random();

    private TornadoVFX() {}

    public static void tick() {
        if (!WindConfig.enableParticles) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        long time = mc.theWorld.getTotalWorldTime();
        StormField field = Wind.stormsFor(mc.theWorld);
        List<StormSystem> active = field.activeAt(time);
        if (active.isEmpty()) return;

        double maxDist = WindConfig.tornadoMaxViewDistance();
        for (int i = 0; i < active.size(); i++) {
            StormSystem system = active.get(i);
            double cx = system.centerX(time);
            double cz = system.centerZ(time);
            double dist = Math.hypot(cx - mc.thePlayer.posX, cz - mc.thePlayer.posZ);
            if (dist > maxDist + system.outerRadius()) continue;
            emit(mc, system, time, cx, cz, dist, maxDist);
        }
    }

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

        for (int i = 0; i < rate; i++) {
            double angle = (2.0 * Math.PI * i) / rate + RND.nextDouble() * 0.3;
            double radius = wallRadius + RND.nextDouble() * spread;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double y = groundY + RND.nextDouble() * height;
            TornadoParticle p = new TornadoParticle(mc.theWorld, x, y, z, radius, angle);
            mc.effectRenderer.addEffect(p);
        }

        if (RND.nextDouble() < 0.15 * WindConfig.tornadoDebrisRate() * intensity) {
            double angle = RND.nextDouble() * 2.0 * Math.PI;
            double radius = RND.nextDouble() * wallRadius * 1.2;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double y = groundY + RND.nextDouble() * 10.0;
            mc.effectRenderer.addEffect(new DebrisParticle(mc.theWorld, x, y, z));
        }
    }
}
