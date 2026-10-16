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
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityWindVane vane = (TileEntityWindVane) te;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        
        GL11.glRotatef(-vane.angle * 180.0f / (float) Math.PI, 0, 1, 0);
        
        bindTexture(TEX);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        
        // Base box with texture mapping
        renderTexturedBox(0.3f, 0.0f, 0.3f, 0.9f, 1.0f, 0.9f);
        
        // Arrow shaft
        renderTexturedBox(0.4f, 0.1f, 0.85f, 0.5f, 1.2f, 1.15f);
        
        // Arrow head
        renderTexturedBox(0.3f, 1.2f, 0.8f, 0.6f, 1.6f, 1.2f);
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
    
    private void renderTexturedBox(float x1, float y1, float z1, float x2, float y2, float z2) {
        GL11.glBegin(GL11.GL_QUADS);
        // Front face (z+)
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x1, y1, z2);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x2, y1, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x2, y2, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x1, y2, z2);
        // Back face (z-)
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x2, y1, z1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x2, y2, z1);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x1, y2, z1);
        // Top face (y+)
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x1, y2, z1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x1, y2, z2);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x2, y2, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x2, y2, z1);
        // Bottom face (y-)
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x1, y1, z2);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x2, y1, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x2, y1, z1);
        // Left face (x-)
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x1, y1, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x1, y2, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x1, y2, z1);
        // Right face (x+)
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x2, y1, z1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x2, y1, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x2, y2, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x2, y2, z1);
        GL11.glEnd();
    }
}
