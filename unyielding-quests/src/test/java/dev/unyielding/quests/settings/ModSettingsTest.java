package dev.unyielding.quests.settings;

import dev.unyielding.quests.settings.ModSettings.Applies;
import dev.unyielding.quests.settings.ModSettings.ExcavationEndpoint;
import dev.unyielding.quests.settings.ModSettings.Result;
import dev.unyielding.quests.settings.ModSettings.Scope;
import dev.unyielding.quests.settings.ModSettings.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSettingsTest {
    @TempDir Path directory;
    private final List<Edit> audit = new ArrayList<>();

    @Test
    void recognizesTheInstalledModByIdAndSuppliesItsDisplayName() {
        assertEquals(new ModSettings.Installed(true, "UwU Dimension Beta"),
                ModSettings.discover(Map.of("uwudimension", "UwU Dimension Beta")));
        assertEquals(new ModSettings.Installed(false, "UwU Dimension"),
                ModSettings.discover(Map.of("othermod", "UwU Dimension")));
        assertEquals(new ModSettings.Installed(false, "UwU Dimension"), ModSettings.discover(Map.of()));
    }

    @Test
    void labelsLiveChangesAndDifferentRestartRequirements() {
        assertEquals("Entire server", Scope.SERVER.label);
        assertEquals("Current world", Scope.WORLD.label);
        assertEquals("Specific dimension", Scope.DIMENSION.label);
        assertEquals("Applies live", Applies.LIVE.label);
        assertEquals("Reopen world", Applies.WORLD_RESTART.label);
        assertEquals("Restart game or dedicated server", Applies.GAME_RESTART.label);
    }

    @Test
    void deniesAnEditWithoutAdministratorPermissionOrOptIn() {
        FileEndpoint endpoint = endpoint(false);
        assertEquals(Result.DENIED, change(false, true, ModSettings.KEY, "false", "true", endpoint));
        assertEquals(Result.NOT_ADDED, change(true, false, ModSettings.KEY, "false", "true", endpoint));
        assertEquals(0, endpoint.attempts);
        assertTrue(audit.isEmpty());
    }

    @Test
    void rejectsUnrecognizedKeysAndNoncanonicalBooleans() {
        FileEndpoint endpoint = endpoint(false);
        assertEquals(Result.INVALID, change(true, true, "external.uwudimension.other", "false", "true", endpoint));
        for (String invalid : List.of("", "False", "TRUE", "1", "on", " true", "\"true\"")) {
            assertEquals(Result.INVALID, change(true, true, ModSettings.KEY, invalid, "true", endpoint));
            assertEquals(Result.INVALID, change(true, true, ModSettings.KEY, "false", invalid, endpoint));
        }
        assertEquals(0, endpoint.attempts);
        assertTrue(audit.isEmpty());
    }

    @Test
    void missingEndpointLeavesTheSettingUnavailable() {
        assertEquals(Result.UNAVAILABLE, ModSettings.change(true, true, ModSettings.KEY, "false", "true",
                Optional.empty(), this::record));
        assertTrue(audit.isEmpty());
    }

    @Test
    void noOpAndStaleCompareDoNotSaveOrAudit() {
        FileEndpoint endpoint = endpoint(true);
        assertEquals(Result.UNCHANGED, change(true, true, ModSettings.KEY, "true", "true", endpoint));
        assertEquals(0, endpoint.attempts);
        assertEquals(Result.CONFLICT, change(true, true, ModSettings.KEY, "false", "true", endpoint));
        assertEquals(1, endpoint.attempts);
        assertTrue(endpoint.read().enabled());
        assertTrue(audit.isEmpty());
    }

    @Test
    void savePersistsAcrossEndpointInstancesAndUndoRecordsTheReverseChange() {
        FileEndpoint endpoint = endpoint(false);
        assertEquals(Result.SAVED, change(true, true, ModSettings.KEY, "false", "true", endpoint));
        Edit original = new Edit(false, true);
        assertEquals(List.of(original), audit);

        FileEndpoint reopened = new FileEndpoint(endpoint.savedValue);
        assertEquals(new State(true, Scope.WORLD, Applies.WORLD_RESTART), reopened.read());
        assertEquals(Result.DENIED, undo(false, original, reopened));
        assertEquals(Result.SAVED, undo(true, original, reopened));
        assertFalse(new FileEndpoint(endpoint.savedValue).read().enabled());
        assertEquals(List.of(original, new Edit(true, false)), audit);
    }

    @Test
    void undoLeavesAnInterveningExternalChangeIntact() {
        FileEndpoint endpoint = endpoint(false);
        assertEquals(Result.SAVED, change(true, true, ModSettings.KEY, "false", "true", endpoint));
        new FileEndpoint(endpoint.savedValue).persist(false);
        assertEquals(Result.CONFLICT, undo(true, audit.getFirst(), endpoint));
        assertFalse(endpoint.read().enabled());
        assertEquals(List.of(new Edit(false, true)), audit);
    }

    private Result change(boolean admin, boolean added, String key, String expected, String desired,
                          FileEndpoint endpoint) {
        return ModSettings.change(admin, added, key, expected, desired, Optional.of(endpoint), this::record);
    }

    private Result undo(boolean admin, Edit edit, FileEndpoint endpoint) {
        return ModSettings.undo(admin, ModSettings.KEY, Boolean.toString(edit.before()), Boolean.toString(edit.after()),
                Optional.of(endpoint), this::record);
    }

    private void record(boolean before, boolean after) {
        audit.add(new Edit(before, after));
    }

    private FileEndpoint endpoint(boolean enabled) {
        FileEndpoint endpoint = new FileEndpoint(directory.resolve("fixture-value"));
        endpoint.persist(enabled);
        return endpoint;
    }

    private record Edit(boolean before, boolean after) {}

    private static final class FileEndpoint implements ExcavationEndpoint {
        private final Path savedValue;
        private int attempts;

        private FileEndpoint(Path savedValue) {
            this.savedValue = savedValue;
        }

        @Override
        public State read() {
            try {
                return new State(Boolean.parseBoolean(Files.readString(savedValue)), Scope.WORLD, Applies.WORLD_RESTART);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override
        public boolean compareAndSet(boolean expected, boolean enabled) {
            attempts++;
            if (read().enabled() != expected) return false;
            persist(enabled);
            return true;
        }

        private void persist(boolean enabled) {
            try {
                Files.writeString(savedValue, Boolean.toString(enabled));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
