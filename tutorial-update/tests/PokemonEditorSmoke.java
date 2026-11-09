package dev.unyielding.bundlesmoke;

import com.google.gson.*;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.quests.settings.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Real final-JAR authorization, persisted species edits, spawns and connected-client synchronization. */
@EventBusSubscriber(modid=BundleSmoke.MOD_ID)
public final class PokemonEditorSmoke {
    private static int phase,ticks;private static boolean wasOp;
    private static final String ID="cobblemon:pikachu";
    private static String originalFile;private static JsonObject originalSpecies;
    private static Pokemon fixture;
    private static final Map<String,Object> report=new LinkedHashMap<>();
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(phase==3||!Files.exists(Path.of("/tmp/uq-balance-live-passed.json")))return;
        ServerPlayer p=event.getServer().getPlayerList().getPlayerByName("Dev");if(p==null)return;
        try {
            if(phase==0) {
                fixture=new Pokemon();fixture.setSpecies(PokemonSpecies.getByName("pikachu"));fixture.setLevel(10);
                require(Cobblemon.INSTANCE.getStorage().getParty(p).add(fixture),"Party fixture needs a free slot");
                Files.writeString(Path.of("/tmp/uq-pokemon-party-uuid"),fixture.getUuid().toString());phase=4;ticks=0;return;
            }
            if(phase==4) {
                require(++ticks<900,"Client did not retain existing party fixture");
                if(!Files.exists(Path.of("/tmp/uq-pokemon-party-retained")))return;
                report.putAll(BundleSmoke.verifyProductionJar());
                wasOp=p.server.getPlayerList().isOp(p.getGameProfile());
                originalFile=Files.exists(PokemonEditor.configPath())?Files.readString(PokemonEditor.configPath()):null;
                CompoundTag all=PokemonEditor.search("",0);require(all.getInt("total")>1000,"Loaded species catalog is incomplete");
                require(PokemonEditor.search("pikachu",0).getInt("total")>=1,"Species search failed");
                originalSpecies=JsonParser.parseString(PokemonEditor.detail(ID).getString("species_json")).getAsJsonObject();
                JsonObject edit=originalSpecies.deepCopy();edit.getAsJsonObject("baseStats").addProperty("hp",123);
                p.server.getPlayerList().deop(p.getGameProfile());long r=PokemonEditor.revision();
                action(p,"save_species",ID,r,edit.toString());require(PokemonEditor.revision()==r,"Non-operator changed Pokemon config");
                p.server.getPlayerList().op(p.getGameProfile());
                for(String invalid:List.of("{}","null","{\"baseStats\":{\"hp\":1e400}}"))reject(p,"save_species",ID,invalid);
                JsonObject bad=originalSpecies.deepCopy();bad.getAsJsonObject("baseStats").addProperty("hp",-1);reject(p,"save_species",ID,bad.toString());
                bad=originalSpecies.deepCopy();JsonArray badAbilities=new JsonArray();badAbilities.add("not_a_real_ability");bad.add("abilities",badAbilities);reject(p,"save_species",ID,bad.toString());
                bad=originalSpecies.deepCopy();bad.addProperty("primaryType","not_a_real_type");reject(p,"save_species",ID,bad.toString());
                PokemonRuntime.validateSpecies(ID,edit);
                action(p,"save_species",ID,r,edit.toString());
                require(PokemonEditor.revision()==r+1,"Species edit did not save");
                require(PokemonEdits.read(PokemonEditor.configPath()).species().get(ID).getAsJsonObject("baseStats").get("hp").getAsInt()==123,"Species override missing on disk");
                action(p,"save_species",ID,r,originalSpecies.toString());require(PokemonEditor.revision()==r+1,"Stale edit was accepted");
                JsonArray files=new JsonArray();JsonObject wrapper=new JsonObject();wrapper.addProperty("id","unyieldingquests:editor/cobblemon/pikachu");
                JsonObject file=JsonParser.parseString("{\"enabled\":true,\"neededInstalledMods\":[],\"neededUninstalledMods\":[],\"spawns\":[{\"id\":\"uq-smoke-pikachu\",\"pokemon\":\"pikachu\",\"type\":\"pokemon\",\"context\":\"grounded\",\"bucket\":\"rare\",\"level\":\"5-20\",\"weight\":2.5,\"presets\":[\"natural\"],\"condition\":{\"biomes\":[\"minecraft:plains\"],\"dimensions\":[\"minecraft:overworld\"]}}]}").getAsJsonObject();
                wrapper.add("data",file);files.add(wrapper);
                // Preserve the other active Pikachu files while adding one new rule.
                JsonArray full=JsonParser.parseString(PokemonEditor.detail(ID).getString("spawns_json")).getAsJsonArray();full.add(wrapper);
                r=PokemonEditor.revision();action(p,"save_spawns",ID,r,full.toString());require(PokemonEditor.revision()==r+1,"Spawn edit did not save");
                require(PokemonEdits.read(PokemonEditor.configPath()).spawns().containsKey("unyieldingquests:editor/cobblemon/pikachu"),"Spawn override missing on disk");
                file.getAsJsonArray("spawns").get(0).getAsJsonObject().addProperty("weight",-1);reject(p,"save_spawns",ID,files.toString());
                // Recreate the editor service from the actual persisted configuration.
                long savedRevision=PokemonEditor.revision();PokemonEditor.start(p.server);
                require(PokemonEditor.revision()==savedRevision,"Configuration revision did not survive reload");
                require(JsonParser.parseString(PokemonEditor.detail(ID).getString("species_json")).getAsJsonObject().getAsJsonObject("baseStats").get("hp").getAsInt()==123,"Species edit did not survive service restart");
                PokemonCounterSmoke.exercise(p,report);
                PokemonSpawnResetSmoke.exercise(p,report);
                report.put("all_loaded_species_search_permissions_invalid_values_and_stale_edits","passed");
                report.put("species_and_spawn_overrides_saved_and_reloaded_from_disk","passed");
                Files.writeString(Path.of("/tmp/uq-pokemon-live-ready"),"123");phase=1;ticks=0;return;
            }
            if(phase==1) {
                require(++ticks<900,"Client did not confirm live species synchronization");
                if(!Files.exists(Path.of("/tmp/uq-pokemon-client-passed.json")))return;
                restore(p);report.put("client_confirmed_live_species_data_and_editor_ui","passed");
                BundleSmoke.write("/tmp/uq-pokemon-server-passed.json",report);phase=3;
            }
        }catch(Throwable failure){try{restore(p);}catch(Throwable ignored){}phase=3;BundleSmoke.fail("/tmp/uq-pokemon-server-failed.txt",failure);}
    }
    private static void action(ServerPlayer p,String operation,String id,long revision,String json){PokemonEditor.handle(p,new PokemonEditor.Action(operation,id,revision,json));}
    private static void reject(ServerPlayer p,String operation,String id,String json)throws Exception {
        long r=PokemonEditor.revision();String disk=Files.exists(PokemonEditor.configPath())?Files.readString(PokemonEditor.configPath()):null;
        action(p,operation,id,r,json);require(PokemonEditor.revision()==r,"Invalid Pokemon settings accepted");
        require(Objects.equals(disk,Files.exists(PokemonEditor.configPath())?Files.readString(PokemonEditor.configPath()):null),"Rejected edit changed persisted configuration");
    }
    private static void restore(ServerPlayer p)throws Exception {
        if(originalSpecies==null)return;p.server.getPlayerList().op(p.getGameProfile());
        action(p,"reset_species",ID,PokemonEditor.revision(),"");
        action(p,"reset_spawns",ID,PokemonEditor.revision(),"");
        if(originalFile==null)Files.deleteIfExists(PokemonEditor.configPath());else Files.writeString(PokemonEditor.configPath(),originalFile);
        if(!wasOp)p.server.getPlayerList().deop(p.getGameProfile());originalSpecies=null;
        if(fixture!=null){Cobblemon.INSTANCE.getStorage().getParty(p).remove(fixture);fixture=null;}
    }
}
