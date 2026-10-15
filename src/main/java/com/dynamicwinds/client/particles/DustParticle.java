package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class DustParticle extends EntityFX {
    public DustParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 60;
        this.particleScale = 0.4f;
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        WindSample s = Wind.getWindAt(worldObj, posX, posY, posZ);
        motionX = s.vector.x * 0.015;
        motionY = s.vector.y * 0.015;
        motionZ = s.vector.z * 0.015;
    }
    @Override
    public int getFXLayer() { return 1; }
}
