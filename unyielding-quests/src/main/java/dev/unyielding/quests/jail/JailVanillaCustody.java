package dev.unyielding.quests.jail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Experimental inventory transactions with opaque snapshots. No admission, chest access or automatic replay. */
public final class JailVanillaCustody {
    private JailVanillaCustody() {}

    public record View(byte[] inventory, boolean empty, UUID removedBy, UUID restoredBy) {
        public View { inventory = Objects.requireNonNull(inventory).clone(); }
        public View(byte[] inventory, boolean empty, UUID removedBy) { this(inventory, empty, removedBy, null); }
        @Override public byte[] inventory() { return inventory.clone(); }
    }
    /** The caller must exclusively own inventory access throughout this synchronous operation. */
    public interface InventoryPort {
        UUID owner();
        /** Verify declared native callback effects during explicit saved-phase confirmation. */
        default void validateSavedOutcome(UUID transaction,boolean restored)throws IOException {}
        /** Data-only rejection of unsupported providers/restrictions; must never invoke mod callbacks. */
        default void validateRemoval() throws IOException {}
        View current() throws IOException;
        View durable() throws IOException;
        /** Must persist, force file AND rename directory metadata, and read back exact inventory and marker. */
        void persistAndVerify() throws IOException;
        /** All slots, including armor/offhand; marker must share the same player save as these slots. */
        void clearAndMark(UUID transaction) throws IOException;
        default boolean online() { return false; }
        /** Read-only full decoding/validation. Must reject every occupied destination and overflow. */
        default View planRestore(byte[] snapshot, UUID transaction) throws IOException { throw new IOException("Restore unsupported"); }
        /** Revalidate before changing any slot; never merge, drop, overwrite or discard overflow. */
        default void restoreExact(byte[] snapshot, UUID transaction) throws IOException { throw new IOException("Restore unsupported"); }
    }
    public enum Boundary { BEFORE_PREPARE, PREPARED, REMOVAL_STARTED, MUTATED, PLAYER_DURABLE, HELD }
    @FunctionalInterface public interface Checkpoint { void at(Boundary boundary) throws IOException; }
    public enum Inspection { NO_RECORD, PREPARED_UNCHANGED, REMOVAL_NOT_PERSISTED_REVIEW,
        REMOVAL_PERSISTED_REVIEW, HELD_VERIFIED, RESTORE_NOT_PERSISTED_REVIEW,
        RESTORE_PERSISTED_REVIEW, COMPLETE_RECORDED, REVIEW_REQUIRED }
    public enum RestoreBoundary { BEFORE_PHASE, STARTED, BEFORE_MUTATION, MUTATED, BEFORE_SAVE, PLAYER_DURABLE, BEFORE_COMPLETE, COMPLETE }
    @FunctionalInterface public interface RestoreCheckpoint { void at(RestoreBoundary boundary) throws IOException; }
    public enum RestoreResult { RESTORED_VERIFIED, ALREADY_COMPLETE }

    public static void removeForDisposableTest(JailEscrowJournal journal, UUID id, InventoryPort port,
                                                Checkpoint checkpoint) throws IOException {
        Objects.requireNonNull(id); Objects.requireNonNull(checkpoint);
        // Duplicates never run a save, callback or mutation a second time.
        if (journal.read(id) != null || journal.list().stream().anyMatch(e -> e.owner().equals(port.owner())
                && e.state() != JailEscrowJournal.State.COMPLETE && e.state() != JailEscrowJournal.State.CANCELLED))
            throw new IOException("Existing custody requires review");
        port.validateRemoval();
        View before = port.current();
        if (before.empty() || before.removedBy() != null || before.restoredBy() != null) throw new IOException("Not a fresh vanilla custody source");
        port.persistAndVerify();
        if (!same(before, port.current()) || !same(before, port.durable())) throw new IOException("Baseline inventory changed");
        checkpoint.at(Boundary.BEFORE_PREPARE);
        journal.begin(id, port.owner(), before.inventory());
        checkpoint.at(Boundary.PREPARED);
        journal.transition(id, JailEscrowJournal.State.PREPARED, JailEscrowJournal.State.REMOVAL_STARTED);
        checkpoint.at(Boundary.REMOVAL_STARTED);
        if (!same(before, port.current())) throw new IOException("Source changed before removal");
        port.clearAndMark(id);
        checkpoint.at(Boundary.MUTATED);
        port.persistAndVerify();
        if (!removed(port.current(), id) || !same(port.current(), port.durable()))
            throw new IOException("Removed inventory not durably verified");
        checkpoint.at(Boundary.PLAYER_DURABLE);
        journal.transition(id, JailEscrowJournal.State.REMOVAL_STARTED, JailEscrowJournal.State.HELD);
        checkpoint.at(Boundary.HELD);
    }

