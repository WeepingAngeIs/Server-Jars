package dev.unyielding.quests.settings;

/** Data minimization rules for the in-game configuration audit. */
final class ConfigAuditPolicy {
    private ConfigAuditPolicy() {}
    static String printable(Object value) {
        return value instanceof Number || value instanceof Boolean ? value.toString() : "[text or list omitted]";
    }
    static int retainedStart(int size, int limit) { return Math.max(0, size - limit); }
}
