package com.dynamicwinds.content;

import com.dynamicwinds.content.blocks.BlockAnemometer;
import com.dynamicwinds.content.blocks.BlockWindVane;
import com.dynamicwinds.content.blocks.BlockWindsock;
import com.dynamicwinds.content.tileentities.TileEntityAnemometer;
import com.dynamicwinds.content.tileentities.TileEntityWindVane;
import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class ContentRegistry {
    public static BlockWindVane windVane;
    public static BlockWindsock windsock;
    public static BlockAnemometer anemometer;

    private ContentRegistry() {}

    public static void register() {
        windVane = new BlockWindVane();
        windsock = new BlockWindsock();
        anemometer = new BlockAnemometer();

        GameRegistry.registerBlock(windVane, "windVane");
        GameRegistry.registerBlock(windsock, "windsock");
        GameRegistry.registerBlock(anemometer, "anemometer");

        GameRegistry.registerTileEntity(TileEntityWindVane.class, "dynamicwinds:windVane");
        GameRegistry.registerTileEntity(TileEntityWindsock.class, "dynamicwinds:windsock");
        GameRegistry.registerTileEntity(TileEntityAnemometer.class, "dynamicwinds:anemometer");

        // recipes
        GameRegistry.addRecipe(new ItemStack(windVane), " S ", " S ", "III",
                'S', Items.stick, 'I', Items.iron_ingot);
        GameRegistry.addRecipe(new ItemStack(windsock), " W ", " W ", " S ",
                'W', Blocks.wool, 'S', Items.stick);
        GameRegistry.addRecipe(new ItemStack(anemometer), " R ", "ICI", " I ",
                'R', Items.redstone, 'I', Items.iron_ingot, 'C', Items.comparator);
    }
}
