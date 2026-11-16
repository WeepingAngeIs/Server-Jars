package dev.unyielding.quests.jail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JailVanillaRestoreTest {
    @TempDir Path temp;
    private static final byte[] ORIGINAL = {7, 3, 11};
    static final class Port implements JailVanillaCustody.InventoryPort {
        final UUID owner = UUID.randomUUID();
        JailVanillaCustody.View live = new JailVanillaCustody.View(ORIGINAL, false, null), disk = live;
        boolean online = true, failBeforeSave, failAfterSave, partial, overflow, badPlan, rejectEffects;
        public void validateSavedOutcome(UUID id,boolean restored)throws IOException {if(rejectEffects)throw new IOException("Unverified callback effects");}
        int grants, saves;
        public UUID owner() { return owner; }
        public boolean online() { return online; }
        public JailVanillaCustody.View current() { return live; }
        public JailVanillaCustody.View durable() { return disk; }
        public void persistAndVerify() throws IOException {
            saves++; if (failBeforeSave) throw new IOException("before save"); disk = live;
            if (failAfterSave) throw new IOException("after save");
        }
        public void clearAndMark(UUID id) { live = new JailVanillaCustody.View(new byte[]{0}, true, id); }
        public JailVanillaCustody.View planRestore(byte[] snapshot, UUID id) throws IOException {
            if (overflow) throw new IOException("overflow");
            return new JailVanillaCustody.View(badPlan ? new byte[]{8} : snapshot, false, null, id);
        }
        public void restoreExact(byte[] snapshot, UUID id) throws IOException {
            grants++;
            if (partial) { live = new JailVanillaCustody.View(new byte[]{7}, false, id); throw new IOException("partial setter failure"); }
            live = new JailVanillaCustody.View(snapshot, false, null, id);
        }
    }
    private static void held(JailEscrowJournal journal, UUID id, Port port) throws Exception {
        JailVanillaCustody.removeForDisposableTest(journal, id, port, at -> {});
    }
    @Test void savedReturnCannotCompleteWithUnverifiedNativeEffects()throws Exception {
        Path dir=temp.resolve("effects");JailEscrowJournal.provisionNew(dir);Port port=new Port();UUID id=UUID.randomUUID();
        try(var journal=new JailEscrowJournal(dir)){
            held(journal,id,port);
            assertThrows(IOException.class,()->JailVanillaCustody.restoreForDisposableTest(journal,id,port,at->{if(at==JailVanillaCustody.RestoreBoundary.PLAYER_DURABLE)throw new IOException("interrupted");}));
            int saves=port.saves,grants=port.grants;port.rejectEffects=true;
            assertThrows(IOException.class,()->JailVanillaCustody.confirmRestoredForDisposableTest(journal,id,port));
            assertEquals(JailEscrowJournal.State.RESTORE_STARTED,journal.read(id).state());
            assertEquals(saves,port.saves);assertEquals(grants,port.grants);
            port.rejectEffects=false;JailVanillaCustody.confirmRestoredForDisposableTest(journal,id,port);
            assertEquals(JailEscrowJournal.State.COMPLETE,journal.read(id).state());
        }
    }
    @Test void everyBoundaryReopensWithNoAutomaticReplayOrSecondGrant() throws Exception {
        for (var boundary : JailVanillaCustody.RestoreBoundary.values()) {
            Path dir = temp.resolve(boundary.name()); JailEscrowJournal.provisionNew(dir);
            Port port = new Port(); UUID id = UUID.randomUUID();
            try (var journal = new JailEscrowJournal(dir)) {
                held(journal, id, port);
                assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port,
                        at -> { if (at == boundary) throw new IOException("interrupted"); }));
            }
            port.live = port.disk;
            try (var journal = new JailEscrowJournal(dir)) {
                var expected = switch (boundary) {
                    case BEFORE_PHASE -> JailVanillaCustody.Inspection.HELD_VERIFIED;
                    case STARTED, BEFORE_MUTATION, MUTATED, BEFORE_SAVE -> JailVanillaCustody.Inspection.RESTORE_NOT_PERSISTED_REVIEW;
                    case PLAYER_DURABLE, BEFORE_COMPLETE -> JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW;
                    case COMPLETE -> JailVanillaCustody.Inspection.COMPLETE_RECORDED;
                };
                int grants = port.grants, saves = port.saves;
                for (int repeat = 0; repeat < 2; repeat++) assertEquals(expected, JailVanillaCustody.inspect(journal, id, port));
                assertEquals(grants, port.grants); assertEquals(saves, port.saves);
                assertArrayEquals(ORIGINAL, journal.read(id).snapshot(), "recoverable source was discarded");
                if (boundary == JailVanillaCustody.RestoreBoundary.BEFORE_PHASE) {
                    assertEquals(0, grants);
                    assertEquals(JailVanillaCustody.RestoreResult.RESTORED_VERIFIED,
                            JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                    grants++;
                } else if (boundary == JailVanillaCustody.RestoreBoundary.COMPLETE) {
                    assertEquals(JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,
                            JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> fail("terminal checkpoint")));
                } else {
                    for (int repeat = 0; repeat < 2; repeat++) assertThrows(IOException.class,
                            () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> fail("replayed checkpoint")));
                }
                assertEquals(grants, port.grants); assertTrue(port.grants <= 1);
                if (expected == JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW) {
                    JailVanillaCustody.confirmRestoredForDisposableTest(journal, id, port);
                    assertEquals(grants, port.grants); assertEquals(saves, port.saves);
                    assertEquals(JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,
                            JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                }
            }
        }
    }
    @Test void failedSaveBeforeAndAfterActualPersistenceNeverAutomaticallyRefunds() throws Exception {
        for (boolean after : new boolean[]{false, true}) {
            Path dir = temp.resolve("save-" + after); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
            try (var journal = new JailEscrowJournal(dir)) {
                held(journal, id, port); port.failBeforeSave = !after; port.failAfterSave = after;
                assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                assertEquals(JailEscrowJournal.State.RESTORE_STARTED, journal.read(id).state());
                assertEquals(after ? JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW : JailVanillaCustody.Inspection.REVIEW_REQUIRED,
                        JailVanillaCustody.inspect(journal, id, port));
                port.live = port.disk;
                assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                assertEquals(1, port.grants); assertArrayEquals(ORIGINAL, journal.read(id).snapshot());
                if (!after) assertThrows(IOException.class, () -> JailVanillaCustody.confirmRestoredForDisposableTest(journal, id, port));
            }
        }
    }
    @Test void occupiedFullOfflineOverflowAndInvalidPlanRetainEscrowWithoutChangingAnySlot() throws Exception {
        for (String problem : new String[]{"occupied", "full", "offline", "overflow", "badPlan"}) {
            Path dir = temp.resolve(problem); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
            try (var journal = new JailEscrowJournal(dir)) {
                held(journal, id, port);
                if (problem.equals("occupied") || problem.equals("full")) port.disk = port.live = new JailVanillaCustody.View(new byte[problem.equals("full") ? 41 : 1], false, id);
                port.online = !problem.equals("offline"); port.overflow = problem.equals("overflow"); port.badPlan = problem.equals("badPlan");
                byte[] untouched = port.live.inventory(); int saves = port.saves;
                assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                assertEquals(JailEscrowJournal.State.HELD, journal.read(id).state());
                assertArrayEquals(untouched, port.live.inventory()); assertArrayEquals(ORIGINAL, journal.read(id).snapshot());
                assertEquals(0, port.grants); assertEquals(saves, port.saves);
            }
        }
    }
    @Test void partialSetterAndLaterSavedPartialStateAreHeldForExplicitReview() throws Exception {
        Path dir = temp.resolve("partial"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
        try (var journal = new JailEscrowJournal(dir)) {
            held(journal, id, port); port.partial = true;
            assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
            port.persistAndVerify(); port.live = port.disk;
            assertArrayEquals(new byte[]{7}, port.live.inventory());
            assertArrayEquals(ORIGINAL, journal.read(id).snapshot());
            assertEquals(JailVanillaCustody.Inspection.REVIEW_REQUIRED, JailVanillaCustody.inspect(journal, id, port));
            assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
            assertThrows(IOException.class, () -> JailVanillaCustody.confirmRestoredForDisposableTest(journal, id, port));
            assertEquals(1, port.grants);
        }
    }
    @Test void destinationChangeAfterIntentIsNotOverwritten() throws Exception {
        Path dir = temp.resolve("changed"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
        try (var journal = new JailEscrowJournal(dir)) {
            held(journal, id, port);
            assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {
                if (at == JailVanillaCustody.RestoreBoundary.BEFORE_MUTATION) port.live = new JailVanillaCustody.View(new byte[]{99}, false, id);
            }));
            assertArrayEquals(new byte[]{99}, port.live.inventory()); assertEquals(0, port.grants);
            assertArrayEquals(ORIGINAL, journal.read(id).snapshot());
        }
    }
    @Test void completedRestoreCannotGrantAgainAfterItemsAreUsedOrMoved() throws Exception {
        Path dir = temp.resolve("complete"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
        try (var journal = new JailEscrowJournal(dir)) {
            held(journal, id, port);
            assertEquals(JailVanillaCustody.RestoreResult.RESTORED_VERIFIED, JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
            port.disk = port.live = new JailVanillaCustody.View(new byte[]{0}, true, null, id);
            for (int i = 0; i < 3; i++) assertEquals(JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,
                    JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> fail("repeated grant")));
            assertEquals(1, port.grants); assertTrue(port.live.empty());
            assertArrayEquals(ORIGINAL, journal.read(id).snapshot());
        }
    }
    @Test void missingCorruptAndUnavailableJournalCannotAuthorizeRestoration() throws Exception {
        Path dir = temp.resolve("journal"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
        var journal = new JailEscrowJournal(dir); held(journal, id, port);
        assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, UUID.randomUUID(), port, at -> {}));
        journal.close();
        assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
        Path corrupt = temp.resolve("corrupt"); Files.createDirectory(corrupt);
        byte[] bytes = Files.readAllBytes(dir.resolve("escrow.v1")); bytes[bytes.length-1] ^= 1;
        Files.write(corrupt.resolve("escrow.v1"), bytes);
        assertThrows(IOException.class, () -> new JailEscrowJournal(corrupt));
        Path missing = temp.resolve("missing"); Files.createDirectory(missing);
        assertThrows(IOException.class, () -> new JailEscrowJournal(missing));
        assertEquals(0, port.grants); assertTrue(port.live.empty());
    }
    @Test void everyTruncatedCompletionAppendFailsClosedAndNeverRegrants() throws Exception {
        Path dir = temp.resolve("completion"); JailEscrowJournal.provisionNew(dir); Port port = new Port(); UUID id = UUID.randomUUID();
        byte[] before, after;
        try (var journal = new JailEscrowJournal(dir)) {
            held(journal, id, port);
            assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port,
                    at -> { if (at == JailVanillaCustody.RestoreBoundary.BEFORE_COMPLETE) throw new IOException("interrupt"); }));
            before = Files.readAllBytes(dir.resolve("escrow.v1"));
            journal.transition(id, JailEscrowJournal.State.RESTORE_STARTED, JailEscrowJournal.State.COMPLETE);
            after = Files.readAllBytes(dir.resolve("escrow.v1"));
        }
        for (int cut = before.length; cut <= after.length; cut++) {
            Path trial = temp.resolve("cut-" + cut); Files.createDirectory(trial);
            Files.write(trial.resolve("escrow.v1"), Arrays.copyOf(after, cut));
            if (cut != before.length && cut != after.length) {
                assertThrows(IOException.class, () -> new JailEscrowJournal(trial));
            } else try (var journal = new JailEscrowJournal(trial)) {
                if (cut == before.length) {
                    assertEquals(JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW, JailVanillaCustody.inspect(journal, id, port));
                    assertThrows(IOException.class, () -> JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                } else assertEquals(JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,
                        JailVanillaCustody.restoreForDisposableTest(journal, id, port, at -> {}));
                assertArrayEquals(ORIGINAL, journal.read(id).snapshot());
            }
            assertEquals(1, port.grants); assertArrayEquals(ORIGINAL, port.live.inventory());
        }
    }
}
