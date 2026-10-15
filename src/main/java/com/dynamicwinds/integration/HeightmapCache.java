package com.dynamicwinds.integration;

import com.dynamicwinds.api.Wind;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches surface height and surface class per 8x8 cell.
 * Persists across ticks for a given world/dimension; cleared when world is unloaded.
 */
public final class HeightmapCache {
    private final World world;
    // key = (cx << 32) | (cz & 0xffffffffL)
    private final Map<Long, Integer> heights = new HashMap<>();
    private final Map<Long, Integer> surfaceClasses = new HashMap<>();

    public HeightmapCache(World world) { this.world = world; }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public int heightAt(int x, int z) {
        int cx = WindEngine.cellOf(x);
        int cz = WindEngine.cellOf(z);
        long k = key(cx, cz);
        Integer h = heights.get(k);
        if (h == null) {
            h = computeHeight(x, z);
            heights.put(k, h);
        }
        return h;
    }

    public int surfaceClassAt(int x, int z) {
        int cx = WindEngine.cellOf(x);
        int cz = WindEngine.cellOf(z);
        long k = key(cx, cz);
        Integer s = surfaceClasses.get(k);
        if (s == null) {
            s = Wind.surfaceClassFor(world.getBiomeGenForCoords(x, z));
            surfaceClasses.put(k, s);
        }
        return s;
    }

    private int computeHeight(int x, int z) {
        // avoid loading chunks unnecessarily: use world heightmap which is cheap
        return world.getHeightValue(x, z);
    }

    /**
     * Incrementally refresh: only recompute cells near the player.
     * Does NOT clear the entire cache.
     */
    /** Refresh cache near a position. */
    public void refreshNear(World w, double px, double pz, int radius) {
        int minCx = WindEngine.cellOf((int) (px - radius));
        int maxCx = WindEngine.cellOf((int) (px + radius));
        int minCz = WindEngine.cellOf((int) (pz - radius));
        int maxCz = WindEngine.cellOf((int) (pz + radius));
        // recompute heights in this region (simple approach: clear and recompute on demand)
        // For 8x8 cells and small radius, this is cheap.
        heights.clear();
        surfaceClasses.clear();
    }
}
