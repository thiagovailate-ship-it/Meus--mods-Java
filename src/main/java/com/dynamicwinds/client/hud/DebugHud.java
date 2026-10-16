package com.dynamicwinds.client.hud;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.client.storm.StormClientState;
import com.dynamicwinds.core.StormKind;
import com.dynamicwinds.core.StormSystem;
import com.dynamicwinds.core.WindSample;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * HUD de depuracao (ligado por {@code debugHud} na config).
 *
 * <p>Mostra o vento no ponto do jogador e, quando ha um sistema por perto, os
 * numeros fisicos dele: categoria, deficit central de pressao, vento de pico,
 * distancia ao centro e a que fracao do raio externo o jogador esta. Serve para
 * conferir no jogo o que os testes verificam no codigo - por exemplo, que o
 * deficit cresce do vortice para o furacao maior, e que o vento cai quando o
 * sistema entra em terra.</p>
 */
public class DebugHud {

    public static void register() { MinecraftForge.EVENT_BUS.register(new DebugHud()); }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Text e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (!com.dynamicwinds.integration.WindConfig.debugHud) return;

        WindSample s = Wind.getWindAt(mc.theWorld, mc.thePlayer.posX,
                mc.thePlayer.posY, mc.thePlayer.posZ);
        e.left.add(String.format("Vento: %.2f m/s (rajada %.2f)", s.speed, s.gust));
        e.left.add(String.format("vec=(%.2f, %.2f, %.2f)", s.vector.x, s.vector.y, s.vector.z));

        StormSystem storm = StormClientState.current();
        if (storm == null) {
            e.left.add("Tempestade: nenhuma no alcance");
            return;
        }
        long time = mc.theWorld.getTotalWorldTime();
        double outer = storm.outerRadius();
        double dist = storm.distanceTo(time, mc.thePlayer.posX, mc.thePlayer.posZ);
        e.left.add(String.format("Tempestade: %s [%s] #%d",
                StormKind.name(storm.kind), StormKind.category(storm.kind), storm.id));
        e.left.add(String.format("  vmax=%.1f m/s  deficit=%.1f hPa  rmax=%.0f",
                storm.vmaxAt(time), storm.centralDeficitHpa(time), storm.rmaxAt(time)));
        e.left.add(String.format("  centro a %.0f blocos (%.0f%% do raio externo)",
                dist, outer > 0.0 ? 100.0 * dist / outer : 0.0));
        e.left.add(String.format("  intensidade=%.2f  %s",
                storm.intensityAt(time), storm.playerSpawned ? "invocada" : "natural"));
        e.left.add(String.format("  nevoa=%.2f", StormClientState.fogStrength()));
    }
}
