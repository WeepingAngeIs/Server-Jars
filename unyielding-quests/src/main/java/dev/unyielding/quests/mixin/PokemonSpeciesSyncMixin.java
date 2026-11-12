package dev.unyielding.quests.mixin;

import com.cobblemon.mod.common.pokemon.Species;
import dev.unyielding.quests.settings.PokemonRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;

/** Keep client party and rendered entity references attached to the newly synchronized data. */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.net.messages.client.data.SpeciesRegistrySyncPacket", remap = false)
public abstract class PokemonSpeciesSyncMixin {
    @ModifyVariable(method = "synchronizeDecoded", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1, remap = false)
    private Collection<Species> unyielding$retainSpeciesInstances(Collection<Species> entries) {
        return PokemonRuntime.preserveClientReferences(entries);
    }
}
