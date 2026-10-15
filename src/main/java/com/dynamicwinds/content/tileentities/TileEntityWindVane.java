package com.dynamicwinds.content.tileentities;

import com.dynamicwinds.api.Wind;
import net.minecraft.tileentity.TileEntity;

public class TileEntityWindVane extends TileEntity {
    public float angle = 0f;
    @Override
    public void updateEntity() {
        if (worldObj == null) return;
        com.dynamicwinds.core.WindSample s = Wind.getWindAt(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
        angle = (float) Math.atan2(s.vector.z, s.vector.x);
    }
}
