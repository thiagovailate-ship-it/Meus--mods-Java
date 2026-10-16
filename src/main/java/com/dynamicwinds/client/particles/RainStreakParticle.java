package com.dynamicwinds.client.particles;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/**
 * Chuva que segue o vento de verdade.
 *
 * <p>A chuva do Minecraft cai reta e ignora o campo de vento. Esta particula e
 * lancada com a velocidade obtida do proprio modelo no ponto de nascimento, o
 * que faz a chuva inclinar e girar junto com a tempestade: inclina na direcao
 * do escoamento tangencial (perpendicular ao raio), entra em espiral na camada
 * limite e fica praticamente vertical no olho, onde o vento e calmo.</p>
 *
 * <p>Como a inclinacao vem do campo, ela muda sozinha quando o sistema se
 * intensifica, se desloca ou perde forca em terra - nada disso e animado a
 * mao.</p>
 */
public class RainStreakParticle extends EntityFX {

    /** Fator de conversao do vento (m/s) para o passo do jogo. */
    private static final double WIND_TO_MOTION = 0.045;
    /** Idade da particula em ticks. */
    private static final int LIFE = 7;

    public RainStreakParticle(World world, double x, double y, double z,
                              double vx, double vy, double vz) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.motionX = vx * WIND_TO_MOTION;
        this.motionY = -0.12 + vy * WIND_TO_MOTION;
        this.motionZ = vz * WIND_TO_MOTION;
        this.particleGravity = 0.0F;
        // worldObj.rand em vez de Math.random(): mantem a chuva determinista por
        // mundo e evita disputa no gerador global durante o render
        this.particleMaxAge = LIFE + this.worldObj.rand.nextInt(4);
        this.particleScale = 0.55F + this.worldObj.rand.nextFloat() * 0.25F;
        this.particleRed = 0.72F;
        this.particleGreen = 0.80F;
        this.particleBlue = 0.94F;
        this.noClip = true;
        ParticleIcons.apply(this, "storm_rain");
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.posX += this.motionX;
        this.posY += this.motionY;
        this.posZ += this.motionZ;
        if (this.particleAge++ >= this.particleMaxAge) {
            this.setDead();
        }
    }

    /** Camada de itens: e onde os sprites proprios foram registrados. */
    @Override
    public int getFXLayer() { return 1; }
}
