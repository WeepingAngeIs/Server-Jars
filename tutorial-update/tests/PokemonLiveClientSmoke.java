package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.FormData;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

@EventBusSubscriber(modid=BundleSmoke.MOD_ID,value=Dist.CLIENT)
public final class PokemonLiveClientSmoke {
    private static boolean done;private static int ticks;
    private static Pokemon retained;
    private static Species retainedSpecies;
    private static FormData retainedForm;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();if(done||mc.player==null)return;
        try {
            if(retained==null && Files.exists(Path.of("/tmp/uq-pokemon-party-uuid"))) {
                UUID fixture=UUID.fromString(Files.readString(Path.of("/tmp/uq-pokemon-party-uuid")).trim());
                for(Pokemon pokemon:CobblemonClient.INSTANCE.getStorage().getParty().getSlots())
                    if(pokemon!=null && pokemon.getUuid().equals(fixture)) {
                        retained=pokemon;retainedSpecies=pokemon.getSpecies();retainedForm=pokemon.getForm();
                        Files.writeString(Path.of("/tmp/uq-pokemon-party-retained"),fixture.toString());
                        break;
                    }
            }
            if(!Files.exists(Path.of("/tmp/uq-pokemon-live-ready")))return;
            var species=PokemonSpecies.getByName("pikachu");if(species==null)return;
            boolean live=species.getBaseStats().entrySet().stream().anyMatch(e->e.getKey().getShowdownId().equals("hp")&&e.getValue()==123);
            if(!live){require(++ticks<900,"Connected client did not receive edited base stats");return;}
            Map<String,Object> report=new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
            require(retained!=null,"Client did not retain the actual party Pokemon before the edit");
            require(retained.getSpecies()==retainedSpecies&&retained.getSpecies()==species,"Existing party Pokemon retained stale species data");
            require(retained.getForm()==retainedForm,"Existing party Pokemon lost its form reference");
            require(retained.getForm().getBaseStats().entrySet().stream().anyMatch(e->e.getKey().getShowdownId().equals("hp")&&e.getValue()==123),
                    "Existing party Pokemon form did not receive edited HP");
            PokemonEditorClientSmoke.exercise(mc,report);
            report.put("real_connected_client_received_edited_species_stats","passed");
            report.put("existing_party_pokemon_species_and_form_references_updated_in_place","passed");
            done=true;
            PokemonEmbeddedClientSmoke.exercise(mc,report).whenComplete((unused,failure)->mc.execute(()->{
                if(failure!=null)BundleSmoke.fail("/tmp/uq-pokemon-client-failed.txt",failure);
                else try{BundleSmoke.write("/tmp/uq-pokemon-client-passed.json",report);}
                catch(Exception error){BundleSmoke.fail("/tmp/uq-pokemon-client-failed.txt",error);}
            }));
        }catch(Throwable failure){done=true;BundleSmoke.fail("/tmp/uq-pokemon-client-failed.txt",failure);}
    }
}
