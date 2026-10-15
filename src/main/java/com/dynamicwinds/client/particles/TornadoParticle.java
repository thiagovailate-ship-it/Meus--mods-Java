package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class TornadoParticle extends EntityFX {
    private double angle;
    private double radius;
    private double height;
    public TornadoParticle(World world, double x, double y, double z, double radius, double angle) {
        super(world, x, y, z);
        this.radius = radius;
        this.angle = angle;
        this.height = y;
        this.particleMaxAge = 60;
        this.particleScale = 1.2f;
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        angle += 0.15;
        radius *= 0.98;
        height += 0.3;
        posX = posX + Math.cos(angle)*0.5;
        posZ = posZ + Math.sin(angle)*0.5;
        posY = height;
        if (particleAge++ >= particleMaxAge) setDead();
    }
    @Override
    public int getFXLayer() { return 1; }
}
