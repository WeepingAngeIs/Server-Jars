package dev.unyielding.jail;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailDataCellsTest {
    private final Loc first = new Loc("minecraft:overworld",-92.5,65,83.5,0,0);
    private final Loc prior = new Loc("minecraft:the_nether",8.75,71,16.5,64,5);

    @Test void savedPrisonersRetainIndividualCellsAndReturnPositionsAfterReload() {
        var original = new JailData();
        original.setLocation(first,DetentionCenter.GEOMETRY);
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        var second=DetentionCenter.cell(first,2);
        var sixth=DetentionCenter.cell(first,6);
        original.assign(a,new JailData.Assignment(second,prior,DetentionCenter.GEOMETRY));
        original.assign(b,new JailData.Assignment(sixth,first,DetentionCenter.GEOMETRY));
        var loaded=JailData.load(original.save(new CompoundTag(),null),null);
        assertEquals(original.assignments(),loaded.assignments());
        assertEquals(2,DetentionCenter.number(loaded.location(),loaded.assignment(a).jail()));
        assertEquals(6,DetentionCenter.number(loaded.location(),loaded.assignment(b).jail()));
    }

    @Test void oldUnversionedAndVersionThreeAssignmentsKeepTheirOriginalDestination() {
        var tag=new CompoundTag();
        var rows=new ListTag();
        UUID unversioned=UUID.randomUUID(),chair=UUID.randomUUID();
        for(UUID id:new UUID[]{unversioned,chair}){
            var row=new CompoundTag();
            row.putUUID("player",id);
            row.put("prior",prior.toTag());
            row.put("jail",first.toTag());
            if(id.equals(chair))row.putInt("geometry",3);
            rows.add(row);
        }
        tag.put("location",first.toTag());
        tag.put("prisoners",rows);
        var data=JailData.load(tag,null);
        assertEquals(new JailData.Assignment(first,prior,1),data.assignment(unversioned));
        assertEquals(new JailData.Assignment(first,prior,3),data.assignment(chair));
        assertEquals(data.assignments(),JailData.load(data.save(new CompoundTag(),null),null).assignments());
    }
}
