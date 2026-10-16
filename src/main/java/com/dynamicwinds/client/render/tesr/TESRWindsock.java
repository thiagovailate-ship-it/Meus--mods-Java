package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TESRWindsock extends TileEntitySpecialRenderer {
    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "blocks/windsock");

    private static final VertexFormat SIMPLE_FORMAT = makeSimpleFormat();

    private static VertexFormat makeSimpleFormat() {
        VertexFormat fmt = new VertexFormat();
        fmt.addElement(new VertexFormatElement(0, VertexFormatElement.VertexStep.SINGLE,
                VertexFormatElement.NormalizedBit.NONE, GL11.GL_FLOAT, 3, 0)); // position
        fmt.addElement(new VertexFormatElement(0, VertexFormatElement.VertexStep.SINGLE,
                VertexFormatElement.NormalizedBit.NONE, GL11.GL_FLOAT, 4, 12)); // color
        return fmt;
    }

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage) {
        TileEntityWindsock ws = (TileEntityWindsock) te;
        float inflation = Math.min(1.0f, ws.inflation);
        
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        
        bindTexture(TEX);
        
        Tessellator tess = Tessellator.instance;
        net.minecraft.client.renderer.WorldRenderer buf = tess.getWorldRenderer();
        buf.begin(GL11.GL_QUADS, SIMPLE_FORMAT);
        
        // Base
        renderBox(buf, 0.4, 0.0, 0.4, 1.0, 0.2, 1.0, 0.7f, 0.7f, 0.7f, 1.0f);
        
        // Sock body - inflated based on wind
        float width = 0.6f + inflation * 0.4f;
        float height = 0.6f + inflation * 0.6f;
        float depth = 0.6f + inflation * 0.3f;
        
        renderBox(buf, 0.5 - width/2, 0.2, 0.5 - depth/2,
                  0.5 + width/2, 0.2 + height, 0.5 + depth/2,
                  0.8f, 0.4f, 0.2f, 1.0f);
        
        // tapered end
        float endX = 0.5f + width/2 + inflation * 0.3f;
        float endZ = 0.5f + depth/2 + inflation * 0.2f;
        renderBox(buf, 0.5 + width/2 - 0.1f, 0.2f + height * 0.9f, 0.5 + depth/2 - 0.1f,
                  endX, 0.2f + height, endZ,
                  0.6f, 0.3f, 0.2f, 1.0f);
        
        tess.draw();
        GL11.glPopMatrix();
    }
    
    private void renderBox(net.minecraft.client.renderer.WorldRenderer buf,
                           double x1, double y1, double z1, double x2, double y2, double z2,
                           float r, float g, float b, float a) {
        buf.pos(x1, y1, z2).color(r, g, b, a).endVertex();
        buf.pos(x2, y1, z2).color(r, g, b, a).endVertex();
        buf.pos(x2, y2, z2).color(r, g, b, a).endVertex();
        buf.pos(x1, y2, z2).color(r, g, b, a).endVertex();
    }
    
    @Override
    public boolean isGlobalRenderer() { return false; }
}
