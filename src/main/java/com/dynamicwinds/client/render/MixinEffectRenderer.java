package com.dynamicwinds.client.render;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.client.particle.EffectRenderer")
public class MixinEffectRenderer {
    // precipitation deviation handled in particle spawn hooks (placeholder)
}
