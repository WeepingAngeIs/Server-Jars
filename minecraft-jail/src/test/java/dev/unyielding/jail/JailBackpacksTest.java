package dev.unyielding.jail;

import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailBackpacksTest {
    private static final UUID BAG=UUID.fromString("f22ffa37-f32d-4734-b56d-02c353e1d41e");

    private static CompoundTag inventory(int count) {
        CompoundTag diamond=new CompoundTag();diamond.putString("id","minecraft:diamond");
        diamond.putInt("count",count);diamond.putInt("Slot",5);
        ListTag items=new ListTag();items.add(diamond);
        CompoundTag inventory=new CompoundTag();inventory.put("Items",items);
        CompoundTag contents=new CompoundTag();contents.put("inventory",inventory);
        contents.putString("display","name and settings preserved");
        return contents;
    }
    private static CompoundTag row(CompoundTag contents) {
        CompoundTag row=new CompoundTag();row.putUUID("uuid",BAG);row.put("contents",contents);return row;
    }
    private static CompoundTag file(CompoundTag... rows) {
        ListTag list=new ListTag();list.addAll(Arrays.asList(rows));
        CompoundTag data=new CompoundTag();data.put("backpackContents",list);return data;
    }
    @Test void externalIdentityAndEveryBackingFieldSurviveTheJournalRoundTrip() throws Exception {
        CompoundTag input=file(row(inventory(13))),unchanged=input.copy();
        CompoundTag records=JailBackpacks.fromDisk(input,Set.of(BAG));
        CompoundTag snapshot=JailNbt.decode(JailNbt.encode(records));
        assertEquals(Set.of(BAG),JailBackpacks.ids(JailBackpacks.digests(snapshot)));
        assertEquals(inventory(13),snapshot.getCompound(BAG.toString()));
        assertEquals(unchanged,input);
        var original=JailBackpacks.digests(records);
        var amountChanged=JailBackpacks.fromDisk(file(row(inventory(12))),Set.of(BAG));
        assertNotEquals(original,JailBackpacks.digests(amountChanged));
        snapshot.getCompound(BAG.toString()).putString("display","different");
        assertNotEquals(original,JailBackpacks.digests(snapshot));
    }
    @Test void missingDuplicatedAndMalformedBackingsAreRefused() {
        assertThrows(IOException.class,()->JailBackpacks.fromDisk(file(),Set.of(BAG)));
        assertThrows(IOException.class,()->JailBackpacks.fromDisk(file(row(inventory(1)),row(inventory(1))),Set.of(BAG)));
        assertThrows(IOException.class,()->JailBackpacks.fromDisk(file(new CompoundTag()),Set.of(BAG)));
        var invalid=new CompoundTag();invalid.putByteArray(BAG.toString(),new byte[31]);
        assertThrows(IOException.class,()->JailBackpacks.ids(invalid));
    }
    @Test void nestedAndLinkedStorageNeverBorrowAnUnboundBacking() throws Exception {
        var record=inventory(4);JailBackpacks.selfContained(record);
        var stack=record.getCompound("inventory").getList("Items",10).getCompound(0);
        stack.putString("id","sophisticatedbackpacks:diamond_backpack");
        assertThrows(IOException.class,()->JailBackpacks.selfContained(record));
        stack.putString("id","minecraft:stone");
        var data=new CompoundTag();data.putUUID(JailBackpacks.COMPONENT,BAG);stack.put("components",data);
        assertThrows(IOException.class,()->JailBackpacks.selfContained(record));
        data.remove(JailBackpacks.COMPONENT);data.putBoolean("sophisticatedcore:linked_storage_primary_endpoint",true);
        assertThrows(IOException.class,()->JailBackpacks.selfContained(record));
    }
    @Test void nativeLoadFilterRequiresAPlayerLogOrMaterialBacking() {
        var empty=new CompoundTag();empty.put("inventory",new CompoundTag());
        assertFalse(JailBackpacks.persistsOnLoad(empty,false));
        assertTrue(JailBackpacks.persistsOnLoad(empty,true));
        assertTrue(JailBackpacks.persistsOnLoad(inventory(4),false));
        empty.put("settings",new CompoundTag());assertTrue(JailBackpacks.persistsOnLoad(empty,false));
    }
    @Test void duplicatePhysicalHandleInsideAContainerIsDetected() {
        var contents=inventory(1);
        var bag=contents.getCompound("inventory").getList("Items",Tag.TAG_COMPOUND).getCompound(0);
        bag.putString("id","sophisticatedbackpacks:iron_backpack");
        CompoundTag components=new CompoundTag();bag.put("components",components);
        components.putUUID(JailBackpacks.COMPONENT,BAG);
        assertTrue(JailBackpacks.nestedHandle(contents,Set.of(BAG)));
        assertFalse(JailBackpacks.nestedHandle(contents,Set.of(UUID.randomUUID())));
        components.remove(JailBackpacks.COMPONENT);
        var legacy=new CompoundTag();legacy.putUUID("contentsUuid",BAG);components.put("minecraft:custom_data",legacy);
        assertTrue(JailBackpacks.nestedHandle(contents,Set.of(BAG)));
    }
}
