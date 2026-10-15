package com.dynamicwinds.content.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class BlockWindVane extends Block {
    public BlockWindVane() {
        super(Material.wood);
        setBlockName("windVane");
        setHardness(1.0f);
    }

    @Override
    public boolean hasTileEntity(int metadata) { return true; }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new com.dynamicwinds.content.tileentities.TileEntityWindVane();
    }
}
