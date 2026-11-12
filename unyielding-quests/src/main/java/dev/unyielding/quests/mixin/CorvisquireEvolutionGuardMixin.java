package dev.unyielding.quests.mixin;

import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.settings.CorvisquireStars;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recheck the new requirement when accepting an offer saved before the update. */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.pokemon.evolution.controller.ServerEvolutionController", remap = false)
public abstract class CorvisquireEvolutionGuardMixin {
    @Shadow(remap = false) @Final private Pokemon pokemon;

    @Inject(method = "start(Lcom/cobblemon/mod/common/api/pokemon/evolution/Evolution;)V", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void unyielding$guardOldOffer(Evolution evolution, CallbackInfo ci) {
        if (pokemon.getSpecies().getName().equalsIgnoreCase("corvisquire")
                && evolution.getId().equals("corvisquire_covislight")
                && CorvisquireStars.count(pokemon) < 120) {
            var owner = pokemon.getOwnerPlayer();
            if (owner != null) owner.sendSystemMessage(Component.literal("Corvisquire must eat 120 Nether Stars before evolving (" + CorvisquireStars.count(pokemon) + "/120)."));
            ci.cancel();
        }
    }
}
