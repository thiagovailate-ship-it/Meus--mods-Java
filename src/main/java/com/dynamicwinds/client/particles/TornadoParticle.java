package com.dynamicwinds.client.particles;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/**
 * Tornado funnel particle: orbits and rises, simulating the vortex.
 * Manual update (no super.onUpdate) to avoid double position/age updates.
 */
public class TornadoParticle extends EntityFX {
    private double angle;
    private double radius;
    private double riseSpeed;
    private int age;

    public TornadoParticle(World world, double x, double y, double z, double radius, double angle) {
        super(world, x, y, z);
        this.radius = radius;
        this.angle = angle;
        this.riseSpeed = 0.3 + (Math.random() - 0.5) * 0.2;
        this.particleMaxAge = 80;
        this.particleScale = 1.0f + (float) (radius / 12.0);
        this.age = 0;
        // set initial motion to zero so super doesn't drift
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
    }

    @Override
    public void onUpdate() {
        // manually update position and age (don't call super to avoid drift)
        angle += 0.12;
        radius *= 0.99;
        double cx = Math.cos(angle) * radius * 0.5;
        double cz = Math.sin(angle) * radius * 0.5;
        posX += cx;
        posZ += cz;
        posY += riseSpeed;
        age++;
        if (age >= particleMaxAge) {
            setDead();
        }
    }

    @Override
    public int getFXLayer() { return 1; }
}
