package com.dynamicwinds.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructionRulesTest {
    @Test
    void criticalSpeedUsesDocumentedHardnessFormula() {
        float[] hardness = {0.0F, 0.2F, 0.5F, 1.5F, 2.0F, 50.0F};
        System.out.println("v_critico table (m/s): hardness -> 9 + 24 * max(0, hardness)");
        for (float value : hardness) {
            double threshold = DestructionHandler.criticalSpeed(value);
            System.out.println(value + " -> " + threshold);
        }
        assertEquals(9.0, DestructionHandler.criticalSpeed(0.0F), 1.0e-12);
        assertEquals(13.8, DestructionHandler.criticalSpeed(0.2F), 1.0e-6);
        assertEquals(21.0, DestructionHandler.criticalSpeed(0.5F), 1.0e-12);
        assertEquals(45.0, DestructionHandler.criticalSpeed(1.5F), 1.0e-12);
        assertEquals(57.0, DestructionHandler.criticalSpeed(2.0F), 1.0e-12);
        assertEquals(1209.0, DestructionHandler.criticalSpeed(50.0F), 1.0e-12);
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
