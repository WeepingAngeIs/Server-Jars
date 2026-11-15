package dev.unyielding.quests.jail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JailEscrowJournalTest {
    @TempDir Path temp;
    @Test void persistsSnapshotAndHoldsAmbiguousOperationAcrossReopen() throws Exception {
        Path dir = temp.resolve("escrow"); JailEscrowJournal.provisionNew(dir);
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID(); byte[] snapshot = {1, 2, 3};
        try (var journal = new JailEscrowJournal(dir)) {
            journal.begin(id, owner, snapshot); snapshot[0] = 42;
            assertArrayEquals(new byte[]{1, 2, 3}, journal.read(id).snapshot());
            journal.transition(id, JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.REMOVAL_STARTED);
        }
        try (var journal = new JailEscrowJournal(dir)) {
            assertEquals(JailEscrowJournal.State.REMOVAL_STARTED, journal.read(id).state());
            assertThrows(IOException.class, () -> journal.begin(UUID.randomUUID(), owner, new byte[]{7}));
            assertThrows(IOException.class, () -> journal.transition(id, JailEscrowJournal.State.REMOVAL_STARTED, JailEscrowJournal.State.PREPARED));
            journal.transition(id, JailEscrowJournal.State.REMOVAL_STARTED, JailEscrowJournal.State.HELD);
            journal.transition(id, JailEscrowJournal.State.HELD, JailEscrowJournal.State.RESTORE_STARTED);
        }
        try (var journal = new JailEscrowJournal(dir)) {
            assertEquals(JailEscrowJournal.State.RESTORE_STARTED, journal.read(id).state());
            assertThrows(IOException.class, () -> journal.begin(UUID.randomUUID(), owner, new byte[]{9}));
            assertThrows(IOException.class, () -> journal.transition(id, JailEscrowJournal.State.RESTORE_STARTED, JailEscrowJournal.State.HELD));
        }
    }
    @Test void rejectsMissingHistoryAndNeverReplacesExistingDirectory() throws Exception {
        Path dir = temp.resolve("escrow");
        assertThrows(IOException.class, () -> new JailEscrowJournal(dir));
        JailEscrowJournal.provisionNew(dir);
        assertThrows(IOException.class, () -> JailEscrowJournal.provisionNew(dir));
        try (var journal = new JailEscrowJournal(dir)) { assertTrue(journal.list().isEmpty()); }
    }
    @Test void corruptOrPartialFramesFailClosed() throws Exception {
        Path dir = temp.resolve("escrow"); JailEscrowJournal.provisionNew(dir);
        try (var journal = new JailEscrowJournal(dir)) { journal.begin(UUID.randomUUID(), UUID.randomUUID(), new byte[]{3, 4}); }
        Files.write(dir.resolve("escrow.v1"), new byte[]{1, 2}, StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> new JailEscrowJournal(dir));
    }
    @Test void completeAndCancelledOperationsAllowNewButNeverReuseTransactionIds() throws Exception {
        Path dir = temp.resolve("escrow"); JailEscrowJournal.provisionNew(dir);
        UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        try (var journal = new JailEscrowJournal(dir)) {
            journal.begin(id, owner, new byte[]{1});
            journal.transition(id, JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.CANCELLED);
            assertThrows(IOException.class, () -> journal.begin(id, owner, new byte[]{1}));
            UUID second = UUID.randomUUID(); journal.begin(second, owner, new byte[]{2});
            journal.transition(second, JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.REMOVAL_STARTED);
            journal.transition(second, JailEscrowJournal.State.REMOVAL_STARTED, JailEscrowJournal.State.HELD);
            journal.transition(second, JailEscrowJournal.State.HELD, JailEscrowJournal.State.RESTORE_STARTED);
            journal.transition(second, JailEscrowJournal.State.RESTORE_STARTED, JailEscrowJournal.State.COMPLETE);
            journal.begin(UUID.randomUUID(), owner, new byte[]{3});
        }
        try (var journal = new JailEscrowJournal(dir)) { assertEquals(3, journal.list().size()); }
    }
}
