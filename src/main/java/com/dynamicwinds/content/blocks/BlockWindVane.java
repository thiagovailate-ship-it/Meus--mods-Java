package com.dynamicwinds.content.blocks;

import com.dynamicwinds.content.RenderTypes;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

public class BlockWindVane extends Block {

    private IIcon itemIcon;

    public BlockWindVane() {
        super(Material.wood);
        setBlockName("windVane");
        setHardness(1.0f);
        setStepSound(Block.soundTypeWood);
        setCreativeTab(CreativeTabs.tabDecorations);
        setLightOpacity(0);
        // o modelo do instrumento e desenhado pelo TESR; a colisao fica so na
        // base para o jogador nao esbarrar em um cubo invisivel
        setBlockBounds(0.15f, 0.0f, 0.15f, 0.85f, 1.0f, 0.85f);
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
        blockIcon = register.registerIcon("dynamicwinds:wind_vane");
        itemIcon = register.registerIcon("dynamicwinds:wind_vane_item");
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        // usado pelo ItemBlock (via getBlockTextureFromSide) para o icone 2D
        return itemIcon != null ? itemIcon : blockIcon;
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new com.dynamicwinds.content.tileentities.TileEntityWindVane();
    }
}
