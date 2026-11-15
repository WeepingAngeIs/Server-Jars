package dev.unyielding.quests.quest;
import java.util.*;
/** Saved with quest progress, never replayed from a separate reset log. Personal across party changes. */
public final class QuestRewardEpochs {
    public static final UUID LEGACY = new UUID(0, 0);
    final Map<UUID, UUID> bases = new HashMap<>();
    final Map<UUID, Map<Long, UUID>> overrides = new HashMap<>();
    public UUID generation(UUID player, long reward) {
        return overrides.getOrDefault(player, Map.of()).getOrDefault(reward, bases.getOrDefault(player, LEGACY));
    }
    public void reset(Set<UUID> players, Set<Long> rewards) {
        for (UUID player : players) {
            if (rewards == null) { bases.put(player, UUID.randomUUID()); overrides.remove(player); }
            else for (long reward : rewards) overrides.computeIfAbsent(player, k -> new HashMap<>()).put(reward, UUID.randomUUID());
        }
    }
}
