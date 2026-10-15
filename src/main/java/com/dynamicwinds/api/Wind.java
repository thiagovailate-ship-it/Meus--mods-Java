package com.dynamicwinds.api;

import com.dynamicwinds.core.SurfaceClass;
import com.dynamicwinds.core.WindModel;
import com.dynamicwinds.core.WindSample;
import com.dynamicwinds.core.WorldContext;
import com.dynamicwinds.integration.WindConfig;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Public facade for other mods and internal features.
 */
public final class Wind {
    private static final java.util.Map<Long, WindModel> MODELS = new java.util.HashMap<>();

    private Wind() {}

    public static WindSample getWindAt(World world, double x, double y, double z) {
        long seed = world.getSeed();
        WindModel model = MODELS.computeIfAbsent(seed, WindModel::new);
        int sx = (int) Math.floor(x);
        int sz = (int) Math.floor(z);
        int surfaceY = world.getHeightValue(sx, sz);
        int surfaceClass = surfaceClassFor(world.getBiomeGenForCoords(sx, sz));
        // storm intensity from world weather state (0..1)
        double storm = 0.0;
        if (world.isThundering()) {
            storm = 1.0;
        } else if (world.isRaining()) {
            // interpolate rain strength for smoothness
            float rs = world.rainingStrength;
            float prs = world.prevRainingStrength;
            float frac = (world.getTotalWorldTime() % 20) / 20.0f;
            float cur = prs + (rs - prs) * frac;
            storm = (cur > 0.05) ? (cur / 1.0f) : 0.0;
        }
        WorldContext ctx = new WorldContext(seed, world.getTotalWorldTime(), surfaceY, storm, surfaceClass, WindConfig.baseSpeedMultiplier);
        return model.compute(ctx, x, y, z);
    }

    public static int surfaceClassFor(BiomeGenBase biome) {
        if (biome == null) return SurfaceClass.GRASS;
        String name = biome.biomeName.toLowerCase();
        if (name.contains("ocean") || name.contains("river")) return SurfaceClass.WATER;
        if (name.contains("desert")) return SurfaceClass.SAND;
        if (name.contains("forest") || name.contains("jungle") || name.contains("taiga")) return SurfaceClass.FOREST;
        if (name.contains("mountain") || name.contains("hills")) return SurfaceClass.STONE;
        return SurfaceClass.GRASS;
    }
}
