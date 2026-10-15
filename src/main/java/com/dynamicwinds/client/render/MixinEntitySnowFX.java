package com.dynamicwinds.client.render;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.particle.EntitySnowShardFX;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySnowShardFX.class)
public class MixinEntitySnowFX {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void dynamicwinds$snowWind(World world, double x, double y, double z, CallbackInfo ci) {
        EntitySnowShardFX self = (EntitySnowShardFX)(Object)this;
        WindSample s = Wind.getWindAt(world, x, y, z);
        self.motionX += s.vector.x * 0.015;
        self.motionZ += s.vector.z * 0.015;
    }
}
