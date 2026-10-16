package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/**
 * Espuma levantada onde a parede do olho bate no chao ou na agua.
 *
 * <p>E o detalhe que da escala ao sistema: sem ele, a nuvem parece flutuar
 * acima da paisagem sem tocar em nada. O respingo nasce no chao, e levantado
 * pelo vento local (o mesmo campo do resto do mod) e se desfaz em poucos
 * ticks.</p>
 */
public class SprayParticle extends EntityFX {

    /** Impulso inicial para cima, em blocos por tick. */
    private static final double LIFT = 0.16;
    /** Converte vento (m/s) em deslocamento por tick. */
    private static final double WIND_TO_MOTION = 0.03;

    public SprayParticle(World world, double x, double y, double z) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        WindSample s = Wind.getWindAt(world, x, y, z);
        this.motionX = s.vector.x * WIND_TO_MOTION;
        this.motionZ = s.vector.z * WIND_TO_MOTION;
        this.motionY = LIFT + s.vector.y * WIND_TO_MOTION * 0.5;
        this.particleGravity = 0.06F;
        this.particleMaxAge = 16 + world.rand.nextInt(10);
        this.particleScale = 0.7F + world.rand.nextFloat() * 0.6F;
        this.particleRed = 0.88F;
        this.particleGreen = 0.93F;
        this.particleBlue = 1.0F;
        ParticleIcons.apply(this, "spray");
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        // desacelera e cai, como respingo de verdade
        this.motionX *= 0.93;
        this.motionZ *= 0.93;
        this.motionY -= 0.012;
    }

    @Override
    public int getFXLayer() { return 1; }
}
