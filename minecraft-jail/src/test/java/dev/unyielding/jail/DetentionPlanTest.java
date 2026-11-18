package dev.unyielding.jail;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DetentionPlanTest {
    @Test void sixSeparateShellsHaveNumberedFrontsAndSafeFeetAndHeadRoom() {
        var numbers = new HashSet<DetentionPlan.Tile>();
        for (int n = 1; n <= DetentionPlan.CELLS; n++) {
            int x = DetentionPlan.centerX(n), z = DetentionPlan.centerZ(n);
            assertEquals(DetentionPlan.Tile.INLAY, DetentionPlan.tile(x, 0, z));
            for (int y = 1; y <= 2; y++) assertEquals(DetentionPlan.Tile.AIR, DetentionPlan.tile(x, y, z));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int y = 0; y <= 6; y++) {
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3 || y == 0 || y == 6)
                    assertNotEquals(DetentionPlan.Tile.AIR, DetentionPlan.tile(x+dx, y, z+dz));
            }
            int front = n % 2 == 1 ? x + 3 : x - 3;
            assertEquals(DetentionPlan.Tile.BARS_NS, DetentionPlan.tile(front, 1, z));
            var plate = DetentionPlan.tile(front, 4, z);
            assertEquals("PLATE_" + n, plate.name());
            assertTrue(numbers.add(plate));
            assertEquals(DetentionPlan.Tile.VAULT, DetentionPlan.tile(DetentionPlan.vaultX(n), 1, z));
        }
    }
    @Test void hallRunsFromNorthEntranceToOpenTreeCourt() {
        for (int z = 0; z < DetentionPlan.DEPTH - 1; z++) for (int y = 1; y <= 2; y++)
            assertEquals(DetentionPlan.Tile.AIR, DetentionPlan.tile(9, y, z));
        assertEquals(DetentionPlan.Tile.GLASS, DetentionPlan.tile(9, 9, 3));
        assertEquals(DetentionPlan.Tile.AIR, DetentionPlan.tile(9, 9, 24));
        assertEquals(DetentionPlan.Tile.CHERRY_TRUNK, DetentionPlan.tile(4, 1, 24));
        assertEquals(DetentionPlan.Tile.BLOSSOM, DetentionPlan.tile(14, 6, 24));
        assertEquals(DetentionPlan.Tile.BARS_EW, DetentionPlan.tile(9, 2, 29));
    }
    @Test void everyPaletteTileIsUsedAndEachCellHasOneCustodyStation() {
        var counts = new EnumMap<DetentionPlan.Tile, Integer>(DetentionPlan.Tile.class);
        for (int x = 0; x < DetentionPlan.WIDTH; x++) for (int y = 0; y < DetentionPlan.HEIGHT; y++)
            for (int z = 0; z < DetentionPlan.DEPTH; z++) counts.merge(DetentionPlan.tile(x,y,z), 1, Integer::sum);
        assertEquals(DetentionPlan.Tile.values().length, counts.size());
        assertEquals(6, counts.get(DetentionPlan.Tile.VAULT).intValue());
    }
    @Test void anchorLookupWorksAcrossNegativeCoordinatesAndRejectsOtherLocations() {
        Loc first = new Loc("minecraft:overworld", -22.5, 65, -93.5, 0, 0);
        var positions = new HashSet<BlockPos>();
        for (int n = 1; n <= DetentionCenter.CELL_COUNT; n++) {
            Loc actual = DetentionCenter.cell(first, n);
            assertEquals(n, DetentionCenter.number(first, actual));
            assertTrue(positions.add(DetentionCenter.vaultCandidates(first, n).getFirst()));
        }
        assertEquals(0, DetentionCenter.number(first, null));
        assertEquals(0, DetentionCenter.number(first, new Loc("minecraft:the_nether", first.x(),first.y(),first.z(),0,0)));
        assertThrows(IllegalArgumentException.class, () -> DetentionCenter.cell(first, 7));
    }
    @Test void predecessorAndNewJournalsUseTheirOriginalIndexOrderAndBounds() throws IOException {
        var tag = new CompoundTag();
        tag.putInt("format", 1);
        var old = JailTerrainJournal.layout(tag);
        assertEquals(637, old.volume);
        var min = new BlockPos(-16, 64, -20);
        assertEquals(min.offset(1,0,0), old.at(min,49));
        assertEquals(min.offset(12,6,6), old.at(min,old.volume-1));
        tag.putInt("format",2); tag.putString("layout","ROSECOURT");
        var updated = JailTerrainJournal.layout(tag);
        assertEquals(5700, updated.volume);
        assertEquals(min.offset(1,0,0), updated.at(min,300));
        assertEquals(updated.max(min), updated.at(min,updated.volume-1));
        tag.putString("layout","mystery");
        assertThrows(IOException.class, () -> JailTerrainJournal.layout(tag));
        tag.putInt("format",1);
        assertThrows(IOException.class, () -> JailTerrainJournal.layout(tag));
    }
}
