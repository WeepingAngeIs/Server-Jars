package dev.unyielding.jail.party.live.mixin;

import com.cobblemon.mod.common.platform.events.ServerPlayerTickEvent;
import dev.unyielding.jail.party.live.SessionBoundary;
import kotlin.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only caller deferral, not authority to expose PCStore.getStruct or mutate custody. */
@Mixin(targets="com.cobblemon.mod.common.events.CallbackHandler",remap=false)
public abstract class SessionMolangBoundary {
    @Inject(method="setup$lambda$75(Lcom/cobblemon/mod/common/platform/events/ServerPlayerTickEvent$Pre;)Lkotlin/Unit;",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private static void defer(ServerPlayerTickEvent.Pre event,CallbackInfoReturnable<Unit> cir){
        if(SessionBoundary.blockMolang(event.getPlayer()))cir.setReturnValue(Unit.INSTANCE);
    }
}
