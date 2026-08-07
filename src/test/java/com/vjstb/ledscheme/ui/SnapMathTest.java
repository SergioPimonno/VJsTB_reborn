package com.vjstb.ledscheme.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SnapMathTest {

    @Test
    void fullStrengthSnapsExactlyToTarget() {
        assertEquals(100.0, SnapMath.blend(37, 100, 100), 1e-9);
    }

    @Test
    void zeroStrengthLeavesRawValueUnchanged() {
        assertEquals(37.0, SnapMath.blend(37, 100, 0), 1e-9);
    }

    @Test
    void halfStrengthBlendsHalfway() {
        assertEquals(68.5, SnapMath.blend(37, 100, 50), 1e-9);
    }

    @Test
    void clampsOutOfRangeStrength() {
        assertEquals(100.0, SnapMath.blend(37, 100, 150), 1e-9);
        assertEquals(37.0, SnapMath.blend(37, 100, -20), 1e-9);
    }
}
