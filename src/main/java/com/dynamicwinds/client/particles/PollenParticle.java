package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class PollenParticle extends EntityFX {
    public PollenParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 100;
        this.particleScale = 0.3f;
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        WindSample s = Wind.getWindAt(worldObj, posX, posY, posZ);
        motionX = s.vector.x * 0.01;
        motionY = s.vector.y * 0.01 + 0.005;
        motionZ = s.vector.z * 0.01;
    }
    @Override
    public int getFXLayer() { return 1; }
}
