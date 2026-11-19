package dev.unyielding.quests.client;

import java.util.Locale;
import java.util.regex.Pattern;

/** Presentation of server-reported state. Only the server authorizes a config change. */
final class ModSettingsView {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+");

    enum ModAction { ADD, OPEN, MISSING, UNSUPPORTED }

    static ModAction action(boolean installed, boolean added, boolean unsupported) {
        if (!installed) return ModAction.MISSING;
        if (unsupported) return ModAction.UNSUPPORTED;
        return added ? ModAction.OPEN : ModAction.ADD;
    }

    static String normalized(String text) {
        return SEPARATORS.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    static boolean matches(String normalizedQuery, String text) {
        return normalized(text).contains(normalizedQuery);
    }

    record Timing(String label, boolean restart) {}

    static Timing timing(String activation, String reportedLabel) {
        return switch (activation) {
            case "LIVE" -> new Timing("Applies immediately", false);
            case "WORLD_RESTART" -> new Timing("World restart required", true);
            case "GAME_RESTART" -> new Timing("Game or server restart required", true);
            default -> new Timing(reportedLabel.isBlank() ? "Restart timing unknown" : reportedLabel, false);
        };
    }

    private ModSettingsView() {}
}
