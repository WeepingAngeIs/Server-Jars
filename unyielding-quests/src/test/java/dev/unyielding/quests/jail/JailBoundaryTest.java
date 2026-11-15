package dev.unyielding.quests.jail;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailBoundaryTest {
    @Test void permitsInteriorButRejectsEscapeAndInvalidCoordinates() {
        assertTrue(JailBoundary.contains("minecraft:the_end", 3.5, 70, 3.5, "minecraft:the_end", 3.5, 70, 3.5));
        assertFalse(JailBoundary.contains("minecraft:overworld", 3.5, 70, 3.5, "minecraft:the_end", 3.5, 70, 3.5));
        assertFalse(JailBoundary.contains("minecraft:the_end", 5.8, 70, 3.5, "minecraft:the_end", 3.5, 70, 3.5));
        assertFalse(JailBoundary.contains("minecraft:the_end", 3.5, 74, 3.5, "minecraft:the_end", 3.5, 70, 3.5));
        assertFalse(JailBoundary.contains("minecraft:the_end", Double.NaN, 70, 3.5, "minecraft:the_end", 3.5, 70, 3.5));
    }
}
