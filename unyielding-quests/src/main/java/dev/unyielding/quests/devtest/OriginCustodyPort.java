package dev.unyielding.quests.devtest;

import com.google.gson.*;
import dev.unyielding.quests.jail.JailEscrowJournal;
import dev.unyielding.quests.jail.JailOriginTransaction;
import dev.unyielding.quests.jail.JailVanillaCustody;
import java.io.*;
import java.lang.reflect.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Exact-version, audited fixture-only native port. No production entry point. */
final class OriginCustodyPort implements JailOriginTransaction.Port {
    static final String DATA="com.cyberday1.neoorigins.attachment.PlayerOriginData", ORIGINS="com.cyberday1.neoorigins.data.OriginDataManager",
            LAYERS="com.cyberday1.neoorigins.data.LayerDataManager", CLAIMS="com.cyberday1.neoorigins.data.OriginClaimsData",
            SERVICE="com.cyberday1.neoorigins.service.ActiveOriginService", NETWORK="com.cyberday1.neoorigins.network.NeoOriginsNetwork";
    static final String RECEIPT="uq_disposable_origin_receipt", ATTACHMENT="neoorigins:origin_data";
    static final ResourceLocation ALPHA=id("uq_bundle_smoke:origin_alpha"),BETA=id("uq_bundle_smoke:origin_beta"),POWER=id("uq_bundle_smoke:origin_luck"),KEPT_CLASS=id("uq_bundle_smoke:kept_class");
    static final String DEPS="neoorigins=2.2.27;origins_classes_ex=1.0.0;origins_fantasy=1.0.0;origins_furries=1.0.0";
    final ServerPlayer player; final JailVanillaCustody.InventoryPort inventory;
    ResourceLocation layer,original,human; CompoundTag intent;
    enum SaveBoundary { BEFORE_PLAYER, AFTER_PLAYER, AFTER_CLAIMS, AFTER_FORCE }
    @FunctionalInterface interface SaveFault { void at(SaveBoundary at)throws IOException; }
    SaveFault saveFault=at->{};
    OriginCustodyPort(ServerPlayer player,JailVanillaCustody.InventoryPort inventory,byte[] existing)throws IOException {
        this.player=player;this.inventory=inventory;
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))
                ||!(Boolean.getBoolean("uq.originCustody")&&player.server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_origin_smoke")
                ||Boolean.getBoolean("uq.custodyInterface")&&player.server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_interface_smoke")
                ||Boolean.getBoolean("uq.expandedJail")&&player.server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_expanded_smoke")
                ||Boolean.getBoolean("uq.certifiedJail")&&player.server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_certified_smoke")))throw new IOException("Origin mutation fixture forbidden here");
        if(existing==null)resolve();else{intent=decode(existing);layer=id(intent.getString("layer"));original=id(intent.getString("original"));human=id(intent.getString("human"));}
    }
    static ResourceLocation id(String value){return ResourceLocation.parse(value);}
    static Class<?> type(String name)throws IOException{try{return Class.forName(name);}catch(ReflectiveOperationException|LinkageError e){throw new IOException("Missing origin API "+name,e);}}
    static Object call(String api,Object target,String method,Class<?>[] types,Object...args)throws IOException {
        try{return type(api).getMethod(method,types).invoke(target,args);}catch(ReflectiveOperationException|RuntimeException e){throw new IOException("Origin API failed: "+method,e);}
    }
    static Object call(String api,Object target,String method)throws IOException{return call(api,target,method,new Class<?>[0]);}
    static Object singleton(String api)throws IOException{try{return type(api).getField("INSTANCE").get(null);}catch(ReflectiveOperationException e){throw new IOException(e);}}
    Object data()throws IOException {
        Object raw=call("com.cyberday1.neoorigins.attachment.OriginAttachments",null,"originData");
        if(!(raw instanceof AttachmentType<?> attachment))throw new IOException("Unknown origin attachment");
        Object data=player.getExistingDataOrNull(attachment);if(data==null)throw new IOException("Origin data missing");return data;
    }
    @SuppressWarnings("unchecked") Map<ResourceLocation,ResourceLocation> selections()throws IOException{return Map.copyOf((Map<ResourceLocation,ResourceLocation>)call(DATA,data(),"getOrigins"));}
    static CompoundTag choices(Map<ResourceLocation,ResourceLocation> choices){CompoundTag tag=new CompoundTag();choices.forEach((key,value)->tag.putString(key.toString(),value.toString()));return tag;}
    private void versions()throws IOException {
        for(String spec:DEPS.split(";")){String[] p=spec.split("=",2);if(!ModList.get().getModContainerById(p[0]).map(c->p[1].equals(c.getModInfo().getVersion().toString())).orElse(false))throw new IOException("Unsupported origin dependency "+p[0]);}
        if(ModList.get().isLoaded("kubejs"))throw new IOException("Unaudited origin scripting hooks");
        if(!player.server.isSameThread())throw new IOException("Origin operation off server thread");
    }
    @SuppressWarnings("unchecked") private void resolve()throws IOException {
        versions();Object manager=singleton(ORIGINS);
        Map<ResourceLocation,?> definitions=(Map<ResourceLocation,?>)call(ORIGINS,manager,"getOrigins");List<ResourceLocation> humans=new ArrayList<>();
        for(ResourceLocation key:definitions.keySet()){
            JsonObject raw=(JsonObject)call(ORIGINS,manager,"getRawOriginJson",new Class<?>[]{ResourceLocation.class},key);
            if(raw!=null&&raw.has("name")){
                JsonElement name=raw.get("name");
                String translation=name.isJsonPrimitive()?name.getAsString():name.isJsonObject()&&name.getAsJsonObject().has("translate")?name.getAsJsonObject().get("translate").getAsString():"";
                if(translation.equals("origins.neoorigins.human.name"))humans.add(key);
            }
        }
        if(humans.size()!=1)throw new IOException("Human registry identity missing or ambiguous");human=humans.getFirst();
        List<ResourceLocation> candidates=new ArrayList<>();for(var entry:selections().entrySet())if(Set.of(ALPHA,BETA).contains(entry.getValue()))candidates.add(entry.getKey());
        if(candidates.size()!=1)throw new IOException("No unique audited main-layer selection");layer=candidates.getFirst();original=selections().get(layer);
        available();
    }
    void available()throws IOException {
        Object l=call(LAYERS,singleton(LAYERS),"getLayer",new Class<?>[]{ResourceLocation.class},layer);
        if(l==null||!Boolean.TRUE.equals(call("com.cyberday1.neoorigins.api.origin.OriginLayer",l,"enabled")))throw new IOException("Origin layer unavailable");
        List<?> available=(List<?>)call("com.cyberday1.neoorigins.api.origin.OriginLayer",l,"getAvailableOriginIds",new Class<?>[]{Map.class},selections());
        if(!available.contains(original)||!available.contains(human))throw new IOException("Selected origin or Human unavailable in this actual layer");
    }
    Object origin(ResourceLocation origin)throws IOException{return call(ORIGINS,singleton(ORIGINS),"getOrigin",new Class<?>[]{ResourceLocation.class},origin);}
    @SuppressWarnings("unchecked") List<ResourceLocation> powers(ResourceLocation origin)throws IOException {
        Object definition=origin(origin);if(definition==null)throw new IOException("Origin definition missing: "+origin);
        return (List<ResourceLocation>)call("com.cyberday1.neoorigins.api.origin.Origin",definition,"powersForTier",new Class<?>[]{int.class},0);
    }
    private static JsonElement canonicalJson(JsonElement value){
        if(value.isJsonObject()){JsonObject sorted=new JsonObject();new TreeSet<>(value.getAsJsonObject().keySet()).forEach(k->sorted.add(k,canonicalJson(value.getAsJsonObject().get(k))));return sorted;}
        if(value.isJsonArray()){JsonArray result=new JsonArray();value.getAsJsonArray().forEach(v->result.add(canonicalJson(v)));return result;}return value.deepCopy();
    }
    private String audit()throws IOException {
        versions();available();Object current=data();
        if((int)call(DATA,current,"getEvolutionTier")!=0)throw new IOException("Higher-tier origin effects not audited");
        if(!((Set<?>)call(DATA,current,"getDynamicGrantedPowers")).isEmpty()||!((Set<?>)call(DATA,current,"getGlobalGrantedPowers")).isEmpty())throw new IOException("Dynamic/global origin powers not audited");
        for(String name:new String[]{"isPendingOrbCommit","isPendingLayerPickerCommit","isPendingAdminReselect"})if(Boolean.TRUE.equals(call(DATA,current,name)))throw new IOException("Pending origin picker/commit");
        try{Field cooldowns=type(DATA).getDeclaredField("activeCooldowns");cooldowns.setAccessible(true);if(!((Map<?,?>)cooldowns.get(current)).isEmpty())throw new IOException("Native cooldown map is not persisted by this version; unsupported");}
        catch(ReflectiveOperationException e){throw new IOException("Cannot audit native cooldown state",e);}
        if(!powers(human).isEmpty()||!powers(original).equals(List.of(POWER)))throw new IOException("Unaudited Human/original power set");
        JsonObject manifest=new JsonObject();Set<ResourceLocation> ids=new HashSet<>(selections().values());ids.add(human);ids.add(original);
        for(var entry:selections().entrySet())if(!entry.getKey().equals(layer)&&!powers(entry.getValue()).isEmpty())throw new IOException("Unaudited unrelated-layer powers");
        for(ResourceLocation key:ids){JsonObject raw=(JsonObject)call(ORIGINS,singleton(ORIGINS),"getRawOriginJson",new Class<?>[]{ResourceLocation.class},key);if(raw==null)throw new IOException("Origin audit JSON missing");manifest.add(key.toString(),canonicalJson(raw));}
        Object manager=singleton("com.cyberday1.neoorigins.data.PowerDataManager");
        JsonObject power=(JsonObject)call("com.cyberday1.neoorigins.data.PowerDataManager",manager,"getRawPowerJson",new Class<?>[]{ResourceLocation.class},POWER);
        if(power==null||!power.get("type").getAsString().equals("neoorigins:attribute_modifier")||!power.get("attribute").getAsString().equals("minecraft:luck")
                ||power.get("amount").getAsDouble()!=1||!power.get("operation").getAsString().equals("add_value")
                ||!Set.of("type","attribute","amount","operation","id").containsAll(power.keySet()))throw new IOException("Unknown native fixture callback/effect definition");
        Object holder=call("com.cyberday1.neoorigins.data.PowerDataManager",manager,"getPower",new Class<?>[]{ResourceLocation.class},POWER);
        Object implementation=call("com.cyberday1.neoorigins.api.power.PowerHolder",holder,"type");
        if(implementation.getClass()!=type("com.cyberday1.neoorigins.power.builtin.AttributeModifierPower"))throw new IOException("Native power implementation replaced");
        manifest.add("power",canonicalJson(power));String hash=HexFormat.of().formatHex(JailOriginTransaction.hash(canonicalJson(manifest).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if(intent!=null&&!hash.equals(intent.getString("definitions")))throw new IOException("Origin definitions changed since custody");return hash;
    }
    private Path claimsFile(){return player.server.getWorldPath(LevelResource.ROOT).resolve("data/neoorigins_origin_claims.dat");}
    Object claims()throws IOException {if(!Files.isRegularFile(claimsFile()))throw new IOException("Origin claim history missing");return call(CLAIMS,null,"get",new Class<?>[]{net.minecraft.server.MinecraftServer.class},player.server);}
    UUID owner(Object claims,ResourceLocation origin)throws IOException{return (UUID)call(CLAIMS,claims,"getOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},layer,origin);}
    boolean unique()throws IOException{return Boolean.TRUE.equals(call("com.cyberday1.neoorigins.config.AdminConfig",null,"isUniqueLayer",new Class<?>[]{ResourceLocation.class},layer));}
    CompoundTag claimState(Object claims)throws IOException {CompoundTag result=new CompoundTag();result.putBoolean("unique",unique());UUID a=owner(claims,original),h=owner(claims,human);if(a!=null)result.putUUID("original",a);if(h!=null)result.putUUID("human",h);return result;}
    @SuppressWarnings("unchecked") private CompoundTag allClaims(Object claims)throws IOException {
        CompoundTag result=new CompoundTag();Map<ResourceLocation,Map<ResourceLocation,UUID>> all=(Map<ResourceLocation,Map<ResourceLocation,UUID>>)call(CLAIMS,claims,"view");
        all.forEach((l,entries)->{CompoundTag nested=new CompoundTag();entries.forEach((o,owner)->nested.putUUID(o.toString(),owner));if(!nested.isEmpty())result.put(l.toString(),nested);});return result;
    }
    private CompoundTag expectedAllClaims(boolean temporary)throws IOException {
        CompoundTag all=intent.getCompound("all_claims_before").copy();
        if(intent.getCompound("claims_before").getBoolean("unique")){CompoundTag selected=all.getCompound(layer.toString()).copy();selected.putUUID(original.toString(),owner());if(temporary||intent.getBoolean("human_preowned"))selected.putUUID(human.toString(),owner());else selected.remove(human.toString());all.put(layer.toString(),selected);}return all;
    }
    static boolean owned(CompoundTag tag,String key,UUID owner){return tag.hasUUID(key)&&owner.equals(tag.getUUID(key));}
    private CompoundTag expectedClaims(boolean temporary)throws IOException {
        CompoundTag initial=intent.getCompound("claims_before"),result=initial.copy();
        if(initial.getBoolean("unique")) {result.putUUID("original",owner());if(temporary||intent.getBoolean("human_preowned"))result.putUUID("human",owner());else result.remove("human");}
        return result;
    }
    private CompoundTag receipt(UUID id,JailOriginTransaction.Receipt phase){CompoundTag receipt=new CompoundTag();receipt.putUUID("transaction",id);receipt.putString("phase",phase.name());receipt.putByteArray("custody_digest",intent.getByteArray("custody_digest"));receipt.putString("layer",layer.toString());receipt.putString("original",original.toString());receipt.putString("human",human.toString());receipt.putString("definitions",intent.getString("definitions"));return receipt;}
    private static CompoundTag attachment(CompoundTag playerTag)throws IOException {
        CompoundTag attachments=playerTag.getCompound("neoforge:attachments");if(!attachments.contains(ATTACHMENT,10))throw new IOException("Serialized origin attachment missing");return attachments.getCompound(ATTACHMENT).copy();
    }
    private static ListTag attributes(CompoundTag playerTag)throws IOException {
        if(!playerTag.contains("attributes",9))throw new IOException("Player attribute state missing");
        List<CompoundTag> entries=new ArrayList<>();
        for(Tag raw:playerTag.getList("attributes",10)){
            CompoundTag tag=((CompoundTag)raw).copy();if(!tag.contains("id",8))throw new IOException("Unknown attribute identity");
            if(tag.contains("modifiers")){List<CompoundTag> modifiers=new ArrayList<>();for(Tag m:tag.getList("modifiers",10))modifiers.add(((CompoundTag)m).copy());modifiers.sort(Comparator.comparing(m->m.getString("id")));ListTag list=new ListTag();list.addAll(modifiers);tag.put("modifiers",list);}
            entries.add(tag);
        }
        entries.sort(Comparator.comparing(tag->tag.getString("id")));ListTag list=new ListTag();list.addAll(entries);return list;
    }
    private static CompoundTag accessoryState(CompoundTag playerTag)throws IOException {
        CompoundTag store=playerTag.getCompound("neoforge:attachments").getCompound("accessories:inventory_holder");
        if(!store.contains("accessories_containers",10))throw new IOException("Accessory store missing from native player save");
        return store.getCompound("accessories_containers").copy();
    }
    private static boolean accessoriesEmpty(CompoundTag stores){for(String name:stores.getAllKeys()){CompoundTag s=stores.getCompound(name);if(!s.getList("items",10).isEmpty()||!s.getList("cosmetics",10).isEmpty())return false;}return true;}
    private void effects()throws IOException {
        ResourceLocation current=selections().get(layer);boolean active=original.equals(current);
        if(!active&&!human.equals(current))throw new IOException("Origin selection changed outside custody");
        var luck=player.getAttribute(Attributes.LUCK);if(luck==null)throw new IOException("Luck attribute unavailable");
        var modifiers=luck.getModifiers();
        if(modifiers.size()!=(active?1:0)||Math.abs(luck.getValue()-luck.getBaseValue()-(active?1:0))>0.000001)throw new IOException("Native origin effects not verified");
        if(active){var m=modifiers.iterator().next();if(m.amount()!=1||m.operation()!=AttributeModifier.Operation.ADD_VALUE||(intent!=null&&!m.id().toString().equals(intent.getString("modifier_id"))))throw new IOException("Unexpected native origin modifier");}
    }
    private JailOriginTransaction.View view(CompoundTag saved,Object claims,JailVanillaCustody.View itemView)throws IOException {
        CompoundTag body=new CompoundTag();body.putString("dependencies",DEPS);body.putString("definitions",audit());body.put("origin_data",attachment(saved));body.put("claims",claimState(claims));
        body.put("all_claims",allClaims(claims));body.putByteArray("inventory",itemView.inventory());body.put("accessories",accessoryState(saved));body.put("attributes",attributes(saved));body.putFloat("health",saved.getFloat("Health"));
        CompoundTag r=saved.getCompound("NeoForgeData").getCompound(RECEIPT).copy();body.put("receipt",r);
        CompoundTag otherPersistent=saved.getCompound("NeoForgeData").copy();otherPersistent.remove(RECEIPT);body.put("other_persistent",otherPersistent);
        UUID tx=r.hasUUID("transaction")?r.getUUID("transaction"):null;JailOriginTransaction.Receipt phase=JailOriginTransaction.Receipt.NONE;
        if(!r.isEmpty())try{phase=JailOriginTransaction.Receipt.valueOf(r.getString("phase"));}catch(IllegalArgumentException unsafe){throw new IOException("Unknown origin receipt",unsafe);}
        return new JailOriginTransaction.View(encode(body),tx,phase);
    }
    @Override public UUID owner(){return player.getUUID();}@Override public boolean online(){return inventory.online();}
    @Override public JailOriginTransaction.View current()throws IOException {audit();effects();return view(player.saveWithoutId(new CompoundTag()),claims(),inventory.current());}
    @Override public JailOriginTransaction.View durable()throws IOException {
        CompoundTag saved;try(var input=Files.newInputStream(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner()+".dat"))){saved=NbtIo.readCompressed(input,NbtAccounter.create(16L*1024*1024));}
        CompoundTag claimsTag;try(var input=Files.newInputStream(claimsFile())){claimsTag=NbtIo.readCompressed(input,NbtAccounter.create(16L*1024*1024));}
        if(!saved.hasUUID("UUID")||!owner().equals(saved.getUUID("UUID"))||!claimsTag.contains("data",10))throw new IOException("Malformed origin persistence");
        Object savedClaims=call(CLAIMS,null,"load",new Class<?>[]{CompoundTag.class,HolderLookup.Provider.class},claimsTag.getCompound("data"),player.registryAccess());return view(saved,savedClaims,inventory.durable());
    }
    @Override public void validateCustody(JailEscrowJournal.Entry custody,boolean releaseStarted)throws IOException {
        if(custody.owner().equals(owner())) {
            var live=inventory.current();var saved=inventory.durable();
            if(!live.empty()||!saved.empty()||!custody.id().equals(live.removedBy())||!custody.id().equals(saved.removedBy())||live.restoredBy()!=null||saved.restoredBy()!=null||!Arrays.equals(live.inventory(),saved.inventory()))throw new IOException("Held inventory changed before origin transaction");
            CompoundTag body=decode(current().state());if(!accessoriesEmpty(body.getCompound("accessories")))throw new IOException("Nonempty accessories require audited custody first");return;
        }
        throw new IOException("Foreign custody owner");
    }
    @Override public byte[] prepareIntent(UUID transaction,byte[] digest)throws IOException {
        if(!online())throw new IOException("Origin player offline");String fingerprint=audit();effects();
        if(!original.equals(selections().get(layer)))throw new IOException("Expected original origin selection");
        if(!player.getPersistentData().getCompound(RECEIPT).isEmpty())throw new IOException("Existing origin receipt requires review");
        CompoundTag claims=claimState(claims());if(claims.getBoolean("unique")&&(!owned(claims,"original",owner())||(claims.hasUUID("human")&&!owned(claims,"human",owner()))))throw new IOException("Unique origin claim conflict");
        CompoundTag result=new CompoundTag();result.putInt("version",1);result.putString("layer",layer.toString());result.putString("original",original.toString());result.putString("human",human.toString());result.putString("dependencies",DEPS);result.putString("definitions",fingerprint);
        result.put("choices",choices(selections()));result.put("claims_before",claims);result.put("all_claims_before",allClaims(claims()));result.putBoolean("human_preowned",owned(claims,"human",owner()));result.putByteArray("custody_digest",digest);
        result.put("original_data",attachment(player.saveWithoutId(new CompoundTag())));var modifier=player.getAttribute(Attributes.LUCK).getModifiers().iterator().next();result.putString("modifier_id",modifier.id().toString());
        CompoundTag tag=luckModifier(attributes(player.saveWithoutId(new CompoundTag())),modifier.id().toString());if(tag==null)throw new IOException("Native permanent power modifier not persisted");result.put("modifier",tag);
        intent=result;return encode(result);
    }
    private static String luckAttribute(){return Attributes.LUCK.unwrapKey().orElseThrow().location().toString();}
    private static CompoundTag luckModifier(ListTag attributes,String id){for(Tag raw:attributes){CompoundTag a=(CompoundTag)raw;if(a.getString("id").equals(luckAttribute()))for(Tag m:a.getList("modifiers",10))if(((CompoundTag)m).getString("id").equals(id))return ((CompoundTag)m).copy();}return null;}
    private void bind(byte[] bytes)throws IOException {CompoundTag expected=decode(bytes);if(expected.getInt("version")!=1||!DEPS.equals(expected.getString("dependencies"))||!layer.toString().equals(expected.getString("layer"))||!original.toString().equals(expected.getString("original"))||!human.toString().equals(expected.getString("human")))throw new IOException("Origin intent identity changed");intent=expected;audit();}
    private boolean selectionsMatch(CompoundTag state,boolean temporary){CompoundTag expected=intent.getCompound("choices").copy();expected.putString(layer.toString(),(temporary?human:original).toString());return expected.equals(state.getCompound("origin_data").getCompound("origins"));}
    @Override public boolean isPrepared(byte[] bytes,JailOriginTransaction.View view)throws IOException {bind(bytes);CompoundTag state=decode(view.state());return view.receipt()==JailOriginTransaction.Receipt.NONE&&view.transaction()==null&&selectionsMatch(state,false)&&state.getCompound("claims").equals(intent.getCompound("claims_before"))&&state.getCompound("all_claims").equals(intent.getCompound("all_claims_before"));}
    @Override public boolean isTemporary(byte[] bytes,UUID id,JailOriginTransaction.View view)throws IOException{return matches(bytes,id,view,true);}
    @Override public boolean isOriginal(byte[] bytes,UUID id,JailOriginTransaction.View view)throws IOException{return matches(bytes,id,view,false);}
    private boolean matches(byte[] bytes,UUID id,JailOriginTransaction.View view,boolean temporary)throws IOException {
        bind(bytes);CompoundTag state=decode(view.state());var phase=temporary?JailOriginTransaction.Receipt.TEMPORARY:JailOriginTransaction.Receipt.ORIGINAL;
        return id.equals(view.transaction())&&view.receipt()==phase&&selectionsMatch(state,temporary)&&state.getCompound("claims").equals(expectedClaims(temporary))&&state.getCompound("all_claims").equals(expectedAllClaims(temporary))&&state.getCompound("receipt").equals(receipt(id,phase));
    }
    private JailOriginTransaction.View plan(byte[] bytes,UUID tx,boolean temporary)throws IOException {
        bind(bytes);var before=current();if(temporary){if(!isPrepared(bytes,before))throw new IOException("Original origin or unique claims changed");}
        else if(!isTemporary(bytes,tx,before))throw new IOException("Temporary origin/unique claims conflict");
        CompoundTag body=decode(before.state());body.getCompound("origin_data").getCompound("origins").putString(layer.toString(),(temporary?human:original).toString());body.put("claims",expectedClaims(temporary));body.put("all_claims",expectedAllClaims(temporary));
        ListTag attrs=body.getList("attributes",10);boolean found=false;
        for(Tag raw:attrs){CompoundTag attribute=(CompoundTag)raw;if(!attribute.getString("id").equals(luckAttribute()))continue;found=true;ListTag mods=new ListTag();for(Tag m:attribute.getList("modifiers",10))if(!((CompoundTag)m).getString("id").equals(intent.getString("modifier_id")))mods.add(m.copy());if(!temporary)mods.add(intent.getCompound("modifier").copy());if(mods.isEmpty())attribute.remove("modifiers");else attribute.put("modifiers",mods);}
        if(!found)throw new IOException("Luck persistence missing");var phase=temporary?JailOriginTransaction.Receipt.TEMPORARY:JailOriginTransaction.Receipt.ORIGINAL;body.put("receipt",receipt(tx,phase));return new JailOriginTransaction.View(encode(body),tx,phase);
    }
    @Override public JailOriginTransaction.View planTemporary(byte[] intent,UUID id)throws IOException{return plan(intent,id,true);}
    @Override public JailOriginTransaction.View planOriginal(byte[] intent,UUID id)throws IOException{return plan(intent,id,false);}
    private void mutate(byte[] bytes,UUID tx,boolean temporary)throws IOException {
        var planned=temporary?planTemporary(bytes,tx):planOriginal(bytes,tx);CompoundTag receiptBefore=player.getPersistentData().getCompound(RECEIPT).copy();Object data=data();int version=(int)call(DATA,data,"version");Object claims=claims();
        CompoundTag expectedBefore=temporary?intent.getCompound("claims_before"):expectedClaims(true);if(!claimState(claims).equals(expectedBefore))throw new IOException("Claim ownership changed before mutation");
        if(temporary&&intent.getCompound("claims_before").getBoolean("unique")&&!intent.getBoolean("human_preowned")){
            if(owner(claims,human)!=null)throw new IOException("Human claim conflict");call(CLAIMS,claims,"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},layer,human,owner());
        }
        ResourceLocation from=temporary?original:human,to=temporary?human:original;
        call(DATA,data,"setOrigin",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},layer,to);
        call(SERVICE,null,"applyOriginPowers",new Class<?>[]{ServerPlayer.class,ResourceLocation.class,ResourceLocation.class,ResourceLocation.class},player,layer,from,to);
        call(NETWORK,null,"syncToPlayer",new Class<?>[]{ServerPlayer.class},player);
        if(data()!=data||(int)call(DATA,data,"version")!=version+1)throw new IOException("Origin callback changed unrelated data/version");
        // Audit callback effects before releasing even our own Human lease.
        // The only planned claim change during callbacks is the earlier lease
        // acquisition; native power hooks are not allowed to change any claims.
        CompoundTag beforeLeaseRelease=decode(current().state()),expectedBeforeRelease=decode(planned.state());
        if(!beforeLeaseRelease.getCompound("receipt").equals(receiptBefore))throw new IOException("Origin callback changed custody receipt");
        expectedBeforeRelease.put("claims",expectedClaims(true));expectedBeforeRelease.put("all_claims",expectedAllClaims(true));beforeLeaseRelease.put("receipt",expectedBeforeRelease.getCompound("receipt").copy());
        if(!beforeLeaseRelease.equals(expectedBeforeRelease))throw new IOException("Origin callback changed undeclared state before lease release");
        if(!temporary&&intent.getCompound("claims_before").getBoolean("unique")&&!intent.getBoolean("human_preowned")){
            if(!owner().equals(owner(claims,human)))throw new IOException("Temporary Human claim changed; not releasing another owner");
            if(!Boolean.TRUE.equals(call(CLAIMS,claims,"releaseIfOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},layer,human,owner())))throw new IOException("Human lease release failed");
        }
        // Receipt is written only after the entire declared callback effect set
        // matches. Unexpected state is retained; no rollback/replay is attempted.
        CompoundTag observed=decode(current().state()),expected=decode(planned.state());observed.put("receipt",expected.getCompound("receipt").copy());
        if(!observed.equals(expected))throw new IOException("Origin callback changed undeclared state");
        player.getPersistentData().put(RECEIPT,expected.getCompound("receipt").copy());
    }
    @Override public void makeTemporary(byte[] b,UUID id)throws IOException{mutate(b,id,true);}
    @Override public void restoreOriginal(byte[] b,UUID id)throws IOException{mutate(b,id,false);}
    static void force(Path file)throws IOException {try(var c=FileChannel.open(file,StandardOpenOption.WRITE)){c.force(true);}try(var c=FileChannel.open(file.getParent(),StandardOpenOption.READ)){c.force(true);}}
    @Override public void persistAndVerify()throws IOException {
        var before=current();durable(); // missing/corrupt history is not recreated by saving over it
        saveFault.at(SaveBoundary.BEFORE_PLAYER);player.server.getPlayerList().saveAll();saveFault.at(SaveBoundary.AFTER_PLAYER);
        player.server.overworld().getDataStorage().save();
        // SavedData.save queues its actual write in this NeoForge version.
        // Waiting only for DimensionDataStorage.save() is not a disk barrier.
        try{net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}catch(RuntimeException failed){throw new IOException("Native SavedData worker failed",failed);}
        saveFault.at(SaveBoundary.AFTER_CLAIMS);
        force(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner()+".dat"));force(claimsFile());saveFault.at(SaveBoundary.AFTER_FORCE);var disk=durable();var live=current();
        if(!Arrays.equals(before.state(),disk.state())||!Arrays.equals(before.state(),live.state())||before.receipt()!=disk.receipt()||!Objects.equals(before.transaction(),disk.transaction()))throw new IOException("Origin/player/claims persistence mismatch: disk="+differences(before,disk)+" live="+differences(before,live));
    }
    private static String differences(JailOriginTransaction.View expected,JailOriginTransaction.View observed)throws IOException{
        CompoundTag a=decode(expected.state()),b=decode(observed.state());List<String> result=new ArrayList<>();Set<String> keys=new TreeSet<>(a.getAllKeys());keys.addAll(b.getAllKeys());
        for(String key:keys)if(!Objects.equals(a.get(key),b.get(key)))result.add(key+" expected="+a.get(key)+" observed="+b.get(key));return result.toString();
    }
    static byte[] encode(CompoundTag tag)throws IOException {var out=new ByteArrayOutputStream();var data=new DataOutputStream(out);data.writeByte(10);data.writeUTF("");VanillaCustodySmoke.canonicalPayload(tag,data);byte[] bytes=out.toByteArray();if(!tag.equals(decode(bytes)))throw new IOException("Origin snapshot lost NBT");return bytes;}
    static CompoundTag decode(byte[] bytes)throws IOException{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.create(16L*1024*1024));}
}
