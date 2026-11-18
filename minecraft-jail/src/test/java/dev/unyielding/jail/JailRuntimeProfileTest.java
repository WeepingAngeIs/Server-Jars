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
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.248","21.1.255",List.of(),null));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.255","21.1.255",List.of("new=1:aaa"),"new=1:aaa"));
        assertThrows(IOException.class,()->JailRuntimeProfile.check("21.1.249","21.1.255",List.of("new=1:aaa"),"new=1:aaa"));
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
