package com.dynamicwinds.core;

import com.dynamicwinds.api.Wind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeightmapCellTest {
    @Test
    void cellsUseFloorDivisionAtPositiveEdges() {
        assertEquals(0, Wind.cellOf(0));
        assertEquals(0, Wind.cellOf(7));
        assertEquals(1, Wind.cellOf(8));
        assertEquals(1, Wind.cellOf(15));
        assertEquals(2, Wind.cellOf(16));
    }

    @Test
    void cellsUseFloorDivisionAtNegativeEdges() {
        assertEquals(-1, Wind.cellOf(-1));
        assertEquals(-1, Wind.cellOf(-8));
        assertEquals(-2, Wind.cellOf(-9));
        assertEquals(-2, Wind.cellOf(-16));
        assertEquals(-3, Wind.cellOf(-17));
    }

    @Test
    void sampleCellCentersAreStable() {
        assertEquals(4, Wind.cellCenter(0));
        assertEquals(12, Wind.cellCenter(1));
        assertEquals(-4, Wind.cellCenter(-1));
        assertEquals(-12, Wind.cellCenter(-2));
    }

    @Test
    void everyCellCenterMapsBackToItsCell() {
        for (int cell = -1000; cell <= 1000; cell++) {
            assertEquals(cell, Wind.cellOf(Wind.cellCenter(cell)));
        }
    }
}
