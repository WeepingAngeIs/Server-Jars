package dev.unyielding.quests.spin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DailyRewardPolicyTest {
    @Test void missedDayGraceContinuesButDoesNotCreditMissedDays() {
        assertEquals(8, DailyRewardPolicy.nextStreak(100, 7, 102, 1));
        assertEquals(1, DailyRewardPolicy.nextStreak(100, 7, 103, 1));
        assertEquals(1, DailyRewardPolicy.nextStreak(100, 7, 102, 0));
    }

    @Test void oneClaimPerDayAndTwentyHourBoundary() {
        long then = 1_000_000_000L;
        assertFalse(DailyRewardPolicy.eligible(100, then, 100, then + 30 * 3_600_000L));
        assertFalse(DailyRewardPolicy.eligible(100, then, 101, then + 20 * 3_600_000L - 1));
        assertTrue(DailyRewardPolicy.eligible(100, then, 101, then + 20 * 3_600_000L));
    }

    @Test void rollbackAndOldSavedRecords() {
        long then = 1_000_000_000L;
        assertFalse(DailyRewardPolicy.eligible(100, then, 99, then + 30 * 3_600_000L));
        assertFalse(DailyRewardPolicy.eligible(100, then, 101, then - 1));
        // Older records lack the timestamp; their saved day remains authoritative.
        assertFalse(DailyRewardPolicy.eligible(100, 0, 100, then));
        assertTrue(DailyRewardPolicy.eligible(100, 0, 101, then));
        assertEquals(8, DailyRewardPolicy.nextStreak(100, 7, 101, 1));
    }
}
