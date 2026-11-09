package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeatureProvider;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures;
import com.cobblemon.mod.common.client.gui.summary.featurerenderers.BarSummarySpeciesFeatureRenderer;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.client.PokemonCounterDisplay;
import dev.unyielding.quests.settings.PokemonCounterRules;
import dev.unyielding.quests.settings.PokemonProgressSync;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Calls the actual native renderer factory after the production client mixin transforms it. */
final class PokemonProgressClientSmoke {
    static void exercise(Minecraft mc,Map<String,Object> report)throws Exception {
        require(PokemonCounterDisplay.synchronizedGoals(),"Joining client did not receive progress goals");
        var field=PokemonCounterDisplay.class.getDeclaredField("goals");field.setAccessible(true);Object original=field.get(null);
        int permission=mc.player.getPermissionLevel();
        Pokemon first=pokemon("mawile"),second=pokemon("sliggoo"),evolved=pokemon("mawonna");
        IntSpeciesFeatureProvider defeats=provider(PokemonCounterRules.DEFEATS),meals=provider(PokemonCounterRules.MEALS),other=provider(PokemonCounterRules.DEFEATS_1000);
        try {
            require(maximum(defeats,first)==666&&maximum(meals,first)==49&&maximum(other,second)==1000,"Default progress bar goals changed");
            require(maximum(defeats,evolved)==666,"Evolved progress bar lost its original goal");
            require(defeats.getMax()==PokemonCounterRules.MAX_PROGRESS,"Display logic changed the storage/parser maximum");
            CompoundTag changed=new CompoundTag();ListTag rows=new ListTag();
            rows.add(row(first,PokemonCounterRules.DEFEATS,777));rows.add(row(evolved,PokemonCounterRules.DEFEATS,777));
            for(int i=0;i<98;i++){CompoundTag extra=new CompoundTag();extra.putString("key","test:"+i);extra.put("goals",new CompoundTag());rows.add(extra);}
            changed.put("rows",rows);var packets=PokemonProgressSync.packets(changed,9001);
            require(packets.size()==3,"Progress goal packets were not bounded");
            mc.player.setPermissionLevel(0);
            PokemonCounterDisplay.accept(roundtrip(mc,packets.get(0)));
            require(maximum(defeats,first)==666,"Partial catalog replaced an active display snapshot");
            PokemonCounterDisplay.accept(roundtrip(mc,packets.get(2)));
            require(maximum(defeats,first)==666,"Out-of-order catalog changed a display goal");
            PokemonCounterDisplay.accept(roundtrip(mc,packets.get(1)));PokemonCounterDisplay.accept(roundtrip(mc,packets.get(2)));
            require(maximum(defeats,first)==777&&maximum(defeats,evolved)==777,"Current server goals did not reach native renderer construction");
            require(defeats.getMax()==PokemonCounterRules.MAX_PROGRESS,"Rendering mutated the provider maximum");
            IntSpeciesFeatureProvider unrelated=new IntSpeciesFeatureProvider();unrelated.setKeys(List.of("unrelated_counter"));unrelated.setMax(321);
            IntSpeciesFeatureProvider.DisplayData display=new IntSpeciesFeatureProvider.DisplayData();display.setName("test.counter");display.setColour(new Vec3(1,1,1));unrelated.setDisplay(display);
            require(maximum(unrelated,first)==321,"Unrelated native feature renderer was changed");
            PokemonCounterDisplay.logout(null);require(!PokemonCounterDisplay.synchronizedGoals(),"Disconnect retained server-specific goals");
            report.put("native_progress_renderer_preserves_default_and_edited_goals_without_changing_storage","passed");
            report.put("progress_goals_codec_chunks_nonoperator_sync_and_disconnect_reset","passed");
        } finally {PokemonCounterDisplay.clear();field.set(null,original);mc.player.setPermissionLevel(permission);}
    }
    private static Pokemon pokemon(String name){Pokemon p=new Pokemon();p.setSpecies(PokemonSpecies.getByName(name));return p;}
    private static IntSpeciesFeatureProvider provider(String key) {
        return SpeciesFeatures.getFeatures().stream().filter(IntSpeciesFeatureProvider.class::isInstance).map(IntSpeciesFeatureProvider.class::cast)
                .filter(provider->provider.getKeys().contains(key)).findFirst().orElseThrow();
    }
    private static int maximum(IntSpeciesFeatureProvider provider,Pokemon pokemon){
        return ((BarSummarySpeciesFeatureRenderer)provider.getRenderer(pokemon)).getMax();
    }
    private static CompoundTag row(Pokemon pokemon,String feature,int maximum){
        CompoundTag row=new CompoundTag();row.putString("key",PokemonProgressSync.formKey(pokemon.getSpecies().getResourceIdentifier().toString(),pokemon.getForm().getName()));
        CompoundTag values=new CompoundTag();values.putInt(feature,maximum);row.put("goals",values);return row;
    }
    private static PokemonProgressSync.Sync roundtrip(Minecraft mc,PokemonProgressSync.Sync packet){
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),mc.level.registryAccess());
        try{PokemonProgressSync.Sync.CODEC.encode(buffer,packet);require(buffer.readableBytes()<100000,"Progress goal packet exceeds its budget");return PokemonProgressSync.Sync.CODEC.decode(buffer);}
        finally{buffer.release();}
    }
}
