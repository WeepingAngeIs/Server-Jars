package dev.unyielding.quests.essentials;

import java.util.Map;
import java.util.UUID;

/** Shared validation for destination lists and command construction. */
public final class DestinationPolicy {
    public static final int MAX_ROWS = 200;
    private static final int MAX_FIELD_BYTES = 256;
    private DestinationPolicy() {}
    public static boolean fitsPayload(String name, String dimension) {
        return name != null && dimension != null
                && name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_FIELD_BYTES
                && dimension.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_FIELD_BYTES;
    }
    public static <T> Map<String, T> ownHomes(Map<UUID, Map<String, T>> homes, UUID player) {
        return homes.getOrDefault(player, Map.of());
    }
    public static String command(String kind, String name) {
        if (!("home".equals(kind) || "warp".equals(kind)) || name == null || !name.matches("[A-Za-z0-9_.+\\-]+")) return null;
        return kind + " " + name;
    }
}
