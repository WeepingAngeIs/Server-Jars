package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailFoodProgress;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Reuse Onion's own clone implementation for the escape already verified by Jail. */
@Pseudo
@Mixin(targets="team.creative.solonion.common.event.SOLOnionEvent",remap=false)
public abstract class OnionCloneMixin {
    @Redirect(method="onClone",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$Clone;isWasDeath()Z",remap=false),remap=false,require=1)
    private boolean jail$onionDeath(PlayerEvent.Clone event) {
        return event.isWasDeath() && !JailFoodProgress.protectsClone(event);
    }
}
