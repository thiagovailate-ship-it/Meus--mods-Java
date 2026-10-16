package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.StormField;
import com.dynamicwinds.core.SurfaceRoughness;
import com.dynamicwinds.core.Vec3;
import com.dynamicwinds.core.WindSample;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import java.util.List;

/**
 * Arrasto do vento sobre entidades, com separacao entre vento de fundo e
 * tempestade.
 *
 * <p>A forca segue {@code F = 1/2 * rho * v^2 * Cd * A}, aplicada como
 * aceleracao {@code a = F/m}. Como nao temos a massa real, a razao area/massa e
 * aproximada por {@link #mobility(Entity)}: um item leve voa facil, um mob largo
 * e pesado quase nao sai do lugar - e o mesmo motivo pelo qual uma folha de
 * papel voa e uma pedra do mesmo tamanho nao.</p>
 *
 * <p><b>Oclusao so vale para o vento de fundo.</b> O vento local pode ser
 * bloqueado por um muro logo a frente, mas a tempestade vem de centenas de
 * blocos de distancia: aplicar o raycast nela apagaria o furacao dentro de
 * qualquer floresta, que era exatamente o bug do comportamento antigo. Por isso
 * as duas parcelas sao calculadas separadamente.</p>
 */
public final class EntityPushHandler {
    /** Densidade do ar em kg/m^3 (nivel do mar). */
    private static final double RHO = 1.225;
    /** Coeficiente de arrasto tipico de um corpo rombudo. */
    private static final double COEFF = 0.05;
    /** Distancia do raycast de oclusao, em blocos. */
    private static final double MAX_DIST = 6.0;
    /** Ganho de velocidade por tick, ja calibrado para o passo do jogo. */
    private static final double SCALE = 0.1;
    /** Velocidade minima para valer a pena calcular. */
    private static final double MIN_SPEED = 0.5;
    /** Teto vertical, para a entidade nao entrar em orbita. */
    private static final double Y_CAP = 1.4;

    private EntityPushHandler() {}

    public static void tick(World world) {
        if (world.isRemote) return;
        if (!WindConfig.enablePush) return;

        long time = world.getTotalWorldTime();
        StormField storms = Wind.stormsFor(world);
        SurfaceRoughness energy = Wind.surfaceEnergyFor(world);

        List loaded = world.loadedEntityList;
        for (Object o : loaded) {
            Entity e = (Entity) o;
            if (e instanceof EntityPlayer) continue;
            if (e.isDead) continue;

            WindSample sample = Wind.getWindAt(world, e.posX, e.posY, e.posZ);
            double v = sample.speed;
            if (v < MIN_SPEED) continue;

            int surfaceY = Wind.surfaceHeightAt(world, (int) Math.floor(e.posX),
                    (int) Math.floor(e.posZ));
            Vec3 stormVec = storms == null ? Vec3.ZERO
                    : storms.sampleWind(time, e.posX, e.posY, e.posZ, surfaceY, energy);
            double stormSpeed = stormVec.length();
            Vec3 background = sample.vector.sub(stormVec);

            double mobility = mobility(e);
            double force = 0.5 * RHO * v * v * COEFF * mobility * SCALE;
            if (force <= 0.0) continue;

            // o vento de fundo pode estar ocluido por um muro; a tempestade, nao
            double occBg = occlusion(world, e, background);
            Vec3 bgDir = background.normalize();
            Vec3 stormDir = stormSpeed > 1e-6 ? stormVec.mul(1.0 / stormSpeed) : Vec3.ZERO;

            e.motionX += (bgDir.x * occBg + stormDir.x) * force;
            e.motionZ += (bgDir.z * occBg + stormDir.z) * force;
            e.motionY += (bgDir.y * occBg + stormDir.y) * force * 0.5;

            // o teto acompanha a forca do vento: com 0.6 bloco/tick nada passa
            // de uma brisa forte; no pico de um furacao maior a entidade e
            // arremessada de verdade
            double cap = 0.6 + Math.max(0.0, v - 12.0) * 0.02;
            if (e instanceof EntityItem) cap *= 1.5;
            if (stormSpeed > 1.0) cap *= 1.2;
            clamp(e, cap);

            // arrasto com o ar parado, para a entidade perder impulso ao sair da
            // zona de vento forte
            if (stormSpeed > 1.0) {
                e.motionX *= 0.985;
                e.motionZ *= 0.985;
            }
        }
    }

    /**
     * Razao area/massa aproximada.
     *
     * <p>Itens sao leves e voam; blocos caindo e mobs sao pesados. Alem disso a
     * razao cresce quando a entidade e estreita (massa ~ largura^2 * altura,
     * area ~ largura * altura), que e o motivo fisico pelo qual um galho fino
     * voa e um tronco grosso nao.</p>
     */
    private static double mobility(Entity e) {
        double base = 1.0;
        if (e instanceof EntityItem) {
            base = 3.0;
        } else if (e instanceof EntityLivingBase) {
            base = 0.6;
        } else if (e instanceof EntityFallingBlock) {
            base = 0.5;
        }
        return base / Math.max(0.5, e.width);
    }

    private static void clamp(Entity e, double cap) {
        if (e.motionX > cap) e.motionX = cap;
        if (e.motionX < -cap) e.motionX = -cap;
        if (e.motionZ > cap) e.motionZ = cap;
        if (e.motionZ < -cap) e.motionZ = -cap;
        if (e.motionY > Y_CAP) e.motionY = Y_CAP;
        if (e.motionY < -Y_CAP) e.motionY = -Y_CAP;
    }

    private static double occlusion(World world, Entity e, Vec3 dir) {
        double len = dir.length();
        if (len < 1e-6) return 1.0;
        net.minecraft.util.Vec3 start = net.minecraft.util.Vec3.createVectorHelper(
                e.posX, e.posY + e.height / 2.0, e.posZ);
        net.minecraft.util.Vec3 end = net.minecraft.util.Vec3.createVectorHelper(
                e.posX + dir.x / len * MAX_DIST,
                e.posY + e.height / 2.0 + dir.y / len * MAX_DIST,
                e.posZ + dir.z / len * MAX_DIST);
        MovingObjectPosition mop = world.rayTraceBlocks(start, end);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            double dist = mop.hitVec.distanceTo(start);
            return Math.max(0.2, dist / MAX_DIST);
        }
        return 1.0;
    }
}
