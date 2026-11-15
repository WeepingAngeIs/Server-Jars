package dev.unyielding.quests.settings;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DurationInputTest {
    @Test void durationsAndPresetsConvertToSeconds() {
        assertEquals(90, DurationInput.seconds("1m 30s"));
        assertEquals(3720, DurationInput.seconds("1h 2m"));
        assertEquals(10, DurationInput.seconds("10"));
        assertEquals(0, DurationInput.seconds("0s"));
        assertEquals("1h 1m 5s", DurationInput.format(3665));
    }
    @Test void rejectsBadInputAndDoesNotAffectOtherIntegerSettings() {
        assertThrows(IllegalArgumentException.class, () -> DurationInput.seconds("1m garbage"));
        assertThrows(IllegalArgumentException.class, () -> DurationInput.seconds("-2s"));
        assertThrows(IllegalArgumentException.class, () -> DurationInput.seconds("9999999999999999999999h"));
        assertTrue(DurationInput.applies("essentials.home.cooldown"));
        assertFalse(DurationInput.applies("essentials.home.max"));
    }
}
