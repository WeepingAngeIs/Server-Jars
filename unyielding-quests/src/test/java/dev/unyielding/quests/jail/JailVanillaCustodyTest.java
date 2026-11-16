package dev.unyielding.quests.jail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JailVanillaCustodyTest {
    @TempDir Path temp;
    static final class Port implements JailVanillaCustody.InventoryPort {
        final UUID owner = UUID.randomUUID();
        JailVanillaCustody.View live = new JailVanillaCustody.View(new byte[]{7, 3, 11}, false, null), disk = live;
        int removals, saves; boolean failSave,online=true,rejectEffects;
        public void validateSavedOutcome(UUID id,boolean restored)throws IOException {if(rejectEffects)throw new IOException("Unverified callback effects");}
        public boolean online(){return online;}
        public UUID owner() { return owner; }
        public JailVanillaCustody.View current() { return live; }
        public JailVanillaCustody.View durable() { return disk; }
        public void persistAndVerify() throws IOException { saves++; if (failSave) throw new IOException("save failed"); disk = live; }
        public void clearAndMark(UUID id) { removals++; live = new JailVanillaCustody.View(new byte[]{0}, true, id); }
    }
    @Test void everyInterruptionReopensWithoutReplayingPlayerMutation() throws Exception {
        for (var boundary : JailVanillaCustody.Boundary.values()) {
            Path dir = temp.resolve(boundary.name()); JailEscrowJournal.provisionNew(dir);
            Port port = new Port(); UUID id = UUID.randomUUID();
            try (var journal = new JailEscrowJournal(dir)) {
                assertThrows(IOException.class, () -> JailVanillaCustody.removeForDisposableTest(journal, id, port,
                        at -> { if (at == boundary) throw new IOException("interruption"); }));
            }
            // Restart loads the persisted player copy, never the uncommitted live copy.
            port.live = port.disk;
            try (var journal = new JailEscrowJournal(dir)) {
                var expected = switch (boundary) {
                    case BEFORE_PREPARE -> JailVanillaCustody.Inspection.NO_RECORD;
                    case PREPARED -> JailVanillaCustody.Inspection.PREPARED_UNCHANGED;
                    case REMOVAL_STARTED, MUTATED -> JailVanillaCustody.Inspection.REMOVAL_NOT_PERSISTED_REVIEW;
                    case PLAYER_DURABLE -> JailVanillaCustody.Inspection.REMOVAL_PERSISTED_REVIEW;
                    case HELD -> JailVanillaCustody.Inspection.HELD_VERIFIED;
                };
                int removals = port.removals, saves = port.saves;
                assertEquals(expected, JailVanillaCustody.inspect(journal, id, port));
                assertEquals(expected, JailVanillaCustody.inspect(journal, id, port));
                assertEquals(removals, port.removals); assertEquals(saves, port.saves);
                if (boundary != JailVanillaCustody.Boundary.BEFORE_PREPARE) {
                    assertThrows(IOException.class, () -> JailVanillaCustody.removeForDisposableTest(journal, id, port, at -> {}));
                    assertThrows(IOException.class, () -> JailVanillaCustody.removeForDisposableTest(journal, UUID.randomUUID(), port, at -> {}));
                    assertEquals(removals, port.removals); assertEquals(saves, port.saves);
                }
            }
        }
    }
    @Test void mutationSaveFailureAndAmbiguousStatesRequireReview() throws Exception {
        Path dir = temp.resolve("save"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
        try (var journal = new JailEscrowJournal(dir)) {
            assertThrows(IOException.class, () -> JailVanillaCustody.removeForDisposableTest(journal, id, port,
                    at -> { if (at == JailVanillaCustody.Boundary.MUTATED) port.failSave = true; }));
            assertEquals(JailEscrowJournal.State.REMOVAL_STARTED, journal.read(id).state());
            assertEquals(JailVanillaCustody.Inspection.REVIEW_REQUIRED, JailVanillaCustody.inspect(journal, id, port));
            port.disk = port.live = new JailVanillaCustody.View(new byte[]{0}, true, UUID.randomUUID());
            assertEquals(JailVanillaCustody.Inspection.REVIEW_REQUIRED, JailVanillaCustody.inspect(journal, id, port));
            port.disk = port.live = new JailVanillaCustody.View(new byte[]{9}, false, null);
            assertEquals(JailVanillaCustody.Inspection.REVIEW_REQUIRED, JailVanillaCustody.inspect(journal, id, port));
            assertEquals(1, port.removals);
        }
    }
    @Test void baselineSaveFailureCannotPrepareOrRemove() throws Exception {
        Path dir = temp.resolve("baseline"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); port.failSave = true;
        try (var journal = new JailEscrowJournal(dir)) {
            assertThrows(IOException.class, () -> JailVanillaCustody.removeForDisposableTest(journal, UUID.randomUUID(), port, at -> {}));
            assertTrue(journal.list().isEmpty()); assertEquals(0, port.removals);
        }
    }
    @Test void confirmationOnlyPromotesExactSavedRemovalAndNeverReplays()throws Exception {
        Path dir=temp.resolve("confirm");JailEscrowJournal.provisionNew(dir);Port port=new Port();UUID id=UUID.randomUUID();
        try(var journal=new JailEscrowJournal(dir)){
            assertThrows(IOException.class,()->JailVanillaCustody.removeForDisposableTest(journal,id,port,at->{if(at==JailVanillaCustody.Boundary.PLAYER_DURABLE)throw new IOException("saved interruption");}));
            int saves=port.saves,removals=port.removals;port.online=false;assertThrows(IOException.class,()->JailVanillaCustody.confirmRemoved(journal,id,port));port.online=true;
            var correct=port.live;port.live=port.disk=new JailVanillaCustody.View(new byte[]{0},true,UUID.randomUUID());assertThrows(IOException.class,()->JailVanillaCustody.confirmRemoved(journal,id,port));
            port.live=correct;assertThrows(IOException.class,()->JailVanillaCustody.confirmRemoved(journal,id,port));port.disk=correct;
            JailVanillaCustody.confirmRemoved(journal,id,port);assertEquals(JailEscrowJournal.State.HELD,journal.read(id).state());assertEquals(saves,port.saves);assertEquals(removals,port.removals);
            assertThrows(IOException.class,()->JailVanillaCustody.confirmRemoved(journal,id,port));
        }
    }
    @Test void journalWriteFailureNeverMutatesPlayer() throws Exception {
        Path dir = temp.resolve("journal"); JailEscrowJournal.provisionNew(dir); Port port = new Port();
        var journal = new JailEscrowJournal(dir); journal.close();
        assertThrows(IOException.class, () -> JailVanillaCustody.removeForDisposableTest(journal, UUID.randomUUID(), port, at -> {}));
        assertEquals(0, port.removals); assertEquals(0, port.saves);
    }
    @Test void savedItemsDoNotAuthorizeUnverifiedNativeEffects()throws Exception {
        Path dir=temp.resolve("effects");JailEscrowJournal.provisionNew(dir);Port port=new Port();UUID id=UUID.randomUUID();
        try(var journal=new JailEscrowJournal(dir)){
            assertThrows(IOException.class,()->JailVanillaCustody.removeForDisposableTest(journal,id,port,at->{if(at==JailVanillaCustody.Boundary.PLAYER_DURABLE)throw new IOException("interrupted");}));
            int saves=port.saves,removals=port.removals;port.rejectEffects=true;
            assertThrows(IOException.class,()->JailVanillaCustody.confirmRemoved(journal,id,port));
            assertEquals(JailEscrowJournal.State.REMOVAL_STARTED,journal.read(id).state());
            assertEquals(saves,port.saves);assertEquals(removals,port.removals);
            port.rejectEffects=false;JailVanillaCustody.confirmRemoved(journal,id,port);
            assertEquals(JailEscrowJournal.State.HELD,journal.read(id).state());
        }
    }
}
