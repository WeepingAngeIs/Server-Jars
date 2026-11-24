package dev.unyielding.bundlesmoke.proof;

import dev.unyielding.bundlesmoke.Policy249Writes;
import dev.unyielding.jail.JailEquipmentPort;
import dev.unyielding.jail.JailVanillaCustody;
import dev.unyielding.jail.JailVanillaPort;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts production calls, without returning a substitute result. */
@Mixin(value={JailVanillaPort.class,JailEquipmentPort.class},remap=false)
public abstract class PolicyCustodyPortObserver {
    @Inject(method="clearAndMark",at=@At("HEAD"))
    private void policyClear(UUID id,CallbackInfo ci) { Policy249Writes.port("clearAndMark"); }
    @Inject(method="planRestore",at=@At("HEAD"))
    private void policyPlan(byte[] bytes,UUID id,CallbackInfoReturnable<JailVanillaCustody.View> ci) { Policy249Writes.port("planRestore"); }
    @Inject(method="restoreExact",at=@At("HEAD"))
    private void policyRestore(CallbackInfo ci) { Policy249Writes.port("restoreExact"); }
}
