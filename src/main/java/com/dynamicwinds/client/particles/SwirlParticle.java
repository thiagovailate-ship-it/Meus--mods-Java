package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class SwirlParticle extends EntityFX {
    public SwirlParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 40;
        this.particleScale = 0.8f;
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        WindSample s = Wind.getWindAt(worldObj, posX, posY, posZ);
        // swirl around vertical axis
        double ang = particleAge * 0.2;
        motionX = Math.cos(ang) * s.speed * 0.02;
        motionZ = Math.sin(ang) * s.speed * 0.02;
        motionY = 0.01;
        posX += motionX; posY += motionY; posZ += motionZ;
        if (particleAge++ >= particleMaxAge) setDead();
    }
    @Override
    public int getFXLayer() { return 1; }
}