    public static RestoreResult restoreForDisposableTest(JailEscrowJournal journal, UUID id, InventoryPort port,
                                                          RestoreCheckpoint checkpoint) throws IOException {
        Objects.requireNonNull(checkpoint);
        var entry = journal.read(id);
        if (entry == null || !entry.owner().equals(port.owner())) throw new IOException("Missing or foreign custody");
        // A terminal record never authorizes another grant, even if the player
        // has since moved, used, lost or destroyed their restored inventory.
        if (entry.state() == JailEscrowJournal.State.COMPLETE) return RestoreResult.ALREADY_COMPLETE;
        if (!port.online()) throw new IOException("Player offline; custody retained");
        if (entry.state() != JailEscrowJournal.State.HELD || inspect(journal, id, port) != Inspection.HELD_VERIFIED)
            throw new IOException("Custody or destination requires review");
        View before = port.current();
        View planned = port.planRestore(entry.snapshot(), id);
        if (!restored(planned, entry)) throw new IOException("Restore plan changed captured inventory");
        checkpoint.at(RestoreBoundary.BEFORE_PHASE);
        journal.transition(id, JailEscrowJournal.State.HELD, JailEscrowJournal.State.RESTORE_STARTED);
        checkpoint.at(RestoreBoundary.STARTED);
        checkpoint.at(RestoreBoundary.BEFORE_MUTATION);
        if (!same(before, port.current()) || !same(before, port.durable())) throw new IOException("Destination changed before restore");
        port.restoreExact(entry.snapshot(), id);
        checkpoint.at(RestoreBoundary.MUTATED);
        if (!same(planned, port.current())) throw new IOException("Partial or changed restoration requires review");
        checkpoint.at(RestoreBoundary.BEFORE_SAVE);
        port.persistAndVerify();
        if (!same(planned, port.current()) || !same(planned, port.durable())) throw new IOException("Restoration not durably verified");
        checkpoint.at(RestoreBoundary.PLAYER_DURABLE);
        checkpoint.at(RestoreBoundary.BEFORE_COMPLETE);
        journal.transition(id, JailEscrowJournal.State.RESTORE_STARTED, JailEscrowJournal.State.COMPLETE);
        checkpoint.at(RestoreBoundary.COMPLETE);
        return RestoreResult.RESTORED_VERIFIED;
    }

    /** Explicit reconciliation of an already persisted exact restore; never grants or removes items. */
    public static void confirmRestoredForDisposableTest(JailEscrowJournal journal, UUID id, InventoryPort port) throws IOException {
        if (!port.online() || inspect(journal, id, port) != Inspection.RESTORE_PERSISTED_REVIEW)
            throw new IOException("No exact persisted restoration to confirm");
        port.validateSavedOutcome(id,true);
        journal.transition(id, JailEscrowJournal.State.RESTORE_STARTED, JailEscrowJournal.State.COMPLETE);
    }

    /** Explicit confirmation of an already saved empty inventory and transaction receipt; no removal replay. */
    public static void confirmRemoved(JailEscrowJournal journal,UUID id,InventoryPort port)throws IOException {
        if(!port.online()||inspect(journal,id,port)!=Inspection.REMOVAL_PERSISTED_REVIEW)throw new IOException("No exact persisted removal to confirm");
        port.validateSavedOutcome(id,false);
        journal.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
    }

    /** Read-only reconciliation. Even a matching interrupted removal is never promoted or replayed. */
    public static Inspection inspect(JailEscrowJournal journal, UUID id, InventoryPort port) throws IOException {
        var entry = journal.read(id);
        if (entry == null) return Inspection.NO_RECORD;
        if (!entry.owner().equals(port.owner())) return Inspection.REVIEW_REQUIRED;
        if (entry.state() == JailEscrowJournal.State.COMPLETE) return Inspection.COMPLETE_RECORDED;
        View live = port.current(), disk = port.durable();
        if (!same(live, disk)) return Inspection.REVIEW_REQUIRED;
        boolean original = live.removedBy() == null && live.restoredBy() == null && Arrays.equals(live.inventory(), entry.snapshot());
        return switch (entry.state()) {
            case PREPARED -> original ? Inspection.PREPARED_UNCHANGED : Inspection.REVIEW_REQUIRED;
            case REMOVAL_STARTED -> original ? Inspection.REMOVAL_NOT_PERSISTED_REVIEW
                    : removed(live, id) ? Inspection.REMOVAL_PERSISTED_REVIEW : Inspection.REVIEW_REQUIRED;
            case HELD -> removed(live, id) ? Inspection.HELD_VERIFIED : Inspection.REVIEW_REQUIRED;
            case RESTORE_STARTED -> removed(live, id) ? Inspection.RESTORE_NOT_PERSISTED_REVIEW
                    : restored(live, entry) ? Inspection.RESTORE_PERSISTED_REVIEW : Inspection.REVIEW_REQUIRED;
            default -> Inspection.REVIEW_REQUIRED;
        };
    }
    private static boolean removed(View view, UUID id) { return view.empty() && id.equals(view.removedBy()) && view.restoredBy() == null; }
    private static boolean restored(View view, JailEscrowJournal.Entry entry) {
        return !view.empty() && view.removedBy() == null && entry.id().equals(view.restoredBy())
                && Arrays.equals(view.inventory(), entry.snapshot());
    }
    private static boolean same(View a, View b) {
        return a.empty() == b.empty() && Objects.equals(a.removedBy(), b.removedBy())
                && Objects.equals(a.restoredBy(), b.restoredBy()) && Arrays.equals(a.inventory(), b.inventory());
    }
}
