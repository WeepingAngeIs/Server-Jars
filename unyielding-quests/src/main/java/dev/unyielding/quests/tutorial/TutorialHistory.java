package dev.unyielding.quests.tutorial;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** World-owned history survives reconnects, deaths and server restarts. */
public final class TutorialHistory extends SavedData {
    private final Set<UUID> dismissed = new HashSet<>();
    public static TutorialHistory get(ServerPlayer player) {
        return player.server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(TutorialHistory::new, TutorialHistory::load, null), "unyieldingquests_tutorial");
    }
    public boolean contains(UUID player) { return dismissed.contains(player); }
    public void mark(UUID player) { if (dismissed.add(player)) setDirty(); }
    public static TutorialHistory load(CompoundTag tag, HolderLookup.Provider provider) {
        TutorialHistory history = new TutorialHistory();
        for (Tag raw : tag.getList("dismissed", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            if (row.hasUUID("player")) history.dismissed.add(row.getUUID("player"));
        }
        return history;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag rows = new ListTag();
        for (UUID player : dismissed) {
            CompoundTag row = new CompoundTag(); row.putUUID("player", player); rows.add(row);
        }
        tag.put("dismissed", rows); return tag;
    }
}
