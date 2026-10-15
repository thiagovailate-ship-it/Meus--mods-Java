package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side engine: ticks every 5 ticks, caches heightmaps in 8x8 cells.
 */
public final class WindEngine {
    private static final int CELL = 8;
    private final Map<Long, HeightmapCache> caches = new HashMap<>();
    private int tickCounter = 0;

    public void tick(World world) {
        if (world.isRemote) return;
        tickCounter++;
        if (tickCounter % 5 != 0) return;
        long key = world.provider.dimensionId;
        HeightmapCache cache = caches.computeIfAbsent(key, k -> new HeightmapCache(world));
        cache.refreshDirty(world);
        EntityPushHandler.tick(world);
    }

    public static int cellOf(int coord) { return Math.floorDiv(coord, CELL); }

    public HeightmapCache cacheFor(World world) {
        return caches.computeIfAbsent((long) world.provider.dimensionId, k -> new HeightmapCache(world));
    }

    public WindSample sample(World world, double x, double y, double z) {
        return Wind.getWindAt(world, x, y, z);
    }
}
