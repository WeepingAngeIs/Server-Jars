package dev.unyielding.bundlesmoke.proof;

import dev.unyielding.bundlesmoke.PlayerOnlyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the packets after vanilla handles them, even if both arrive in one rendered tick. */
@Mixin(ClientPacketListener.class)
public abstract class CenterClientPackets {
    @Inject(method="handlePlayerCombatKill", at=@At("RETURN"), require=1)
    private void sawDeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        var p = Minecraft.getInstance().player;
        if (p != null && packet.playerId() == p.getId()) PlayerOnlyClient.killed(p);
    }
    @Inject(method="handleRespawn", at=@At("RETURN"), require=1)
    private void sawRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        PlayerOnlyClient.respawned(Minecraft.getInstance().player);
    }
}
