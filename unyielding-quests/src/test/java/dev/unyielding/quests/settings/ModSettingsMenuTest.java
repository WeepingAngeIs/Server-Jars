package dev.unyielding.quests.settings;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ModSettingsMenuTest {
    private static final String REGISTERED = "registered.examplemod." + "a".repeat(64);

    @Test void pageRequestsCarryOnlyQueryAndIndexAndEditsRequireExpectedAndDesiredValues() {
        var data = new JsonObject();
        data.addProperty("q", "speed \"at night\"");
        data.addProperty("p", 3);
        var page = ModSettingsMenu.Request.parse(data.toString(), false);
        assertEquals("speed \"at night\"", page.query());
        assertEquals(3, page.index());
        assertThrows(RuntimeException.class, () -> ModSettingsMenu.Request.parse(data.toString(), true));

        data.addProperty("key", REGISTERED);
        data.addProperty("expected", "FAST");
        data.addProperty("value", "SLOW");
        var edit = ModSettingsMenu.Request.parse(data.toString(), true);
        assertEquals(REGISTERED, edit.key());
        assertEquals("FAST", edit.expected());
        assertEquals("SLOW", edit.desired());
        assertThrows(RuntimeException.class, () -> ModSettingsMenu.Request.parse(data.toString(), false));
        data.addProperty("value", true);
        assertThrows(RuntimeException.class, () -> ModSettingsMenu.Request.parse(data.toString(), true));
    }

    @Test void malformedAndOversizedRequestsCannotBeUsedAsServerSettingsPages() {
        for (var json : List.of("{}", "[]", "{\"p\":-1,\"q\":\"\"}", "{\"p\":0.5,\"q\":\"\"}",
                "{\"p\":\"1\",\"q\":\"\"}", "{\"p\":0,\"q\":true}", "{\"q\":\"\",\"p\":0,\"cmd\":\"x\"}",
                "{\"p\":0,\"q\":\"" + "a".repeat(97) + "\"}", " ".repeat(1601))) {
            assertThrows(RuntimeException.class, () -> ModSettingsMenu.Request.parse(json, false), json);
        }
    }

    @Test void legacySavedUwUPagesAndEmptySelectionsSurviveMigrationPerAdministrator() {
        UUID legacy = UUID.randomUUID(), modern = UUID.randomUUID(), empty = UUID.randomUUID();
        ListTag admins = new ListTag();
        admins.add(admin(legacy));
        var selected = admin(modern);
        ListTag mods = new ListTag();
        for (String id : List.of("uwudimension", "examplemod", "examplemod")) mods.add(StringTag.valueOf(id));
        selected.put("mods", mods);
        admins.add(selected);
        var unselected = admin(empty);
        unselected.put("mods", new ListTag());
        admins.add(unselected);
        admins.add(new CompoundTag());
        var oldSave = new CompoundTag();
        oldSave.put("admins", admins);

        var migrated = ModSettingsMenu.Selections.load(oldSave, null).save(new CompoundTag(), null);
        var reopened = ModSettingsMenu.Selections.load(migrated, null).save(new CompoundTag(), null);
        var expected = Map.of(legacy, List.of("uwudimension"), modern, List.of("examplemod", "uwudimension"),
                empty, List.<String>of());
        assertEquals(expected, ids(migrated));
        assertEquals(expected, ids(reopened));
    }

    @Test void auditCanUndoCanonicalRegisteredEnumNamesAndPreservesRedactionForText() {
        assertTrue(ModSettingsMenu.undoableKey(REGISTERED));
        assertTrue(ModSettingsMenu.undoableKey(ModSettings.KEY));
        assertFalse(ModSettingsMenu.undoableKey("registered.examplemod.wrong"));
        assertFalse(ModSettingsMenu.undoableKey("__mod_settings_page"));
        assertEquals("NORMAL", ConfigAudit.snapshotValue(REGISTERED, Speed.NORMAL));
        assertEquals("42", ConfigAudit.snapshotValue(REGISTERED, 42));
        assertEquals("false", ConfigAudit.snapshotValue(ModSettings.KEY, false));
        assertEquals("[text or list omitted]", ConfigAudit.snapshotValue(REGISTERED, "private text"));
        assertEquals("[text or list omitted]", ConfigAudit.snapshotValue("other.key", Speed.NORMAL));
    }

    private static CompoundTag admin(UUID uuid) {
        var entry = new CompoundTag();
        entry.putUUID("id", uuid);
        return entry;
    }

    private static Map<UUID, List<String>> ids(CompoundTag tag) {
        var result = new HashMap<UUID, List<String>>();
        for (Tag raw : tag.getList("admins", Tag.TAG_COMPOUND)) {
            var admin = (CompoundTag) raw;
            result.put(admin.getUUID("id"), admin.getList("mods", Tag.TAG_STRING).stream().map(Tag::getAsString).toList());
        }
        return result;
    }

    private enum Speed {
        NORMAL;
        @Override public String toString() { return "Default flight speed"; }
    }
}
