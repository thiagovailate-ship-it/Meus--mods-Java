package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindVane;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class TESRWindVane extends TileEntitySpecialRenderer {
    private static final ResourceLocation TEX =
        new ResourceLocation("dynamicwinds", "blocks/wind_vane");

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
        TileEntityWindVane vane = (TileEntityWindVane) te;
        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        
        // Rotate to face wind direction
        GL11.glRotatef(-vane.angle * 180.0f / (float) Math.PI, 0, 1, 0);
        
        bindTexture(TEX);
        
        Tessellator tess = Tessellator.instance;
        net.minecraft.client.renderer.WorldRenderer buf = tess.getWorldRenderer();
        buf.begin(GL11.GL_QUADS, SIMPLE_FORMAT);
        
        // Base box
        renderBox(buf, 0.4, 0.0, 0.4, 1.0, 1.0, 1.0, 0.8f, 0.8f, 0.8f, 1.0f);
        
        // Arrow shaft
        renderBox(buf, 0.4, 0.1, 0.9, 0.5, 1.2, 1.1, 0.6f, 0.6f, 0.6f, 1.0f);
        
        // Arrow head
        renderBox(buf, 0.3, 1.2, 0.8, 0.6, 1.6, 1.2, 0.4f, 0.4f, 0.4f, 1.0f);
        
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
