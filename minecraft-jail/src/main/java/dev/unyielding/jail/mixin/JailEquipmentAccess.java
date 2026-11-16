package dev.unyielding.jail.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invoke only the native equipment reconciliation, never a full entity/world tick. */
@Mixin(LivingEntity.class)
public interface JailEquipmentAccess {
    @Invoker("detectEquipmentUpdates") void uq$detectEquipmentUpdates();
    @Accessor("lastArmorItemStacks") NonNullList<ItemStack> uq$lastArmor();
    @Accessor("lastHandItemStacks") NonNullList<ItemStack> uq$lastHands();
    @Accessor("lastBodyItemStack") ItemStack uq$lastBody();
}
