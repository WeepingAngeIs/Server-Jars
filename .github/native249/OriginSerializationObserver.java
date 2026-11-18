package dev.unyielding.bundlesmoke.proof;

import dev.unyielding.bundlesmoke.FullPackDiagnostics;
import dev.unyielding.jail.JailNativeOriginPort;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=JailNativeOriginPort.class,remap=false)
public abstract class OriginSerializationObserver {
    @Inject(method="requireCallbackState",at=@At("HEAD"),require=1)
    private static void observe(CompoundTag expected,CompoundTag observed,String stage,CallbackInfo callback) {
        FullPackDiagnostics.originDifference(expected,observed,stage);
    }
}
