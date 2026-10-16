package com.dynamicwinds.client.particles;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.TextureStitchEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Registro dos sprites das particulas proprias.
 *
 * <p>Este registro e obrigatorio no 1.7.10 e a ausencia dele era um bug silencioso:
 * uma particula com {@code getFXLayer() == 1} e desenhada com o atlas de itens,
 * e sem {@code setParticleIcon} ela nao tem sprite nenhum - o jogo acaba
 * sorteando um icone qualquer. Na pratica, as texturas em
 * {@code textures/particle/} existiam mas nunca apareciam na tela.</p>
 *
 * <p>O {@link TextureStitchEvent.Pre} e o ponto correto para isso porque ele
 * dispara de novo a cada recarga de pacote de texturas; os sprites sao
 * re-registrados automaticamente em vez de guardar referencias que ficam
 * invalidas.</p>
 */
public final class ParticleIcons {

    /** Nomes logico -> arquivo em textures/particle/&lt;nome&gt;.png. */
    private static final String[] NAMES = {
        "debris", "dust", "gust", "leaf", "pollen", "raindrop",
        "rainsplash", "snowflake", "swirl", "tornado", "storm_rain", "spray"
    };

    private static final Map<String, IIcon> ICONS = new HashMap<String, IIcon>();

    /**
     * Construtor publico porque a instancia e registrada no barramento de eventos
     * do Forge (o handler de stitch e um metodo de instancia).
     */
    public ParticleIcons() {}

    /** Handler de stitch: registra os sprites no atlas de itens (camada 1). */
    @SubscribeEvent
    public void onStitch(TextureStitchEvent.Pre e) {
        if (e.map == null || e.map.getTextureType() != 1) return;
        synchronized (ICONS) {
            ICONS.clear();
            for (String name : NAMES) {
                ICONS.put(name, e.map.registerIcon("dynamicwinds:particle/" + name));
            }
        }
    }

    /** Sprite pelo nome, ou null se o atlas ainda nao foi montado. */
    public static IIcon get(String name) {
        synchronized (ICONS) {
            return ICONS.get(name);
        }
    }

    /**
     * Aplica o sprite na particula, se ele ja existir.
     *
     * <p>Nao faz nada quando o atlas ainda nao passou pelo stitch: nesse caso o
     * pior cenario e uma particula com o sprite padrao por um instante, em vez
     * de uma excecao no meio do tick de render.</p>
     */
    public static void apply(EntityFX fx, String name) {
        IIcon icon = get(name);
        if (icon != null) fx.setParticleIcon(icon);
    }
}
