package com.dynamicwinds.client;

import com.dynamicwinds.client.audio.AudioBeds;
import com.dynamicwinds.client.audio.StormAudio;
import com.dynamicwinds.client.audio.WindAudio;
import com.dynamicwinds.client.particles.ParticleIcons;
import com.dynamicwinds.client.storm.StormClientState;
import com.dynamicwinds.client.storm.StormFogHandler;
import com.dynamicwinds.client.storm.StormRenderer;
import com.dynamicwinds.client.hud.DebugHud;
import com.dynamicwinds.client.particles.WindVFX;
import com.dynamicwinds.client.particles.TornadoVFX;
import com.dynamicwinds.client.render.tesr.TESRAnemometer;
import com.dynamicwinds.client.render.tesr.TESRWindVane;
import com.dynamicwinds.client.render.tesr.TESRWindsock;
import com.dynamicwinds.content.tileentities.TileEntityAnemometer;
import com.dynamicwinds.content.tileentities.TileEntityWindVane;
import com.dynamicwinds.content.tileentities.TileEntityWindsock;
import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

public class ClientEvents {

    /** Reforca o registro depois de o cliente estar de pe (ver {@link #bind}). */
    private static final int REBIND_AFTER_TICKS = 20;
    private static int ticksSinceRegister = 0;
    private static boolean rebound = false;

    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ClientEvents());
        // nevoa, nuvens e sprites das particulas proprias
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new StormFogHandler());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ParticleIcons());
        DebugHud.register();
        registerRenderers();
    }

    /** Nuvens, parede do olho e bandas de chuva, desenhadas depois do mundo. */
    @SubscribeEvent
    public void onRenderWorldLast(net.minecraftforge.client.event.RenderWorldLastEvent e) {
        StormRenderer.render(e.partialTicks);
    }

    /**
     * Associa os TESRs as tile entities dos instrumentos.
     *
     * <p>Antes isso era feito apenas por reflexao, acessando
     * {@code mapSpecialRenderers} como se fosse um campo estatico. Esse campo e
     * de instancia no 1.7.10, entao {@code Field.get(null)} lancava
     * NullPointerException, a excecao era engolida e nenhum TESR era registrado -
     * os instrumentos nunca apareciam no mundo. Alem disso o nome
     * {@code mapSpecialRenderers} nao existe em producao, onde os nomes sao
     * ofuscados, logo reflexao por nome nunca funcionaria fora do ambiente de
     * desenvolvimento.</p>
     *
     * <p>Agora usamos a API do FML ({@link ClientRegistry}), que e remapeada no
     * reobf e portanto funciona tambem no jar de producao. A reflexao fica
     * somente como rede de seguranca, corrigida para o caso de campo de
     * instancia.</p>
     */
    public static void registerRenderers() {
        bind(TileEntityWindVane.class, new TESRWindVane());
        bind(TileEntityWindsock.class, new TESRWindsock());
        bind(TileEntityAnemometer.class, new TESRAnemometer());
        ticksSinceRegister = 0;
    }

    private static void bind(Class<? extends TileEntity> teClass, TileEntitySpecialRenderer renderer) {
        try {
            ClientRegistry.bindTileEntitySpecialRenderer(teClass, renderer);
        } catch (Throwable t) {
            try {
                Field field = TileEntityRendererDispatcher.class.getDeclaredField("mapSpecialRenderers");
                field.setAccessible(true);
                Object target = Modifier.isStatic(field.getModifiers())
                        ? null : TileEntityRendererDispatcher.instance;
                @SuppressWarnings("unchecked")
                Map<Class<? extends TileEntity>, TileEntitySpecialRenderer> map =
                        (Map<Class<? extends TileEntity>, TileEntitySpecialRenderer>) field.get(target);
                map.put(teClass, renderer);
            } catch (Throwable inner) {
                System.err.println("DynamicWinds: failed to register TESR for "
                        + teClass.getSimpleName() + ": " + inner);
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            // rede de seguranca barata: se o dispatcher so existir depois do
            // preInit, o vinculo e refeito no primeiro segundo de jogo
            if (!rebound && ++ticksSinceRegister > REBIND_AFTER_TICKS) {
                rebound = true;
                registerRenderers();
            }
            // pacotes de tempestade invocada chegam pela thread do netty: aqui,
            // no tick do cliente, eles sao aplicados no mundo
            com.dynamicwinds.integration.network.StormNetwork.drainClient(
                    net.minecraft.client.Minecraft.getMinecraft().theWorld);
            // o estado do sistema dominante e recalculado uma vez por tick; o
            // render, a nevoa e o audio consomem este mesmo resultado
            StormClientState.tick();
            WindVFX.tick();
            TornadoVFX.tick();
            // os leitos sao pedidos a cada tick: quem nao for pedido comeca a
            // sumir. beginTick abre a janela, tick a fecha e recolhe o canal
            AudioBeds.beginTick();
            WindAudio.update();
            StormAudio.update();
            AudioBeds.tick();
        }
    }
}
