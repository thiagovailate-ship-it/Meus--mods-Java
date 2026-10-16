package com.dynamicwinds.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructionRulesTest {
    @Test
    void criticalSpeedUsesDocumentedHardnessFormula() {
        assertEquals(9.0, DestructionHandler.criticalSpeed(0.0F), 1.0e-12);
        assertEquals(13.8, DestructionHandler.criticalSpeed(0.2F), 1.0e-6);
        assertEquals(21.0, DestructionHandler.criticalSpeed(0.5F), 1.0e-12);
    }

    @Test
    void indestructibleHardnessDoesNotProduceNegativeThreshold() {
        assertEquals(9.0, DestructionHandler.criticalSpeed(-1.0F), 1.0e-12);
    }

    @Test
    void defaultStormScopeIsOverworldOnlyAndDestructionExemptionIsEmpty() {
        assertTrue(WindConfig.stormsAllowedInDimension(0));
        assertFalse(WindConfig.stormsAllowedInDimension(-1));
        assertFalse(WindConfig.destructionProtectedDimension(0));
    }

    @Test
    void protectedDefaultBlocksAreCaseInsensitive() {
        assertTrue(WindConfig.isProtectedBlock("minecraft:obsidian"));
        assertTrue(WindConfig.isProtectedBlock("MINECRAFT:BEDROCK"));
        assertFalse(WindConfig.isProtectedBlock("minecraft:dirt"));
    }
}
