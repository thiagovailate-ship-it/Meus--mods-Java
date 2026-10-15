package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.List;

/**
 * Applies wind force to entities/items with simple occlusion (raycast).
 * Force ~ 1/2 * rho * v^2 * coeff, occluded if a solid block is in the way.
 */
public final class EntityPushHandler {
    private static final double RHO = 1.225; // air density (scaled)
    private static final double COEFF = 0.05;
    private static final double MAX_DIST = 6.0;

    private EntityPushHandler() {}

    public static void tick(World world) {
        if (world.isRemote) return;
        if (!WindConfig.enablePush) return;
        List loaded = world.loadedEntityList;
        for (Object o : loaded) {
            Entity e = (Entity) o;
            if (e instanceof EntityPlayer) continue;
            if (e.isDead) continue;
            WindSample s = Wind.getWindAt(world, e.posX, e.posY, e.posZ);
            double v = s.speed;
            if (v < 0.5) continue;
            double force = 0.5 * RHO * v * v * COEFF;
            // occlusion: raycast in wind direction
            double occ = occlusion(world, e, s);
            force *= occ;
            if (force <= 0) continue;
            e.motionX += s.vector.x / (v + 1e-6) * force * 0.1;
            e.motionY += s.vector.y / (v + 1e-6) * force * 0.05;
            e.motionZ += s.vector.z / (v + 1e-6) * force * 0.1;
            // clamp horizontal speed
            double cap = 0.6;
            if (e.motionX > cap) e.motionX = cap;
            if (e.motionX < -cap) e.motionX = -cap;
            if (e.motionZ > cap) e.motionZ = cap;
            if (e.motionZ < -cap) e.motionZ = -cap;
            // drag
            e.motionX *= 0.97;
            e.motionZ *= 0.97;
            // items are lighter: scale force by a mass factor instead of multiplying velocity
            // (mass handling is implicit via force scaling above; no velocity multiplication)
        }
    }

    private static double occlusion(World world, Entity e, WindSample s) {
        if (s.speed < 1e-6) return 1.0;
        double dx = s.vector.x / s.speed;
        double dy = s.vector.y / s.speed;
        double dz = s.vector.z / s.speed;
        net.minecraft.util.Vec3 start = net.minecraft.util.Vec3.createVectorHelper(e.posX, e.posY + e.height / 2.0, e.posZ);
        net.minecraft.util.Vec3 end = net.minecraft.util.Vec3.createVectorHelper(
                e.posX + dx * MAX_DIST, e.posY + e.height / 2.0 + dy * MAX_DIST, e.posZ + dz * MAX_DIST);
        MovingObjectPosition mop = world.rayTraceBlocks(start, end);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            // partial occlusion based on distance
            double dist = mop.hitVec.distanceTo(start);
            return Math.max(0.2, dist / MAX_DIST);
        }
        return 1.0;
    }
}
