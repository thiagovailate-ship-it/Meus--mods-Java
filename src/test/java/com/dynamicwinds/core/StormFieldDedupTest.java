package com.dynamicwinds.core;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StormFieldDedupTest {
    @Test
    void dynamicSystemNeverSharesIdSpaceWithTenThousandNaturalSlots() {
        StormField field = new StormField(912345L, true, 1.0);
        StormSystem dynamic = field.spawnAtProgress(StormKind.HURRICANE, 20000L,
                0.0, 0.0, 0.0, 1.0, 0.0);
        assertTrue(dynamic.id < 0L);

        for (long slot = 0; slot < 10000L; slot++) {
            long time = slot * StormField.SLOT_TICKS + 20000L;
            List<StormSystem> active = field.activeAt(time);
            Set<Long> ids = new HashSet<Long>();
            for (StormSystem system : active) {
                assertTrue(ids.add(Long.valueOf(system.id)), "duplicate id at slot " + slot);
                if (system.playerSpawned) assertTrue(system.id < 0L);
                else assertTrue(system.id >= 0L);
            }
        }
    }

    @Test
    void persistedNaturalSnapshotSuppressesTheSameDeterministicSlot() {
        StormField field = new StormField(441L, true, 1.0);
        StormSystem saved = new StormSystem(StormKind.MAJOR_HURRICANE, 1L, 24000L,
                100.0, -50.0, 0.4, 0.0, true, 1.0, false);
        field.addDynamic(saved);
        int matchingIds = 0;
        for (StormSystem active : field.activeAt(44000L)) {
            if (active.id == saved.id) matchingIds++;
        }
        assertEquals(1, matchingIds);
    }

    @Test
    void expiredReplicatedSystemsArePruned() {
        StormField field = new StormField(44L, true, 0.0);
        field.spawn(StormKind.HURRICANE, 0L, 0.0, 0.0, 0.0, 1.0);
        assertEquals(1, field.dynamicCount());
        field.pruneExpired(StormKind.durationTicks(StormKind.HURRICANE));
        assertEquals(0, field.dynamicCount());
    }

    @Test
    void duplicateDynamicSnapshotReplacesRatherThanDuplicates() {
        StormField field = new StormField(12L, true, 0.0);
        StormSystem original = field.spawn(StormKind.TROPICAL_STORM, 1000L,
                10.0, 20.0, 0.3, 1.0);
        StormSystem restored = new StormSystem(original.kind, original.id, original.spawnTick,
                original.spawnX, original.spawnZ, original.heading0, original.turnRate,
                original.north, original.intensityMul, original.playerSpawned);
        field.addDynamic(restored);
        assertEquals(1, field.dynamicCount());
        assertEquals(original.id, field.dynamicSystems().get(0).id);
    }
}
