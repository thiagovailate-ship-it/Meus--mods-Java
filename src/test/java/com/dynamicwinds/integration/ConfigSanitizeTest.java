package com.dynamicwinds.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSanitizeTest {
    @Test
    void outOfRangeNumericValuesResetToDocumentedDefaults() {
        assertEquals(1.0, WindConfig.sanitizeDoubleValue(8.0, 0.0, 5.0, 1.0), 0.0);
        assertEquals(0.35, WindConfig.sanitizeDoubleValue(-2.0, 0.0, 1.0, 0.35), 0.0);
        assertEquals(24, WindConfig.sanitizeIntValue(1000, 0, 256, 24));
    }

    @Test
    void validNumericValuesArePreserved() {
        assertEquals(2.5, WindConfig.sanitizeDoubleValue(2.5, 0.0, 5.0, 1.0), 0.0);
        assertEquals(0.7, WindConfig.sanitizeDoubleValue(0.7, 0.0, 1.0, 0.35), 0.0);
        assertEquals(180, WindConfig.sanitizeIntValue(180, 0, 256, 24));
    }

    @Test
    void nonFiniteValuesResetToDefault() {
        assertEquals(1.0, WindConfig.sanitizeDoubleValue(Double.NaN, 0.0, 5.0, 1.0), 0.0);
        assertEquals(1.0, WindConfig.sanitizeDoubleValue(Double.POSITIVE_INFINITY,
                0.0, 5.0, 1.0), 0.0);
    }
}
