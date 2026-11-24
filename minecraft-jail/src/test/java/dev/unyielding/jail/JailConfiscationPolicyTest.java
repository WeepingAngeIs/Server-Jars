package dev.unyielding.jail;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailConfiscationPolicyTest {
    private final Loc jail=new Loc("minecraft:overworld",8.5,70,8.5,0,0);
    private final Loc prior=new Loc("minecraft:overworld",40.5,70,40.5,0,0);

    @Test void onAndOffAdmissionsKeepTheirOwnPolicyAcrossRepeatedSaveReload(){
        var data=new JailData();UUID on=UUID.randomUUID(),off=UUID.randomUUID();
        data.assign(on,new JailData.Assignment(jail,prior,DetentionCenter.GEOMETRY,true));
        data.assign(off,new JailData.Assignment(jail,prior,DetentionCenter.GEOMETRY,false));
        data.markRetainedCustodyInitialized();
        var loaded=JailData.load(data.save(new CompoundTag(),null),null);
        assertTrue(loaded.assignment(on).confiscateInventory());
        assertFalse(loaded.assignment(off).confiscateInventory());
        assertTrue(loaded.retainedCustodyInitialized());
        var again=JailData.load(loaded.save(new CompoundTag(),null),null);
        assertEquals(data.assignments(),again.assignments());
    }
    @Test void legacyPolicyIsOnAndMalformedPolicyCannotCreateAValidOffAssignment(){
        for(String malformed:new String[]{"absent","string","negative","two"}){
            var data=new CompoundTag();var rows=new ListTag();var row=new CompoundTag();UUID player=UUID.randomUUID();
            row.putUUID("player",player);row.put("jail",jail.toTag());row.put("prior",prior.toTag());row.putInt("geometry",DetentionCenter.GEOMETRY);
            switch(malformed){case "string"->row.putString("confiscate_inventory","false");case "negative"->row.putByte("confiscate_inventory",(byte)-1);case "two"->row.putByte("confiscate_inventory",(byte)2);}
            rows.add(row);data.put("prisoners",rows);var assignment=JailData.load(data,null).assignment(player);
            assertTrue(assignment.confiscateInventory());
            assertEquals(malformed.equals("absent")?DetentionCenter.GEOMETRY:JailData.INVALID_GEOMETRY,assignment.geometry());
        }
    }
    @Test void completedNativeReceiptIsBoundToExactHistoryRatherThanLatestSession(){
        UUID on=UUID.randomUUID(),off=UUID.randomUUID();var intent=new CompoundTag();
        byte[] digest=new byte[32];digest[0]=17;intent.putByteArray("custody_digest",digest);
        for(String key:new String[]{"layer","original","human","definitions"})intent.putString(key,"bound:"+key);
        var receipt=new CompoundTag();receipt.putUUID("transaction",off);receipt.putString("phase","ORIGINAL");
        receipt.putByteArray("custody_digest",digest);
        for(String key:new String[]{"layer","original","human","definitions"})receipt.putString(key,intent.getString(key));
        assertTrue(JailIntegrated.completedNativeReceipt(receipt,intent,off));
        assertFalse(JailIntegrated.completedNativeReceipt(receipt,intent,on));
        receipt.putString("phase","TEMPORARY");assertFalse(JailIntegrated.completedNativeReceipt(receipt,intent,off));
        receipt.putString("phase","ORIGINAL");receipt.putString("unreviewed","extra");assertFalse(JailIntegrated.completedNativeReceipt(receipt,intent,off));
        assertThrows(java.io.IOException.class,()->JailIntegrated.requireCompleted(new JailEscrowJournal.Entry(off,on,JailEscrowJournal.State.HELD,new byte[]{1}),off,on));
        assertThrows(java.io.IOException.class,()->JailIntegrated.requireCompleted(new JailEscrowJournal.Entry(off,on,JailEscrowJournal.State.COMPLETE,new byte[]{1}),off,UUID.randomUUID()));
    }
}
