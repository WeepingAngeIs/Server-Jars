package dev.unyielding.jail;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;

/** Data-only audit: no origin/unequip permission callback is invoked. */
final class JailHumanAudit {
    static final String DATA="com.cyberday1.neoorigins.attachment.PlayerOriginData",ORIGINS="com.cyberday1.neoorigins.data.OriginDataManager",LAYERS="com.cyberday1.neoorigins.data.LayerDataManager",ATTACHMENT="neoorigins:origin_data";
    record Result(ResourceLocation layer,ResourceLocation human,String runtime,CompoundTag slots){}
    private record Hashed(Object key,long size,long modified,String hash){}
    private static final Map<Path,Hashed> hashes=new HashMap<>();
    static Object call(String type,Object target,String method,Class<?>[] signature,Object...args)throws IOException {try{return Class.forName(type).getMethod(method,signature).invoke(target,args);}catch(ReflectiveOperationException|RuntimeException e){throw new IOException("Unsupported audited API: "+type+"."+method,e);}}
    static Object call(String type,Object target,String method)throws IOException{return call(type,target,method,new Class<?>[0]);}
    static Object singleton(String type)throws IOException{try{return Class.forName(type).getField("INSTANCE").get(null);}catch(ReflectiveOperationException e){throw new IOException("Missing audited registry",e);}}
    static Object data(ServerPlayer p)throws IOException {var type=(AttachmentType<?>)call("com.cyberday1.neoorigins.attachment.OriginAttachments",null,"originData");Object data=p.getExistingDataOrNull(type);if(data==null)throw new IOException("Origin attachment absent; never create one during admission");return data;}
    static boolean disposable(ServerPlayer p){
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))||!Boolean.getBoolean("uq.integratedJail"))return false;
        String world=p.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
        return Boolean.getBoolean("uq.retainedXp")&&world.equals("jail_xp_smoke")||world.equals("jail_integrated_smoke")||Boolean.getBoolean("uq.expandedJail")&&world.equals("jail_expanded_smoke")||Boolean.getBoolean("uq.certifiedJail")&&world.equals("jail_certified_smoke");
    }
    private static String hash(Path path)throws IOException {
        var a=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class);Hashed old=hashes.get(path);if(old!=null&&Objects.equals(old.key(),a.fileKey())&&old.size()==a.size()&&old.modified()==a.lastModifiedTime().toMillis())return old.hash();
        try{MessageDigest digest=MessageDigest.getInstance("SHA-512");try(var input=Files.newInputStream(path)){byte[] bytes=new byte[65536];for(int n;(n=input.read(bytes))>=0;)digest.update(bytes,0,n);}String value=HexFormat.of().formatHex(digest.digest());hashes.put(path,new Hashed(a.fileKey(),a.size(),a.lastModifiedTime().toMillis(),value));return value;}catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static String runtime(ServerPlayer p)throws IOException {return runtimeWithArtifact(p,null);}
    /** Migration proof only: the dependency certificate is checked exactly as for the current artifact. */
    static String predecessorRuntime(ServerPlayer p)throws IOException {return runtimeWithArtifact(p,JailRuntimeMigration.OLD_SHA512);}
    static String ownArtifact()throws IOException {
        Path own=ModList.get().getModFileById(MinecraftJail.MODID).getFile().getFilePath().toAbsolutePath().normalize();
        if(!Files.isRegularFile(own,LinkOption.NOFOLLOW_LINKS)||!own.equals(own.toRealPath()))throw new IOException("Unsafe implementation artifact");
        return hash(own);
    }
    private static String runtimeWithArtifact(ServerPlayer p,String predecessor)throws IOException {
        JsonObject certificate;try(var stream=JailHumanAudit.class.getResourceAsStream("/assets/unyieldingquests/jail-certified-runtime.json")){if(stream==null)throw new IOException("Audit certificate missing");certificate=JsonParser.parseReader(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
        Set<String> allowed=new HashSet<>();certificate.getAsJsonArray("sha512").forEach(e->allowed.add(e.getAsString()));List<String> rows=new ArrayList<>();
        Path own=ModList.get().getModFileById(MinecraftJail.MODID).getFile().getFilePath().toAbsolutePath().normalize();
        if(!Files.isRegularFile(own)||!own.equals(own.toRealPath()))throw new IOException("Jail implementation must be an exact regular artifact");
        rows.add(JailArtifactBinding.row(predecessor==null?hash(own):predecessor));
        for(var mod:ModList.get().getMods()){
            String id=mod.getModId(),version=mod.getVersion().toString();Path path=mod.getOwningFile().getFile().getFilePath().toAbsolutePath().normalize();
            if(path.equals(own))continue;
            if(id.equals("uq_bundle_smoke")&&disposable(p)){rows.add("disposable-harness");continue;}
            if(id.equals("minecraft")||id.equals("neoforge")){if(!certificate.get(id).getAsString().equals(version))throw new IOException("Uncertified kernel "+id+"="+version);rows.add(id+"="+version);continue;}
            if(!Files.isRegularFile(path))throw new IOException("Uncertified mod path "+id+" "+path);String digest=hash(path);
            if(!allowed.contains(digest))throw new IOException("Uncertified mod "+id+"="+version+" sha512="+digest);rows.add(id+"="+version+":"+digest);
        }
        Collections.sort(rows);return HexFormat.of().formatHex(JailOriginTransaction.hash(String.join("\n",rows).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    @SuppressWarnings("unchecked") static Result inspect(ServerPlayer p)throws IOException {
        if(!p.server.isSameThread()||!p.isAlive()||p.hasDisconnected()||p.server.getPlayerList().getPlayer(p.getUUID())!=p)throw new IOException("Subject offline, dead or not authoritative");
        String runtime=runtime(p);Object data=data(p);
        if(!p.getActiveEffects().isEmpty())throw new IOException("Active effects require another audited contract");
        for(Tag raw:p.saveWithoutId(new CompoundTag()).getList("attributes",10))if(!((CompoundTag)raw).getList("modifiers",10).isEmpty())throw new IOException("Residual/equipped attribute modifiers are unsupported");
        if((int)call(DATA,data,"getEvolutionTier")!=0||!Boolean.TRUE.equals(call(DATA,data,"isHadAllOrigins")))throw new IOException("Only settled tier-zero Human origin is certified");
        for(String pending:List.of("isPendingOrbCommit","isPendingLayerPickerCommit","isPendingAdminReselect"))if(Boolean.TRUE.equals(call(DATA,data,pending)))throw new IOException("Pending origin selection");
        for(String granted:List.of("getDynamicGrantedPowers","getGlobalGrantedPowers"))if(!((Set<?>)call(DATA,data,granted)).isEmpty())throw new IOException("Unaudited dynamic/global powers");
        try{Field f=Class.forName(DATA).getDeclaredField("activeCooldowns");f.setAccessible(true);if(!((Map<?,?>)f.get(data)).isEmpty())throw new IOException("Unpersisted cooldown state is unsupported");}catch(ReflectiveOperationException bad){throw new IOException("Cannot inspect cooldowns",bad);}
        Map<ResourceLocation,ResourceLocation> choices=Map.copyOf((Map<ResourceLocation,ResourceLocation>)call(DATA,data,"getOrigins"));if(choices.size()!=1)throw new IOException("Other selected origin/class layers are not certified");var selection=choices.entrySet().iterator().next();
        Map<ResourceLocation,?> origins=(Map<ResourceLocation,?>)call(ORIGINS,singleton(ORIGINS),"getOrigins");List<ResourceLocation> humans=new ArrayList<>();for(ResourceLocation id:origins.keySet()){
            JsonObject raw=(JsonObject)call(ORIGINS,singleton(ORIGINS),"getRawOriginJson",new Class<?>[]{ResourceLocation.class},id);if(raw==null||!raw.has("name"))continue;JsonElement name=raw.get("name");String key=name.isJsonPrimitive()?name.getAsString():name.isJsonObject()&&name.getAsJsonObject().has("translate")?name.getAsJsonObject().get("translate").getAsString():"";if(key.equals("origins.neoorigins.human.name"))humans.add(id);
        }
        if(humans.size()!=1||!selection.getValue().equals(humans.getFirst()))throw new IOException("Non-Human origin requires a separately audited native callback adapter");
        Object origin=call(ORIGINS,singleton(ORIGINS),"getOrigin",new Class<?>[]{ResourceLocation.class},humans.getFirst());if(!((List<?>)call("com.cyberday1.neoorigins.api.origin.Origin",origin,"powersForTier",new Class<?>[]{int.class},0)).isEmpty())throw new IOException("Human has unaudited active powers");
        Object layer=call(LAYERS,singleton(LAYERS),"getLayer",new Class<?>[]{ResourceLocation.class},selection.getKey());if(layer==null||!Boolean.TRUE.equals(call("com.cyberday1.neoorigins.api.origin.OriginLayer",layer,"enabled"))||!((List<?>)call("com.cyberday1.neoorigins.api.origin.OriginLayer",layer,"getAvailableOriginIds",new Class<?>[]{Map.class},choices)).contains(humans.getFirst()))throw new IOException("Human layer unavailable");
        CompoundTag slots=emptyAccessories(p);return new Result(selection.getKey(),humans.getFirst(),runtime,slots);
    }
    @SuppressWarnings("unchecked") private static CompoundTag emptyAccessories(ServerPlayer p)throws IOException {
        Object capability=((Optional<?>)call("io.wispforest.accessories.api.AccessoriesCapability",null,"getOptionally",new Class<?>[]{LivingEntity.class},p)).orElseThrow(()->new IOException("Accessory capability missing"));
        Map<String,Object> canonical=new TreeMap<>((Map<String,Object>)call("io.wispforest.accessories.api.AccessoriesCapability",capability,"getContainers"));Object holder=call("io.wispforest.accessories.api.AccessoriesCapability",capability,"getHolder");
        try{for(Object value:(List<?>)Class.forName("io.wispforest.accessories.impl.AccessoriesHolderImpl").getField("invalidStacks").get(holder))if(!(value instanceof ItemStack stack)||!stack.isEmpty())throw new IOException("Queued accessory inventory is unsupported");}catch(ReflectiveOperationException bad){throw new IOException("Unknown accessory queue",bad);}
        Object curios=((Optional<?>)call("top.theillusivec4.curios.api.CuriosApi",null,"getCuriosInventory",new Class<?>[]{LivingEntity.class},p)).orElseThrow(()->new IOException("Curios capability missing"));Map<?,?> wrappers=(Map<?,?>)call("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler",curios,"getCurios");Map<String,Object> aliases=new TreeMap<>();
        for(var e:wrappers.entrySet()){if(!(e.getKey() instanceof String key)||e.getValue()==null||!e.getValue().getClass().getName().equals("io.wispforest.accessories_compat.curios.wrapper.AccessoriesBasedStackHandler"))throw new IOException("Uncertified Curios backing");aliases.put(key,call(e.getValue().getClass().getName(),e.getValue(),"container"));}
        Map<String,AccessoryCustodyPolicy.Size> sizes=new TreeMap<>();CompoundTag result=new CompoundTag();
        for(var e:canonical.entrySet()){
            Object c=e.getValue();Container normal=(Container)call("io.wispforest.accessories.api.AccessoriesContainer",c,"getAccessories"),cosmetic=(Container)call("io.wispforest.accessories.api.AccessoriesContainer",c,"getCosmeticAccessories");int count=(int)call("io.wispforest.accessories.api.AccessoriesContainer",c,"getSize");
            if(!((Map<?,?>)call("io.wispforest.accessories.api.AccessoriesContainer",c,"getModifiers")).isEmpty())throw new IOException("Slot modifiers/lifecycle are not certified");
            if(count!=normal.getContainerSize()||count!=cosmetic.getContainerSize()||!normal.isEmpty()||!cosmetic.isEmpty())throw new IOException("Only empty, settled accessory/cosmetic stores are certified");
            for(Container channel:List.of(normal,cosmetic))for(int i=0;i<count;i++)if(!((ItemStack)call("io.wispforest.accessories.impl.ExpandedSimpleContainer",channel,"getPreviousItem",new Class<?>[]{int.class},i)).isEmpty())throw new IOException("Pending accessory lifecycle");
            sizes.put(e.getKey(),new AccessoryCustodyPolicy.Size(count,count));result.putInt(e.getKey(),count);
        }
        AccessoryCustodyPolicy.slots(canonical,aliases,sizes);return result;
    }
}
