package com.dynamicwinds.integration;

import net.minecraftforge.common.config.Configuration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSanitizeTest {
    @Test
    void outOfRangeNumericPropertiesAreLoggedAndResetToDefaults() {
        File file = new File(System.getProperty("java.io.tmpdir"), "dynamicwinds-sanitize-test.cfg");
        Configuration previous = WindConfig.cfg;
        try {
            WindConfig.cfg = new Configuration(file);
            WindConfig.cfg.get("general", "baseSpeedMultiplier", 1.0).set(8.0);
            WindConfig.cfg.get("storms", "stormSpawnChance", 0.35).set(-2.0);
            WindConfig.cfg.get("destruction", "destructionBlocksPerTick", 24).set(1000);
            WindConfig.sanitize();
            assertEquals(1.0, WindConfig.baseSpeedMultiplier, 0.0);
            assertEquals(0.35, WindConfig.stormSpawnChance, 0.0);
            assertEquals(24, WindConfig.destructionBlocksPerTick);
        } finally {
            WindConfig.cfg = previous;
            file.delete();
        }
    }

    @Test
    void validNumericPropertiesArePreserved() {
        File file = new File(System.getProperty("java.io.tmpdir"), "dynamicwinds-sanitize-valid.cfg");
        Configuration previous = WindConfig.cfg;
        try {
            WindConfig.cfg = new Configuration(file);
            WindConfig.cfg.get("general", "baseSpeedMultiplier", 1.0).set(2.5);
            WindConfig.cfg.get("storms", "stormSpawnChance", 0.35).set(0.7);
            WindConfig.sanitize();
            assertEquals(2.5, WindConfig.baseSpeedMultiplier, 0.0);
            assertEquals(0.7, WindConfig.stormSpawnChance, 0.0);
        } finally {
            WindConfig.cfg = previous;
            file.delete();
        }
    }
}
