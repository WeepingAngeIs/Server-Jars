package dev.unyielding.quests.spin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Simulates persisted SavedData snapshots on either side of journal transitions. */
class RewardRecoveryBoundaryTest {
    @TempDir Path temp;
    @Test void recordedIntentSurvivesRestartAndDebitReplaysOnlyOnce() throws Exception {
        UUID tx = UUID.randomUUID(), player = UUID.randomUUID();
        Path dir = temp.resolve("journal");
        RewardJournal.provisionNew(dir);
        long[] balance = {0, 0, 2, 0, 0};
        var savedMarkers = new HashSet<UUID>();
        try (RewardJournal j = new RewardJournal(dir)) {
            assertTrue(j.list().isEmpty()); // failure before creation has no transaction
            j.begin(tx, player, 2, "chosen-prize".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        try (RewardJournal j = new RewardJournal(dir)) {
            var entry = j.read(tx);
            assertEquals(RewardJournal.State.PREPARED, entry.state());
            assertTrue(RewardDebit.apply(balance, savedMarkers, tx, entry.slot()));
            j.transition(tx, entry.state(), RewardJournal.State.DEBIT_APPLIED);
            j.transition(tx, RewardJournal.State.DEBIT_APPLIED, RewardJournal.State.DELIVERING);
        }
        try (RewardJournal j = new RewardJournal(dir)) {
            assertEquals(RewardJournal.State.DELIVERING, j.read(tx).state()); // mutation may have occurred: no replay
            assertTrue(RewardDebit.apply(balance, savedMarkers, tx, 2));
            assertEquals(1, balance[2]);
            j.transition(tx, RewardJournal.State.DELIVERING, RewardJournal.State.HELD);
            assertThrows(java.io.IOException.class, () -> j.transition(tx, RewardJournal.State.DELIVERING, RewardJournal.State.COMPLETE));
            assertEquals("chosen-prize", new String(j.read(tx).payload(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test void unsavedDebitIsReappliedAgainstPersistedBalance() throws Exception {
        UUID tx = UUID.randomUUID(); Path dir = temp.resolve("other");
        RewardJournal.provisionNew(dir);
        try (RewardJournal j = new RewardJournal(dir)) {
            j.begin(tx, UUID.randomUUID(), 3, new byte[]{1});
            j.transition(tx, RewardJournal.State.PREPARED, RewardJournal.State.DEBIT_APPLIED);
        }
        long[] persisted = {0, 0, 0, 4, 0}; var savedMarkers = new HashSet<UUID>();
        try (RewardJournal j = new RewardJournal(dir)) {
            assertTrue(RewardDebit.apply(persisted, savedMarkers, tx, j.read(tx).slot()));
            persisted[3]++; // later credit must be retained on repeat
            assertTrue(RewardDebit.apply(persisted, savedMarkers, tx, 3));
            assertEquals(4, persisted[3]);
        }
    }
}
