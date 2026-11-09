package dev.unyielding.quests.settings;

import com.google.gson.*;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.nio.file.Path;
import java.util.*;

/** Server-authoritative, searchable access to effective Cobblemon data and persistent overrides. */
public final class PokemonEditor {
    public static final int PAGE_SIZE = 30;
    private static final int PART_SIZE = 24_000;
    private static PokemonEdits.State state = PokemonEdits.empty();
    private static Map<String,JsonObject> baseSpecies = Map.of(), baseSpawns = Map.of();
    private static MinecraftServer server;
    private static String unavailable = "Pokemon data is still loading.";
    private static final Map<UUID,Upload> uploads = new HashMap<>();
    private static final class Upload {
        final String operation,id; final long revision,started; final int total; final StringBuilder json = new StringBuilder(); int next;
        Upload(String operation,String id,long revision,int total) {
            this.operation=operation;this.id=id;this.revision=revision;this.total=total;started=System.nanoTime();
        }
    }
    public record Action(String operation,String id,long revision,String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:pokemon_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(100),Action::operation,ByteBufCodecs.stringUtf8(200),Action::id,
                ByteBufCodecs.VAR_LONG,Action::revision,ByteBufCodecs.stringUtf8(26000),Action::json,Action::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Sync(CompoundTag data,boolean open) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:pokemon_editor"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG,Sync::data,ByteBufCodecs.BOOL,Sync::open,Sync::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("1");
        r.playToServer(Action.TYPE,Action.CODEC,(p,c)->handle((ServerPlayer)c.player(),p));
        r.playToClient(Sync.TYPE,Sync.CODEC,(p,c)->dev.unyielding.quests.client.PokemonEditorScreen.receive(p));
    }
    /** Keeps each client-to-server payload below Minecraft's 32 KB limit, including UTF-8. */
    public static void send(Action action) {
        if(action.json().length()<=6000){Net.sendToServer(action);return;}
        // 6000 UTF-16 units are at most 24 KB, even for non-ASCII text.
        List<String> parts=new ArrayList<>();
        for(int start=0;start<action.json().length();) {
            int end=Math.min(start+6000,action.json().length());
            if(end<action.json().length()&&Character.isHighSurrogate(action.json().charAt(end-1)))end--;
            parts.add(action.json().substring(start,end));start=end;
        }
        for(int i=0;i<parts.size();i++)Net.sendToServer(new Action("part:"+action.operation()+":"+i+":"+parts.size(),action.id(),action.revision(),parts.get(i)));
    }
    public static Path configPath(){return FMLPaths.CONFIGDIR.get().resolve("unyielding/pokemon/overrides.json");}
    public static long revision(){return state.revision();}
    public static void start(MinecraftServer current) {
        stop();server=current;
        try {
            baseSpecies=PokemonRuntime.captureSpecies(current);baseSpawns=PokemonRuntime.captureSpawns(current);
            if(baseSpecies.isEmpty())throw new IllegalStateException("Cobblemon is not loaded.");
            state=PokemonEdits.read(configPath());
            for(var entry:state.species().entrySet()) {
                if(!baseSpecies.containsKey(entry.getKey()))throw new IllegalArgumentException("An overridden species is no longer installed: "+entry.getKey());
                PokemonRuntime.validateSpecies(entry.getKey(),entry.getValue());
            }
            PokemonRuntime.validateSpawns(state.spawns());
            if(!state.species().isEmpty()||!state.spawns().isEmpty())PokemonRuntime.apply(current,species(),spawns());
            unavailable="";
        } catch(Throwable failure) {
            unavailable="Pokemon editor unavailable: "+Objects.toString(failure.getMessage(),"could not load data.");
            UnyieldingQuests.LOGGER.error("Could not initialize Pokemon editor; existing config retained",failure);
        }
    }
    public static void stop(){server=null;state=PokemonEdits.empty();baseSpecies=Map.of();baseSpawns=Map.of();uploads.clear();unavailable="Pokemon data is still loading.";}
    public static void logout(ServerPlayer p){uploads.remove(p.getUUID());}
    public static Map<String,JsonObject> species(){Map<String,JsonObject> result=PokemonEdits.copy(baseSpecies);result.putAll(PokemonEdits.copy(state.species()));return result;}
    public static Map<String,JsonObject> spawns(){Map<String,JsonObject> result=PokemonEdits.copy(baseSpawns);result.putAll(PokemonEdits.copy(state.spawns()));return result;}
    public static void handle(ServerPlayer p,Action action) {
        if(!p.hasPermissions(2))return;
        try {
            if(!unavailable.isBlank())throw new IllegalStateException(unavailable);
            if(p.server!=server)throw new IllegalStateException("This server is not ready.");
            if(action.operation().startsWith("part:")){part(p,action);return;}
            if(action.operation().equals("search")){reply(p,search(action.id(),action.revision()),true);return;}
            if(action.operation().equals("get")){reply(p,detail(action.id()),true);return;}
            if(action.revision()!=state.revision())throw new IllegalArgumentException("Settings changed. Reopen this Pokemon before saving your draft.");
            String id=PokemonEdits.identifier(action.id());
            if(!baseSpecies.containsKey(id))throw new IllegalArgumentException("Unknown Pokemon.");
            Map<String,JsonObject> nextSpecies=PokemonEdits.copy(state.species()),nextSpawns=PokemonEdits.copy(state.spawns());
            switch(action.operation()) {
                case "save_species" -> {
                    JsonObject json=PokemonEdits.parse(action.json()).getAsJsonObject();
                    JsonObject original=baseSpecies.get(id);
                    for(String identity:List.of("name","nationalPokedexNumber"))if(!Objects.equals(original.get(identity),json.get(identity)))
                        throw new IllegalArgumentException("Species identity and Pokedex number cannot be changed here.");
                    PokemonRuntime.validateSpecies(id,json);
                    if(json.equals(original))nextSpecies.remove(id);else nextSpecies.put(id,json);
                }
                case "save_spawns" -> saveSpawnDraft(id,action.json(),nextSpawns);
                case "reset_species" -> nextSpecies.remove(id);
                case "reset_spawns" -> resetSpawnDraft(id,nextSpawns);
                case "reload" -> {
                    var disk=PokemonEdits.read(configPath());
                    nextSpecies=PokemonEdits.copy(disk.species());nextSpawns=PokemonEdits.copy(disk.spawns());
                    for(var entry:nextSpecies.entrySet()) {
                        if(!baseSpecies.containsKey(entry.getKey()))throw new IllegalArgumentException("Unknown configured Pokemon: "+entry.getKey());
                        PokemonRuntime.validateSpecies(entry.getKey(),entry.getValue());
                    }
                    PokemonRuntime.validateSpawns(nextSpawns);
                }
                default -> throw new IllegalArgumentException("Unknown Pokemon editor action.");
            }
            commit(p,nextSpecies,nextSpawns);
            CompoundTag data=detail(id);data.putString("message","Saved on server. Spawn rules and species data are active; existing Pokemon keep their individual abilities and moves.");
            reply(p,data,false);
        } catch(Throwable failure) {
            CompoundTag data=new CompoundTag();data.putString("view","error");data.putBoolean("error",true);
            data.putLong("revision",state.revision());data.putString("id",action.id());
            String message=Objects.toString(failure.getMessage(),"Invalid Pokemon data.");
            data.putString("message","Not saved: "+message.substring(0,Math.min(400,message.length())));reply(p,data,false);
            if(failure instanceof IllegalArgumentException)UnyieldingQuests.LOGGER.warn("Rejected Pokemon editor action {} for {}: {}",action.operation(),action.id(),message);
            else UnyieldingQuests.LOGGER.warn("Pokemon editor action failed",failure);
        }
    }
    private static void part(ServerPlayer p,Action action) {
        String[] parts=action.operation().split(":");
        if(parts.length!=4||!Set.of("save_species","save_spawns").contains(parts[1]))throw new IllegalArgumentException("Invalid upload.");
        int index=Integer.parseInt(parts[2]),total=Integer.parseInt(parts[3]);
        if(index<0||total<1||total>160||action.json().length()>PART_SIZE)throw new IllegalArgumentException("Upload is too large.");
        uploads.entrySet().removeIf(entry->System.nanoTime()-entry.getValue().started>30_000_000_000L);
        if(index==0)uploads.put(p.getUUID(),new Upload(parts[1],action.id(),action.revision(),total));
        Upload upload=uploads.get(p.getUUID());
        if(upload==null||upload.next!=index||upload.total!=total||!upload.id.equals(action.id())||upload.revision!=action.revision()||!upload.operation.equals(parts[1]))
            throw new IllegalArgumentException("Upload interrupted; save again.");
        upload.json.append(action.json());upload.next++;
        if(upload.json.length()>PokemonEdits.MAX_DOCUMENT){uploads.remove(p.getUUID());throw new IllegalArgumentException("Upload is too large.");}
        if(upload.next==total){uploads.remove(p.getUUID());handle(p,new Action(upload.operation,upload.id,upload.revision,upload.json.toString()));}
    }
    public static CompoundTag search(String query,long page) {
        query=query.trim().toLowerCase(Locale.ROOT);
        if(query.length()>120)throw new IllegalArgumentException("Search is too long.");
        String q=query;
        List<String> matches=baseSpecies.keySet().stream().filter(id->id.contains(q)||name(id).toLowerCase(Locale.ROOT).contains(q)).sorted(Comparator.comparing(PokemonEditor::name).thenComparing(x->x)).toList();
        int index=(int)Math.min(Math.max(0,page),Math.max(0,(matches.size()-1)/PAGE_SIZE));
        CompoundTag data=new CompoundTag();data.putString("view","search");data.putString("query",query);data.putInt("page",index);data.putInt("page_size",PAGE_SIZE);data.putInt("total",matches.size());data.putLong("revision",state.revision());
        ListTag list=new ListTag();for(int i=index*PAGE_SIZE;i<Math.min(matches.size(),(index+1)*PAGE_SIZE);i++) {
            String id=matches.get(i);CompoundTag row=new CompoundTag();row.putString("id",id);row.putString("name",name(id));list.add(row);
        }
        data.put("results",list);return data;
    }
    private static String name(String id){JsonObject json=baseSpecies.get(id);return json!=null&&json.has("name")?json.get("name").getAsString():id;}
    public static CompoundTag detail(String id) {
        PokemonEdits.identifier(id);
        if(!baseSpecies.containsKey(id))throw new IllegalArgumentException("Unknown Pokemon.");
        CompoundTag data=new CompoundTag();data.putString("view","species");data.putString("id",id);data.putString("name",name(id));data.putLong("revision",state.revision());
        data.putString("species_json",PokemonEdits.JSON.toJson(state.species().getOrDefault(id,baseSpecies.get(id))));
        JsonArray files=new JsonArray();spawns().forEach((key,value)->{if(contains(value,id)){
            JsonObject file=new JsonObject();file.addProperty("id",key);file.add("data",value.deepCopy());files.add(file);
        }});
        data.putString("spawns_json",PokemonEdits.JSON.toJson(files));data.putBoolean("overridden",state.species().containsKey(id));
        data.putBoolean("spawn_overridden",state.spawns().entrySet().stream().anyMatch(entry->contains(entry.getValue(),id)||contains(baseSpawns.get(entry.getKey()),id)));
        return data;
    }
    private static boolean contains(JsonObject file,String id) {
        if(file==null||!file.has("spawns")||!file.get("spawns").isJsonArray())return false;
        for(JsonElement element:file.getAsJsonArray("spawns"))if(element.isJsonObject()&&matches(element.getAsJsonObject(),id))return true;
        return false;
    }
    private static boolean matches(JsonObject spawn,String id) {
        if(!spawn.has("pokemon")||!spawn.get("pokemon").isJsonPrimitive())return false;
        String text=PokemonEdits.speciesToken(spawn.get("pokemon").getAsString());
        return text.equals(id)||text.equals(id.substring(id.indexOf(':')+1))&&id.startsWith("cobblemon:");
    }
    private static void saveSpawnDraft(String id,String json,Map<String,JsonObject> target) {
        JsonArray files=PokemonEdits.parse(json).getAsJsonArray();
        if(files.size()>1000)throw new IllegalArgumentException("Too many spawn files.");
        Map<String,JsonObject> effective=spawns(),draft=new TreeMap<>();
        for(JsonElement element:files) {
            JsonObject wrapper=element.getAsJsonObject();String key=PokemonEdits.identifier(wrapper.get("id").getAsString());JsonObject value=wrapper.getAsJsonObject("data");
            if(value==null||draft.put(key,value)!=null)throw new IllegalArgumentException("Duplicate or missing spawn file.");
            if(effective.containsKey(key)&&!contains(effective.get(key),id))throw new IllegalArgumentException("This file belongs to another Pokemon. Open its editor first.");
            if(!effective.containsKey(key)&&!key.startsWith("unyieldingquests:editor/"))throw new IllegalArgumentException("New spawn files must use unyieldingquests:editor/ names.");
        }
        // Omitting a file removes just this species' entries and retains shared spawns.
        effective.forEach((key,value)->{if(contains(value,id)&&!draft.containsKey(key)){
            JsonObject stripped=value.deepCopy();JsonArray rows=new JsonArray();
            for(JsonElement entry:value.getAsJsonArray("spawns"))if(!matches(entry.getAsJsonObject(),id))rows.add(entry.deepCopy());
            stripped.add("spawns",rows);target.put(key,stripped);
        }});
        draft.forEach((key,value)->{if(value.equals(baseSpawns.get(key)))target.remove(key);else target.put(key,value);});
        PokemonRuntime.validateSpawns(target);
    }
    /** Restore only this species' rows, retaining unrelated edits in shared files. */
    private static void resetSpawnDraft(String id,Map<String,JsonObject> target) {
        Map<String,JsonObject> effective=spawns();
        Set<String> related=new TreeSet<>();
        effective.forEach((key,value)->{if(contains(value,id))related.add(key);});
        baseSpawns.forEach((key,value)->{if(contains(value,id))related.add(key);});
        String generated="unyieldingquests:editor/"+id.replace(':','/');
        if(target.containsKey(generated))related.add(generated);
        for(String key:related) {
            JsonObject current=effective.get(key),baseline=baseSpawns.get(key);
            if(current==null){target.remove(key);continue;}
            boolean shared=hasOtherSpawns(current,id)||hasOtherSpawns(baseline,id);
            if(!shared){target.remove(key);continue;}
            JsonObject restored=current.deepCopy();JsonArray rows=new JsonArray();
            for(JsonElement row:current.getAsJsonArray("spawns"))
                if(!matches(row.getAsJsonObject(),id))rows.add(row.deepCopy());
            if(baseline!=null)for(JsonElement row:baseline.getAsJsonArray("spawns"))
                if(matches(row.getAsJsonObject(),id))rows.add(row.deepCopy());
            restored.add("spawns",rows);
            if(baseline==null&&rows.isEmpty()||sameSpawnFile(restored,baseline))target.remove(key);
            else target.put(key,restored);
        }
    }
    private static boolean hasOtherSpawns(JsonObject file,String id) {
        if(file==null||!file.has("spawns"))return false;
        for(JsonElement row:file.getAsJsonArray("spawns"))if(!matches(row.getAsJsonObject(),id))return true;
        return false;
    }
    private static boolean sameSpawnFile(JsonObject first,JsonObject second) {
        if(second==null)return false;
        JsonObject left=first.deepCopy(),right=second.deepCopy();
        JsonArray leftRows=left.remove("spawns").getAsJsonArray(),rightRows=right.remove("spawns").getAsJsonArray();
        if(!left.equals(right)||leftRows.size()!=rightRows.size())return false;
        List<JsonElement> remaining=new ArrayList<>();rightRows.forEach(remaining::add);
        for(JsonElement row:leftRows)if(!remaining.remove(row))return false;
        return remaining.isEmpty();
    }
    private static void commit(ServerPlayer p,Map<String,JsonObject> speciesOverrides,Map<String,JsonObject> spawnOverrides)throws Exception {
        PokemonEdits.State next=new PokemonEdits.State(Math.addExact(state.revision(),1),speciesOverrides,spawnOverrides);
        Map<String,JsonObject> species=PokemonEdits.copy(baseSpecies),spawns=PokemonEdits.copy(baseSpawns);
        species.putAll(speciesOverrides);spawns.putAll(spawnOverrides);
        // Apply validates all candidates before mutation and can reject unsafe active battles.
        PokemonRuntime.apply(server,species,spawns);
        try{PokemonEdits.write(configPath(),next);state=next;}
        catch(Exception failure){
            try{PokemonRuntime.apply(server,species(),spawns());}catch(Throwable rollback){failure.addSuppressed(rollback);throw new IllegalStateException("Saving failed and live restoration failed. Restart with the preserved config.",failure);}
            throw failure;
        }
        UnyieldingQuests.LOGGER.info("{} saved Pokemon data revision {}",p.getGameProfile().getName(),state.revision());
    }
    public static final int SNAPSHOT_MAX_BYTES = 8 * 1024 * 1024;
    public static final int SNAPSHOT_PART_CHARS = 16_000;
    public static final int SNAPSHOT_MAX_PARTS = 1024;

