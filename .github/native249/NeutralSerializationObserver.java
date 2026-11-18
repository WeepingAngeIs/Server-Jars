package dev.unyielding.bundlesmoke.proof;

import dev.unyielding.bundlesmoke.FullPackDiagnostics;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observation only at the serialization boundary; leaves arguments and result intact. */
@Mixin(targets="dev.unyielding.jail.JailNbt",remap=false)
public abstract class NeutralSerializationObserver {
    @Inject(method="encode(Lnet/minecraft/nbt/CompoundTag;)[B",at=@At("HEAD"),require=1)
    private static void observe(CompoundTag tag,CallbackInfoReturnable<byte[]> callback) {
        FullPackDiagnostics.observe(tag);
    }
}
