package dev.unyielding.quests.jail;

/**
 * Read-only classification of recorded escrow phases. This does not inspect or
 * mutate inventories, origins, chests, or journal files. No value returned here
 * authorizes a transfer or a journal transition.
 */
public final class JailEscrowRecovery {
    public enum Status {
        PREPARED_REVIEW,
        REMOVAL_UNCERTAIN,
        HELD_REVIEW,
        RESTORATION_UNCERTAIN,
        COMPLETE,
        CANCELLED,
        INVALID
    }
    private JailEscrowRecovery() {}

    public static Status inspect(JailEscrowJournal.Entry entry) {
        // A null supplied by an unverified caller can mean an unavailable
        // journal, not a proven absence of history. Treat it as ambiguous.
        if (entry == null) return Status.INVALID;
        if (entry.snapshot().length == 0) return Status.INVALID;
        return switch (entry.state()) {
            case PREPARED -> Status.PREPARED_REVIEW;
            case REMOVAL_STARTED -> Status.REMOVAL_UNCERTAIN;
            case HELD -> Status.HELD_REVIEW;
            case RESTORE_STARTED -> Status.RESTORATION_UNCERTAIN;
            case COMPLETE -> Status.COMPLETE;
            case CANCELLED -> Status.CANCELLED;
        };
    }

    /** Classifies one record only; false does not establish journal health, other records, or admission permission. */
    public static boolean blocksNewTransfer(Status status) {
        return status == null || switch (status) {
            case COMPLETE, CANCELLED -> false;
            default -> true;
        };
    }
}
