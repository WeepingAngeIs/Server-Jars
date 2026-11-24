package dev.unyielding.jail;

import dev.unyielding.jail.Loc;
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
    public static final int LEGACY_GEOMETRY = 1;
    public static final int INTERROGATION_GEOMETRY = 2;
    public static final int INTERROGATION_CHAIR_GEOMETRY = 3;
    public static final int INVALID_GEOMETRY = Integer.MIN_VALUE;
    public record Assignment(Loc jail, Loc prior, int geometry, boolean confiscateInventory) {
        public Assignment(Loc jail, Loc prior, int geometry) { this(jail, prior, geometry, true); }
        /** Existing call sites and saved assignments use the original 7-block cage. */
        public Assignment(Loc jail, Loc prior) { this(jail, prior, LEGACY_GEOMETRY); }
    }
    private Loc location;
    private int geometry = LEGACY_GEOMETRY;
    private boolean integratedCustodyInitialized;
    private boolean retainedCustodyInitialized;
    private final Map<UUID, Assignment> prisoners = new HashMap<>();

    public static JailData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(JailData::new, JailData::load, null), "unyielding_jail");
    }
    public Loc location() { return location; }
    public int geometry() { return geometry; }
    boolean integratedCustodyInitialized(){return integratedCustodyInitialized;}
    void markIntegratedCustodyInitialized(){integratedCustodyInitialized=true;setDirty();}
    boolean retainedCustodyInitialized(){return retainedCustodyInitialized;}
    void markRetainedCustodyInitialized(){retainedCustodyInitialized=true;setDirty();}
    public void setLocation(Loc location) { setLocation(location, LEGACY_GEOMETRY); }
    public void setLocation(Loc location, int geometry) { this.location = location; this.geometry = geometry; setDirty(); }
    public boolean hasPrisoners() { return !prisoners.isEmpty(); }
    Map<UUID, Assignment> assignments() { return Map.copyOf(prisoners); }
    public Assignment assignment(UUID player) { return prisoners.get(player); }
    public void assign(UUID player, Assignment assignment) { prisoners.put(player, assignment); setDirty(); }
    public void remove(UUID player) { prisoners.remove(player); setDirty(); }

    static JailData load(CompoundTag tag, HolderLookup.Provider ignored) {
        JailData data = new JailData();
        if (tag.contains("location", Tag.TAG_COMPOUND)) data.location = Loc.fromTag(tag.getCompound("location"));
        data.geometry = readGeometry(tag);
        data.integratedCustodyInitialized=tag.getBoolean("integratedCustodyInitialized");
        data.retainedCustodyInitialized=tag.getBoolean("retainedCustodyInitialized");
        for (Tag item : tag.getList("prisoners", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) item;
            if (row.hasUUID("player") && row.contains("jail", Tag.TAG_COMPOUND) && row.contains("prior", Tag.TAG_COMPOUND))
                data.prisoners.put(row.getUUID("player"), new Assignment(Loc.fromTag(row.getCompound("jail")), Loc.fromTag(row.getCompound("prior")), readGeometry(row), readConfiscation(row)));
        }
        return data;
    }
    private static int readGeometry(CompoundTag tag) {
        if (tag.contains("confiscate_inventory") && (!tag.contains("confiscate_inventory",Tag.TAG_BYTE)
                || tag.getByte("confiscate_inventory")<0 || tag.getByte("confiscate_inventory")>1)) return INVALID_GEOMETRY;
        if (!tag.contains("geometry")) return LEGACY_GEOMETRY;
        return tag.contains("geometry", Tag.TAG_INT) ? tag.getInt("geometry") : INVALID_GEOMETRY;
    }
    /** A legacy assignment always belongs to the original confiscation contract. */
    static boolean readConfiscation(CompoundTag tag) {
        return !tag.contains("confiscate_inventory",Tag.TAG_BYTE) || tag.getByte("confiscate_inventory")!=0;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider ignored) {
        if (location != null) tag.put("location", location.toTag());
        tag.putInt("geometry", geometry);
        if(integratedCustodyInitialized)tag.putBoolean("integratedCustodyInitialized",true);
        if(retainedCustodyInitialized)tag.putBoolean("retainedCustodyInitialized",true);
        ListTag rows = new ListTag();
        prisoners.forEach((player, assignment) -> {
            CompoundTag row = new CompoundTag();
            row.putUUID("player", player);
            row.put("jail", assignment.jail().toTag());
            row.put("prior", assignment.prior().toTag());
            row.putInt("geometry", assignment.geometry());
            row.putBoolean("confiscate_inventory", assignment.confiscateInventory());
            rows.add(row);
        });
        tag.put("prisoners", rows);
        return tag;
    }
}

