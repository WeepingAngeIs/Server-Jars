package dev.unyielding.quests.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent quest progress of all teams. */
public final class QuestData extends SavedData {
    public static final String NAME = "unyieldingquests_progress";
    public final Map<UUID, TeamProgress> teams = new HashMap<>();

    public static SavedData.Factory<QuestData> factory() {
        return new SavedData.Factory<>(QuestData::new, QuestData::load, null);
    }

    public static QuestData load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestData d = new QuestData();
        for (Tag e : tag.getList("teams", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            d.teams.put(c.getUUID("team"), TeamProgress.fromTag(c.getCompound("p")));
        }
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
        return tag;
    }

    public TeamProgress get(UUID team) {
        return teams.computeIfAbsent(team, k -> new TeamProgress());
    }
}
