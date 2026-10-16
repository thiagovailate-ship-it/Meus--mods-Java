package com.dynamicwinds.content.tileentities;

import com.dynamicwinds.api.Wind;
import net.minecraft.tileentity.TileEntity;

public class TileEntityWindVane extends TileEntity {
    /** Angulo do vento em radianos, medido a partir de +Z (o mesmo eixo do modelo). */
    public float angle = 0f;
    /** Amostra anterior, usada para interpolar o giro entre ticks. */
    public float prevAngle = 0f;
    /** Falso ate o primeiro tick depois de carregar o chunk. */
    private boolean initialized;

    @Override
    public void updateEntity() {
        if (worldObj == null) return;
        com.dynamicwinds.core.WindSample s = Wind.getWindAt(worldObj, xCoord + 0.5, yCoord + 0.5, zCoord + 0.5);
        // atan2(x, z) da o angulo em relacao a +Z, que e a direcao que a seta
        // aponta quando o modelo esta rotacionado em zero.
        float target = (float) Math.atan2(s.vector.x, s.vector.z);
        if (!initialized) {
            // Primeiro tick depois de carregar o chunk: a seta entra ja na
            // direcao do vento. Sem isto o angulo anterior ficava em zero e o
            // catavento girava visivelmente ate a posicao a cada carregamento.
            initialized = true;
            angle = target;
            prevAngle = target;
            return;
        }
        prevAngle = angle;
        angle = target;
    }

    /** Angulo em graus, interpolado, pronto para o TESR. */
    public float renderAngle(float partialTicks) {
        float prev = (float) Math.toDegrees(prevAngle);
        float cur = (float) Math.toDegrees(angle);
        float delta = cur - prev;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return prev + delta * partialTicks;
    }
}
