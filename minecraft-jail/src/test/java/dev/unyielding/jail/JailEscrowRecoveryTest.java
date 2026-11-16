package dev.unyielding.jail;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JailEscrowRecoveryTest {
    @Test void interruptedPhasesAlwaysRequireReviewAndBlockAnotherTransfer() {
        UUID tx = UUID.randomUUID(), player = UUID.randomUUID();
        var removal = new JailEscrowJournal.Entry(tx, player, JailEscrowJournal.State.REMOVAL_STARTED, new byte[]{1});
        var restoration = new JailEscrowJournal.Entry(tx, player, JailEscrowJournal.State.RESTORE_STARTED, new byte[]{1});
        assertEquals(JailEscrowRecovery.Status.REMOVAL_UNCERTAIN, JailEscrowRecovery.inspect(removal));
        assertEquals(JailEscrowRecovery.Status.RESTORATION_UNCERTAIN, JailEscrowRecovery.inspect(restoration));
        assertTrue(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.inspect(removal)));
        assertTrue(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.inspect(restoration)));
    }
    @Test void heldAndPreparedRecordsDoNotAuthorizeAutomaticTransfer() {
        for (var state : new JailEscrowJournal.State[]{JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.HELD}) {
            var entry = new JailEscrowJournal.Entry(UUID.randomUUID(), UUID.randomUUID(), state, new byte[]{1});
            assertTrue(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.inspect(entry)));
        }
        assertTrue(JailEscrowRecovery.blocksNewTransfer(null));
        assertEquals(JailEscrowRecovery.Status.INVALID, JailEscrowRecovery.inspect(null));
        assertTrue(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.inspect(null)));
        assertFalse(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.Status.COMPLETE));
        assertFalse(JailEscrowRecovery.blocksNewTransfer(JailEscrowRecovery.Status.CANCELLED));
    }
}
