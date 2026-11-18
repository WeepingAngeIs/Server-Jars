package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailBackpacks;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Provider reads still expose the real data for inspection; no writable live reference leaves storage. */
@Pseudo
@Mixin(targets="net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage",remap=false)
public abstract class BackpackStorageMixin {
    @Inject(method="getBackpackContents",at=@At("RETURN"),cancellable=true,require=0)
    private void jail$read(UUID id,CallbackInfoReturnable<Optional<CompoundTag>> result) {
        if(JailBackpacks.locked(id))result.setReturnValue(result.getReturnValue().map(CompoundTag::copy));
    }
    @Inject(method="getOrCreateBackpackContents",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$writable(UUID id,CallbackInfoReturnable<CompoundTag> result) {
        if(JailBackpacks.locked(id))result.setReturnValue(new CompoundTag());
    }
    @Inject(method="removeBackpackContents",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$remove(UUID id,CallbackInfo result) {
        if(JailBackpacks.locked(id))result.cancel();
    }
    @Inject(method="setBackpackContents",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$replace(UUID id,CompoundTag replacement,CallbackInfo result) {
        if(JailBackpacks.locked(id))result.cancel();
    }
    @Inject(method="lambda$removeNonPlayerBackpackContents$2",at=@At("HEAD"),cancellable=true,require=0)
    private void jail$cleanup(boolean emptyOnly,AtomicInteger removed,Map.Entry<UUID,CompoundTag> row,CallbackInfoReturnable<Boolean> result) {
        if(JailBackpacks.locked(row.getKey()))result.setReturnValue(false);
    }
}
