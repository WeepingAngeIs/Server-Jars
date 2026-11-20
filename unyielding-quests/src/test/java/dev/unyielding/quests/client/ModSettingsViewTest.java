package dev.unyielding.quests.client;

import org.junit.jupiter.api.Test;

import static dev.unyielding.quests.client.ModSettingsView.ModAction.*;
import static org.junit.jupiter.api.Assertions.*;

class ModSettingsViewTest {
    @Test void searchUsesDisplayNameOrIdAndIgnoresCaseAndSeparators() {
        String mod = "UwU Dimension uwudimension";
        for (String query : new String[]{"uwudimension", " UWU-  Dimension ", "uwu_dimension", ""}) {
            assertTrue(ModSettingsView.matches(ModSettingsView.normalized(query), mod));
        }
        assertFalse(ModSettingsView.matches("heart launcher", mod));
    }

    @Test void searchesForSettingTitlesAndAliases() {
        String searchable = "Heart Launcher excavation explosions, block breaking, excavation";
        for (String query : new String[]{"Heart Launcher", "explosions", "BLOCK-BREAKING"}) {
            assertTrue(ModSettingsView.matches(ModSettingsView.normalized(query), searchable));
        }
        assertFalse(ModSettingsView.matches("weather", searchable));
    }

    @Test void missingAndUnsupportedModsDoNotOfferAddOrOpen() {
        assertEquals(ADD, ModSettingsView.action(true, false, false));
        assertEquals(OPEN, ModSettingsView.action(true, true, false));
        assertEquals(MISSING, ModSettingsView.action(false, false, false));
        assertEquals(MISSING, ModSettingsView.action(false, true, false));
        assertEquals(UNSUPPORTED, ModSettingsView.action(true, false, true));
        assertEquals(UNSUPPORTED, ModSettingsView.action(true, true, true));
    }

    @Test void rendersOnlyServerReportedActivationTiming() {
        assertEquals(new ModSettingsView.Timing("Applies immediately", false), ModSettingsView.timing("LIVE", ""));
        assertEquals(new ModSettingsView.Timing("No restart declared by the mod", false),
                ModSettingsView.timing("LIVE", "No restart declared by the mod"));
        assertEquals(new ModSettingsView.Timing("World restart required", true), ModSettingsView.timing("WORLD_RESTART", ""));
        assertEquals(new ModSettingsView.Timing("Game or server restart required", true), ModSettingsView.timing("GAME_RESTART", ""));
        assertEquals(new ModSettingsView.Timing("Reopen world", false), ModSettingsView.timing("", "Reopen world"));
        assertEquals(new ModSettingsView.Timing("Restart timing unknown", false), ModSettingsView.timing("UNKNOWN", ""));
    }

    @Test void acceptsDecimalsAndScientificNotationWithoutAcceptingNonNumericFloatValues() {
        for (String value : new String[]{"0", "-42", "0.25", "1E-6"}) {
            assertTrue(ModSettingsView.isNumber(value));
        }
        for (String value : new String[]{"", "1.2.3", "NaN", "Infinity", "1".repeat(65)}) {
            assertFalse(ModSettingsView.isNumber(value));
        }
    }
}
