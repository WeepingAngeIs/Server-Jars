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
    public final QuestRewardEpochs rewardEpochs = new QuestRewardEpochs();
    private Tag invalidRewardEpochs;
    public boolean validRewardEpochs() { return invalidRewardEpochs == null; }
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
        if (tag.contains("reward_epochs")) {
            Tag raw = tag.get("reward_epochs");
            try {
                if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
                Set<UUID> seen = new HashSet<>();
                for (Tag e : (ListTag)raw) {
                    CompoundTag c = (CompoundTag)e;
                    if (!c.hasUUID("player") || !c.hasUUID("base") || !seen.add(c.getUUID("player"))) throw new IllegalArgumentException();
                    UUID player = c.getUUID("player"); d.rewardEpochs.bases.put(player, c.getUUID("base"));
                    if (!(c.get("overrides") instanceof ListTag entries) || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)) throw new IllegalArgumentException();
                    Map<Long, UUID> overrides = new HashMap<>();
                    for (Tag item : entries) {
                        CompoundTag entry = (CompoundTag)item;
                        if (!entry.contains("reward", Tag.TAG_LONG) || !entry.hasUUID("epoch") || overrides.put(entry.getLong("reward"), entry.getUUID("epoch")) != null) throw new IllegalArgumentException();
                    }
                    d.rewardEpochs.overrides.put(player, overrides);
                }
            } catch (RuntimeException invalid) { d.invalidRewardEpochs = raw.copy(); }
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
        ListTag epochs = new ListTag();
        Set<UUID> epochPlayers = new HashSet<>(rewardEpochs.bases.keySet()); epochPlayers.addAll(rewardEpochs.overrides.keySet());
        for (UUID player : epochPlayers) {
            CompoundTag c = new CompoundTag(); c.putUUID("player", player); c.putUUID("base", rewardEpochs.bases.getOrDefault(player, QuestRewardEpochs.LEGACY));
            ListTag overrides = new ListTag();
            rewardEpochs.overrides.getOrDefault(player, Map.of()).forEach((reward, epoch) -> {
                CompoundTag entry = new CompoundTag(); entry.putLong("reward", reward); entry.putUUID("epoch", epoch); overrides.add(entry);
            });
            c.put("overrides", overrides); epochs.add(c);
        }
        tag.put("reward_epochs", invalidRewardEpochs == null ? epochs : invalidRewardEpochs.copy());
        tag.putBoolean("sharing_initialized", sharingInitialized);
        tag.putBoolean("sharing_enabled", sharingEnabled);
        return tag;
    }

    public TeamProgress get(UUID team) {
        return teams.computeIfAbsent(team, k -> new TeamProgress());
    }

    /** Reconcile only shared-mode membership changes; null means no shared team. */
    public void membershipChanged(UUID player, UUID fromTeam, UUID toTeam) {
        if (!sharingEnabled) return;
        TeamProgress mine = get(player);
        if (fromTeam != null) mine.mergeFrom(get(fromTeam));
        if (toTeam != null) get(toTeam).mergeFrom(mine);
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
        if (!validRewardEpochs()) throw new IllegalStateException("Quest reward generations require review");
        rewardEpochs.reset(players, rewards);
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
