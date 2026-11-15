package dev.unyielding.quests.mixin;

import dev.unyielding.quests.jail.JailEscape;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe only a movement packet actually handled by the server, never a generic teleport. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class JailMovementMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void unyielding$beforePlayerMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        JailEscape.begin(player, packet);
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void unyielding$afterPlayerMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        JailEscape.finish(player);
    }
}
