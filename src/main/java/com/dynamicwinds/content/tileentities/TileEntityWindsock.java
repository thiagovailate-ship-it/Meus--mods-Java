package com.dynamicwinds.content.tileentities;

import com.dynamicwinds.api.Wind;
import net.minecraft.tileentity.TileEntity;

public class TileEntityWindsock extends TileEntity {
    /** Inflacao suavizada da manga, de 0 (murcha) a 1 (estufada). */
    public float inflation = 0f;
    public float prevInflation = 0f;
    /** Direcao do vento em radianos, a partir de +Z. */
    public float yaw = 0f;
    public float prevYaw = 0f;
    /** Deslocamento vertical da ponta, para a manga "pescar" no vento fraco. */
    public float droop = 0f;
    public float prevDroop = 0f;
    /** Falso ate o primeiro tick depois de carregar o chunk. */
    private boolean initialized;

    @Override
    public void updateEntity() {
        if (worldObj == null) return;
        com.dynamicwinds.core.WindSample s = Wind.getWindAt(worldObj, xCoord + 0.5, yCoord + 1.0, zCoord + 0.5);

        float target = (float) Math.min(1.0, s.speed / 8.0);
        float newYaw = (float) Math.atan2(s.vector.x, s.vector.z);
        if (!initialized) {
            // Primeiro tick depois de carregar o chunk: a manga ja aparece
            // estufada na medida do vento. Antes ela recomecava de zero e
            // desinflava/inflava visivelmente a cada carregamento, porque a
            // inflacao e um valor suavizado, nao um valor derivado na hora.
            initialized = true;
            inflation = target;
            prevInflation = target;
            yaw = newYaw;
            prevYaw = newYaw;
            droop = 0.22f * (1.0f - inflation);
            prevDroop = droop;
            return;
        }

        prevInflation = inflation;
        prevYaw = yaw;
        prevDroop = droop;

        inflation += (target - inflation) * 0.12f;

        float previousYaw = yaw;
        yaw = newYaw;

        // giro brusco = escoamento desprendendo e voltando a grudar no tecido
        if (worldObj.isRemote) {
            com.dynamicwinds.client.audio.InstrumentAudio.windsockFlap(
                    worldObj, xCoord + 0.5, yCoord + 1.5, zCoord + 0.5, Math.abs(yaw - previousYaw));
        }

        // quase sem vento a manga cai; estufada ela fica na horizontal
        droop = 0.22f * (1.0f - inflation);
    }

    /** Inflacao interpolada para o frame atual. */
    public float renderInflation(float partialTicks) {
        return prevInflation + (inflation - prevInflation) * partialTicks;
    }

    /** Direcao da manga em graus, interpolada. */
    public float renderYaw(float partialTicks) {
        float prev = (float) Math.toDegrees(prevYaw);
        float cur = (float) Math.toDegrees(yaw);
        float delta = cur - prev;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return prev + delta * partialTicks;
    }

    /** Queda da ponta, interpolada. */
    public float renderDroop(float partialTicks) {
        return prevDroop + (droop - prevDroop) * partialTicks;
    }
}
