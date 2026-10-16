package com.dynamicwinds.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.util.ResourceLocation;

/**
 * Leito sonoro continuo: um loop ancorado ao jogador cujo volume muda com o
 * tempo, tick a tick.
 *
 * <p>No 1.7.10 o gerenciador de som mantem uma lista de sons "tickaveis"
 * ({@link ITickableSound}): a cada tick ele chama {@code update()} e, logo
 * depois, reaplica volume, altura e posicao do canal. E isso que permite mixar
 * um leito em tempo real em vez de re-disparar o arquivo inteiro de tempo em
 * tempo - o que sempre deixa emenda audivel.</p>
 *
 * <p>O loop e do proprio OpenAL: {@code canRepeat()} verdadeiro junto com
 * {@code getRepeatDelay() == 0} faz o gerenciador criar a fonte ja em modo de
 * repeticao, sem silencio entre as voltas. Vale a pena registrar o motivo: o
 * arquivo nao pode ser "steamado" para isso funcionar bem, porque fonte em
 * fluxo le o arquivo de novo a cada volta e a emenda volta a aparecer.</p>
 */
public final class AmbientBed implements ITickableSound {

    /** Piso de volume: abaixo disso nada se ouve, mas o canal precisa existir. */
    public static final float MIN_GAIN = 0.002F;
    /** Ganho a partir do qual vale criar o canal (volume zero nao toca). */
    public static final float START_GAIN = 0.02F;

    private final ResourceLocation location;
    private final float step;
    private float pitch;
    private float gain;
    private float target;
    private float shelter = 1.0F;
    private boolean started;
    private boolean done;
    private long requestedTick = -1L;

    /**
     * @param location     arquivo a repetir
     * @param pitch        altura da reproducao
     * @param fadeSeconds  tempo para o ganho ir de 0 ao alvo (e voltar)
     */
    public AmbientBed(ResourceLocation location, float pitch, float fadeSeconds) {
        this.location = location;
        this.pitch = pitch;
        this.gain = 0.0F;
        this.target = 0.0F;
        this.step = fadeSeconds <= 0.0F ? 1.0F : 1.0F / (fadeSeconds * 20.0F);
    }

    public ResourceLocation location() {
        return location;
    }

    public float gain() {
        return gain;
    }

    public boolean started() {
        return started;
    }

    public void markStarted() {
        started = true;
    }

    /** Volta a marcar como nao iniciado para o gerenciador tentar de novo. */
    public void markStopped() {
        started = false;
    }

    public void setPitch(float p) {
        this.pitch = p < 0.5F ? 0.5F : (p > 2.0F ? 2.0F : p);
    }

    /** Abrigo: 1 a ceu aberto, menos que isso sob teto ou no subsolo. */
    public void setShelter(float s) {
        this.shelter = s < 0.0F ? 0.0F : (s > 1.0F ? 1.0F : s);
    }

    public void setTarget(float g) {
        this.target = g < 0.0F ? 0.0F : (g > 1.0F ? 1.0F : g);
    }

    public void fadeOut() {
        this.target = 0.0F;
    }

    /** Encerra ja: o gerenciador para o canal no proximo tick. */
    public void kill() {
        this.gain = 0.0F;
        this.target = 0.0F;
        this.done = true;
    }

    public void requested(long tick) {
        this.requestedTick = tick;
    }

    public long requestedTick() {
        return requestedTick;
    }

    @Override
    public void update() {
        if (gain < target) {
            gain = Math.min(target, gain + step);
        } else if (gain > target) {
            gain = Math.max(0.0F, gain - step);
        }
        if (target <= 0.0F && gain <= 0.0F) {
            done = true;
        }
    }

    @Override
    public boolean isDonePlaying() {
        return done;
    }

    @Override
    public ResourceLocation getPositionedSoundLocation() {
        return location;
    }

    @Override
    public boolean canRepeat() {
        return true;
    }

    @Override
    public int getRepeatDelay() {
        return 0;
    }

    @Override
    public float getVolume() {
        float v = gain * shelter;
        return v < MIN_GAIN ? MIN_GAIN : v;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public float getXPosF() {
        return axis(0);
    }

    @Override
    public float getYPosF() {
        return axis(1);
    }

    @Override
    public float getZPosF() {
        return axis(2);
    }

    /**
     * Sem atenuacao por distancia: o leito acompanha o jogador, nao o mundo. O
     * volume e decidido pela fisica do evento (velocidade do vento, intensidade
     * da chuva), nao pela distancia - quem esta dentro da tempestade ouve tudo.
     */
    @Override
    public ISound.AttenuationType getAttenuationType() {
        return ISound.AttenuationType.NONE;
    }

    private static float axis(int which) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return 0.0F;
        }
        if (which == 0) {
            return (float) mc.thePlayer.posX;
        }
        if (which == 1) {
            return (float) (mc.thePlayer.posY + 1.0D);
        }
        return (float) mc.thePlayer.posZ;
    }
}
