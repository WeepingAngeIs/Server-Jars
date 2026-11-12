package dev.unyielding.quests.mixin;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.settings.PokemonCounterRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.pokemon.requirements.PokemonPropertiesRequirement", remap = false)
public abstract class PokemonProgressRequirementMixin {
    @Shadow(remap = false) @Final private PokemonProperties target;

    @Inject(method = "check", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void unyielding$progressThresholds(Pokemon pokemon, CallbackInfoReturnable<Boolean> result) {
        if (PokemonCounterRules.hasThreshold(target)) {
            if (pokemon.getSpecies().getName().equalsIgnoreCase("corvisquire")
                    && target.getCustomProperties().stream().anyMatch(p -> p instanceof com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature f && f.getName().equals(PokemonCounterRules.DEFEATS_1000))
                    && dev.unyielding.quests.settings.CorvisquireStars.count(pokemon) < 120) {
                result.setReturnValue(false);
                return;
            }
            result.setReturnValue(PokemonCounterRules.propertiesMatch(target, pokemon, false));
        }
    }
}
