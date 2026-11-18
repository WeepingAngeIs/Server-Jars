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
}
