package dev.unyielding.jail;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailPolymorphTest {
    @Test void onlyTheExactEmptyCompoundIsAReviewedSerializedState() {
        assertTrue(JailPolymorph.neutralTag(new CompoundTag()));
        assertFalse(JailPolymorph.neutralTag(StringTag.valueOf("")));
        var selected = new CompoundTag();
        selected.putString("SelectedRecipe", "minecraft:stick");
        assertFalse(JailPolymorph.neutralTag(selected));
        var unknown = new CompoundTag();
        unknown.putBoolean("future", false);
        assertFalse(JailPolymorph.neutralTag(unknown));
    }

    @Test void unloadedSelectionAndEachLiveActivityRefuseNeutrality() {
        var empty = List.of();
        var freshCache = new Object[10];
        assertTrue(JailPolymorph.neutralLive(null,null,null,null,empty,empty,freshCache));
        Object active = new Object();
        assertFalse(JailPolymorph.neutralLive(active,null,null,null,empty,empty,freshCache));
        assertFalse(JailPolymorph.neutralLive(null,active,null,null,empty,empty,freshCache));
        // A recipe read from disk can be invisible in the provider's serialized output.
        assertFalse(JailPolymorph.neutralLive(null,null,active,null,empty,empty,freshCache));
        assertFalse(JailPolymorph.neutralLive(null,null,null,active,empty,empty,freshCache));
        assertFalse(JailPolymorph.neutralLive(null,null,null,null,List.of(active),empty,freshCache));
        assertFalse(JailPolymorph.neutralLive(null,null,null,null,empty,List.of(active),freshCache));
        var populated = new Object[10];
        populated[3] = active;
        assertFalse(JailPolymorph.neutralLive(null,null,null,null,empty,empty,populated));
    }
}
