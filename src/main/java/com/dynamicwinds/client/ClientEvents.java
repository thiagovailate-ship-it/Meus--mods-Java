package com.dynamicwinds.client;

import com.dynamicwinds.client.audio.WindAudio;
import com.dynamicwinds.client.hud.DebugHud;
import com.dynamicwinds.client.particles.WindVFX;
import com.dynamicwinds.client.particles.TornadoVFX;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientEvents {
    public static void register() {
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
