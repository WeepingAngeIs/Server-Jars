package dev.unyielding.quests.mixin;

import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeatureProvider;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.client.PokemonCounterDisplay;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets="com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeatureProvider",remap=false)
public abstract class PokemonCounterDisplayMixin {
    @Redirect(method="getRenderer",at=@At(value="FIELD",
            target="Lcom/cobblemon/mod/common/api/pokemon/feature/IntSpeciesFeatureProvider;max:I",opcode=Opcodes.GETFIELD),require=1,remap=false)
    private int unyielding$displayGoal(IntSpeciesFeatureProvider provider,Pokemon pokemon) {
        return PokemonCounterDisplay.maximum(provider.getKeys(),pokemon,provider.getMax());
    }
}
