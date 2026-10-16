package com.dynamicwinds.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeightmapCacheCapacityTest {
    @Test
    void boundedMapsResetBeforeASecondEntryWouldExceedTheCap() {
        assertFalse(HeightmapCache.needsCapacityReset(16383, 16383));
        assertTrue(HeightmapCache.needsCapacityReset(16384, 0));
        assertTrue(HeightmapCache.needsCapacityReset(0, 16384));
    }
}
