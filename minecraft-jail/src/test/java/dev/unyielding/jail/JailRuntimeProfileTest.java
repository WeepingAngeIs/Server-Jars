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
