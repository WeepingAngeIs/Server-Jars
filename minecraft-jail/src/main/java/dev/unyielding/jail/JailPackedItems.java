package dev.unyielding.jail;

import java.io.IOException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Only an audited inert provider in a non-equipped inventory slot, never a broad mod-item bypass. */
final class JailPackedItems {
    private JailPackedItems(){}
    static boolean coin(ServerPlayer p,int slot,ItemStack stack)throws IOException {
        if(slot<0||slot>=36||slot==p.getInventory().selected)return false;
        // Pinned 2.3.0.5 ModItems: coin_gold -> lambda$static$2 -> CoinItem.
        // Copper/iron use plain Item factories; do not infer their compatibility.
        if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("lightmanscurrency:coin_gold")||!stack.getItem().getClass().getName().equals("io.github.lightman314.lightmanscurrency.common.items.CoinItem"))return false;
        for(var component:stack.getComponents())if(!BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).getNamespace().equals("minecraft"))throw new IOException("Packed coin has an unaudited component; no items moved");
        CompoundTag encoded=(CompoundTag)stack.save(p.registryAccess());if(encoded.contains("neoforge:attachments"))throw new IOException("Attached coin storage is not certified");
        return true;
    }
}
