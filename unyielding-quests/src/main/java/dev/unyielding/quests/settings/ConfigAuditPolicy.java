package dev.unyielding.quests.settings;

/** Data minimization rules for the in-game configuration audit. */
final class ConfigAuditPolicy {
    private ConfigAuditPolicy() {}
    static String printable(Object value) {
        return value instanceof Number || value instanceof Boolean ? value.toString() : "[text or list omitted]";
    }
    static String display(String key,String value) {
        if (DurationInput.applies(key)) {
            try { int seconds = Integer.parseInt(value); return seconds >= 0 ? DurationInput.format(seconds) : value; }
            catch (NumberFormatException failure) { return value; }
        }
        if (!key.equals("ground.interval") && !key.equals("ground.grace")) return value;
        try { long ticks=Long.parseLong(value); if(ticks<0) return value; long seconds=ticks/20; return (seconds/60)+"m "+(seconds%60)+"s"; }
        catch(NumberFormatException failure) { return value; }
    }
    static boolean undoable(String key, String before, String after, String current) {
        if (!key.equals("quests.custom_pokemon.map_icon_scale") && !key.equals("quests.origin_creative_flight_speed") && !DurationInput.applies(key)) return false;
        if (!after.equals(current) || before.equals(after)) return false;
        try { return Double.isFinite(Double.parseDouble(before)) && Double.isFinite(Double.parseDouble(after)); }
        catch (NumberFormatException failure) { return false; }
    }
    static int retainedStart(int size, int limit) { return Math.max(0, size - limit); }
    static int pageCount(int size) { return Math.max(1, (size + 9) / 10); }
    static int pageHighIndex(int size, int page) { return size - 1 - (page - 1) * 10; }
}
