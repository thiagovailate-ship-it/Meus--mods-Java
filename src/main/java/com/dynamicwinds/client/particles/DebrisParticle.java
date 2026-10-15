package com.dynamicwinds.client.particles;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class DebrisParticle extends EntityFX {
    public DebrisParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 80;
        this.particleScale = 0.5f;
        this.motionY = -0.02;
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        motionX += (Math.random()-0.5)*0.05;
        motionZ += (Math.random()-0.5)*0.05;
    }
    @Override
    public int getFXLayer() { return 1; }
}
