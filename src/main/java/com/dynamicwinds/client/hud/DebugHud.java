package com.dynamicwinds.client.hud;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class DebugHud {
    public static void register() { MinecraftForge.EVENT_BUS.register(new DebugHud()); }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        WindSample s = Wind.getWindAt(mc.theWorld, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        e.left.add("Wind: " + String.format("%.2f m/s", s.speed));
        e.left.add(String.format("vec=(%.2f,%.2f,%.2f)", s.vector.x, s.vector.y, s.vector.z));
        e.left.add(String.format("gust=%.2f", s.gust));
    }
}
