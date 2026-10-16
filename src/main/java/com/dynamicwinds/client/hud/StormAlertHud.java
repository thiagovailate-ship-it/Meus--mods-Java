package com.dynamicwinds.client.hud;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.SurfaceRoughness;
import com.dynamicwinds.core.Vec3;
import com.dynamicwinds.integration.StormLanguage;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.List;

/** Player-facing storm warning, refreshed at most twice per second. */
public final class StormAlertHud {
    private long lastUpdate = Long.MIN_VALUE;
    private final List<String> cachedLines = new ArrayList<String>();

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new StormAlertHud());
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        long now = mc.theWorld.getTotalWorldTime();
        if (now != lastUpdate && (lastUpdate == Long.MIN_VALUE || now - lastUpdate >= 10L)) {
            update(mc, now);
            lastUpdate = now;
        }
        event.left.addAll(cachedLines);
    }

    private void update(Minecraft mc, long now) {
        cachedLines.clear();
        StormField field = Wind.stormsFor(mc.theWorld);
        if (field == null) return;
        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        SurfaceRoughness roughness = Wind.surfaceEnergyFor(mc.theWorld);
        List<StormSystem> systems = field.activeAt(now, roughness);
        StormSystem chosen = null;
        double chosenDistance = Double.MAX_VALUE;
        double etaSeconds = Double.MAX_VALUE;
        double predicted = 0.0;
        for (StormSystem storm : systems) {
            double distance = storm.distanceTo(now, px, pz);
            double speed = StormKind.translationSpeed(storm.kind);
            double eta = speed > 0.0 ? distance / (speed * 20.0) : Double.MAX_VALUE;
            if (distance > storm.outerRadius() + speed * 20.0 * 120.0) continue;
            if (eta >= etaSeconds) continue;
            chosen = storm;
            chosenDistance = distance;
            etaSeconds = eta;
            predicted = forecastAtPlayer(storm, now, px, py, pz, roughness, mc);
        }
        if (chosen == null) return;

        double dx = chosen.centerX(now) - px;
        double dz = chosen.centerZ(now) - pz;
        double bearing = Math.toDegrees(Math.atan2(dx, dz));
        double relative = bearing - mc.thePlayer.rotationYaw;
        while (relative <= -180.0) relative += 360.0;
        while (relative > 180.0) relative -= 360.0;
        long etaTicks = (long) Math.max(0.0, etaSeconds * 20.0);
        double vmaxKmh = chosen.vmaxAt(now + etaTicks) * 3.6;
        double wallDistance = Math.abs(chosenDistance - chosen.rmaxAt(now));
        boolean emergency = wallDistance <= 60.0 || etaSeconds < 60.0;
        boolean alert = chosenDistance <= chosen.outerRadius();
        EnumChatFormatting color = emergency ? EnumChatFormatting.RED
                : alert ? EnumChatFormatting.GOLD : EnumChatFormatting.YELLOW;
        if (emergency && (now / 10L) % 2L == 0L) color = EnumChatFormatting.DARK_RED;

        String level = emergency ? StormLanguage.text("alert.emergency")
                : alert ? StormLanguage.text("alert.alert") : StormLanguage.text("alert.warning");
        cachedLines.add(color + "[DynamicWinds] " + StormLanguage.name(chosen.kind) + " - " + level);
        cachedLines.add(color + StormLanguage.text("alert.position", chosenDistance, relative, etaSeconds));
        cachedLines.add(color + StormLanguage.text("alert.wind", vmaxKmh, predicted * 3.6));
    }

    private static double forecastAtPlayer(StormSystem storm, long now, double x, double y, double z,
                                           SurfaceRoughness roughness, Minecraft mc) {
        int surface = Wind.surfaceHeightAt(mc.theWorld, (int) Math.floor(x), (int) Math.floor(z));
        double energy = roughness.energyAt(x, z);
        double max = 0.0;
        for (int step = 0; step <= 120; step++) {
            long future = now + step * 20L;
            Vec3 wind = storm.wind(future, x, y, z, surface, energy);
            double speed = wind.length();
            if (speed > max) max = speed;
        }
        return max;
    }
}
