package com.dynamicwinds.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormPhaseTest {
    @Test
    void spawnAtGenesisStartsAtRequestedTick() {
        StormSystem system = new StormField(1L, true, 0.0).spawnAtProgress(
                StormKind.HURRICANE, 10000L, 10, 20, 0.5, 1.0, 0.0);
        assertEquals(10000L, system.spawnTick);
        assertEquals(0.0, system.progress(10000L), 1.0e-12);
    }

    @Test
    void spawnAtMaturityUsesLifetimeProgressWithoutChangingPath() {
        StormField field = new StormField(2L, true, 0.0);
        StormSystem mature = field.spawnAtProgress(StormKind.HURRICANE, 10000L,
                -30, 80, 0.5, 1.0, 0.5);
        assertEquals(0.5, mature.progress(10000L), 1.0 / mature.durationTicks());
        assertTrue(mature.intensityAt(10000L) > 0.99);
        assertEquals(-30.0, mature.centerX(10000L), 1.0e-12);
        assertEquals(80.0, mature.centerZ(10000L), 1.0e-12);
    }

    @Test
    void dissipationPhaseHasLowerIntensityAndContinuousLifeCycle() {
        StormSystem system = new StormField(3L, true, 0.0).spawnAtProgress(
                StormKind.MAJOR_HURRICANE, 20000L, 0, 0, 0.0, 1.0, 0.88);
        assertEquals(0.88, system.progress(20000L), 1.0 / system.durationTicks());
        assertTrue(system.intensityAt(20000L) < 1.0);
        assertTrue(system.intensityAt(20000L) > 0.0);
        assertEquals(0.0, system.intensityAt(20000L + system.durationTicks()), 0.0);
    }
}
