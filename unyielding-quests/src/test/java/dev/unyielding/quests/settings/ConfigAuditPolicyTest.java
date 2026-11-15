package dev.unyielding.quests.settings;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConfigAuditPolicyTest {
    @Test void numericChangesRemainReadableButTextAndListsAreNotStored() {
        assertEquals("42", ConfigAuditPolicy.printable(42));
        assertEquals("true", ConfigAuditPolicy.printable(true));
        assertEquals("[text or list omitted]", ConfigAuditPolicy.printable("a private value"));
        assertEquals("[text or list omitted]", ConfigAuditPolicy.printable(List.of("a private value")));
    }
    @Test void loadKeepsOnlyMostRecentEntries() {
        assertEquals(0, ConfigAuditPolicy.retainedStart(0, 100));
        assertEquals(0, ConfigAuditPolicy.retainedStart(100, 100));
        assertEquals(25, ConfigAuditPolicy.retainedStart(125, 100));
    }
}
