package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TESRWindsock extends TileEntitySpecialRenderer {
    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "blocks/windsock");

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage) {
        TileEntityWindsock ws = (TileEntityWindsock) te;
        float inflation = Math.min(1.0f, ws.inflation);
        
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        
        bindTexture(TEX);
        
        Tessellator tess = Tessellator.instance;
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        
        // Base
        renderBox(0.4f, 0.0f, 0.4f, 1.0f, 0.2f, 1.0f, 0.7f, 0.7f, 0.7f, 1.0f);
        
        // Sock body - inflated based on wind
        float width = 0.6f + inflation * 0.4f;
        float height = 0.6f + inflation * 0.6f;
        float depth = 0.6f + inflation * 0.3f;
        
        renderBox(0.5f - width/2, 0.2f, 0.5f - depth/2,
                  0.5f + width/2, 0.2f + height, 0.5f + depth/2,
                  0.8f, 0.4f, 0.2f, 1.0f);
        
        // tapered end
        float endX = 0.5f + width/2 + inflation * 0.3f;
        float endZ = 0.5f + depth/2 + inflation * 0.2f;
        renderBox(0.5f + width/2 - 0.1f, 0.2f + height * 0.9f, 0.5f + depth/2 - 0.1f,
                  endX, 0.2f + height, endZ,
                  0.6f, 0.3f, 0.2f, 1.0f);
        
        tess.draw();
        GL11.glPopMatrix();
    }
    
    private void renderBox(double x1, double y1, double z1, double x2, double y2, double z2,
                           float r, float g, float b, float a) {
        Tessellator tess = Tessellator.instance;
        BufferBuilder buf = tess.getBuffer();
        buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buf.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buf.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buf.pos(x1, y2, z2).color(r, g, b, a).endVertex();
    }
    
    @Override
    public boolean isGlobalRenderer() { return false; }
}
