package dev.unyielding.jail;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.neoforged.fml.ModList;

/** A read-only view of lazy AttributeMap instances for the sealed 21.1.249 pack. */
final class JailAttributeView {
    private JailAttributeView() {}

    static boolean applies(ServerPlayer player) throws IOException {
        return ModList.get().isLoaded("twilightforest") && JailHumanAudit.hasPackStateContract(player);
    }

    // A supplier is the baseline for newly created instances. Never request an instance
    // from the live player's map here: that would change what Minecraft later serializes.
    static ListTag complete(ListTag explicit, boolean equipmentView) throws IOException {
        var rows=index(explicit);
        var supplier=DefaultAttributes.getSupplier(EntityType.PLAYER);
        for(var holder:BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            String id=holder.key().location().toString();
            if(rows.containsKey(id) || !supplier.hasAttribute(holder)) continue;
            var baseline=supplier.createInstance(ignored->{},holder);
            if(baseline==null || !baseline.getModifiers().isEmpty())
                throw new IOException("Cannot infer a missing player attribute with provider modifiers: "+id);
            CompoundTag expected=baseline.save();
            if(!id.equals(expected.getString("id")) || !expected.contains("base",Tag.TAG_DOUBLE)
                    || !expected.getAllKeys().equals(Set.of("id","base")))
                throw new IOException("Unknown default player attribute representation: "+id);
            addDefault(rows,expected,equipmentView);
        }
        ListTag result=new ListTag(); result.addAll(rows.values()); return result;
    }

    static SortedMap<String,CompoundTag> index(ListTag explicit) throws IOException {
        if (explicit.getElementType()!=Tag.TAG_END && explicit.getElementType()!=Tag.TAG_COMPOUND)
            throw new IOException("Stored attributes must be a compound list");
        SortedMap<String,CompoundTag> result=new TreeMap<>();
        for(Tag tag:explicit) {
            if(!(tag instanceof CompoundTag source) || !source.contains("id",Tag.TAG_STRING)
                    || !source.contains("base",Tag.TAG_DOUBLE))
                throw new IOException("Invalid stored player attribute");
            CompoundTag row=source.copy();
            if(row.contains("modifiers")) {
                if(!(row.get("modifiers") instanceof ListTag mods) ||
                        !mods.isEmpty() && mods.getElementType()!=Tag.TAG_COMPOUND)
                    throw new IOException("Malformed stored attribute modifier list");
                if(!mods.isEmpty()) {
                    SortedMap<String,CompoundTag> sorted=new TreeMap<>();
                    for(Tag value:mods) {
                        CompoundTag modifier=(CompoundTag)value;
                        if(!modifier.contains("id",Tag.TAG_STRING) || sorted.put(modifier.getString("id"),modifier)!=null)
                            throw new IOException("Missing or duplicate stored modifier ID");
                    }
                    ListTag ordered=new ListTag(); ordered.addAll(sorted.values()); row.put("modifiers",ordered);
                }
            }
            if(result.put(row.getString("id"),row)!=null) throw new IOException("Duplicate stored player attribute");
        }
        return result;
    }

    static void addDefault(Map<String,CompoundTag> rows,CompoundTag baseline,boolean equipmentView) {
        CompoundTag copy=baseline.copy();
        if(equipmentView) copy.put("modifiers",new ListTag());
        rows.putIfAbsent(copy.getString("id"),copy);
    }
}
