package com.dynamicwinds.content.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockWindsock extends Block {
    public BlockWindsock() {
        super(Material.cloth);
        setBlockName("windsock");
        setHardness(0.5f);
    }
    @Override
    public boolean hasTileEntity(int metadata) { return true; }
    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new com.dynamicwinds.content.tileentities.TileEntityWindsock();
    }
}
