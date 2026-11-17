package dev.unyielding.jail.party.live.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.server.level.ServerPlayer;
import dev.unyielding.jail.party.live.SessionBridge;
/** Observes exact pinned callback; never cancels or grants access. */
@Mixin(targets="com.cobblemon.mod.common.Cobblemon",remap=false)
public abstract class SessionLoginTrace {
    @Inject(method="preInitialize$lambda$0(Lnet/minecraft/server/level/ServerPlayer;)Lkotlin/Unit;",at=@At("HEAD"),require=1,remap=false)
    private static void enter(ServerPlayer p,CallbackInfoReturnable<kotlin.Unit> ci){SessionBridge.trace("native-login-callback-enter",p);}
    @Inject(method="preInitialize$lambda$0(Lnet/minecraft/server/level/ServerPlayer;)Lkotlin/Unit;",at=@At("RETURN"),require=1,remap=false)
    private static void exit(ServerPlayer p,CallbackInfoReturnable<kotlin.Unit> ci){SessionBridge.trace("native-login-callback-return",p);}
}
