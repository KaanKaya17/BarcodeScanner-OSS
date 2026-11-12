package com.example.barcodescanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScanGateTest {
    @Test
    public void repeatsOnlyAfterLeavingTheFrameForLongEnough() {
        ScanGate gate = new ScanGate();

        assertTrue(gate.accept("A", 0));
        assertFalse(gate.accept("A", 5000));
        assertFalse(gate.accept(null, 5100));
        assertFalse(gate.accept("A", 5200));
        assertFalse(gate.accept(null, 5300));
        assertFalse(gate.accept(null, 5900));
        assertTrue(gate.accept("A", 6000));
    }

    @Test
    public void permitsADifferentCodeImmediatelyAfterManualLookup() {
        ScanGate gate = new ScanGate();
        gate.hold("A");

        assertFalse(gate.accept("A", 0));
        assertTrue(gate.accept("B", 1));
        assertFalse(gate.accept("B", 2));
    }
}
