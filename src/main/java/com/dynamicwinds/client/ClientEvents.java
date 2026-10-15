package com.dynamicwinds.client;

import com.dynamicwinds.client.audio.WindAudio;
import com.dynamicwinds.client.hud.DebugHud;
import com.dynamicwinds.client.particles.WindVFX;
import com.dynamicwinds.client.particles.TornadoVFX;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.tileentity.ClientRegistry;

public class ClientEvents {
    public static void register() {
        ClientRegistry.bindTileEntitySpecialRenderer(
                com.dynamicwinds.content.tileentities.TileEntityWindVane.class,
                new com.dynamicwinds.client.render.tesr.TESRWindVane());
        ClientRegistry.bindTileEntitySpecialRenderer(
                com.dynamicwinds.content.tileentities.TileEntityWindsock.class,
                new com.dynamicwinds.client.render.tesr.TESRWindsock());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClientEvents());
        DebugHud.register();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            WindVFX.tick();
            TornadoVFX.tick();
            WindAudio.update();
        }
    }
}
