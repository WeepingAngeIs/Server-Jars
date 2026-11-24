package dev.unyielding.bundlesmoke.proof;

import dev.unyielding.bundlesmoke.Policy249Writes;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes direct Jail mutations separately from Minecraft's ordinary respawn copy. */
@Mixin(Inventory.class)
public abstract class PolicyInventoryObserver {
    @Shadow @Final public Player player;
    @Inject(method="setItem",at=@At("HEAD"))
    private void policySet(int slot,ItemStack stack,CallbackInfo ci) { Policy249Writes.inventory(player,"setItem"); }
    @Inject(method="clearContent",at=@At("HEAD"))
    private void policyClear(CallbackInfo ci) { Policy249Writes.inventory(player,"clearContent"); }
    @Inject(method="load",at=@At("HEAD"))
    private void policyLoad(ListTag tag,CallbackInfo ci) { Policy249Writes.inventory(player,"load"); }
    @Inject(method="replaceWith",at=@At("HEAD"))
    private void policyReplace(Inventory from,CallbackInfo ci) { Policy249Writes.inventory(player,"replaceWith"); }
    @Inject(method="removeItem(II)Lnet/minecraft/world/item/ItemStack;",at=@At("HEAD"))
    private void policyRemove(int slot,int count,CallbackInfoReturnable<ItemStack> ci) { Policy249Writes.inventory(player,"removeItem"); }
    @Inject(method="removeItemNoUpdate",at=@At("HEAD"))
    private void policyRemoveSilent(int slot,CallbackInfoReturnable<ItemStack> ci) { Policy249Writes.inventory(player,"removeItemNoUpdate"); }
}
