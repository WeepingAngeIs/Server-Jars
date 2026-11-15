package dev.unyielding.quests.jail;

import dev.unyielding.quests.essentials.Loc;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned jail assignments; does not expose the saved return point to clients. */
public final class JailData extends SavedData {
    public record Assignment(Loc jail, Loc prior) {}
    private Loc location;
    private final Map<UUID, Assignment> prisoners = new HashMap<>();

    public static JailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(JailData::new, JailData::load, null), "unyielding_jail");
    }
    public Loc location() { return location; }
    public void setLocation(Loc location) { this.location = location; setDirty(); }
    public boolean hasPrisoners() { return !prisoners.isEmpty(); }
    public Assignment assignment(UUID player) { return prisoners.get(player); }
    public void assign(UUID player, Assignment assignment) { prisoners.put(player, assignment); setDirty(); }
    public void remove(UUID player) { prisoners.remove(player); setDirty(); }

    private static JailData load(CompoundTag tag, HolderLookup.Provider ignored) {
        JailData data = new JailData();
        if (tag.contains("location", Tag.TAG_COMPOUND)) data.location = Loc.fromTag(tag.getCompound("location"));
        for (Tag item : tag.getList("prisoners", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            if (row.hasUUID("player") && row.contains("jail", Tag.TAG_COMPOUND) && row.contains("prior", Tag.TAG_COMPOUND))
                data.prisoners.put(row.getUUID("player"), new Assignment(Loc.fromTag(row.getCompound("jail")), Loc.fromTag(row.getCompound("prior"))));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider ignored) {
        if (location != null) tag.put("location", location.toTag());
        ListTag rows = new ListTag();
        prisoners.forEach((player, assignment) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("player", player);
            row.put("jail", assignment.jail().toTag());
            row.put("prior", assignment.prior().toTag());
            rows.add(row);
        });
        tag.put("prisoners", rows);
        return tag;
    }
}
