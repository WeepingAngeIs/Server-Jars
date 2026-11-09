package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.requirements.PokemonPropertiesRequirement;
import dev.unyielding.quests.settings.PokemonCounterRules;
import dev.unyielding.quests.settings.PokemonProgressSync;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Server goals come from the current native requirements, including retained evolved progress. */
final class PokemonProgressServerSmoke {
    static void exercise(ServerPlayer player,Map<String,Object> report) {
        Species source=PokemonSpecies.getByName("mawile"),result=PokemonSpecies.getByName("mawonna");
        String first=PokemonProgressSync.formKey(source.getResourceIdentifier().toString(),source.getStandardForm().getName());
        String evolved=PokemonProgressSync.formKey(result.getResourceIdentifier().toString(),result.getStandardForm().getName());
        IntSpeciesFeature threshold=source.getEvolutions().stream().filter(e->e.getId().equals("mawile_mawonna"))
                .flatMap(e->e.getRequirements().stream()).filter(PokemonPropertiesRequirement.class::isInstance)
                .map(PokemonPropertiesRequirement.class::cast).flatMap(r->r.getTarget().getCustomProperties().stream())
                .filter(IntSpeciesFeature.class::isInstance).map(IntSpeciesFeature.class::cast)
                .filter(f->f.getName().equals(PokemonCounterRules.DEFEATS)).findFirst().orElseThrow();
        int original=threshold.getValue();
        try {
            require(goal(PokemonProgressSync.snapshot(),first,PokemonCounterRules.DEFEATS)==original,"Source progress goal missing");
            require(goal(PokemonProgressSync.snapshot(),evolved,PokemonCounterRules.DEFEATS)==original,"Evolved progress goal missing");
            threshold.setValue(777);
            CompoundTag changed=PokemonProgressSync.snapshot();
            require(goal(changed,first,PokemonCounterRules.DEFEATS)==777,"Catalog ignored an edited native threshold");
            require(goal(changed,evolved,PokemonCounterRules.DEFEATS)==777,"Inherited progress goal retained an old threshold");
            threshold.setValue(0);
            require(goal(PokemonProgressSync.snapshot(),first,PokemonCounterRules.DEFEATS)==1,"Zero threshold produced an unsafe display denominator");
        } finally {threshold.setValue(original);}
        PokemonProgressSync.login(player);
        report.put("progress_display_catalog_uses_current_native_and_incoming_requirements","passed");
    }
    private static int goal(CompoundTag catalog,String key,String feature) {
        for(var raw:catalog.getList("rows",10)) {
            CompoundTag row=(CompoundTag)raw;if(row.getString("key").equals(key))return row.getCompound("goals").getInt(feature);
        }
        return -1;
    }
}
