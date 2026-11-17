package dev.unyielding.jail.party.live.mixin;
import com.cobblemon.mod.common.api.net.NetworkPacket;
import dev.unyielding.jail.party.live.SessionBoundary;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevent fixture source objects escaping to asynchronous native encoders before custody. */
@Mixin(targets="com.cobblemon.mod.common.CobblemonNetwork",remap=false)
public abstract class SessionOutgoingBoundary {
    @Inject(method="sendPacketToPlayer(Lnet/minecraft/server/level/ServerPlayer;Lcom/cobblemon/mod/common/api/net/NetworkPacket;)V",at=@At("HEAD"),cancellable=true,require=1,remap=false)
    private void defer(ServerPlayer player,NetworkPacket<?> packet,CallbackInfo ci){
        if(SessionBoundary.blockFixturePartyPacket(player,packet))ci.cancel();
    }
}
