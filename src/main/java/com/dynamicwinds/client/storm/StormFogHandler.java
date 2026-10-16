package com.dynamicwinds.client.storm;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;

/**
 * Escurece e fecha a nevoa dentro da tempestade.
 *
 * <p>A nevoa e a forma mais barata e mais convincente de dizer "voce esta dentro
 * disso": o horizonte some, a luz cai e o alcance de visao encolhe. O valor vem
 * de {@link StormClientState#fogStrength()}, que cresce para o centro do sistema
 * e reabre no olho - o mesmo dado que decide o resto do VFX, para nevoa, som e
 * nuvem nunca discordarem entre si.</p>
 */
public class StormFogHandler {

    /** Cor de nevoa dentro da tempestade (cinza-azulado de chuva forte). */
    private static final float FOG_R = 0.20F;
    private static final float FOG_G = 0.23F;
    private static final float FOG_B = 0.28F;
    /**
     * Densidade de nevoa no pico da tempestade.
     *
     * <p>Valores absolutos, e nao um acrescimo ao valor do jogo: ao cancelar o
     * evento, quem manda no modo de nevoa somos nos (EXPC), e somar a um valor
     * pensado para nevoa linear daria um resultado imprevisivel.</p>
     */
    private static final float MIN_DENSITY = 0.010F;
    private static final float MAX_DENSITY = 0.095F;
    /**
     * Cor da nevoa durante o clarao de um relampago.
     *
     * <p>Nao e branco puro: o que se ve e a nuvem acesa por dentro, um cinza
     * claro que lava a cena por um piscar sem virar tela branca.</p>
     */
    private static final float FLASH_R = 0.62F;
    private static final float FLASH_G = 0.66F;
    private static final float FLASH_B = 0.72F;

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors e) {
        float f = StormClientState.fogStrength();
        if (f > 0.001F) {
            e.red = lerp(e.red, FOG_R, f);
            e.green = lerp(e.green, FOG_G, f);
            e.blue = lerp(e.blue, FOG_B, f);
        }
        // o clarao acontece mesmo com a nevoa fraca: um relampago distante
        // acende o horizonte antes de qualquer outra coisa
        float flash = StormClientState.flashStrength();
        if (flash > 0.001F) {
            float t = flash * 0.8F;
            e.red = lerp(e.red, FLASH_R, t);
            e.green = lerp(e.green, FLASH_G, t);
            e.blue = lerp(e.blue, FLASH_B, t);
        }
    }

    @SubscribeEvent
    public void onFogDensity(EntityViewRenderEvent.FogDensity e) {
        float f = StormClientState.fogStrength();
        if (f <= 0.001F) return;
        // a nevoa fica mais fechada, mas com teto: mais que isso o jogador nao
        // consegue nem ver a propria mao
        // quadrado: a nevoa cresce devagar na periferia e fecha rapido no nucleo
        float flash = StormClientState.flashStrength();
        e.density = (MIN_DENSITY + (MAX_DENSITY - MIN_DENSITY) * f * f) * (1.0F - 0.5F * flash);
        e.setCanceled(true);   // nossa densidade substitui a do jogo
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
