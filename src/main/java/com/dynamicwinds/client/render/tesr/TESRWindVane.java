package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindVane;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TESRWindVane extends TileEntitySpecialRenderer {
    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "blocks/wind_vane");

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage) {
        TileEntityWindVane vane = (TileEntityWindVane) te;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        
        // Rotate to face wind direction
        GL11.glRotatef(-vane.angle * 180.0f / (float) Math.PI, 0, 1, 0);
        
        bindTexture(TEX);
        
        // Render a simple box using direct GL calls
        renderBox(0.4f, 0.0f, 0.4f, 1.0f, 1.0f, 1.0f, 0.8f, 0.8f, 0.8f);
        renderBox(0.4f, 0.1f, 0.9f, 0.5f, 1.2f, 1.1f, 0.6f, 0.6f, 0.6f);
        renderBox(0.3f, 1.2f, 0.8f, 0.6f, 1.6f, 1.2f, 0.4f, 0.4f, 0.4f);
        
        GL11.glPopMatrix();
    }
    
    private void renderBox(float x1, float y1, float z1, float x2, float y2, float z2,
                           float r, float g, float b) {
        GL11.glColor3f(r, g, b);
        GL11.glBegin(GL11.GL_QUADS);
        // Front face
        GL11.glVertex3f(x1, y1, z2);
        GL11.glVertex3f(x2, y1, z2);
        GL11.glVertex3f(x2, y2, z2);
        GL11.glVertex3f(x1, y2, z2);
        GL11.glEnd();
    }
    
    @Override
    public boolean isGlobalRenderer() { return false; }
}
