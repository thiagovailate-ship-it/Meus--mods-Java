package com.dynamicwinds;

import com.dynamicwinds.integration.WindConfig;
import com.dynamicwinds.integration.WindEngine;
import com.dynamicwinds.integration.commands.WindCommand;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = DynamicWinds.MODID, name = DynamicWinds.NAME, version = DynamicWinds.VERSION, guiFactory = "com.dynamicwinds.client.gui.WindGuiFactory")
public class DynamicWinds {
    public static final String MODID = "dynamicwinds";
    public static final String NAME = "DynamicWinds";
    public static final String VERSION = "1.2.0";

    private final WindEngine engine = new WindEngine();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        WindConfig.load(e.getSuggestedConfigurationFile());
        com.dynamicwinds.integration.network.StormNetwork.register();
        com.dynamicwinds.content.ContentRegistry.register();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(this);
        if (cpw.mods.fml.common.FMLCommonHandler.instance().getSide() == cpw.mods.fml.relauncher.Side.CLIENT) {
            com.dynamicwinds.client.ClientEvents.register();
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent e) {
        if (e.phase == TickEvent.Phase.END) engine.tick(e.world);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        e.registerServerCommand(new WindCommand());
    }
}
