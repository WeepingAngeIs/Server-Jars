package dev.unyielding.bundlesmoke;

import com.google.gson.*;
import dev.unyielding.quests.settings.PokemonEditor;
import dev.unyielding.quests.settings.PokemonEdits;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;

/** Actual server actions must reset one species without deleting shared spawn edits. */
final class PokemonSpawnResetSmoke {
    private static final String FIRST="cobblemon:pikachu",SECOND="cobblemon:eevee";
    private static final String SHARED="unyieldingquests:editor/shared-reset-fixture";
    private static final String GENERATED="unyieldingquests:editor/cobblemon/pikachu";

    @SuppressWarnings("unchecked")
    static void exercise(ServerPlayer player,Map<String,Object> report)throws Exception {
        boolean wasOp=player.server.getPlayerList().isOp(player.getGameProfile());
        boolean hadFile=Files.exists(PokemonEditor.configPath());
        PokemonEdits.State original=PokemonEdits.read(PokemonEditor.configPath());
        Field baseField=PokemonEditor.class.getDeclaredField("baseSpawns");baseField.setAccessible(true);
        Map<String,JsonObject> originalBase=(Map<String,JsonObject>)baseField.get(null);
        try {
            player.server.getPlayerList().op(player.getGameProfile());
            // Seed a shared loaded-pack fixture, then use the normal editor and disk persistence.
            Map<String,JsonObject> seeded=PokemonEdits.copy(originalBase);
            JsonObject defaults=file();seeded.put(SHARED,defaults);baseField.set(null,seeded);
            JsonArray draft=files(FIRST);
            JsonObject shared=find(draft,SHARED).getAsJsonObject("data");
            shared.addProperty("enabled",false);
            row(shared,FIRST).addProperty("weight",2);row(shared,SECOND).addProperty("weight",7);
            save(player,FIRST,draft);
            action(player,"reset_spawns",FIRST,"");
            JsonObject saved=PokemonEdits.read(PokemonEditor.configPath()).spawns().get(SHARED);
            require(saved!=null,"Reset deleted the shared override file");
            require(row(saved,FIRST).get("weight").getAsDouble()==1,"Selected species did not regain its pack rule");
            require(row(saved,SECOND).get("weight").getAsDouble()==7,"Reset erased another species' saved rule");
            require(!saved.get("enabled").getAsBoolean(),"Reset erased shared file-level settings");
            action(player,"reset_spawns",SECOND,"");
            saved=PokemonEdits.read(PokemonEditor.configPath()).spawns().get(SHARED);
            require(saved!=null&&row(saved,FIRST).get("weight").getAsDouble()==1
                    &&row(saved,SECOND).get("weight").getAsDouble()==1&&!saved.get("enabled").getAsBoolean(),
                    "Second reset lost restored rows or shared settings");

            // Omitting a generated file intentionally removes this species' rows.
            // Reset must then remove its empty override even though it no longer matches a species.
            draft=files(FIRST);JsonObject generated=file();
            JsonArray one=new JsonArray();JsonObject generatedRow=row(generated,FIRST).deepCopy();
            generatedRow.addProperty("id","uq-generated-reset");one.add(generatedRow);generated.add("spawns",one);
            draft.add(wrapper(GENERATED,generated));save(player,FIRST,draft);
            draft=files(FIRST);JsonArray without=new JsonArray();
            for(JsonElement value:draft)if(!value.getAsJsonObject().get("id").getAsString().equals(GENERATED))without.add(value.deepCopy());
            save(player,FIRST,without);
            JsonObject empty=PokemonEdits.read(PokemonEditor.configPath()).spawns().get(GENERATED);
            require(empty!=null&&empty.getAsJsonArray("spawns").isEmpty(),"Omission did not create its removal override");
            action(player,"reset_spawns",FIRST,"");
            require(!PokemonEdits.read(PokemonEditor.configPath()).spawns().containsKey(GENERATED),"Reset retained an empty generated override");
            report.put("per_species_spawn_reset_preserves_shared_rules_and_settings","passed");
            report.put("reset_removes_empty_generated_spawn_overrides","passed");
        } finally {
            baseField.set(null,originalBase);
            PokemonEdits.write(PokemonEditor.configPath(),original);
            action(player,"reload",FIRST,"");
            if(!hadFile)Files.deleteIfExists(PokemonEditor.configPath());
            if(!wasOp)player.server.getPlayerList().deop(player.getGameProfile());
        }
    }
    private static JsonArray files(String species){return JsonParser.parseString(PokemonEditor.detail(species).getString("spawns_json")).getAsJsonArray();}
    private static JsonObject wrapper(String id,JsonObject file){JsonObject wrapper=new JsonObject();wrapper.addProperty("id",id);wrapper.add("data",file);return wrapper;}
    private static JsonObject find(JsonArray files,String id){
        for(JsonElement value:files)if(value.getAsJsonObject().get("id").getAsString().equals(id))return value.getAsJsonObject();
        throw new IllegalStateException("Missing shared fixture");
    }
    private static JsonObject row(JsonObject file,String species){
        for(JsonElement value:file.getAsJsonArray("spawns"))if(value.getAsJsonObject().get("pokemon").getAsString().equals(species))return value.getAsJsonObject();
        throw new IllegalStateException("Missing shared species row");
    }
    private static JsonObject file(){
        return JsonParser.parseString("{\"enabled\":true,\"neededInstalledMods\":[],\"neededUninstalledMods\":[],\"spawns\":["
                +"{\"id\":\"uq-shared-reset-first\",\"pokemon\":\"cobblemon:pikachu\",\"type\":\"pokemon\",\"context\":\"grounded\",\"bucket\":\"rare\",\"level\":\"5-20\",\"weight\":1},"
                +"{\"id\":\"uq-shared-reset-second\",\"pokemon\":\"cobblemon:eevee\",\"type\":\"pokemon\",\"context\":\"grounded\",\"bucket\":\"rare\",\"level\":\"5-20\",\"weight\":1}]}").getAsJsonObject();
    }
    private static void save(ServerPlayer player,String id,JsonArray draft){action(player,"save_spawns",id,draft.toString());}
    private static void action(ServerPlayer player,String operation,String id,String json){
        long revision=PokemonEditor.revision();
        PokemonEditor.handle(player,new PokemonEditor.Action(operation,id,revision,json));
        require(PokemonEditor.revision()==revision+1,"Spawn reset fixture action failed: "+operation);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
