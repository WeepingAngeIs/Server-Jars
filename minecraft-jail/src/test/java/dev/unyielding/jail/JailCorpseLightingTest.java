package dev.unyielding.jail;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailCorpseLightingTest {
    private static CompoundTag state(int sky,int block) throws Exception {
        CompoundTag result=TagParser.parseTag("{inventory:[B;1B,7B],receipt:{phase:'TEMPORARY'},claims:{layer:'held'},attributes:[{base:2.0d}],other_persistent:{insanelib:{nbt_tags:{}},PlayerPersisted:{progress:23}}}");
        CompoundTag cache=result.getCompound("other_persistent").getCompound("insanelib").getCompound("nbt_tags");
        cache.putInt("sky_light",sky);cache.putInt("block_light",block);return result;
    }
    private static boolean allowed(CompoundTag actual,CompoundTag saved) throws Exception {
        return JailPackState.onlyCorpseLightingDiffers(JailNativeOriginPort.encode(actual),JailNativeOriginPort.encode(saved));
    }
    private static boolean allowedOwnKill(CompoundTag actual,CompoundTag saved,Long tick) throws Exception {
        return JailPackState.onlyCorpseLightingDiffers(JailNativeOriginPort.encode(actual),JailNativeOriginPort.encode(saved),tick);
    }

    @Test void onlyTwoValidLightReadingsMayDifferInDetachedProjections() throws Exception {
        CompoundTag actual=state(0,15),saved=state(15,0);
        var actualCopy=actual.copy();var savedCopy=saved.copy();
        assertTrue(allowed(actual,saved));assertTrue(allowed(saved,actual));
        assertEquals(actualCopy,actual);assertEquals(savedCopy,saved);
        assertFalse(allowed(actual,actual));
        for(String field:java.util.List.of("inventory","receipt","claims","attributes")) {
            var altered=actual.copy();altered.putString(field,"changed");
            assertFalse(allowed(altered,saved));
        }
        actual.getCompound("other_persistent").getCompound("PlayerPersisted").putInt("progress",24);
        assertFalse(allowed(actual,saved));
    }

    @Test void malformedOrExtendedCacheNeverGrantsAnEquivalence() throws Exception {
        var original=state(0,15);var saved=state(15,0);
        for(String malformed:java.util.List.of("{sky_light:4b,block_light:0}","{sky_light:0,block_light:16}",
                "{sky_light:-1,block_light:0}","{sky_light:1}","{}","{sky_light:1,block_light:0,extra:1}")) {
            var changed=original.copy();
            changed.getCompound("other_persistent").getCompound("insanelib").put("nbt_tags",TagParser.parseTag(malformed));
            assertFalse(allowed(changed,saved));assertFalse(allowed(saved,changed));
        }
        for(var pair:java.util.List.of(original,saved))
            pair.getCompound("other_persistent").getCompound("insanelib").putInt("another_owner_field",3);
        assertFalse(allowed(original,saved));
        var malformed=state(0,15);malformed.getCompound("other_persistent").putString("PlayerPersisted","bad");
        assertFalse(allowed(malformed,state(15,0)));
        malformed=state(0,15);malformed.putInt("other_persistent",1);assertFalse(allowed(malformed,state(15,0)));
    }

    @Test void onlyBoundOwnDamageTimestampCanAccompanyChangedLighting() throws Exception {
        var actual=state(0,15);var saved=state(15,0);
        for(var pair:java.util.List.of(actual,saved))
            pair.getCompound("other_persistent").getCompound("insanelib").put("in_combat",TagParser.parseTag("{last_timestamp:42L}"));
        var beforeActual=actual.copy();var beforeSaved=saved.copy();
        assertFalse(allowed(actual,saved));
        assertFalse(allowedOwnKill(actual,saved,null));
        assertFalse(allowedOwnKill(actual,saved,41L));
        assertTrue(allowedOwnKill(actual,saved,42L));
        assertEquals(beforeActual,actual);assertEquals(beforeSaved,saved);
        var changed=actual.copy();
        changed.getCompound("other_persistent").getCompound("insanelib").getCompound("in_combat").putLong("last_timestamp",43L);
        assertFalse(allowedOwnKill(changed,saved,42L));
        changed=actual.copy();
        changed.getCompound("other_persistent").getCompound("insanelib").getCompound("in_combat").putInt("last_timestamp",42);
        assertFalse(allowedOwnKill(changed,saved,42L));
        changed=actual.copy();
        changed.getCompound("other_persistent").getCompound("insanelib").getCompound("in_combat").putInt("other",1);
        assertFalse(allowedOwnKill(changed,saved,42L));
        changed=actual.copy();
        changed.getCompound("other_persistent").getCompound("insanelib").putInt("another_owner_field",1);
        assertFalse(allowedOwnKill(changed,saved,42L));
        changed=actual.copy();changed.getCompound("other_persistent").getCompound("PlayerPersisted").putInt("progress",24);
        assertFalse(allowedOwnKill(changed,saved,42L));
        changed=actual.copy();changed.putString("inventory","changed");
        assertFalse(allowedOwnKill(changed,saved,42L));
    }
}
