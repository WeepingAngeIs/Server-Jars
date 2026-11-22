package dev.unyielding.quests.settings;

import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StoragePlusSettingsTest {
    private static StoragePlusSettings.Setting setting(String key, String type, Number min, Number max) {
        return new StoragePlusSettings.Setting(key, type, "Label", "Provider description", min, max, "",
                StoragePlusSettings.legacyApplyMode(key), null);
    }

    @Test void honorsTheOwnersPublishedUnitsConfirmationAndApplyTiming() {
        var custom = new StoragePlusSettings.Setting("example", "INTEGER", "Setting", "Description", 1, 16,
                "blocks", "live", null);
        var value = custom.row(4, 1);
        assertTrue(value.getString("note").contains("blocks"));
        assertEquals("LIVE", value.getString("activation"));
        assertFalse(value.contains("confirmValue"));
        var secure = new StoragePlusSettings.Setting("expansion.imageModeration", "BOOLEAN", "Moderation", "Description",
                null, null, "", "resource_reload", Boolean.FALSE).row(true, 1);
        assertTrue(secure.contains("confirmValue", Tag.TAG_BYTE));
        assertFalse(secure.getBoolean("confirmValue"));
        assertTrue(secure.getString("apply").contains("reload"));
    }

    @Test void restrictsInputToThePublishedKeyNamespaceAndRequiresAnActor() {
        assertTrue(StoragePlusSettings.owns("storageplus.expansion.tabletOpeningChancePercent"));
        assertTrue(StoragePlusSettings.owns("storageplus.recipes.terminals"));
        for (String key : new String[]{null, "storageplus.", "storageplus.a..b", "storageplus.x/y",
                "registered.anyastorage.setting", "storageplus.foo\n"}) {
            assertFalse(StoragePlusSettings.owns(key));
        }
        assertEquals(0, StoragePlusSettings.rows(null, "", 0, 10).total());
        assertEquals(ModSettings.Result.DENIED.message,
                StoragePlusSettings.change(null, "storageplus.expansion.pocketStorage", "false", "true", 1));
    }

    @Test void preservesProviderBoundsTypesLabelsAndDisplayedRevision() {
        var opening = setting("expansion.tabletOpeningChancePercent", "INTEGER", 0, 100);
        assertEquals(Integer.valueOf(100), opening.parse("100"));
        assertEquals(Integer.valueOf(0), opening.parse("0"));
        for (String input : new String[]{"101", "-1", "1.0", "true", "2147483648", "1e2"}) {
            assertNull(opening.parse(input));
        }
        var row = opening.row(35, 4_000_000_000L);
        assertEquals("storageplus.expansion.tabletOpeningChancePercent", row.getString("key"));
        assertEquals("expansion.tabletOpeningChancePercent", row.getString("aliases"));
        assertEquals("Label", row.getString("title"));
        assertEquals("Provider description", row.getString("note"));
        assertEquals("number", row.getString("kind"));
        assertEquals("35", row.getString("current"));
        assertEquals("LIVE", row.getString("activation"));
        assertEquals(4_000_000_000L, row.getLong("revision"));
        assertEquals(100.0, row.getDouble("max"));
        assertEquals(0.0, row.getDouble("min"));
        assertTrue(row.getBoolean("available"));
        assertFalse(opening.row(null, 1).getBoolean("available"));
        assertFalse(opening.row("35", 1).contains("current", Tag.TAG_STRING));
    }

    @Test void validatesDecimalsAndRegistryIdsAndDistinguishesRecipeReloads() {
        var reduction = setting("expansion.magmaReduction", "DECIMAL", 0.0, .5);
        assertEquals(Double.valueOf(.25), reduction.parse(".25"));
        assertEquals(Double.valueOf(.25), reduction.parse("25e-2"));
        for (String input : new String[]{"NaN", "Infinity", "-0.1", "0.51", "1d", "0x1p-2", "2e999"}) {
            assertNull(reduction.parse(input));
        }
        assertTrue(reduction.row(.25, 5).getString("apply").contains("reload"));
        assertFalse(reduction.row(.25, 5).contains("activation"));
        var material = setting("expansion.pocketCoreMaterial", "REGISTRY_ID", null, null);
        assertEquals("minecraft:diamond", material.parse("minecraft:diamond"));
        assertEquals("registry_id", material.row("minecraft:diamond", 9).getString("kind"));
        assertNull(material.parse("diamond"));
        assertNull(material.parse("minecraft:BLUE"));
        var enabled = setting("network", "BOOLEAN", null, null);
        assertEquals(Boolean.TRUE, enabled.parse("true"));
        assertNull(enabled.parse("False"));
        assertTrue(enabled.row(true, 4).getBoolean("value"));
    }
}
