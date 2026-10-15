package com.dynamicwinds.client.render;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

// NOTE: Real mixin application requires the SpongePowered Mixin library on the
// classpath. The render logic below is kept as a callable helper so the project
// builds without that dependency; re-add @Mixin/@Inject annotations when the
// library is available.
public class MixinRenderGlobal {
    public static void tiltClouds(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return;
        WindSample s = Wind.getWindAt(mc.theWorld,
                mc.renderViewEntity.posX, mc.renderViewEntity.posY, mc.renderViewEntity.posZ);
        double angle = Math.atan2(s.vector.z, s.vector.x);
        GL11.glPushMatrix();
        GL11.glRotated(Math.toDegrees(angle) * 0.15, 0, 1, 0);
        // matching popMatrix is expected to be called by the caller after render
    }
}
