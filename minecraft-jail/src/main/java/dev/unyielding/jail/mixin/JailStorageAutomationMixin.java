package dev.unyielding.jail.mixin;

import dev.unyielding.jail.JailCustodyService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The pinned Storage automation must not consume or rewrite a retained prisoner's backpack. */
@Pseudo
@Mixin(targets="dev.anya.storage.Upgrades",remap=false)
public abstract class JailStorageAutomationMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true,require=1)
    private static void jail$freezeHeldAutomation(PlayerTickEvent.Post event,CallbackInfo callback){
        if(event.getEntity() instanceof ServerPlayer player&&JailCustodyService.quarantined(player))callback.cancel();
    }
}
