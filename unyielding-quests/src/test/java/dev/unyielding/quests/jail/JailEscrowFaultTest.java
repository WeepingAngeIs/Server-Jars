package dev.unyielding.quests.jail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Simulates interrupted journal writes only; does not simulate cross-store durability. */
class JailEscrowFaultTest {
    @TempDir Path temp;

    @Test void truncationAtEveryWriteBoundaryNeverInventsCompletion() throws Exception {
        Path source = temp.resolve("source"); JailEscrowJournal.provisionNew(source);
        Path file = source.resolve("escrow.v1"); UUID id = UUID.randomUUID(), owner = UUID.randomUUID();
        byte[] prepared, removal, held, restoring;
        try (var journal = new JailEscrowJournal(source)) {
            journal.begin(id, owner, new byte[]{9, 8, 7}); prepared = Files.readAllBytes(file);
            journal.transition(id, JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.REMOVAL_STARTED); removal = Files.readAllBytes(file);
            journal.transition(id, JailEscrowJournal.State.REMOVAL_STARTED, JailEscrowJournal.State.HELD); held = Files.readAllBytes(file);
            journal.transition(id, JailEscrowJournal.State.HELD, JailEscrowJournal.State.RESTORE_STARTED); restoring = Files.readAllBytes(file);
        }
        byte[][] snapshots = {prepared, removal, held, restoring};
        var phases = new JailEscrowJournal.State[]{JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.REMOVAL_STARTED,
                JailEscrowJournal.State.HELD, JailEscrowJournal.State.RESTORE_STARTED};
        for (int i = 0; i < snapshots.length; i++) {
            Path dir = temp.resolve("boundary-" + i); Files.createDirectory(dir); Files.write(dir.resolve("escrow.v1"), snapshots[i]);
            try (var journal = new JailEscrowJournal(dir)) {
                assertEquals(phases[i], journal.read(id).state());
                assertTrue(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.inspect(journal.read(id))));
                assertThrows(IOException.class, () -> journal.begin(UUID.randomUUID(), owner, new byte[]{1}));
            }
        }
        // Every partial byte of the final append is rejected rather than replayed.
        for (int cut = held.length + 1; cut < restoring.length; cut++) {
            Path dir = temp.resolve("cut-" + cut); Files.createDirectory(dir);
            Files.write(dir.resolve("escrow.v1"), Arrays.copyOf(restoring, cut));
            assertThrows(IOException.class, () -> new JailEscrowJournal(dir), "accepted partial frame at " + cut);
        }
    }

    @Test void checksumCorruptionDoesNotProduceAUsableJournal() throws Exception {
        Path dir = temp.resolve("escrow"); JailEscrowJournal.provisionNew(dir);
        try (var journal = new JailEscrowJournal(dir)) { journal.begin(UUID.randomUUID(), UUID.randomUUID(), new byte[]{1,2,3}); }
        Path file = dir.resolve("escrow.v1"); byte[] bytes = Files.readAllBytes(file); bytes[bytes.length - 1] ^= 1; Files.write(file, bytes);
        assertThrows(IOException.class, () -> new JailEscrowJournal(dir));
    }
}
