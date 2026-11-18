package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailCustodyService;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packet fence on the server thread; vanilla's first invocation still queues normally. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CustodyPacketMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleClientCommand",at=@At("HEAD"),cancellable=true)
    private void awaitFoodClone(net.minecraft.network.protocol.game.ServerboundClientCommandPacket packet,CallbackInfo ci) {
        if(player.server.isSameThread() && packet.getAction()==net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                && dev.unyielding.jail.JailFoodProgress.awaitVerifiedRespawn(player))ci.cancel();
    }
    @Inject(method={"handleContainerClick","handleSetCreativeModeSlot","handlePlayerAction","handleUseItem","handleUseItemOn","handleInteract","handleSetCarriedItem","handlePlaceRecipe","handleContainerButtonClick","handlePickItem"},at=@At("HEAD"),cancellable=true)
    private void custodyFence(CallbackInfo ci){
        if(!player.server.isSameThread())return;
        if(player.containerMenu instanceof dev.unyielding.jail.JailCustodyMenu menu&&!menu.stillValid(player)){player.closeContainer();ci.cancel();return;}
        if(JailCustodyService.quarantined(player)){player.containerMenu.sendAllDataToRemote();ci.cancel();}
    }
    @Inject(method="handleCustomPayload",at=@At("HEAD"),cancellable=true)
    private void custodyPayload(net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket packet,CallbackInfo ci){
        // NeoForge queues individual payload handlers, not this method. Queue the whole
        // method first so the quarantine decision cannot be bypassed on the Netty thread.
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,(ServerGamePacketListenerImpl)(Object)this,player.serverLevel());
        if(dev.unyielding.jail.JailAdmissions.protectedCustody(player)&&!(packet.payload() instanceof net.minecraft.network.protocol.common.custom.BrandPayload))ci.cancel();
    }
}
