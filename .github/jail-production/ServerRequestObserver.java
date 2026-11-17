package dev.unyielding.bundlesmoke.proof;
import dev.unyielding.jail.JailAutoRespawn;
import dev.unyielding.bundlesmoke.ProductionObserver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerRequestObserver {
 @Shadow public ServerPlayer player;
 @Inject(method="handleClientCommand",at=@At("HEAD"),require=1)
 private void observe(ServerboundClientCommandPacket packet,CallbackInfo ci){
  if(player.server==null||!player.server.isSameThread()||packet.getAction()!=ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)return;
  if(JailAutoRespawn.isAutomaticInvocation(player))ProductionObserver.automaticCalls++;else ProductionObserver.clientRespawnRequests++;
 }
}
