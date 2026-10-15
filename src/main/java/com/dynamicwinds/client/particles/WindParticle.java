package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class WindParticle extends EntityFX {
    public WindParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 60;
        this.particleScale = 0.5f;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        WindSample s = Wind.getWindAt(worldObj, posX, posY, posZ);
        motionX = s.vector.x * 0.05;
        motionY = s.vector.y * 0.05;
        motionZ = s.vector.z * 0.05;
        posX += motionX;
        posY += motionY;
        posZ += motionZ;
    }
}
