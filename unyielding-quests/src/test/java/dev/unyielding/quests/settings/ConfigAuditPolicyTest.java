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
    @Test void groundDurationsAreReadableInAuditHistory() {
        assertEquals("10m 0s", ConfigAuditPolicy.display("ground.interval", "12000"));
        assertEquals("1m 5s", ConfigAuditPolicy.display("ground.grace", "1300"));
        assertEquals("3", ConfigAuditPolicy.display("ground.clear_food", "3"));
        assertEquals("1m 5s", ConfigAuditPolicy.display("essentials.home.cooldown", "65"));
        assertEquals("invalid", ConfigAuditPolicy.display("essentials.home.cooldown", "invalid"));
    }
    @Test void undoRequiresAllowlistedNumericValuesAndAnUnchangedCurrentValue() {
        assertTrue(ConfigAuditPolicy.undoable("quests.custom_pokemon.map_icon_scale", "1.0", "2.0", "2.0"));
        assertTrue(ConfigAuditPolicy.undoable("quests.origin_creative_flight_speed", "1.5", "2.0", "2.0"));
        for (String key : List.of("essentials.home.cooldown", "essentials.back.cooldown", "essentials.rtp.cooldown", "essentials.cooldowns.spawn", "essentials.cooldowns.playerspawn", "essentials.cooldowns.warp", "essentials.cooldowns.tpa")) {
            assertTrue(ConfigAuditPolicy.undoable(key, "10", "60", "60"));
            assertFalse(ConfigAuditPolicy.undoable(key, "10", "60", "30"));
        }
        assertFalse(ConfigAuditPolicy.undoable("quests.custom_pokemon.map_icon_scale", "1.0", "2.0", "3.0"));
        assertFalse(ConfigAuditPolicy.undoable("ground.interval", "12000", "6000", "6000"));
        assertFalse(ConfigAuditPolicy.undoable("quests.custom_pokemon.map_icon_scale", "NaN", "2.0", "2.0"));
        assertFalse(ConfigAuditPolicy.undoable("quests.custom_pokemon.map_icon_scale", "private", "2.0", "2.0"));
        assertFalse(ConfigAuditPolicy.undoable("quests.custom_pokemon.map_icon_scale", "2.0", "2.0", "2.0"));
    }
    @Test void loadKeepsOnlyMostRecentEntries() {
        assertEquals(0, ConfigAuditPolicy.retainedStart(0, 100));
        assertEquals(0, ConfigAuditPolicy.retainedStart(100, 100));
        assertEquals(25, ConfigAuditPolicy.retainedStart(125, 100));
    }
    @Test void auditPagesCoverNewestThroughOldestWithoutOverlap() {
        assertEquals(1, ConfigAuditPolicy.pageCount(0));
        assertEquals(1, ConfigAuditPolicy.pageCount(10));
        assertEquals(2, ConfigAuditPolicy.pageCount(11));
        assertEquals(10, ConfigAuditPolicy.pageCount(100));
        assertEquals(99, ConfigAuditPolicy.pageHighIndex(100, 1));
        assertEquals(89, ConfigAuditPolicy.pageHighIndex(100, 2));
        assertEquals(9, ConfigAuditPolicy.pageHighIndex(100, 10));
    }
}
