package dev.unyielding.quests.mixin;

import dev.unyielding.quests.jail.JailCustodyService;
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
    @Inject(method={"handleContainerClick","handleSetCreativeModeSlot","handlePlayerAction","handleUseItem","handleUseItemOn","handleInteract","handleSetCarriedItem","handlePlaceRecipe"},at=@At("HEAD"),cancellable=true)
    private void custodyFence(CallbackInfo ci){if(player.server.isSameThread()&&JailCustodyService.quarantined(player)){player.containerMenu.sendAllDataToRemote();ci.cancel();}}
}
