package dev.unyielding.jail.party.live.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.unyielding.jail.party.live.SessionBoundary;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Exact test-only periodic consumer boundary; no native mutation permission. */
@Mixin(targets="com.cobblemon.mod.common.events.ServerTickHandler",remap=false)
public abstract class SessionPeriodicBoundary {
    @WrapOperation(method="onTick(Lnet/minecraft/server/MinecraftServer;)V",at=@At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;",remap=true),require=1,remap=false)
    private List<ServerPlayer> deferEnrolled(PlayerList list,Operation<List<ServerPlayer>> original){
        return SessionBoundary.periodicPlayers(original.call(list));
    }
}
