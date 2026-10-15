package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import com.dynamicwinds.core.WindSample;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side engine: ticks every 5 ticks, caches heightmaps in 8x8 cells.
 * Cache persists across ticks; only refreshes near the player occasionally.
 */
public final class WindEngine {
    private static final int CELL = 8;
    private static final int UPDATE_RADIUS = 64; // blocks
    private static final int REFRESH_INTERVAL = 40; // ticks (~2 seconds)

    private final Map<Long, HeightmapCache> caches = new HashMap<>();
    private final Map<Long, Integer> lastRefresh = new HashMap<>();
    private int tickCounter = 0;

    public void tick(World world) {
        if (world.isRemote) return;
        tickCounter++;
        if (tickCounter % 5 != 0) return;

        long key = world.provider.dimensionId;
        HeightmapCache cache = caches.computeIfAbsent(key, k -> new HeightmapCache(world));

        // Periodically refresh near player to pick up terrain changes
        int last = lastRefresh.getOrDefault(key, 0);
        if (tickCounter - last >= REFRESH_INTERVAL) {
            cache.refreshNear(world, world.rand.nextDouble() * 100, world.rand.nextDouble() * 100, UPDATE_RADIUS);
            lastRefresh.put(key, tickCounter);
        }

        EntityPushHandler.tick(world);
    }

    public static int cellOf(int coord) { return Math.floorDiv(coord, CELL); }

    public HeightmapCache cacheFor(World world) {
        return caches.computeIfAbsent((long) world.provider.dimensionId, k -> new HeightmapCache(world));
    }

    public WindSample sample(World world, double x, double y, double z) {
        return Wind.getWindAt(world, x, y, z);
    }

    /** Called when a world is unloaded to free resources. */
    public void unloadWorld(World world) {
        caches.remove((long) world.provider.dimensionId);
        lastRefresh.remove((long) world.provider.dimensionId);
    }
}
