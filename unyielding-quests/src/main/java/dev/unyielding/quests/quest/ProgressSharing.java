package dev.unyielding.quests.quest;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Progress ownership and mode transitions, independent of Minecraft and party membership changes. */
public final class ProgressSharing {
    private ProgressSharing() { }

    public static UUID owner(UUID player, UUID team, boolean shared) {
        return shared ? team : player;
    }

    /** Preserve the progress current members could already use. Future joiners are not included. */
    public static <T> void preservePersonal(Map<UUID, UUID> memberships, Map<UUID, T> records,
                                           Supplier<T> create, BiConsumer<T, T> merge) {
        for (Map.Entry<UUID, UUID> member : memberships.entrySet()) {
            if (member.getKey().equals(member.getValue())) continue;
            T shared = records.get(member.getValue());
            if (shared != null) merge.accept(records.computeIfAbsent(member.getKey(), ignored -> create.get()), shared);
        }
    }

    /** Sharing is explicitly enabled; current members contribute their personal progress. */
    public static <T> void enableShared(Map<UUID, UUID> memberships, Map<UUID, T> records,
                                       Supplier<T> create, BiConsumer<T, T> merge) {
        for (Map.Entry<UUID, UUID> member : memberships.entrySet()) {
            if (member.getKey().equals(member.getValue())) continue;
            T personal = records.get(member.getKey());
            if (personal != null) merge.accept(records.computeIfAbsent(member.getValue(), ignored -> create.get()), personal);
        }
    }
}
