package dev.unyielding.jail.party.live.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import dev.unyielding.jail.party.live.SessionBoundary;
/** Test-only denial adapter. The reviewed companion bytes and non-enrolled behavior are unchanged. */
@Mixin(targets="dev.mawonna.GhostJirachiEyeAspect",remap=false)
public abstract class SessionCosmeticBoundary {
    @Inject(method="afterPlayerTick(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V",at=@At("HEAD"),cancellable=true,remap=false)
    private static void defer(PlayerTickEvent.Post event,CallbackInfo ci){
        if(event.getEntity() instanceof ServerPlayer p&&SessionBoundary.blockCosmetic(p))ci.cancel();
    }
}
