package dev.unyielding.quests.spin;

import java.util.Set;
import java.util.UUID;

/** The balance and applied ID must be saved together by the owning SavedData. */
final class RewardDebit {
    static boolean journalCoversMarkers(Set<UUID> applied, Set<UUID> journalIds) {
        return journalIds.containsAll(applied);
    }
    static boolean apply(long[] balance, Set<UUID> applied, UUID transaction, int slot) {
        if (applied.contains(transaction)) return true;
        if ((slot != 2 && slot != 3) || balance == null || balance.length <= slot || balance[slot] <= 0) return false;
        balance[slot]--;
        applied.add(transaction);
        return true;
    }
    private RewardDebit() {}
}
