package com.dynamicwinds.content.blocks;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.content.RenderTypes;
import com.dynamicwinds.core.WindSample;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockAnemometer extends Block {

    private IIcon itemIcon;

    public BlockAnemometer() {
        super(Material.iron);
        setBlockName("anemometer");
        setHardness(2.0f);
        setStepSound(Block.soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
        setLightOpacity(0);
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
        blockIcon = register.registerIcon("dynamicwinds:anemometer");
        itemIcon = register.registerIcon("dynamicwinds:anemometer_item");
    }

    @Override
    public IIcon getIcon(int side, int metadata) {
        return itemIcon != null ? itemIcon : blockIcon;
    }

    @Override
    public boolean canProvidePower() { return true; }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        if (!(world instanceof World)) return 0;
        WindSample s = Wind.getWindAt((World) world, x + 0.5, y + 0.5, z + 0.5);
        int sig = (int) Math.round(Math.min(15, s.speed));
        return sig;
    }

    @Override
    public int isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int side) {
        return isProvidingWeakPower(world, x, y, z, side);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int metadata) {
        return new com.dynamicwinds.content.tileentities.TileEntityAnemometer();
    }
}
