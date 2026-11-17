package dev.unyielding.bundlesmoke.proof;
import dev.unyielding.jail.JailAutoRespawn;
import dev.unyielding.bundlesmoke.ProductionObserver;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Observation at successful return only; no mutation, cancellation or replacement. */
@Mixin(value=JailAutoRespawn.class,remap=false)
public abstract class RespawnCompletionObserver {
 @Inject(method="respawn",at=@At("RETURN"),require=1)
 private static void completed(ServerPlayer player,CallbackInfo ci){ProductionObserver.automaticCompletions++;}
}
