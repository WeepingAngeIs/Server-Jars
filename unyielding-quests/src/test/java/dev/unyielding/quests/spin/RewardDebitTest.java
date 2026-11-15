package dev.unyielding.quests.spin;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RewardDebitTest {
    @Test void missingJournalHistoryFailsClosed() {
        UUID known = UUID.randomUUID(), missing = UUID.randomUUID();
        assertTrue(RewardDebit.journalCoversMarkers(java.util.Set.of(known), java.util.Set.of(known)));
        assertFalse(RewardDebit.journalCoversMarkers(java.util.Set.of(known, missing), java.util.Set.of(known)));
        assertTrue(RewardDebit.journalCoversMarkers(java.util.Set.of(), java.util.Set.of()));
    }
    @Test void replayDoesNotDebitTwiceOrOverwriteLaterCredit() {
        long[] record = {0, 0, 3, 2, 0};
        var applied = new HashSet<UUID>();
        UUID id = UUID.randomUUID();
        assertTrue(RewardDebit.apply(record, applied, id, 2));
        record[2] += 5;
        assertTrue(RewardDebit.apply(record, applied, id, 2));
        assertEquals(7, record[2]);
        assertEquals(2, record[3]);
    }
    @Test void unavailableDebitFailsClosedWithoutMarker() {
        var applied = new HashSet<UUID>(); UUID id = UUID.randomUUID();
        assertFalse(RewardDebit.apply(new long[]{0,0,0,0,0}, applied, id, 3));
        assertFalse(applied.contains(id));
    }
}
