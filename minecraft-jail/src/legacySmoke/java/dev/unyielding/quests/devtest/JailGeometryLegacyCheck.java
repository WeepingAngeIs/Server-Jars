package dev.unyielding.quests.devtest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;

/** Preserved pre-extraction fixture; not a configured or executed source set. */
final class JailGeometryLegacyCheck {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void checkJailGeometry(MinecraftServer server) throws Exception {
        var id = java.util.UUID.randomUUID();
        var loc = new dev.unyielding.quests.essentials.Loc("minecraft:overworld", 3.5, 70, 3.5, 0, 0);
        var row = new CompoundTag(); row.putUUID("player", id); row.put("jail", loc.toTag()); row.put("prior", loc.toTag());
        var rows = new net.minecraft.nbt.ListTag(); rows.add(row);
        var old = new CompoundTag(); old.put("location", loc.toTag()); old.put("prisoners", rows);
        var load = dev.unyielding.quests.jail.JailData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var legacy = (dev.unyielding.quests.jail.JailData) load.invoke(null, old, null);
        require(legacy.geometry() == dev.unyielding.quests.jail.JailData.LEGACY_GEOMETRY && legacy.assignment(id).geometry() == dev.unyielding.quests.jail.JailData.LEGACY_GEOMETRY && loc.equals(legacy.assignment(id).jail()), "legacy jail assignment or location was reinterpreted");
        var updated = new dev.unyielding.quests.jail.JailData();
        updated.setLocation(loc, dev.unyielding.quests.jail.JailData.INTERROGATION_GEOMETRY);
        updated.assign(id, new dev.unyielding.quests.jail.JailData.Assignment(loc, loc, 42));
        var reloaded = (dev.unyielding.quests.jail.JailData) load.invoke(null, updated.save(new CompoundTag(), null), null);
        require(reloaded.geometry() == dev.unyielding.quests.jail.JailData.INTERROGATION_GEOMETRY && reloaded.assignment(id).geometry() == 42, "jail geometry version did not survive serialization");
        row.putString("geometry", "broken"); old.putString("geometry", "broken");
        var invalid = (dev.unyielding.quests.jail.JailData) load.invoke(null, old, null);
        require(invalid.geometry() == dev.unyielding.quests.jail.JailData.INVALID_GEOMETRY && invalid.assignment(id) != null && invalid.assignment(id).geometry() == dev.unyielding.quests.jail.JailData.INVALID_GEOMETRY && loc.equals(invalid.assignment(id).prior()), "malformed geometry was not retained as invalid");
        require(!dev.unyielding.quests.jail.JailPlacement.intact(server, loc, invalid.assignment(id).geometry()), "invalid geometry did not fail closed");
        var structure = dev.unyielding.quests.jail.JailPlacement.class.getDeclaredMethod("newStructure", int.class, int.class, int.class);
        structure.setAccessible(true);
        for (int z=0; z<7; z++) for (int y=0; y<7; y++) {
            require(Boolean.TRUE.equals(structure.invoke(null, 0,y,z)) && Boolean.TRUE.equals(structure.invoke(null, 6,y,z)), "prisoner wall has a gap");
        }
        for (int x=0; x<=6; x++) for (int y=0; y<7; y++) {
            require(Boolean.TRUE.equals(structure.invoke(null, x,y,0)) && Boolean.TRUE.equals(structure.invoke(null, x,y,6)), "prisoner side has a gap");
        }
        require(Boolean.FALSE.equals(structure.invoke(null, 12,1,3)) && Boolean.FALSE.equals(structure.invoke(null, 12,2,3)) && Boolean.TRUE.equals(structure.invoke(null, 12,3,3)) && Boolean.FALSE.equals(structure.invoke(null, 9,1,3)), "staff entrance is not accessible");
    }

}
