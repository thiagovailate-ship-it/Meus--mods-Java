package com.dynamicwinds.client.render.tesr;

import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

public class TESRWindsock extends TileEntitySpecialRenderer {
    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        TileEntityWindsock ws = (TileEntityWindsock) te;
        // render fabric inflating by ws.inflation (placeholder)
    }
}
