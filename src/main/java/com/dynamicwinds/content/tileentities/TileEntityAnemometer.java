package com.dynamicwinds.content.tileentities;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.tileentity.TileEntity;

/**
 * Anemometro: mantem o estado visual do rotor (angulo) e da bandeira de
 * direcao. A saida de redstone continua sendo calculada por
 * {@code BlockAnemometer}, a partir do mesmo campo de vento determinista.
 */
public class TileEntityAnemometer extends TileEntity {

    /** Angulo do rotor em graus (0..360). */
    public float rotation = 0f;
    public float prevRotation = 0f;

    /** Direcao do vento em graus, para o catavento auxiliar do topo. */
    public float direction = 0f;
    public float prevDirection = 0f;

    /** Suavizacao da velocidade (o vento instantaneo e muito ruidoso para o rotor). */
    public float smoothedSpeed = 0f;

    @Override
    public void updateEntity() {
        if (worldObj == null) {
            return;
        }
        // Roda nos dois lados de proposito: o campo de vento e deterministico,
        // entao cliente e servidor chegam ao mesmo angulo sem pacotes de
        // sincronizacao.
        prevRotation = rotation;
        prevDirection = direction;

        WindSample sample = Wind.getWindAt(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
        float speed = (float) sample.speed;
        smoothedSpeed += (speed - smoothedSpeed) * 0.15f;

        // ~28 graus por (unidade de vento * tick): gira visivelmente a partir de
        // vento fraco, sem parecer um ventilador em ventania.
        float previous = rotation;
        rotation = wrap(rotation + smoothedSpeed * 28.0f);

        // o angulo so da a volta quando passa de 360: ai o ima do rotor passou
        // pelo sensor, que e o clique de um anemometro de conchas de verdade
        if (worldObj.isRemote && rotation < previous) {
            com.dynamicwinds.client.audio.InstrumentAudio.anemometerPulse(
                    worldObj, xCoord + 0.5, yCoord + 0.95, zCoord + 0.5, smoothedSpeed);
        }

        float target = (float) Math.toDegrees(Math.atan2(sample.vector.z, sample.vector.x));
        direction = target;
    }

    /** Direcao interpolada para o frame atual (graus). */
    public float renderDirection(float partialTicks) {
        return prevDirection + shortestDelta(prevDirection, direction) * partialTicks;
    }

    /** Angulo do rotor interpolado para o frame atual (graus). */
    public float renderRotation(float partialTicks) {
        return prevRotation + shortestDelta(prevRotation, rotation) * partialTicks;
    }

    private static float shortestDelta(float from, float to) {
        float delta = to - from;
        while (delta > 180.0f) {
            delta -= 360.0f;
        }
        while (delta < -180.0f) {
            delta += 360.0f;
        }
        return delta;
    }

    private static float wrap(float angle) {
        float a = angle % 360.0f;
        return a < 0.0f ? a + 360.0f : a;
    }
}
