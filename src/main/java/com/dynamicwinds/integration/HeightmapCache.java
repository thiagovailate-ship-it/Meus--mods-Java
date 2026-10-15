package com.dynamicwinds.integration;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Caches surface height per 8x8 cell.
 */
public final class HeightmapCache {
    private final World world;
    private final Map<Long, Integer> heights = new HashMap<>();
    private final Map<Long, Integer> surfaceClasses = new HashMap<>();

    public HeightmapCache(World world) { this.world = world; }

    private static long key(int cx, int cz) { return ((long) cx << 32) | (cz & 0xffffffffL); }

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
            s = com.dynamicwinds.api.Wind.surfaceClassFor(world.getBiomeGenForCoords(x, z));
            surfaceClasses.put(k, s);
        }
        return s;
    }

    private int computeHeight(int x, int z) {
        Chunk c = world.getChunkFromBlockCoords(x, z);
        return c != null ? c.getHeightValue(x & 15, z & 15) : world.getHeightValue(x, z);
    }

    public void refreshDirty(World w) {
        // simple: clear cache to pick up terrain changes (cheap enough for 8x8)
        heights.clear();
        surfaceClasses.clear();
    }
}
