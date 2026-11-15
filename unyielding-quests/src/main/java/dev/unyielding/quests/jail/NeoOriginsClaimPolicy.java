package dev.unyielding.quests.jail;

import java.util.UUID;

/** Pure ownership classification; it never authorizes jail admission. */
final class NeoOriginsClaimPolicy {
    enum ClaimStatus { OWNED, ORIGINAL_NOT_OWNED, HUMAN_OWNED_BY_OTHER }
    private NeoOriginsClaimPolicy() { }

    static ClaimStatus classify(UUID player, UUID originalOwner, UUID humanOwner) {
        if (player == null || !player.equals(originalOwner)) return ClaimStatus.ORIGINAL_NOT_OWNED;
        if (humanOwner != null && !player.equals(humanOwner)) return ClaimStatus.HUMAN_OWNED_BY_OTHER;
        return ClaimStatus.OWNED;
    }
}
