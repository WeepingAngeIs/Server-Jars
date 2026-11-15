package dev.unyielding.quests.jail;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class JailEscapeBoundaryTest {
    private static boolean outside(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return JailEscapeBoundary.fullyOutside(minX, minY, minZ, maxX, maxY, maxZ, 3.5, 1, 3.5);
    }

    @Test void normalContactAndPartialClippingAreNotAnEscape() {
        assertFalse(outside(5.4, 1, 3.2, 6.0, 2.8, 3.8));
        assertFalse(outside(6.8, 1, 3.2, 7.4, 2.8, 3.8));
        assertFalse(outside(3.2, 6.9, 3.2, 3.8, 8.7, 3.8));
    }

    @Test void entireBodyMustCrossAnOuterFace() {
        assertTrue(outside(7, 1, 3.2, 7.6, 2.8, 3.8));
        assertTrue(outside(-0.6, 1, 3.2, 0, 2.8, 3.8));
        assertTrue(outside(3.2, 7, 3.2, 3.8, 8.8, 3.8));
        assertFalse(outside(Double.NaN, 1, 3.2, 9, 2.8, 3.8));
    }
}
