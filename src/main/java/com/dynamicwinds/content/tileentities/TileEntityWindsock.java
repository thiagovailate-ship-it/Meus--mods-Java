package com.dynamicwinds.content.tileentities;

import com.dynamicwinds.api.Wind;
import net.minecraft.tileentity.TileEntity;

public class TileEntityWindsock extends TileEntity {
    public float inflation = 0f;
    @Override
    public void updateEntity() {
        if (worldObj == null) return;
        com.dynamicwinds.core.WindSample s = Wind.getWindAt(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
        inflation = (float) Math.min(1.0, s.speed / 8.0);
    }
}
