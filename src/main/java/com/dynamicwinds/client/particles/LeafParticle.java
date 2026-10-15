package com.dynamicwinds.client.particles;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public class LeafParticle extends EntityFX {
    public LeafParticle(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.particleMaxAge = 80;
        this.particleScale = 0.6f;
        this.motionY = 0.02;
    }
    @Override
    public void onUpdate() {
        super.onUpdate();
        WindSample s = Wind.getWindAt(worldObj, posX, posY, posZ);
        motionX = s.vector.x * 0.02;
        motionZ = s.vector.z * 0.02;
        motionY += 0.001 * (s.vector.y);
        posX += motionX; posY += motionY; posZ += motionZ;
        particleAge++;
        if (particleAge >= particleMaxAge) setDead();
    }
    @Override
    public int getFXLayer() { return 1; }
}
