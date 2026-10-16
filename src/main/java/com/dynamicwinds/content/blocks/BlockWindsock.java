package com.dynamicwinds.content.blocks;

import com.dynamicwinds.content.RenderTypes;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockWindsock extends Block {

    private IIcon itemIcon;

    public BlockWindsock() {
        super(Material.cloth);
        setBlockName("windsock");
        setHardness(0.5f);
        setStepSound(Block.soundTypeCloth);
        setCreativeTab(CreativeTabs.tabDecorations);
        setLightOpacity(0);
        setBlockBounds(0.2f, 0.0f, 0.2f, 0.8f, 1.0f, 0.8f);
    }

    @Override
    public int getRenderType() {
        return RenderTypes.DYNAMICWINDS_INSTRUMENT;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        blockIcon = register.registerIcon("dynamicwinds:windsock");
        itemIcon = register.registerIcon("dynamicwinds:windsock_item");
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        return itemIcon != null ? itemIcon : blockIcon;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new com.dynamicwinds.content.tileentities.TileEntityWindsock();
    }
}
