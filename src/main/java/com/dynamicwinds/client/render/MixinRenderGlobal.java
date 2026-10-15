package com.dynamicwinds.client.render;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.renderer.RenderGlobal")
public class MixinRenderGlobal {

    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void dynamicwinds$cloudsHead(float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return;
        WindSample s = Wind.getWindAt(mc.theWorld,
                mc.renderViewEntity.posX, mc.renderViewEntity.posY, mc.renderViewEntity.posZ);
        double angle = Math.atan2(s.vector.z, s.vector.x);
        GL11.glPushMatrix();
        GL11.glRotated(Math.toDegrees(angle) * 0.15, 0, 1, 0);
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void dynamicwinds$cloudsReturn(float partialTicks, CallbackInfo ci) {
        GL11.glPopMatrix();
    }
}
