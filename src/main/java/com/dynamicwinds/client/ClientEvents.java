package com.dynamicwinds.client;

import com.dynamicwinds.client.audio.WindAudio;
import com.dynamicwinds.client.hud.DebugHud;
import com.dynamicwinds.client.particles.WindVFX;
import com.dynamicwinds.client.particles.TornadoVFX;
import com.dynamicwinds.content.tileentities.TileEntityWindVane;
import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import com.dynamicwinds.client.render.tesr.TESRWindVane;
import com.dynamicwinds.client.render.tesr.TESRWindsock;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntitySpecialRenderer;

import java.lang.reflect.Field;
import java.util.Map;

public class ClientEvents {
    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClientEvents());
        DebugHud.register();
        registerTESR(TileEntityWindVane.class, new TESRWindVane());
        registerTESR(TileEntityWindsock.class, new TESRWindsock());
    }

    private static void registerTESR(Class<? extends net.minecraft.tileentity.TileEntity> teClass,
                                      TileEntitySpecialRenderer<?> renderer) {
        try {
            Field field = TileEntityRendererDispatcher.class.getDeclaredField("mapSpecialRenderers");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Class<? extends net.minecraft.tileentity.TileEntity>, TileEntitySpecialRenderer<?>> map =
                (Map<Class<? extends net.minecraft.tileentity.TileEntity>, TileEntitySpecialRenderer<?>>) field.get(null);
            map.put(teClass, renderer);
        } catch (Exception e) {
            System.err.println("DynamicWinds: Failed to register TESR for " + teClass.getSimpleName() + ": " + e);
        }
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
