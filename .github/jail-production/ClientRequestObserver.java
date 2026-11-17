package dev.unyielding.bundlesmoke.proof;
import dev.unyielding.bundlesmoke.ClientRequests;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public abstract class ClientRequestObserver {
 @Inject(method="send(Lnet/minecraft/network/protocol/Packet;)V",at=@At("HEAD"),require=1)
 private void observe(Packet<?> packet,CallbackInfo ci){
  if(packet instanceof ServerboundClientCommandPacket p&&p.getAction()==ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)ClientRequests.sent++;
 }
}
