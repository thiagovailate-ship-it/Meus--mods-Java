package com.dynamicwinds.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gerenciador dos leitos sonoros continuos.
 *
 * <p>Cada leito e pedido por um nome estavel (chave) a cada tick. Quem nao for
 * pedido em um tick comeca a sumir, e some de vez quando chega a zero: e assim
 * que a troca de faixas fica sem corte. Nao existe "parar" explicito - o leito
 * se encerra sozinho, e o proprio gerenciador de som recolhe o canal quando
 * {@code isDonePlaying()} passa a verdadeiro.</p>
 *
 * <p>Este tambem e o lugar do <b>abrigo</b>: a atenuacao por estar sob teto ou
 * no subsolo e a mesma para todos os efeitos do mod (vento, chuva, trovao e
 * detritos), entao ela mora aqui e e calculada uma vez por tick.</p>
 */
public final class AudioBeds {

    /** Tempo de subida e descida do ganho de cada leito, em segundos. */
    private static final float FADE_SECONDS = 2.5F;
    /** De quanto em quanto tempo se verifica se um canal sobreviveu. */
    private static final int RETRY_TICKS = 20;
    /** Abrigo minimo: mesmo no fundo de uma caverna algo chega aos ouvidos. */
    private static final float MIN_SHELTER = 0.12F;

    private static final Map<String, AmbientBed> BEDS = new LinkedHashMap<String, AmbientBed>();
    private static long tickId;
    private static float shelter = 1.0F;
    private static long shelterTick = -1L;

    private AudioBeds() {}

    /** Marca o inicio do tick: os leitos que nao forem pedidos agora vao sumir. */
    public static void beginTick() {
        tickId++;
    }

    /**
     * Pede um leito ativo neste tick.
     *
     * <p>Se a chave ja existe com outro arquivo, o leito antigo e encerrado e um
     * novo entra no lugar: trocar de faixa e trocar de material, nao ha como
     * reaproveitar o canal.</p>
     *
     * @param key      identificador estavel do leito (ex.: "wind_forte")
     * @param location arquivo de som a repetir
     * @param gain     ganho alvo, de 0 a 1
     * @param pitch    altura da reproducao
     */
    public static void request(String key, ResourceLocation location, float gain, float pitch) {
        AmbientBed bed = BEDS.get(key);
        if (bed != null && !bed.location().equals(location)) {
            bed.kill();
            BEDS.remove(key);
            bed = null;
        }
        if (bed == null) {
            bed = new AmbientBed(location, pitch, FADE_SECONDS);
            BEDS.put(key, bed);
        }
        bed.setPitch(pitch);
        bed.setShelter(shelter());
        bed.setTarget(gain);
        bed.requested(tickId);
    }

    /** Aplica os pedidos do tick: inicia canais novos, apaga quem ficou para tras. */
    public static void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.getSoundHandler() == null) {
            BEDS.clear();
            return;
        }
        Iterator<Map.Entry<String, AmbientBed>> it = BEDS.entrySet().iterator();
        while (it.hasNext()) {
            AmbientBed bed = it.next().getValue();
            if (bed.requestedTick() != tickId) {
                bed.fadeOut();
            }
            if (bed.isDonePlaying()) {
                it.remove();
                continue;
            }
            if (!bed.started()) {
                // enquanto o canal nao existe, quem faz o ganho subir somos nos:
                // o gerenciador so chama update() depois que o som esta tocando
                bed.update();
                if (bed.gain() >= AmbientBed.START_GAIN) {
                    mc.getSoundHandler().playSound(bed);
                    bed.markStarted();
                }
            } else if (tickId % RETRY_TICKS == 0L && !mc.getSoundHandler().isSoundPlaying(bed)) {
                // Canal recusado (volume mestre em zero, por exemplo) ou ja
                // encerrado: voltar a marcar como parado faz o bloco acima
                // recria o canal no proximo tick, sem insistir a cada tick.
                bed.markStopped();
            }
        }
    }

    /**
     * Abrigo do jogador: quanto do som do tempo chega ate ele.
     *
     * <p>Um teto de um bloco quase nao muda nada; vinte blocos de pedra em cima
     * abafam quase tudo. O modelo e linear na profundidade, com piso, porque a
     * queda real e exponencial e a linear ja e conservadora no comeco - e o que
     * interessa aqui e a diferenca entre "embaixo de uma arvore" e "no fundo de
     * uma caverna".</p>
     */
    public static float shelter() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return 1.0F;
        }
        long now = mc.theWorld.getTotalWorldTime();
        if (now == shelterTick) {
            return shelter;
        }
        shelterTick = now;
        int x = MathHelper.floor_double(mc.thePlayer.posX);
        int y = MathHelper.floor_double(mc.thePlayer.posY);
        int z = MathHelper.floor_double(mc.thePlayer.posZ);
        int top = mc.theWorld.getHeightValue(x, z);
        double depth = top - (y + 1);
        if (depth <= 0.0D) {
            shelter = 1.0F;
        } else {
            float s = (float) (1.0D - depth * 0.06D);
            shelter = s < MIN_SHELTER ? MIN_SHELTER : s;
        }
        return shelter;
    }
}
