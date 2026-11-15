package dev.unyielding.quests.essentials;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Separate server-only storage: never copies homes or broadcasts coordinates in team lists. */
public final class PartyMeetingData extends SavedData {
    public record Meeting(UUID setter, Loc location) {}
    final Map<UUID, Meeting> points = new HashMap<>();
    static PartyMeetingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PartyMeetingData::new, PartyMeetingData::load, null), "unyielding_party_meetings");
    }
    static PartyMeetingData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new PartyMeetingData();
        for (Tag entry : tag.getList("points", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (row.hasUUID("party") && row.hasUUID("setter") && row.contains("location", Tag.TAG_COMPOUND))
                data.points.put(row.getUUID("party"), new Meeting(row.getUUID("setter"), Loc.fromTag(row.getCompound("location"))));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag rows = new ListTag();
        points.forEach((party, meeting) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("party", party); row.putUUID("setter", meeting.setter());
            row.put("location", meeting.location().toTag()); rows.add(row);
        });
        tag.put("points", rows); return tag;
    }
}
