package dev.unyielding.quests.jail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Experimental vanilla-only transaction. No admission, chest access, refund or automatic replay. */
public final class JailVanillaCustody {
    private JailVanillaCustody() {}

    public record View(byte[] inventory, boolean empty, UUID removedBy) {
        public View { inventory = Objects.requireNonNull(inventory).clone(); }
        @Override public byte[] inventory() { return inventory.clone(); }
    }
    /** The caller must exclusively own inventory access throughout this synchronous operation. */
    public interface InventoryPort {
        UUID owner();
        View current() throws IOException;
        View durable() throws IOException;
        /** Must persist, force file AND rename directory metadata, and read back exact inventory and marker. */
        void persistAndVerify() throws IOException;
        /** All slots, including armor/offhand; marker must share the same player save as these slots. */
        void clearAndMark(UUID transaction) throws IOException;
    }
    public enum Boundary { BEFORE_PREPARE, PREPARED, REMOVAL_STARTED, MUTATED, PLAYER_DURABLE, HELD }
    @FunctionalInterface public interface Checkpoint { void at(Boundary boundary) throws IOException; }
    public enum Inspection { NO_RECORD, PREPARED_UNCHANGED, REMOVAL_NOT_PERSISTED_REVIEW,
        REMOVAL_PERSISTED_REVIEW, HELD_VERIFIED, REVIEW_REQUIRED }

    public static void removeForDisposableTest(JailEscrowJournal journal, UUID id, InventoryPort port,
                                                Checkpoint checkpoint) throws IOException {
        Objects.requireNonNull(id); Objects.requireNonNull(checkpoint);
        // Duplicates never run a save, callback or mutation a second time.
        if (journal.read(id) != null || journal.list().stream().anyMatch(e -> e.owner().equals(port.owner())
                && e.state() != JailEscrowJournal.State.COMPLETE && e.state() != JailEscrowJournal.State.CANCELLED))
            throw new IOException("Existing custody requires review");
        View before = port.current();
        if (before.empty() || before.removedBy() != null) throw new IOException("Not a fresh vanilla custody source");
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

    /** Read-only reconciliation. Even a matching interrupted removal is never promoted or replayed. */
    public static Inspection inspect(JailEscrowJournal journal, UUID id, InventoryPort port) throws IOException {
        var entry = journal.read(id);
        if (entry == null) return Inspection.NO_RECORD;
        if (!entry.owner().equals(port.owner())) return Inspection.REVIEW_REQUIRED;
        View live = port.current(), disk = port.durable();
        if (!same(live, disk)) return Inspection.REVIEW_REQUIRED;
        boolean original = live.removedBy() == null && Arrays.equals(live.inventory(), entry.snapshot());
        return switch (entry.state()) {
            case PREPARED -> original ? Inspection.PREPARED_UNCHANGED : Inspection.REVIEW_REQUIRED;
            case REMOVAL_STARTED -> original ? Inspection.REMOVAL_NOT_PERSISTED_REVIEW
                    : removed(live, id) ? Inspection.REMOVAL_PERSISTED_REVIEW : Inspection.REVIEW_REQUIRED;
            case HELD -> removed(live, id) ? Inspection.HELD_VERIFIED : Inspection.REVIEW_REQUIRED;
            default -> Inspection.REVIEW_REQUIRED;
        };
    }
    private static boolean removed(View view, UUID id) { return view.empty() && id.equals(view.removedBy()); }
    private static boolean same(View a, View b) {
        return a.empty() == b.empty() && Objects.equals(a.removedBy(), b.removedBy())
                && Arrays.equals(a.inventory(), b.inventory());
    }
}
