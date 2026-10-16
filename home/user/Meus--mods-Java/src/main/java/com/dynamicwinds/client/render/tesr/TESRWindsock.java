package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TESRWindsock extends TileEntitySpecialRenderer {
    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "blocks/windsock");

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityWindsock ws = (TileEntityWindsock) te;
        float inflation = Math.min(1.0f, ws.inflation);
        
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        
        bindTexture(TEX);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        
        // Base block
        renderTexturedBox(0.4f, 0.0f, 0.4f, 1.0f, 0.2f, 1.0f);
        
        // Sock body - inflated based on wind
        float width = 0.6f + inflation * 0.4f;
        float height = 0.6f + inflation * 0.6f;
        float depth = 0.6f + inflation * 0.3f;
        
        float cx = 0.5f, cy = 0.2f + height / 2, cz = 0.5f;
        float hx = width / 2, hy = height / 2, hz = depth / 2;
        renderTexturedBox(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
        
        // Tapered end
        float endW = width * 0.6f;
        float endH = height * 0.4f;
        float endD = depth * 0.6f;
        float endX = cx + width / 2 + inflation * 0.3f;
        float endY = cy + height * 0.9f;
        float endZ = cz + depth / 2 + inflation * 0.2f;
        renderTexturedBox(endX - endW / 2, endY - endH / 2, endZ - endD / 2,
                          endX + endW / 2, endY + endH / 2, endZ + endD / 2);
        
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
    
    private void renderTexturedBox(float x1, float y1, float z1, float x2, float y2, float z2) {
        GL11.glBegin(GL11.GL_QUADS);
        // Front (z+)
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x1, y1, z2);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x2, y1, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x2, y2, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x1, y2, z2);
        // Back (z-)
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x2, y1, z1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x2, y2, z1);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x1, y2, z1);
        // Top (y+)
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x1, y2, z1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x1, y2, z2);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x2, y2, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x2, y2, z1);
        // Bottom (y-)
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x1, y1, z2);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x2, y1, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x2, y1, z1);
        // Left (x-)
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x1, y1, z1);
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x1, y1, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x1, y2, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x1, y2, z1);
        // Right (x+)
        GL11.glTexCoord2f(0, 0); GL11.glVertex3f(x2, y1, z1);
        GL11.glTexCoord2f(1, 0); GL11.glVertex3f(x2, y1, z2);
        GL11.glTexCoord2f(1, 1); GL11.glVertex3f(x2, y2, z2);
        GL11.glTexCoord2f(0, 1); GL11.glVertex3f(x2, y2, z1);
        GL11.glEnd();
    }
}
