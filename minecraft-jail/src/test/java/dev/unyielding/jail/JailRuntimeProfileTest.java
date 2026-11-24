package dev.unyielding.jail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailRuntimeProfileTest {
    @Test void fullRuntimeIdentityIncludesEveryLoadedFileIdAndVersion() {
        var first="some_mod=1.1:"+"a".repeat(128);
        var second="part_of_jar=7:"+"b".repeat(128);
        String pin=JailRuntimeProfile.seal(List.of(first,second));
        assertTrue(JailRuntimeProfile.matches(JailRuntimeProfile.seal(List.of(second,first)),pin+"\n"));
        assertNotEquals(pin,JailRuntimeProfile.seal(List.of(first)));
        assertNotEquals(pin,JailRuntimeProfile.seal(List.of(first,second.replace("=7:","=8:"))));
        assertNotEquals(pin,JailRuntimeProfile.seal(List.of(first,second.replace("b".repeat(128),"c".repeat(128)))));
        assertFalse(JailRuntimeProfile.matches(pin,"*"));
        assertFalse(JailRuntimeProfile.matches(pin,""));
    }

    @Test void unknownKernelAndUnapprovedLoadedFilesRefuse() {
        assertDoesNotThrow(()->JailRuntimeProfile.check("21.1.255","21.1.255",List.of(),null));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.249","21.1.255",List.of(),null));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.248","21.1.255",List.of(),null));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.255","21.1.255",List.of("new=1:aaa"),"new=1:aaa"));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.249","21.1.255",List.of("new=1:aaa"),"new=1:aaa"));
    }

    @Test void exactSpawnSwapRosterIsAcceptedAndAnyChangeRemainsUnapproved() throws Exception {
        java.util.List<String> roster;
        try(var stream=JailRuntimeProfileTest.class.getResourceAsStream("/spawn249-roster.txt")) {
            assertNotNull(stream);
            roster=new java.io.BufferedReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).lines().toList();
        }
        assertEquals(380,roster.size());
        assertTrue(JailRuntimeProfile.spawnWithStorage("21.1.249",roster));
        assertTrue(JailRuntimeProfile.check("21.1.249","21.1.255",roster,"exact-pinned-companion"));
        assertFalse(JailRuntimeProfile.spawnWithStorage("21.1.255",roster));
        var missing=new java.util.ArrayList<>(roster);missing.removeFirst();
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.249","21.1.255",missing,"changed"));
        var added=new java.util.ArrayList<>(roster);added.add("unapproved=1:"+"0".repeat(128));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.249","21.1.255",added,"changed"));
        var changed=new java.util.ArrayList<>(roster);changed.set(0,changed.getFirst()+"0");
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.249","21.1.255",changed,"changed"));
    }

    @Test void teleportRequestsCanOnlyBeAcceptedOutsideCustodyByTheIntendedReceiver() {
        var player=java.util.UUID.randomUUID();var other=java.util.UUID.randomUUID();var third=java.util.UUID.randomUUID();
        assertTrue(JailUnyieldingSwapState.blocksRequest(player,other,player,false));
        assertTrue(JailUnyieldingSwapState.blocksRequest(player,other,player,true));
        assertTrue(JailUnyieldingSwapState.blocksRequest(player,player,other,false));
        assertFalse(JailUnyieldingSwapState.blocksRequest(player,player,other,true));
        assertFalse(JailUnyieldingSwapState.blocksRequest(player,other,third,false));
    }

    @Test void missingNativeLedgerCannotReplaceExistingUniqueOrDirtyHistory() throws Exception {
        CompoundTag empty=new CompoundTag(),owners=new CompoundTag();
        owners.putUUID("neoorigins:human",java.util.UUID.randomUUID());
        JailNativeOriginPort.requireUnusedClaimLedger(List.of(),false,empty);
        assertThrows(IOException.class,()->JailNativeOriginPort.requireUnusedClaimLedger(List.of("neoorigins:origin"),false,empty));
        assertThrows(IOException.class,()->JailNativeOriginPort.requireUnusedClaimLedger(List.of(),true,empty));
        assertThrows(IOException.class,()->JailNativeOriginPort.requireUnusedClaimLedger(List.of(),false,owners));
        CompoundTag receipt=new CompoundTag(); // Historic intents always had a materialized ledger.
        JailNativeOriginPort.requireObservedClaimFile(receipt,true);
        assertThrows(IOException.class,()->JailNativeOriginPort.requireObservedClaimFile(receipt,false));
        CompoundTag marker=new CompoundTag(); marker.putBoolean("unmaterialized",true);receipt.put("claims_before",marker);
        JailNativeOriginPort.requireObservedClaimFile(receipt,false);
        assertThrows(IOException.class,()->JailNativeOriginPort.requireObservedClaimFile(receipt,true));
    }

    @Test void unknownOrNonNeutralAttachmentsRefuse() {
        JsonObject reviewed=new JsonObject();
        JsonArray values=new JsonArray();
        values.add("0");
        reviewed.add("example:timer",values);
        assertTrue(JailPackState.neutral(reviewed,"example:timer",IntTag.valueOf(0)));
        assertFalse(JailPackState.neutral(reviewed,"example:timer",IntTag.valueOf(1)));
        assertFalse(JailPackState.neutral(reviewed,"example:timer",StringTag.valueOf("0")));
        assertFalse(JailPackState.neutral(reviewed,"example:backpack",new CompoundTag()));
        assertFalse(JailPackState.neutral(null,"example:timer",IntTag.valueOf(0)));
    }

    @Test void activeCarriedContentCannotMatchTransientFields() {
        JsonObject reviewed=new JsonObject(); JsonArray values=new JsonArray(); values.add("{keyPressed:0b}");
        reviewed.add("carryon:carry_on_data",values);
        CompoundTag carry=new CompoundTag(); carry.putBoolean("keyPressed",false);
        carry.putInt("tick",123);carry.putInt("selected",5);
        assertTrue(JailPackState.neutral(reviewed,"carryon:carry_on_data",carry));
        carry.put("tile",new CompoundTag());
        assertFalse(JailPackState.neutral(reviewed,"carryon:carry_on_data",carry));
        carry.remove("tile");carry.putBoolean("keyPressed",true);
        assertFalse(JailPackState.neutral(reviewed,"carryon:carry_on_data",carry));
    }

    @Test void aetherLifeShardPermissionIsExactlyZeroOnMaxHealth() {
        assertTrue(JailPackState.reviewedIntrinsic("minecraft:generic.max_health","aether:life_shard_max_health",0d,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic("minecraft:generic.max_health","aether:life_shard_max_health",2d,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic("minecraft:generic.armor","aether:life_shard_max_health",0d,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic("minecraft:generic.max_health","aether:life_shard_max_health",0d,"ADD_MULTIPLIED_BASE"));
    }

    @Test void onlyExactStarterArsModifiersMatchTheDeclaredTuples() {
        String regen="ars_nouveau:ars_nouveau.perk.mana_regen", max="ars_nouveau:ars_nouveau.perk.max_mana";
        String regenId="ars_nouveau:max_regen_mod",maxId="ars_nouveau:max_mana_mod";
        assertTrue(JailPackState.reviewedIntrinsic(regen,regenId,5,"ADD_VALUE"));
        assertTrue(JailPackState.reviewedIntrinsic(max,maxId,100,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic(max,regenId,5,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic(regen,maxId,100,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic(regen,regenId,5.00001,"ADD_VALUE"));
        assertFalse(JailPackState.reviewedIntrinsic(regen,regenId,5,"ADD_MULTIPLIED_BASE"));
        assertFalse(JailPackState.reviewedIntrinsic(max,"ars_nouveau:equipment",100,"ADD_VALUE"));
    }

    @Test void manaRegenCannotConcealProgressEquipmentOrOutOfRangeCharge() throws Exception {
        String key="ars_nouveau:mana_cap";
        var schema=net.minecraft.nbt.TagParser.parseTag("{book_tier:0,glyph:0,max:100,reserved:0.0f}");
        JsonObject profile=new JsonObject(); JsonArray defaults=new JsonArray();
        defaults.add(schema.toString()); profile.add(key,defaults);
        for (double valid:List.of(0d,25d,26.25d,100d)) {
            schema.putDouble("current",valid); assertTrue(JailPackState.neutral(profile,key,schema));
        }
        for (double invalid:List.of(-1d,100.01d,Double.NaN,Double.POSITIVE_INFINITY)) {
            schema.putDouble("current",invalid); assertFalse(JailPackState.neutral(profile,key,schema));
        }
        schema.putInt("current",25); assertFalse(JailPackState.neutral(profile,key,schema));
        schema.putDouble("current",25d); schema.putFloat("reserved",1f);
        assertFalse(JailPackState.neutral(profile,key,schema));
        schema.putFloat("reserved",0f); schema.putInt("max",200);
        assertFalse(JailPackState.neutral(profile,key,schema));
        schema.putInt("max",100); schema.putInt("book_tier",1);
        assertFalse(JailPackState.neutral(profile,key,schema));
        schema.putInt("book_tier",0); schema.put("inventory",new CompoundTag());
        assertFalse(JailPackState.neutral(profile,key,schema));
    }

    @Test void onlyInsaneLibsRecomputedLightCacheIsNeutral() throws Exception {
        var reviewed=new JsonObject(); var values=new JsonArray(); values.add("{}"); reviewed.add("insanelib",values);
        for(String allowed:List.of("{}", "{nbt_tags:{}}", "{nbt_tags:{sky_light:0,block_light:15}}"))
            assertTrue(JailPackState.neutral(reviewed,"insanelib",net.minecraft.nbt.TagParser.parseTag(allowed)));
        for(String active:List.of("{nbt_tags:{sky_light:-1,block_light:0}}","{nbt_tags:{sky_light:0,block_light:16}}",
                "{nbt_tags:{sky_light:0b,block_light:1}}","{nbt_tags:{sky_light:1}}","{nbt_tags:{xp_multiplier:1}}",
                "{in_combat:{last_timestamp:1L},nbt_tags:{sky_light:1,block_light:2}}"))
            assertFalse(JailPackState.neutral(reviewed,"insanelib",net.minecraft.nbt.TagParser.parseTag(active)));
    }

    @Test void activeOrUnknownNonpersistedProgressRefusesBeforeCustody() throws Exception {
        var data=new CompoundTag(); data.put("PlayerPersisted",new CompoundTag());
        JailPackState.verifyPersistent(data,null);
        data.putInt("test:bank_balance",100);
        assertThrows(IOException.class,()->JailPackState.verifyPersistent(data,null));
    }

    @Test void twilightDeathRecoveryBlocksCustodyEvenWithAnEmptyLiveInventory() {
        var stored=new CompoundTag();
        assertDoesNotThrow(()->JailPackState.verifyTwilightState(stored));
        for (var key:List.of("TFCharmInventory","CharmStack","CasketDamage")) {
            stored.put(key,new CompoundTag());
            assertThrows(IOException.class,()->JailPackState.verifyTwilightState(stored));
            stored.remove(key);
        }
    }

    @Test void nativeReforgeCloneMustRetainEveryIntAndOnlyMaterializeAnAbsentSourceAsZero() throws Exception {
        String key="apoth_reforge_seed";
        var source=new CompoundTag();
        var replacement=new CompoundTag();
        assertThrows(IOException.class,()->JailPackState.reforgeDigest(replacement,true));
        replacement.putInt(key,0);
        assertArrayEquals(JailPackState.reforgeDigest(source,false),JailPackState.reforgeDigest(replacement,true));
        assertFalse(source.contains(key));
        for(int seed:List.of(Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE)) {
            source.putInt(key,seed);
            replacement.putInt(key,seed);
            JailPackState.verifyPersistent(source,null);
            assertArrayEquals(JailPackState.reforgeDigest(source,false),JailPackState.reforgeDigest(replacement,true));
            replacement.putInt(key,~seed);
            assertFalse(java.util.Arrays.equals(JailPackState.reforgeDigest(source,false),JailPackState.reforgeDigest(replacement,true)));
        }
        source.putLong(key,1L);
        assertThrows(IOException.class,()->JailPackState.verifyPersistent(source,null));
        assertThrows(IOException.class,()->JailPackState.reforgeDigest(source,false));
        assertThrows(IOException.class,()->JailPackState.reforgeDigest(source,true));
    }

    @Test void nativeCurrencyProgressAndWalletVisibilityMustSurviveTheClone() throws Exception {
        CompoundTag attachments=new CompoundTag();
        byte[] fresh=JailPackState.currencyCloneDigest(attachments);
        CompoundTag wallet=new CompoundTag();
        wallet.put("Wallet",new CompoundTag()); wallet.putBoolean("Visible",true);
        CompoundTag eventProgress=new CompoundTag(); eventProgress.putString("Unlocked","");
        attachments.put("lightmanscurrency:event_unlocks",eventProgress);
        attachments.put("lightmanscurrency:wallet",wallet);
        assertArrayEquals(fresh,JailPackState.currencyCloneDigest(attachments));
        eventProgress.putString("Unlocked","summer;winter");
        assertFalse(java.util.Arrays.equals(fresh,JailPackState.currencyCloneDigest(attachments)));
        eventProgress.putString("Unlocked",""); wallet.putBoolean("Visible",false);
        assertFalse(java.util.Arrays.equals(fresh,JailPackState.currencyCloneDigest(attachments)));
        eventProgress.putInt("Unlocked",7);
        assertThrows(IOException.class,()->JailPackState.currencyCloneDigest(attachments));
    }

    @Test void occupiedOrMalformedWalletAndUnknownOriginStateRefuse() {
        CompoundTag row=new CompoundTag(),wallet=new CompoundTag(),slot=new CompoundTag();
        wallet.put("Wallet",slot);wallet.putBoolean("Visible",true);row.put("lightmanscurrency:wallet",wallet);
        assertDoesNotThrow(()->JailPackState.verifyWalletAndResources(row));
        slot.putString("id","lightmanscurrency:wallet_leather");
        assertThrows(IOException.class,()->JailPackState.verifyWalletAndResources(row));
        slot.remove("id");wallet.putString("Visible","true");
        assertThrows(IOException.class,()->JailPackState.verifyWalletAndResources(row));
        row.remove("lightmanscurrency:wallet");row.putInt("neoorigins:resource_state",0);
        assertThrows(IOException.class,()->JailPackState.verifyWalletAndResources(row));
    }
}
