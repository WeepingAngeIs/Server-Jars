package dev.unyielding.quests.jail;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessoryCustodyPolicyTest {
    @Test void translatedAndRepeatedAliasesCaptureEachPhysicalChannelOnlyOnce() throws Exception {
        Object hat = new Object(), ring = new Object();
        var slots = AccessoryCustodyPolicy.slots(Map.of("hat", hat, "ring", ring),
                Map.of("head", hat, "hat", hat, "ring", ring),
                Map.of("hat", new AccessoryCustodyPolicy.Size(1,1), "ring", new AccessoryCustodyPolicy.Size(2,2)));
        assertEquals(6, slots.size()); assertEquals(6, slots.stream().distinct().count());
        assertEquals(new AccessoryCustodyPolicy.Slot("hat", false, 0), slots.getFirst());
        assertEquals(3, slots.stream().filter(AccessoryCustodyPolicy.Slot::cosmetic).count());
    }
    @Test void missingForeignOrDuplicateCanonicalBackingAndChangedChannelSizesFailClosed() {
        Object ring = new Object(); var canonical = Map.of("ring", ring);
        var sizes = Map.of("ring", new AccessoryCustodyPolicy.Size(1,1));
        assertThrows(IOException.class, () -> AccessoryCustodyPolicy.slots(canonical, Map.of(), sizes));
        assertThrows(IOException.class, () -> AccessoryCustodyPolicy.slots(canonical, Map.of("ring", new Object()), sizes));
        assertThrows(IOException.class, () -> AccessoryCustodyPolicy.slots(Map.of("ring", ring, "duplicate", ring), canonical,
                Map.of("ring", sizes.get("ring"), "duplicate", sizes.get("ring"))));
        assertThrows(IOException.class, () -> AccessoryCustodyPolicy.slots(canonical, canonical, Map.of("ring", new AccessoryCustodyPolicy.Size(1,0))));
        assertThrows(IOException.class, () -> AccessoryCustodyPolicy.slots(canonical, canonical, Map.of("ring", new AccessoryCustodyPolicy.Size(513,513))));
    }
}
