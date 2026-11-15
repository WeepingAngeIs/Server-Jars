package dev.unyielding.quests.spin;
import java.util.Set;
import java.util.UUID;
/** Balance and marker are persisted in the same SavedData snapshot. */
final class RewardCredit {
    static boolean apply(long[] balance, Set<UUID> applied, UUID id) {
        if (applied.contains(id)) return false;
        long next = Math.addExact(balance[3], 1);
        balance[3] = next;
        applied.add(id);
        return true;
    }
}
