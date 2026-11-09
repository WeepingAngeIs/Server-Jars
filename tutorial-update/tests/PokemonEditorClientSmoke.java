package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.AdminSettingsScreen;
import dev.unyielding.quests.client.PokemonEditorScreen;
import dev.unyielding.quests.settings.PokemonEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.Map;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Runs against the distributed editor on the connected cloud client. */
final class PokemonEditorClientSmoke {
    static void exercise(Minecraft mc, Map<String,Object> report) throws Exception {
        PokemonTransferSmoke.exercise(mc,report);
        Screen previous = mc.screen; int permission = mc.player.getPermissionLevel();
        try {
            mc.player.setPermissionLevel(2);
            for (int[] size : new int[][]{{640,400},{320,240}}) {
                PokemonEditorScreen browser = new PokemonEditorScreen(null, search()); mc.setScreen(browser); draw(mc,browser,size);
                require(has(browser,"Eevee  (cobblemon:eevee)"),"Loaded-species search result missing");
                require(fields(browser).length == 1,"Species browser needs searchable input");
                PokemonEditorScreen.SpeciesScreen species = new PokemonEditorScreen.SpeciesScreen(browser,species()); mc.setScreen(species); draw(mc,species,size);
                require(has(species,"Base stats") && has(species,"Abilities and types") && has(species,"Evolution conditions") && has(species,"Spawns and weights"),"Species category missing");
                press(species,"Base stats"); draw(mc,mc.screen,size); Screen stats = mc.screen;
                require(fields(stats).length == 6,"Six stat inputs required");
                fields(stats)[0].setValue("0");press(stats,"Save");
                var pending=stats.getClass().getSuperclass().getDeclaredField("pending");pending.setAccessible(true);
                require(!pending.getBoolean(stats),"Invalid stat draft submitted");
                fields(stats)[0].setValue("123");
                CompoundTag stale=species();stale.putBoolean("error",true);stale.putString("message","Not saved: settings changed. Reopen this Pokemon.");
                PokemonEditorScreen.receive(new PokemonEditor.Sync(stale,false));
                require(fields(stats)[0].getValue().equals("123"),"Stale reply overwrote stat draft");
                mc.setScreen(species); draw(mc,species,size);press(species,"Search all species fields");draw(mc,mc.screen,size);
                fields(mc.screen)[0].setValue("moves");draw(mc,mc.screen,size);
                require(has(mc.screen,"moves") && !has(mc.screen,"baseStats"),"Advanced field search incorrect");
                mc.setScreen(species);draw(mc,species,size);press(species,"Evolution conditions");draw(mc,mc.screen,size);
                Screen json=mc.screen;MultiLineEditBox editor=json.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                editor.setValue("{ invalid json");press(json,"Save");
                var jsonPending=json.getClass().getDeclaredField("pending");jsonPending.setAccessible(true);
                require(!jsonPending.getBoolean(json),"Invalid evolution JSON submitted");
                PokemonEditorScreen.receive(new PokemonEditor.Sync(stale,false));
                editor=json.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                require(editor.getValue().equals("{ invalid json"),"Error reply overwrote JSON draft");
                bounds(json,size);
                mc.setScreen(species);draw(mc,species,size);press(species,"Spawns and weights");draw(mc,mc.screen,size);
                require(has(mc.screen,"Create a spawn rule"),"Command-only Pokemon need in-game spawn creation");
                require(has(mc.screen,"Edit all spawn files and conditions"),"Full spawn/condition editor missing");
                press(mc.screen,"editor-eevee-1 · 1.0");draw(mc,mc.screen,size);
                Screen rule=mc.screen;require(fields(rule).length == 11,"Spawn weight, bucket, level, biome, dimension and area controls missing");bounds(rule,size);
                press(rule,"Use my current dimension");require(fields(rule)[4].getValue().equals(mc.level.dimension().location().toString()),"Current dimension helper incorrect");
                fields(rule)[5].setValue("100");fields(rule)[6].setValue("0");press(rule,"Review");
                require(mc.screen==rule,"Inverted spawn area limits accepted");
                fields(rule)[5].setValue("-16");fields(rule)[6].setValue("16");press(rule,"Review");draw(mc,mc.screen,size);
                require(mc.screen instanceof PokemonEditorScreen.JsonScreen,"Spawn review did not open JSON editor");
                MultiLineEditBox spawnReview=mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                var condition=com.google.gson.JsonParser.parseString(spawnReview.getValue()).getAsJsonArray().get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("spawns").get(0).getAsJsonObject().getAsJsonObject("condition");
                require(condition.get("minX").getAsDouble()==-16&&condition.get("maxX").getAsDouble()==16,"Spawn area fields did not reach review draft");
                require(condition.getAsJsonArray("dimensions").get(0).getAsString().equals(mc.level.dimension().location().toString()),"Spawn dimension field did not reach review draft");
                CompoundTag complex=species();var complexJson=com.google.gson.JsonParser.parseString(complex.getString("species_json")).getAsJsonObject();
                var complexPool=com.google.gson.JsonParser.parseString("[\"runaway\",{\"ability\":\"adaptability\",\"priority\":2}]");complexJson.add("abilities",complexPool);complex.putString("species_json",complexJson.toString());
                var complexSpecies=new PokemonEditorScreen.SpeciesScreen(browser,complex);var abilityScreen=new PokemonEditorScreen.AbilitiesScreen(complexSpecies);mc.setScreen(abilityScreen);draw(mc,abilityScreen,size);
                require(fields(abilityScreen).length==2,"Structured abilities must use preserving JSON controls");
                fields(abilityScreen)[0].setValue("fire");
                var draftMethod=abilityScreen.getClass().getDeclaredMethod("draft");draftMethod.setAccessible(true);
                var retained=(com.google.gson.JsonObject)draftMethod.invoke(abilityScreen);
                require(retained.get("abilities").equals(complexPool),"Type draft flattened structured ability entries");
                press(abilityScreen,"Edit abilities JSON");draw(mc,mc.screen,size);
                require(mc.screen instanceof PokemonEditorScreen.JsonScreen,"Structured ability JSON editor missing");
                var abilityBox=mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                require(com.google.gson.JsonParser.parseString(abilityBox.getValue()).equals(complexPool),"Ability JSON editor changed structured entries");
                var serializerField=mc.screen.getClass().getDeclaredField("serializer");serializerField.setAccessible(true);
                @SuppressWarnings("unchecked") var serializer=(java.util.function.Function<String,String>)serializerField.get(mc.screen);
                var serialized=com.google.gson.JsonParser.parseString(serializer.apply(abilityBox.getValue())).getAsJsonObject();
                require(serialized.get("primaryType").getAsString().equals("fire")&&serialized.get("abilities").equals(complexPool),"Opening ability JSON lost unsaved type changes");
                AdminSettingsScreen admin=new AdminSettingsScreen(null);draw(mc,admin,size);
                require(has(admin,"Pokemon editor"),"Admin menu lacks Pokemon editor");bounds(admin,size);
            }
            report.put("pokemon_search_categories_stat_validation_evolution_json_and_spawn_controls","passed");
            report.put("pokemon_drafts_survive_errors_and_editor_renders_at_640x400_and_320x240","passed");
            report.put("pokemon_structured_ability_rules_survive_type_edits_and_json_editor","passed");
        } finally { mc.player.setPermissionLevel(permission);mc.setScreen(previous); }
    }
    private static CompoundTag search(){
        CompoundTag data=new CompoundTag();data.putString("view","search");data.putString("query","");data.putInt("page",0);data.putInt("page_size",30);data.putInt("total",1);
        ListTag rows=new ListTag();CompoundTag row=new CompoundTag();row.putString("id","cobblemon:eevee");row.putString("name","Eevee");rows.add(row);data.put("results",rows);return data;
    }
    private static CompoundTag species(){
        CompoundTag data=new CompoundTag();data.putString("view","species");data.putString("id","cobblemon:eevee");data.putString("name","Eevee");data.putLong("revision",5);
        data.putString("species_json","{\"name\":\"Eevee\",\"primaryType\":\"normal\",\"abilities\":[\"runaway\",\"adaptability\",\"h:anticipation\"],\"baseStats\":{\"hp\":55,\"attack\":55,\"defence\":50,\"special_attack\":45,\"special_defence\":65,\"speed\":55},\"evolutions\":[],\"moves\":[\"1:tackle\"],\"forms\":[]}");
        data.putString("spawns_json","[{\"id\":\"unyieldingquests:editor/cobblemon/eevee\",\"data\":{\"enabled\":true,\"spawns\":[{\"id\":\"editor-eevee-1\",\"pokemon\":\"eevee\",\"weight\":1.0,\"bucket\":\"rare\",\"level\":\"5-20\",\"condition\":{\"biomes\":[\"minecraft:plains\"]}}]}}]");return data;
    }
    private static EditBox[] fields(Screen s){return s.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toArray(EditBox[]::new);}
    private static boolean has(Screen s,String text){return s.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().equals(text));}
    private static void press(Screen s,String text){s.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(text)).findFirst().orElseThrow(()->new IllegalStateException("Missing Pokemon editor control "+text)).onPress();}
    private static void bounds(Screen s,int[] size){for(var c:s.children())if(c instanceof AbstractWidget w)require(w.getX()>=0&&w.getX()+w.getWidth()<=size[0],"Pokemon editor control exceeds narrow screen");}
    private static void draw(Minecraft mc,Screen s,int[] size){s.init(mc,size[0],size[1]);GuiGraphics g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());s.render(g,-1000,-1000,0);g.flush();}
}
