package com.dynamicwinds.client.render;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntityRainFX;
import net.minecraft.world.World;

public class MixinEntityRainFX {
    public static void applyWind(EntityRainFX self, World world, double x, double y, double z) {
        WindSample s = Wind.getWindAt(world, x, y, z);
        self.motionX += s.vector.x * 0.02;
        self.motionZ += s.vector.z * 0.02;
    }
}
