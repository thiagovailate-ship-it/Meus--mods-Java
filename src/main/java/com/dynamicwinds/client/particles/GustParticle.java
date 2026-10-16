package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

public class GustParticle extends EntityFX {
    public GustParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 30;
        this.particleScale = 1.0f;
    
        ParticleIcons.apply(this, "gust");
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        WindSample s = Wind.getWindAt(worldObj, posX, posY, posZ);
        motionX = s.vector.x * 0.05;
        motionY = 0.02;
        motionZ = s.vector.z * 0.05;
    }
    @Override
    public int getFXLayer() { return 1; }
}
