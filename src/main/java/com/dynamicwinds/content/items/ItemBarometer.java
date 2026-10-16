package com.dynamicwinds.content.items;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.WindSample;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

/** Handheld pressure/wind instrument; trend is sampled from deterministic history. */
public final class ItemBarometer extends Item {
    private static final String UNIT_TAG = "dwBarometerUnit";

    public ItemBarometer() {
        setUnlocalizedName("barometer");
        setTextureName("dynamicwinds:barometer");
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.tabTools);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            stack.setTagCompound(nbt);
        }
        int unit = nbt.getByte(UNIT_TAG) & 255;
        if (player.isSneaking()) {
            unit = (unit + 1) % 3;
            nbt.setByte(UNIT_TAG, (byte) unit);
            player.addChatMessage(new ChatComponentText(com.dynamicwinds.integration.StormLanguage.text(
                    unit == 0 ? "barometer.unit.ms" : unit == 1
                            ? "barometer.unit.kmh" : "barometer.unit.hpa")));
            return stack;
        }

        long time = world.getTotalWorldTime();
        double x = player.posX, y = player.posY, z = player.posZ;
        WindSample sample = Wind.getWindAt(world, x, y, z);
        StormField field = Wind.stormsFor(world);
        double deficit = field == null ? 0.0 : field.sampleDeficitHpa(time, x, z);
        double oldDeficit = field == null ? 0.0
                : field.sampleDeficitHpa(Math.max(0L, time - 200L), x, z);
        double trend = deficit - oldDeficit;
        if (unit == 2) {
            String trendKey = trend > 0.02 ? "barometer.falling"
                    : trend < -0.02 ? "barometer.rising" : "barometer.steady";
            player.addChatMessage(new ChatComponentText(com.dynamicwinds.integration.StormLanguage.text(
                    "barometer.pressure", deficit, trend,
                    com.dynamicwinds.integration.StormLanguage.text(trendKey))));
        } else {
            double speed = unit == 1 ? sample.speed * 3.6 : sample.speed;
            player.addChatMessage(new ChatComponentText(com.dynamicwinds.integration.StormLanguage.text(
                    "barometer.wind", speed, unit == 1 ? "km/h" : "m/s", deficit, trend)));
        }
        return stack;
    }
}
