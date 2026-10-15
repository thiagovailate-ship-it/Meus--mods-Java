package com.dynamicwinds.client.audio;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

public class WindAudio {
    private static ISound ambience;
    private static long lastGustTick = -1000;

    public static void update() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        WindSample s = Wind.getWindAt(mc.theWorld, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        // ambience loop
        if (ambience == null || !mc.getSoundHandler().isSoundPlaying(ambience)) {
            ambience = PositionedSoundRecord.create(new ResourceLocation("dynamicwinds:wind_ambience"));
            mc.getSoundHandler().playSound(ambience);
        }
        // adjust volume by speed (best-effort; re-trigger not continuous in 1.7.10)
        if (s.speed > 6.0 && mc.theWorld.getTotalWorldTime() - lastGustTick > 40) {
            lastGustTick = mc.theWorld.getTotalWorldTime();
            PositionedSoundRecord gust = PositionedSoundRecord.create(new ResourceLocation("dynamicwinds:wind_gust"));
            mc.getSoundHandler().playSound(gust);
        }
        if (s.gust > 2.0 && mc.theWorld.getTotalWorldTime() - lastGustTick > 60) {
            PositionedSoundRecord rustle = PositionedSoundRecord.create(new ResourceLocation("dynamicwinds:leaf_rustle"));
            mc.getSoundHandler().playSound(rustle);
        }
    }
}
