package dev.unyielding.quests.team;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent team storage (world data). */
public final class TeamData extends SavedData {
    public static final String NAME = "unyieldingquests_teams";

    public final Map<UUID, Team> teams = new LinkedHashMap<>();
    /** player -> team they currently belong to */
    public final Map<UUID, UUID> playerTeam = new HashMap<>();
    /** party team -> (player -> invite time ms) */
    public final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();

    public static SavedData.Factory<TeamData> factory() {
        return new SavedData.Factory<>(TeamData::new, TeamData::load, null);
    }

    public static TeamData load(CompoundTag tag, HolderLookup.Provider provider) {
        TeamData d = new TeamData();
        for (Tag e : tag.getList("teams", Tag.TAG_COMPOUND)) {
            Team t = Team.fromTag((CompoundTag) e);
            d.teams.put(t.id, t);
        }
        for (Tag e : tag.getList("pt", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            d.playerTeam.put(c.getUUID("p"), c.getUUID("t"));
        }
        for (Tag e : tag.getList("inv", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) e;
            d.invites.computeIfAbsent(c.getUUID("t"), k -> new HashMap<>()).put(c.getUUID("p"), c.getLong("time"));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag ts = new ListTag();
        for (Team t : teams.values()) ts.add(t.toTag(true));
        tag.put("teams", ts);
        ListTag pt = new ListTag();
        for (Map.Entry<UUID, UUID> e : playerTeam.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("p", e.getKey());
            c.putUUID("t", e.getValue());
            pt.add(c);
        }
        tag.put("pt", pt);
        ListTag inv = new ListTag();
        for (Map.Entry<UUID, Map<UUID, Long>> e : invites.entrySet()) {
            for (Map.Entry<UUID, Long> p : e.getValue().entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putUUID("t", e.getKey());
                c.putUUID("p", p.getKey());
                c.putLong("time", p.getValue());
                inv.add(c);
            }
        }
        tag.put("inv", inv);
        return tag;
    }
}
