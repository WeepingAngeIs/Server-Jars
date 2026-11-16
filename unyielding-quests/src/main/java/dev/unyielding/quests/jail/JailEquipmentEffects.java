package dev.unyielding.quests.jail;

import dev.unyielding.quests.mixin.JailEquipmentAccess;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Declared vanilla equipment deltas. All other observed state must stay unchanged. */
final class JailEquipmentEffects {
    private JailEquipmentEffects(){}
    private record Key(ResourceLocation attribute,ResourceLocation modifier){}
    static void supported(ServerPlayer player)throws IOException {
        if(!player.server.isSameThread()||!player.isAlive()||player.hasDisconnected()||player.isUsingItem()||player.isPassenger()||player.isVehicle()||!player.getShoulderEntityLeft().isEmpty()||!player.getShoulderEntityRight().isEmpty())throw new IOException("Unsupported/offline equipment owner");
        JailHumanAudit.runtime(player);
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            ItemStack stack=player.getInventory().getItem(i);if(stack.isEmpty())continue;
            if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")||!stack.getItem().getClass().getName().startsWith("net.minecraft.world.item.")||stack.getCount()>stack.getMaxStackSize())throw new IOException("Unknown vanilla equipment/item provider");
            if(EnchantmentHelper.has(stack,EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))throw new IOException("Bound equipment is not admitted");
        }
        settled(player);neutral(player);
    }
    static void settled(ServerPlayer p)throws IOException {
        var access=(JailEquipmentAccess)p;
        for(EquipmentSlot slot:EquipmentSlot.values()){
            ItemStack before=switch(slot.getType()) {case HAND->access.uq$lastHands().get(slot.getIndex());case HUMANOID_ARMOR->access.uq$lastArmor().get(slot.getIndex());case ANIMAL_ARMOR->access.uq$lastBody();};
            if(!ItemStack.matches(before,p.getItemBySlot(slot)))throw new IOException("Pending vanilla equipment reconciliation");
        }
    }
    static void reconcile(ServerPlayer p)throws IOException {((JailEquipmentAccess)p).uq$detectEquipmentUpdates();settled(p);}
    static byte[] neutral(ServerPlayer p)throws IOException {
        Map<Key,AttributeModifier> equipment=new HashMap<>();List<String> rejected=new ArrayList<>();
        Set<String> allowed=Set.of("armor","armor_toughness","knockback_resistance","attack_damage","attack_speed","movement_speed");
        for(EquipmentSlot slot:EquipmentSlot.values())p.getItemBySlot(slot).forEachModifier(slot,(attribute,modifier)->{
            var id=attribute.unwrapKey().orElseThrow().location();String path=id.getPath().replace("generic.","");
            if(!id.getNamespace().equals("minecraft")||!allowed.contains(path)||!modifier.id().getNamespace().equals("minecraft")||!Double.isFinite(modifier.amount())||Math.abs(modifier.amount())>100||equipment.put(new Key(id,modifier.id()),modifier)!=null)rejected.add(id.toString());
        });
        if(!rejected.isEmpty())throw new IOException("Unsupported/colliding equipment attribute effects: "+rejected);
        CompoundTag all=new CompoundTag();ListTag attributes=new ListTag();Set<Key> seen=new HashSet<>();
        List<CompoundTag> sorted=new ArrayList<>();
        for(Tag raw:p.getAttributes().save()){
            CompoundTag saved=(CompoundTag)raw;ResourceLocation id=ResourceLocation.parse(saved.getString("id"));var holder=BuiltInRegistries.ATTRIBUTE.getHolder(id).orElseThrow(()->new IOException("Unknown attribute"));var live=p.getAttribute(holder);if(live==null)throw new IOException("Missing live attribute");
            CompoundTag row=new CompoundTag();row.putString("id",id.toString());row.putDouble("base",live.getBaseValue());ListTag mods=new ListTag();
            for(AttributeModifier modifier:live.getModifiers().stream().sorted(Comparator.comparing(m->m.id().toString())).toList()){
                Key key=new Key(id,modifier.id());var expected=equipment.get(key);
                if(expected!=null){if(!expected.equals(modifier)||!seen.add(key))throw new IOException("Equipment modifier is not the declared value");continue;}
                CompoundTag m=new CompoundTag();m.putString("id",modifier.id().toString());m.putDouble("amount",modifier.amount());m.putString("operation",modifier.operation().name());mods.add(m);
            }
            row.put("modifiers",mods);sorted.add(row);
        }
        if(!seen.equals(equipment.keySet()))throw new IOException("Declared equipment effects are not settled");sorted.sort(Comparator.comparing(t->t.getString("id")));attributes.addAll(sorted);all.put("attributes",attributes);
        CompoundTag saved=p.saveWithoutId(new CompoundTag());all.putFloat("health",p.getHealth());all.put("effects",saved.getList("active_effects",10).copy());all.put("abilities",saved.getCompound("abilities").copy());
        CompoundTag persistent=p.getPersistentData().copy();persistent.remove(JailVanillaPort.REMOVED);persistent.remove(JailVanillaPort.RESTORED);all.put("persistent",persistent);
        return JailNbt.encode(all);
    }
}
