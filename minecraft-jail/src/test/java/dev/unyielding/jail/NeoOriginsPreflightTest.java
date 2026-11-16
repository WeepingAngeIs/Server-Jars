package dev.unyielding.jail;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NeoOriginsPreflightTest {
    @Test void uniqueClaimsMustRemainOwnedAndHumanMustNotBelongToAnotherPlayer() {
        UUID player = UUID.randomUUID(), stranger = UUID.randomUUID();
        assertEquals(NeoOriginsClaimPolicy.ClaimStatus.ORIGINAL_NOT_OWNED,
                NeoOriginsClaimPolicy.classify(null, null, null));
        assertEquals(NeoOriginsClaimPolicy.ClaimStatus.ORIGINAL_NOT_OWNED,
                NeoOriginsClaimPolicy.classify(player, null, null));
        assertEquals(NeoOriginsClaimPolicy.ClaimStatus.ORIGINAL_NOT_OWNED,
                NeoOriginsClaimPolicy.classify(player, stranger, null));
        assertEquals(NeoOriginsClaimPolicy.ClaimStatus.HUMAN_OWNED_BY_OTHER,
                NeoOriginsClaimPolicy.classify(player, player, stranger));
        assertEquals(NeoOriginsClaimPolicy.ClaimStatus.OWNED,
                NeoOriginsClaimPolicy.classify(player, player, null));
        assertEquals(NeoOriginsClaimPolicy.ClaimStatus.OWNED,
                NeoOriginsClaimPolicy.classify(player, player, player));
    }
}
