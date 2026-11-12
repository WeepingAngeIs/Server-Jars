package dev.unyielding.quests.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.settings.PokemonCounterRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.mawonna.MawonnaCompanion", remap = false)
public abstract class PokemonCompanionProgressMixin {
    @Inject(method = "creditDefeat", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void unyielding$countBeyondOriginalThreshold(Pokemon pokemon, CallbackInfoReturnable<Integer> result) {
        result.setReturnValue(PokemonCounterRules.creditDefeat(pokemon));
    }

    @Inject(method = "rememberMeal", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void unyielding$rememberEveryDistinctMeal(Pokemon pokemon, String canonical, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(PokemonCounterRules.rememberMeal(pokemon, canonical));
    }

    @Inject(method = "readyToMarry", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void unyielding$useCurrentEvolutionConditions(Pokemon pokemon, CallbackInfoReturnable<Boolean> result) {
        result.setReturnValue(PokemonCounterRules.readyToMarry(pokemon));
    }

    @ModifyConstant(method = "onInteract", constant = @Constant(intValue = 49), require = 1, remap = false)
    private int unyielding$showActualMealProgress(int original) {
        return PokemonCounterRules.MAX_PROGRESS;
    }

    // Keep the companion's original full regression command valid. Only the
    // first literals declare its counter-cap fixture; later evolution boundary
    // fixtures intentionally retain the unchanged default conditions.
    @ModifyConstant(method = "selfTest", constant = @Constant(intValue = 666, ordinal = 0), require = 1, remap = false)
    private static int unyielding$updatedPrimaryCapFixture(int original) {
        return PokemonCounterRules.MAX_PROGRESS;
    }

    @ModifyConstant(method = "selfTest", constant = @Constant(intValue = 1000, ordinal = 0), require = 1, remap = false)
    private static int unyielding$updatedSecondaryCapFixture(int original) {
        return PokemonCounterRules.MAX_PROGRESS;
    }
}
