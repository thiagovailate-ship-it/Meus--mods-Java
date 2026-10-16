package com.dynamicwinds.client.particles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleBudgetTest {
    @Test
    void decorativeParticlesUseOnlyTheirLowPriorityAllowance() {
        assertTrue(ParticleBudget.allowed(0, 9, 9, 50));
        assertFalse(ParticleBudget.allowed(0, 10, 10, 50));
    }

    @Test
    void tornadoParticlesRespectTheNormalBudget() {
        assertTrue(ParticleBudget.allowed(1, 34, 10, 50));
        assertFalse(ParticleBudget.allowed(1, 35, 10, 50));
    }

    @Test
    void sprayUsesTheDebrisAllowanceButRainCanUseTheReserve() {
        assertTrue(ParticleBudget.allowed(2, 44, 10, 50));
        assertFalse(ParticleBudget.allowed(2, 45, 10, 50));
        assertTrue(ParticleBudget.allowed(3, 49, 10, 50));
        assertFalse(ParticleBudget.allowed(3, 50, 10, 50));
    }

    @Test
    void zeroOrExhaustedBudgetRejectsAllParticles() {
        assertFalse(ParticleBudget.allowed(3, 0, 0, 0));
        assertFalse(ParticleBudget.allowed(3, 50, 10, 50));
    }
}
