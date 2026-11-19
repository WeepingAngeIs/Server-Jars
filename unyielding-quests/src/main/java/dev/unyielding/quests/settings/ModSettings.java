package dev.unyielding.quests.settings;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/** The reviewed setting and its server-owned integration contract. */
final class ModSettings {
    static final String UWU = "uwudimension";
    static final String KEY = "external." + UWU + ".heart_launcher_excavation";
    static final String TITLE = "Heart Launcher excavation";
    static final String ALIASES = "explosions, block breaking, excavation";

    record Installed(boolean present, String name) {}

    static Installed discover(Map<String, String> installed) {
        return new Installed(installed.containsKey(UWU), installed.getOrDefault(UWU, "UwU Dimension"));
    }

    enum Scope {
        SERVER("Entire server"), WORLD("Current world"), DIMENSION("Specific dimension");

        final String label;

        Scope(String label) {
            this.label = label;
        }
    }

    enum Applies {
        LIVE("Applies live"), WORLD_RESTART("Reopen world"), GAME_RESTART("Restart game or dedicated server");

        final String label;

        Applies(String label) {
            this.label = label;
        }
    }

    /** The persisted value used for compare-and-set, including changes still waiting for a restart. */
    record State(boolean enabled, Scope scope, Applies applies) {
        State {
            Objects.requireNonNull(scope);
            Objects.requireNonNull(applies);
        }
    }

    interface ExcavationEndpoint {
        /** Read the setting, effective scope and activation timing from UwU. */
        State read();

        /**
         * On the server thread, compare the saved setting and durably set the requested value.
         * True acknowledges the save. False means the value changed and nothing was written.
         * The endpoint owns persistence and must preserve all normal claim protections.
         */
        boolean compareAndSet(boolean expected, boolean enabled);
    }

    enum Result {
        SAVED("Saved."), DENIED("Server administrator permission is required."),
        INVALID("Unknown setting or invalid on/off value."), NOT_ADDED("Add UwU Dimension first."),
        UNAVAILABLE("The UwU server integration is unavailable."),
        CONFLICT("The value changed. Review the current value and try again."),
        UNCHANGED("The value is unchanged.");

        final String message;

        Result(String message) {
            this.message = message;
        }
    }

    static Result change(boolean admin, boolean added, String key, String expected, String value,
                         Optional<ExcavationEndpoint> endpoint, BiConsumer<Boolean, Boolean> audit) {
        if (!admin) return Result.DENIED;
        if (!KEY.equals(key) || !isBoolean(expected) || !isBoolean(value)) return Result.INVALID;
        if (!added) return Result.NOT_ADDED;
        if (endpoint.isEmpty()) return Result.UNAVAILABLE;
        boolean before = Boolean.parseBoolean(expected);
        boolean next = Boolean.parseBoolean(value);
        if (before == next) return Result.UNCHANGED;
        if (!endpoint.get().compareAndSet(before, next)) return Result.CONFLICT;
        audit.accept(before, next);
        return Result.SAVED;
    }

    static Result undo(boolean admin, String key, String before, String after,
                       Optional<ExcavationEndpoint> endpoint, BiConsumer<Boolean, Boolean> audit) {
        // The world audit is shared by admins. Undo doesn't require a personal menu bookmark.
        return change(admin, true, key, after, before, endpoint, audit);
    }

    private static boolean isBoolean(String value) {
        return "true".equals(value) || "false".equals(value);
    }

    private ModSettings() {}
}
