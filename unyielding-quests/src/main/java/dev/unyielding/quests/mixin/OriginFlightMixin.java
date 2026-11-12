package dev.unyielding.quests.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.unyielding.quests.settings.OriginFlightSpeed;

@Pseudo
@Mixin(targets="com.cyberday1.neoorigins.power.builtin.CreativeFlightPower", remap=false)
public class OriginFlightMixin {
    @Inject(method="tickEffect(Lnet/minecraft/server/level/ServerPlayer;Lcom/cyberday1/neoorigins/power/builtin/CreativeFlightPower$Config;)V", at=@At(value="INVOKE", target="Lnet/minecraft/server/level/ServerPlayer;getAbilities()Lnet/minecraft/world/entity/player/Abilities;"), remap=false)
    private void unyielding$activeFlight(ServerPlayer player, @Coerce Object config, CallbackInfo ci) {
        OriginFlightSpeed.markActive(player);
    }
}
