package dev.unyielding.quests.essentials;

import java.util.List;

/** Pure rules shared by command validation and unit tests. */
public final class PartyMeetingPolicy {
    private PartyMeetingPolicy() {}
    public static boolean permitted(boolean enabled, boolean permission, boolean party, boolean member) {
        return enabled && permission && party && member;
    }
    public static boolean dimensionAllowed(String source, String target, List<? extends String> allowed) {
        return source != null && source.equals(target) && allowed.contains(target);
    }
    public static boolean validCoordinates(double x, double y, double z, float yaw, float pitch) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch) && Math.abs(x) < 29999984 && Math.abs(z) < 29999984;
    }
}
