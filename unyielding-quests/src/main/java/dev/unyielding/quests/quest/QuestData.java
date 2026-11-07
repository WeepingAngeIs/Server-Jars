package dev.unyielding.quests.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent quest progress of all teams. */
public final class QuestData extends SavedData {
    public static final String NAME = "unyieldingquests_progress";
    public final Map<UUID, TeamProgress> teams = new HashMap<>();
    /** A reward belongs to the player, regardless of the active progress record or party. */
    public final Map<UUID, Set<Long>> claimedRewards = new HashMap<>();
    public boolean sharingInitialized;
    public boolean sharingEnabled;

    public static SavedData.Factory<QuestData> factory() {
        return new SavedData.Factory<>(QuestData::new, QuestData::load, null);
    }

    public static QuestData load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestData d = new QuestData();
        for (Tag e : tag.getList("teams", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            d.teams.put(c.getUUID("team"), TeamProgress.fromTag(c.getCompound("p")));
        }
        for (Tag e : tag.getList("player_claims", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Set<Long> rewards = d.claimedRewards.computeIfAbsent(c.getUUID("player"), ignored -> new HashSet<>());
            for (long reward : c.getLongArray("rewards")) rewards.add(reward);
        }
        // Import claims from every legacy team, including teams the player has since left.
        for (TeamProgress progress : d.teams.values()) {
            progress.claimed.forEach((player, rewards) ->
                    d.claimedRewards.computeIfAbsent(player, ignored -> new HashSet<>()).addAll(rewards));
        }
        d.sharingInitialized = tag.getBoolean("sharing_initialized");
        d.sharingEnabled = tag.getBoolean("sharing_enabled");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag l = new ListTag();
        for (Map.Entry<UUID, TeamProgress> e : teams.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("team", e.getKey());
            c.put("p", e.getValue().toTag(null));
            l.add(c);
        }
        tag.put("teams", l);
        ListTag claims = new ListTag();
        claimedRewards.forEach((player, rewards) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("player", player);
            c.putLongArray("rewards", rewards.stream().mapToLong(Long::longValue).toArray());
            claims.add(c);
        });
        tag.put("player_claims", claims);
        tag.putBoolean("sharing_initialized", sharingInitialized);
        tag.putBoolean("sharing_enabled", sharingEnabled);
        return tag;
    }

    public TeamProgress get(UUID team) {
        return teams.computeIfAbsent(team, k -> new TeamProgress());
    }

    public boolean isClaimed(UUID player, long reward) {
        return claimedRewards.getOrDefault(player, Set.of()).contains(reward);
    }

    public void markClaimed(UUID player, long reward) {
        claimedRewards.computeIfAbsent(player, ignored -> new HashSet<>()).add(reward);
        setDirty();
    }

    public void unclaim(UUID player, long reward) {
        Set<Long> rewards = claimedRewards.get(player);
        if (rewards != null) rewards.remove(reward);
        setDirty();
    }

    /** Explicit administrator resets are the only action that clears the player claim ledger. */
    public void forgetClaims(Set<UUID> players, Set<Long> rewards) {
        for (UUID player : players) {
            if (rewards == null) claimedRewards.remove(player);
            else {
                Set<Long> claimed = claimedRewards.get(player);
                if (claimed != null) claimed.removeAll(rewards);
            }
            // Clear redundant legacy copies too, so a later load cannot import removed claims again.
            for (TeamProgress progress : teams.values()) {
                if (rewards == null) progress.claimed.remove(player);
                else {
                    Set<Long> claimed = progress.claimed.get(player);
                    if (claimed != null) claimed.removeAll(rewards);
                }
            }
        }
        setDirty();
    }

    /** Reconcile live and offline players, including upgrades and configuration changes while stopped. */
    public boolean prepareSharing(boolean enabled, Map<UUID, UUID> memberships) {
        if (sharingInitialized && sharingEnabled == enabled) return false;
        if (!sharingInitialized || sharingEnabled) {
            ProgressSharing.preservePersonal(memberships, teams, TeamProgress::new, TeamProgress::mergeFrom);
        }
        if (enabled) ProgressSharing.enableShared(memberships, teams, TeamProgress::new, TeamProgress::mergeFrom);
        sharingInitialized = true;
        sharingEnabled = enabled;
        setDirty();
        return true;
    }
}
