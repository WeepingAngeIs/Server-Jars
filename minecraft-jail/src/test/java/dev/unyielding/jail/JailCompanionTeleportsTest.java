package dev.unyielding.jail;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailCompanionTeleportsTest {
    private final UUID player = UUID.randomUUID(), other = UUID.randomUUID(), third = UUID.randomUUID();
    private static final long NOW = 1_000_000L;

    @Test void incomingAndBothOutgoingDirectionsRefuseWithoutChangingQueue() {
        for (boolean here : List.of(false, true)) {
            for (boolean incoming : List.of(false, true)) {
                var queue = List.of(new JailCompanionTeleports.Pending(incoming ? player : other, incoming ? other : player, here, NOW));
                var before = List.copyOf(queue);
                assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW, queue));
                assertEquals(before, queue);
            }
        }
    }

    @Test void matchesNativeExpiryBoundaryAndTreatsFutureTimeAsLive() {
        assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW,
                List.of(new JailCompanionTeleports.Pending(other, player, false, NOW - 60_000))));
        assertDoesNotThrow(() -> JailCompanionTeleports.verifyRequests(player, NOW,
                List.of(new JailCompanionTeleports.Pending(other, player, true, NOW - 60_001))));
        assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW,
                List.of(new JailCompanionTeleports.Pending(player, other, false, NOW + 1))));
    }

    @Test void unrelatedRequestsStayUntouchedAndMalformedStateFailsClosed() {
        var queue = List.of(new JailCompanionTeleports.Pending(other, third, true, NOW));
        assertDoesNotThrow(() -> JailCompanionTeleports.verifyRequests(player, NOW, queue));
        assertEquals(1, queue.size());
        assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW,
                List.of(new JailCompanionTeleports.Pending(other, player, false, -1))));
        assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW, null));
        assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW,
                List.of(new JailCompanionTeleports.Pending(null, player, false, NOW))));
        assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW,
                java.util.Collections.nCopies(16_385, new JailCompanionTeleports.Pending(other, third, false, NOW))));
    }

    @Test void outsidersCannotBlockExistingCustodyWithIncomingRequests() {
        for (boolean here : List.of(false, true)) {
            var incoming = List.of(new JailCompanionTeleports.Pending(player, other, here, NOW));
            assertDoesNotThrow(() -> JailCompanionTeleports.verifyRequests(player, NOW, incoming, true));
            assertEquals(1, incoming.size());
            var outgoing = List.of(new JailCompanionTeleports.Pending(other, player, here, NOW));
            assertThrows(IOException.class, () -> JailCompanionTeleports.verifyRequests(player, NOW, outgoing, true));
        }
    }
}
