package dev.unyielding.quests.essentials;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class DestinationPolicyTest {
    @Test void homesAreScopedToTheRequestingPlayer() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var homes = Map.of(a, Map.of("private-a", "overworld"), b, Map.of("private-b", "the_nether"));
        assertEquals(Map.of("private-a", "overworld"), DestinationPolicy.ownHomes(homes, a));
        assertFalse(DestinationPolicy.ownHomes(homes, a).containsKey("private-b"));
        assertTrue(DestinationPolicy.ownHomes(homes, UUID.randomUUID()).isEmpty());
    }
    @Test void destinationFieldsAreBoundedByUtf8Bytes() {
        assertTrue(DestinationPolicy.fitsPayload("a".repeat(256), "minecraft:overworld"));
        assertFalse(DestinationPolicy.fitsPayload("a".repeat(257), "minecraft:overworld"));
        assertFalse(DestinationPolicy.fitsPayload("é".repeat(129), "minecraft:overworld"));
        assertFalse(DestinationPolicy.fitsPayload("home", "a".repeat(257)));
        assertEquals(200, DestinationPolicy.MAX_ROWS);
    }
    @Test void destinationCommandsCannotInjectAnotherCommandOrUseAnotherRoot() {
        assertEquals("home cottage-1", DestinationPolicy.command("home", "cottage-1"));
        assertEquals("warp market", DestinationPolicy.command("warp", "market"));
        assertNull(DestinationPolicy.command("home", "x\nstop"));
        assertNull(DestinationPolicy.command("home", "x y"));
        assertNull(DestinationPolicy.command("tp", "market"));
        assertNull(DestinationPolicy.command("warp", ""));
    }
}
