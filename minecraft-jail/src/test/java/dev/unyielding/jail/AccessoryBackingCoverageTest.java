package dev.unyielding.jail;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessoryBackingCoverageTest {
    @Test void translatedNamesAndDuplicateAliasesCoverTheSameBacking() {
        Object hat = new Object(), ring = new Object();
        assertTrue(AccessoryBackingCoverage.covers(Map.of("hat", hat, "ring", ring), Map.of("head", hat, "ring", ring, "head_alias", hat)));
    }
    @Test void unknownMissingOrDuplicatedSourceBackingsAreRejected() {
        Object a = new Object(), b = new Object();
        assertFalse(AccessoryBackingCoverage.covers(Map.of("hat", a), Map.of("head", b)));
        assertFalse(AccessoryBackingCoverage.covers(Map.of("hat", a, "ring", b), Map.of("head", a)));
        assertFalse(AccessoryBackingCoverage.covers(Map.of("hat", a, "head", a), Map.of("head", a)));
    }
    @Test void identityIsRequiredRatherThanValueEquality() {
        assertFalse(AccessoryBackingCoverage.covers(Map.of("hat", new String("same")), Map.of("head", new String("same"))));
    }
}
