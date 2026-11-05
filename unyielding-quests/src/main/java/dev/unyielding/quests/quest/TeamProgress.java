package dev.unyielding.quests.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Quest progress of one team. */
public final class TeamProgress {
    public final Set<Long> completedQuests = new HashSet<>();
    public final Set<Long> completedTasks = new HashSet<>();
    public final Map<Long, Long> taskProgress = new HashMap<>();
    public final Map<String, Long> keys = new HashMap<>();
    /** player -> claimed reward ids */
    public final Map<UUID, Set<Long>> claimed = new HashMap<>();
    public transient boolean dirty;

    public boolean questDone(long id) {
        return completedQuests.contains(id);
    }

    public boolean taskDone(long id) {
        return completedTasks.contains(id);
    }

    public long progress(long taskId) {
        return taskProgress.getOrDefault(taskId, 0L);
    }

    public boolean isClaimed(UUID player, long reward) {
        Set<Long> s = claimed.get(player);
        return s != null && s.contains(reward);
    }

    public long key(String k) {
        return keys.getOrDefault(k, 0L);
    }

    public void mergeFrom(TeamProgress o) {
        completedQuests.addAll(o.completedQuests);
        completedTasks.addAll(o.completedTasks);
        for (Map.Entry<Long, Long> e : o.taskProgress.entrySet()) taskProgress.merge(e.getKey(), e.getValue(), Math::max);
        for (Map.Entry<String, Long> e : o.keys.entrySet()) keys.merge(e.getKey(), e.getValue(), Math::max);
        for (Map.Entry<UUID, Set<Long>> e : o.claimed.entrySet()) claimed.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
        dirty = true;
    }

    public TeamProgress copy() {
        TeamProgress p = new TeamProgress();
        p.mergeFrom(this);
        return p;
    }

    /** @param onlyFor if not null only this player's claim list is written (client sync). */
    public CompoundTag toTag(UUID onlyFor) {
        CompoundTag t = new CompoundTag();
        t.putLongArray("q", completedQuests.stream().mapToLong(Long::longValue).toArray());
        t.putLongArray("t", completedTasks.stream().mapToLong(Long::longValue).toArray());
        ListTag tp = new ListTag();
        for (Map.Entry<Long, Long> e : taskProgress.entrySet()) {
            if (e.getValue() <= 0 || completedTasks.contains(e.getKey())) continue;
            CompoundTag c = new CompoundTag();
            c.putLong("i", e.getKey());
            c.putLong("v", e.getValue());
            tp.add(c);
        }
        t.put("tp", tp);
        CompoundTag ks = new CompoundTag();
        for (Map.Entry<String, Long> e : keys.entrySet()) ks.putLong(e.getKey(), e.getValue());
        t.put("keys", ks);
        ListTag cl = new ListTag();
        for (Map.Entry<UUID, Set<Long>> e : claimed.entrySet()) {
            if (onlyFor != null && !e.getKey().equals(onlyFor)) continue;
            CompoundTag c = new CompoundTag();
            c.putUUID("p", e.getKey());
            c.putLongArray("r", e.getValue().stream().mapToLong(Long::longValue).toArray());
            cl.add(c);
        }
        t.put("claimed", cl);
        return t;
    }

    public static TeamProgress fromTag(CompoundTag t) {
        TeamProgress p = new TeamProgress();
        for (long l : t.getLongArray("q")) p.completedQuests.add(l);
        for (long l : t.getLongArray("t")) p.completedTasks.add(l);
        for (Tag e : t.getList("tp", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            p.taskProgress.put(c.getLong("i"), c.getLong("v"));
        }
        CompoundTag ks = t.getCompound("keys");
        for (String k : ks.getAllKeys()) p.keys.put(k, ks.getLong(k));
        for (Tag e : t.getList("claimed", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            Set<Long> s = new HashSet<>();
            for (long l : c.getLongArray("r")) s.add(l);
            p.claimed.put(c.getUUID("p"), s);
        }
        return p;
    }
}