    /** Bounded packets also avoid NBT's 65,535-byte modified-UTF limit for a single string. */
    public static List<Sync> snapshotPackets(CompoundTag data,boolean open) {
        String text=data.toString();
        if(text.length()>SNAPSHOT_MAX_BYTES)return List.of(snapshotTooLarge(data));
        int bytes=utf8Size(text);
        if(bytes>SNAPSHOT_MAX_BYTES)return List.of(snapshotTooLarge(data));
        if(bytes<200_000&&!largeString(data))return List.of(new Sync(data,open));
        List<String> parts=new ArrayList<>();
        for(int start=0;start<text.length();) {
            int end=Math.min(start+SNAPSHOT_PART_CHARS,text.length());
            if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))end--;
            parts.add(text.substring(start,end));start=end;
        }
        String token=UUID.randomUUID().toString();
        List<Sync> packets=new ArrayList<>(parts.size());
        for(int i=0;i<parts.size();i++) {
            CompoundTag part=new CompoundTag();part.putString("view","transfer");part.putString("token",token);
            part.putInt("index",i);part.putInt("total",parts.size());part.putInt("bytes",bytes);
            part.putString("id",data.getString("id"));part.putLong("revision",data.getLong("revision"));
            part.putString("text",parts.get(i));packets.add(new Sync(part,open));
        }
        return packets;
    }
    private static Sync snapshotTooLarge(CompoundTag original) {
        CompoundTag error=new CompoundTag();error.putString("view","error");error.putBoolean("error",true);
        error.putString("id",original.getString("id"));error.putLong("revision",original.getLong("revision"));
        error.putString("message","This Pokemon's data is too large to display. Edit its server configuration file.");
        return new Sync(error,false);
    }
    private static boolean largeString(Tag value) {
        if(value instanceof StringTag text)return text.getAsString().length()>SNAPSHOT_PART_CHARS;
        if(value instanceof CompoundTag compound) {
            for(String key:compound.getAllKeys())if(key.length()>SNAPSHOT_PART_CHARS||largeString(compound.get(key)))return true;
        } else if(value instanceof ListTag list)for(Tag child:list)if(largeString(child))return true;
        return false;
    }
    /** Conservative for unpaired surrogates; avoids allocating a second multi-megabyte byte array. */
    public static int utf8Size(String text) {
        int bytes=0;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(c<0x80)bytes++;
            else if(c<0x800)bytes+=2;
            else if(Character.isHighSurrogate(c)&&i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))){bytes+=4;i++;}
            else bytes+=3;
            if(bytes>SNAPSHOT_MAX_BYTES)return bytes;
        }
        return bytes;
    }
    private static void reply(ServerPlayer p,CompoundTag data,boolean open) {
        for(Sync packet:snapshotPackets(data,open))Net.sendTo(p,packet);
    }
}
