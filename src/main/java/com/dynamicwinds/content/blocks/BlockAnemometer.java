package com.dynamicwinds.content.blocks;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockAnemometer extends Block {
    public BlockAnemometer() {
        super(Material.iron);
        setTextureName("dynamicwinds:blocks/anemometer");
        setBlockName("anemometer");
        setHardness(2.0f);
        setTickRandomly(true);
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
}
