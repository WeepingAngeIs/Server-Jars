package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
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
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();if(done||mc.player==null||!Files.exists(Path.of("/tmp/uq-pokemon-live-ready")))return;
        try {
            var species=PokemonSpecies.getByName("pikachu");if(species==null)return;
            boolean live=species.getBaseStats().entrySet().stream().anyMatch(e->e.getKey().getShowdownId().equals("hp")&&e.getValue()==123);
            if(!live){require(++ticks<900,"Connected client did not receive edited base stats");return;}
            Map<String,Object> report=new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
            PokemonEditorClientSmoke.exercise(mc,report);
            report.put("real_connected_client_received_edited_species_stats","passed");
            BundleSmoke.write("/tmp/uq-pokemon-client-passed.json",report);done=true;
        }catch(Throwable failure){done=true;BundleSmoke.fail("/tmp/uq-pokemon-client-failed.txt",failure);}
    }
}
