package dev.unyielding.jail;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailFoodProgressTest {
    private static final String HEALTH="minecraft:generic.max_health";
    private static final String TOUGHNESS="minecraft:generic.armor_toughness";
    private static final String FOOD="solonion:foodlist";
    private static final String BENEFIT="solonion:benefit";

    private static CompoundTag modifier(String id,double value) {
        var mod=new CompoundTag();
        mod.putString("id",id);
        mod.putString("operation","add_value");
        mod.putDouble("amount",value);
        return mod;
    }

    private static CompoundTag scores() {
        var scores=new CompoundTag();
        scores.putInt("so_cycle",2);
        scores.putInt("so_bonus",14);
        scores.putInt("so_lock",0);
        scores.putInt("so_death",0);
        return scores;
    }

    private static CompoundTag player() {
        var player=new CompoundTag();
        var attachments=new CompoundTag();
        player.put("neoforge:attachments",attachments);
        var foods=new ListTag();
        for(String name:new String[]{"minecraft:apple","minecraft:carrot"}){
            var food=new CompoundTag();
            food.putString("id",name);
            var components=new CompoundTag();
            components.putString("minecraft:custom_name","A component belonging to the historical food");
            food.put("components",components);
            foods.add(food);
        }
        attachments.put(FOOD,foods);
        var benefit=new CompoundTag();
        attachments.put(BENEFIT,benefit);
        var entries=new ListTag();
        benefit.put("att",entries);
        var attributes=new ListTag();
        player.put("attributes",attributes);
        for(String attribute:new String[]{HEALTH,TOUGHNESS}) {
            var mod=modifier("solonion:add_value",2);
            var entry=new CompoundTag();
            entry.putString("att",attribute);
            entry.putInt("op",0);
            entry.put("mod",mod.copy());
            entries.add(entry);
            var row=new CompoundTag();
            row.putString("id",attribute);
            var mods=new ListTag();
            mods.add(mod);
            if(attribute.equals(HEALTH)) mods.add(modifier("solcycle:cycle_2",8));
            row.put("modifiers",mods);
            attributes.add(row);
        }
        return player;
    }

    @Test void orderedHistoryWithComponentsAndEveryBankValueAffectTheReceipt() throws Exception {
        var saved=player();
        var original=JailFoodProgress.captured(saved,scores());
        assertEquals(8,original.getCompound("modifiers").getCompound(HEALTH).getCompound("solcycle:cycle_2").getDouble("amount"));
        var foods=saved.getCompound("neoforge:attachments").getList(FOOD,Tag.TAG_COMPOUND);
        foods.add(foods.remove(0));
        assertNotEquals(original,JailFoodProgress.captured(saved,scores()));
        foods.add(foods.remove(0));
        assertEquals(original,JailFoodProgress.captured(saved,scores()));
        foods.getCompound(0).getCompound("components").putString("minecraft:custom_name","Changed");
        assertNotEquals(original,JailFoodProgress.captured(saved,scores()));
        var changedScores=scores();
        changedScores.putInt("so_bonus",12);
        assertNotEquals(original,JailFoodProgress.captured(player(),changedScores));
    }

    @Test void mapOrderIsIrrelevantButChangedOrUnsupportedBenefitsAreRejected() throws Exception {
        var saved=player();
        var original=JailFoodProgress.captured(saved,scores());
        var entries=saved.getCompound("neoforge:attachments").getCompound(BENEFIT).getList("att",Tag.TAG_COMPOUND);
        entries.add(entries.remove(0));
        assertEquals(original,JailFoodProgress.captured(saved,scores()));
        entries.getCompound(0).getCompound("mod").putDouble("amount",4);
        assertThrows(IOException.class,()->JailFoodProgress.captured(saved,scores()));
        var malformed=player();
        malformed.getCompound("neoforge:attachments").getCompound(BENEFIT).put("unknown",StringTag.valueOf("unexpected"));
        assertThrows(IOException.class,()->JailFoodProgress.captured(malformed,scores()));
    }

    @Test void bankModifiersMustBelongToANativeRecordedCycleAndPositivePermanentAmount() {
        for(String id:new String[]{"solcycle:cycle_3","solcycle:cycle_02","solcycle:cycle_0","solcycle:cycle_+1"}) {
            var saved=player();
            var bank=saved.getList("attributes",Tag.TAG_COMPOUND).getCompound(0).getList("modifiers",Tag.TAG_COMPOUND).getCompound(1);
            bank.putString("id",id);
            assertThrows(IOException.class,()->JailFoodProgress.captured(saved,scores()));
        }
        var saved=player();
        saved.getList("attributes",Tag.TAG_COMPOUND).getCompound(0).getList("modifiers",Tag.TAG_COMPOUND)
                .getCompound(1).putDouble("amount",Double.NaN);
        assertThrows(IOException.class,()->JailFoodProgress.captured(saved,scores()));
    }
}
