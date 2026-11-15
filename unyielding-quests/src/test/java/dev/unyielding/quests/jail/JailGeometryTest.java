package dev.unyielding.quests.jail;

import dev.unyielding.quests.essentials.Loc;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JailGeometryTest {
    private static JailData read(CompoundTag tag) throws Exception {
        Method load = JailData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
        load.setAccessible(true);
        return (JailData) load.invoke(null, tag, null);
    }

    @Test void oldAssignmentsRemainLegacyWhenNoGeometryWasStored() throws Exception {
        UUID id = UUID.randomUUID();
        Loc loc = new Loc("minecraft:overworld", 3.5, 70, 3.5, 0, 0);
        CompoundTag row = new CompoundTag();
        row.putUUID("player", id); row.put("jail", loc.toTag()); row.put("prior", loc.toTag());
        ListTag rows = new ListTag(); rows.add(row);
        CompoundTag old = new CompoundTag(); old.put("location", loc.toTag()); old.put("prisoners", rows);
        JailData restored = read(old);
        assertEquals(JailData.LEGACY_GEOMETRY, restored.geometry());
        assertEquals(JailData.LEGACY_GEOMETRY, restored.assignment(id).geometry());
        assertEquals(loc, restored.assignment(id).jail());
    }

    @Test void newGeometryAndUnknownVersionsSurviveRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        Loc loc = new Loc("minecraft:the_end", 12.5, 80, 3.5, 0, 0);
        JailData data = new JailData();
        data.setLocation(loc, JailData.INTERROGATION_GEOMETRY);
        data.assign(id, new JailData.Assignment(loc, loc, 42));
        JailData restored = read(data.save(new CompoundTag(), null));
        assertEquals(JailData.INTERROGATION_GEOMETRY, restored.geometry());
        assertEquals(42, restored.assignment(id).geometry());
    }

    @Test void malformedVersionFailsClosedWithoutDiscardingAssignment() throws Exception {
        UUID id = UUID.randomUUID();
        Loc loc = new Loc("minecraft:overworld", 3.5, 70, 3.5, 0, 0);
        CompoundTag row = new CompoundTag();
        row.putUUID("player", id); row.put("jail", loc.toTag()); row.put("prior", loc.toTag());
        row.putString("geometry", "broken");
        ListTag rows = new ListTag(); rows.add(row);
        CompoundTag tag = new CompoundTag(); tag.putString("geometry", "broken"); tag.put("prisoners", rows);
        JailData data = read(tag);
        assertEquals(JailData.INVALID_GEOMETRY, data.geometry());
        assertNotNull(data.assignment(id));
        assertEquals(JailData.INVALID_GEOMETRY, data.assignment(id).geometry());
        assertEquals(loc, data.assignment(id).prior());
    }

    @Test void prisonerAreaIsEnclosedAndStaffDoorIsOutsideIt() {
        for (int z=0; z<7; z++) for (int y=0; y<7; y++) {
            assertTrue(JailPlacement.newStructure(0,y,z));
            assertTrue(JailPlacement.newStructure(6,y,z));
        }
        for (int x=0; x<=6; x++) for (int y=0; y<7; y++) {
            assertTrue(JailPlacement.newStructure(x,y,0));
            assertTrue(JailPlacement.newStructure(x,y,6));
        }
        assertFalse(JailPlacement.newStructure(12,1,3));
        assertFalse(JailPlacement.newStructure(12,2,3));
        assertTrue(JailPlacement.newStructure(12,3,3));
        assertFalse(JailPlacement.newStructure(9,1,3));
    }
}
