package com.dynamicwinds.client.render;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntitySnowShovelFX;
import net.minecraft.world.World;

public class MixinEntitySnowShovelFX {
    public static void applyWind(EntitySnowShovelFX self, World world, double x, double y, double z) {
        WindSample s = Wind.getWindAt(world, x, y, z);
        self.motionX += s.vector.x * 0.015;
        self.motionZ += s.vector.z * 0.015;
    }
}
