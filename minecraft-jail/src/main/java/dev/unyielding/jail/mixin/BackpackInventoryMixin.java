package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailBackpacks;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** The native backpack item handler keeps the original contents tag. An automation may already
 * hold this handler when custody starts. Bind it once, before the physical stack can be moved. */
@Pseudo
@Mixin(targets="net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler",remap=false)
public abstract class BackpackInventoryMixin {
    @Unique private UUID jail$backpack;

    @Inject(method="<init>",at=@At("RETURN"),require=0)
    private void jail$bind(int size,@Coerce Object wrapper,CompoundTag contents,Runnable save,int slotLimit,
                           @Coerce Object settings,CallbackInfo result){
        jail$backpack=JailBackpacks.handlerIdentity(wrapper);
    }
    @Inject(method={"getStackInSlot","getSlotStack"},at=@At("HEAD"),cancellable=true,require=0)
    private void jail$read(int slot,CallbackInfoReturnable<ItemStack> result){
        if(JailBackpacks.locked(jail$backpack))result.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method={"extractItem(IIZ)Lnet/minecraft/world/item/ItemStack;","extractItemInternal"},at=@At("HEAD"),cancellable=true,require=0)
    private void jail$extract(int slot,int amount,boolean simulate,CallbackInfoReturnable<ItemStack> result){
        if(JailBackpacks.locked(jail$backpack))result.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="extractItem(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$extractMatching(ItemStack filter,boolean simulate,CallbackInfoReturnable<ItemStack> result){
        if(JailBackpacks.locked(jail$backpack))result.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method={"insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;","insertItemOnlyToSlot"},at=@At("HEAD"),cancellable=true,require=0)
    private void jail$insert(int slot,ItemStack stack,boolean simulate,CallbackInfoReturnable<ItemStack> result){
        if(JailBackpacks.locked(jail$backpack))result.setReturnValue(stack);
    }
    @Inject(method="insertItem(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$insertAnywhere(ItemStack stack,boolean simulate,CallbackInfoReturnable<ItemStack> result){
        if(JailBackpacks.locked(jail$backpack))result.setReturnValue(stack);
    }
    @Inject(method={"setStackInSlot","setSlotStack"},at=@At("HEAD"),cancellable=true,require=0)
    private void jail$replace(int slot,ItemStack stack,CallbackInfo result){
        if(JailBackpacks.locked(jail$backpack))result.cancel();
    }
    @Inject(method="setSlotStacks",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$replaceMultiple(Map<Integer,ItemStack> stacks,Set<Integer> empty,CallbackInfo result){
        if(JailBackpacks.locked(jail$backpack))result.cancel();
    }
    @Inject(method="saveInventory",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$save(CallbackInfo result){
        if(JailBackpacks.locked(jail$backpack))result.cancel();
    }
}
