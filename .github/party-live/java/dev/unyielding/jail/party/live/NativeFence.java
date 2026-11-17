package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.*;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.nbt.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Test-only native entry-point fence. Never unlocks enrolled identities or changes admission policy. */
public final class NativeFence {
    public static final class Denied extends RuntimeException { public Denied(String message){super(message);} }
    @FunctionalInterface public interface Call { Object run() throws Throwable; }
    public record Entry(UUID owner, UUID transaction, String provider, String sourceHash, Set<UUID> ids) {
        public Entry { ids=Set.copyOf(ids); }
    }
    private static final boolean ENABLED=Boolean.getBoolean("uq.partyLiveFence") && FMLEnvironment.dist==Dist.DEDICATED_SERVER;
    private static final ReentrantLock LOCK=new ReentrantLock(true);
    private static final Map<UUID,Entry> OWNERS=new LinkedHashMap<>();
    private static final Map<UUID,UUID> IDS=new HashMap<>();
    private static final IdentityHashMap<Object,UUID> ALIASES=new IdentityHashMap<>();
    private static final Map<String,Long> DENIALS=new TreeMap<>();
    private static final ThreadLocal<Scope> SCOPE=new ThreadLocal<>();
    private static GenerationJournal catalog;
    private static boolean ready, attempted;
    private static final int MAGIC=0x4e465831;
    private record Scope(UUID owner,Object adapter,Path current,Object payload,boolean inspect) {}
    private NativeFence() {}

    private static void initialize(Path root,String binding,boolean fresh)throws IOException {
        LOCK.lock();try {
            if(!ENABLED||ready||catalog!=null)throw new IOException("Fence is disabled, already opened or poisoned");
            if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))throw new IOException("Hosted synthetic environment required");
            catalog=fresh?GenerationJournal.create(root,binding):GenerationJournal.open(root,binding);
            byte[] state=catalog.state();
            if(state.length>0)try(var in=new DataInputStream(new ByteArrayInputStream(state))){
                if(in.readInt()!=MAGIC)throw new IOException("Unknown fence catalog");
                int n=in.readInt();if(n<1||n>16)throw new IOException("Fence bound");
                for(int i=0;i<n;i++){
                    UUID owner=uuid(in),tx=uuid(in);String provider=in.readUTF(),hash=in.readUTF();int m=in.readInt();
                    if(m<1||m>36||!hash.matches("[0-9a-f]{64}"))throw new IOException("Invalid identity manifest");
                    Set<UUID> ids=new HashSet<>();for(int j=0;j<m;j++)if(!ids.add(uuid(in)))throw new IOException("Duplicate identity");
                    Entry e=new Entry(owner,tx,provider,hash,ids);install(e);
                }
                if(in.available()!=0)throw new IOException("Trailing fence state");
            }
            ready=true;
        }catch(IOException|RuntimeException fail){ready=false;throw fail;}finally{LOCK.unlock();}
    }
    private static void install(Entry e)throws IOException {
        if(OWNERS.containsKey(e.owner())||e.ids().stream().anyMatch(IDS::containsKey))throw new IOException("Conflicting fence ownership");
        OWNERS.put(e.owner(),e);for(UUID id:e.ids())IDS.put(id,e.owner());
    }
    public static GenerationJournal.Receipt enroll(UUID tx,PartyCustodyGuard.Snapshot original,Object... oldReferences)throws IOException {
        LOCK.lock();try {
            ensure();catalog.verify(catalog.receipt());
            Set<UUID> ids=new HashSet<>(original.partyIds());ids.addAll(original.pcIds());
            Entry e=new Entry(original.owner(),tx,original.provider(),hash(original),ids);
            if(OWNERS.size()>=16||OWNERS.containsKey(e.owner())||ids.isEmpty()||ids.stream().anyMatch(IDS::containsKey))throw new IOException("Identity/owner already enrolled or out of bounds");
            var all=new ArrayList<>(OWNERS.values());all.add(e);
            GenerationJournal.Receipt receipt;
            try{receipt=catalog.commit(catalog.receipt(),encode(all),step->{});}catch(IOException fail){ready=false;throw fail;}
            install(e);for(Object ref:oldReferences)if(ref!=null)ALIASES.put(ref,e.owner());
            return receipt;
        }finally{LOCK.unlock();}
    }
    public static Entry entry(UUID owner)throws IOException {
        LOCK.lock();try{ensure();catalog.verify(catalog.receipt());Entry e=OWNERS.get(owner);if(e==null)throw new IOException("Owner not fenced");return e;}finally{LOCK.unlock();}
    }
    public static void verifySource(UUID tx,PartyCustodyGuard.Snapshot original)throws IOException {
        Entry e=entry(original.owner());if(!e.transaction().equals(tx)||!e.provider().equals(original.provider())||!e.sourceHash().equals(hash(original)))throw new IOException("Fence and custody source disagree");
    }
    public static Map<String,Long> denials(){LOCK.lock();try{return Map.copyOf(DENIALS);}finally{LOCK.unlock();}}
    public static boolean ready(){return ready;}
    public static int enrolled(){LOCK.lock();try{return OWNERS.size();}finally{LOCK.unlock();}}
    public static void boot() throws IOException {
        LOCK.lock();try {
            if(!ENABLED)return;
            if(ready)return;
            if(attempted)throw new IOException("Fence startup failed; no retry or implicit reset");
            attempted=true;
            Path world=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-harness/run-server/jail_integrated_smoke/party-live").toAbsolutePath().normalize();
            Files.createDirectories(world);
            initialize(world.resolve("catalog"),System.getenv("UQ_JAIL_SHA256")+":"+System.getenv("UQ_BUNDLE_SHA256")+":"+System.getProperty("uq.liveBackend"),Integer.getInteger("uq.livePhase")==1);
        }finally{LOCK.unlock();}
    }
    public static Object invoke(Object receiver,String key,Object[] arguments,Call operation)throws Throwable {
        if(!ENABLED)return operation.run();
        boot();
        LOCK.lock();try {
            ensure();UUID affected=affected(receiver);for(Object arg:arguments){UUID found=affected(arg);if(found!=null)affected=found;}
            if(affected!=null&&!allowed(receiver,key,arguments,affected))deny(key);
            Object result=operation.run();UUID output=affected(result);
            if(output!=null&&!allowed(receiver,key,arguments,output))deny(key+":return");
            return result;
        }finally{LOCK.unlock();}
    }
    private static void ensure(){if(!ready||catalog==null)throw new Denied("Native fence not initialized or uncertain; no fallthrough");}
    private static void deny(String key){DENIALS.merge(key,1L,Long::sum);throw new Denied("Native custody ownership fence: "+key);}
    private static boolean allowed(Object receiver,String key,Object[] args,UUID owner) {
        Scope s=SCOPE.get();if(s==null||!s.owner().equals(owner))return false;
        String method=key.substring(key.indexOf('#')+1,key.indexOf('('));
        if(s.inspect()) {
            // Controlled codec work only. This does not authorize native IO, publication or gameplay.
            if(key.contains("Factory#")||key.contains("Adapter#")||key.contains("Manager#"))return false;
            if(method.startsWith("send")||method.startsWith("recall")||method.startsWith("tryRecall")||method.equals("initialize")||method.contains("Battle")||method.contains("trade")||method.contains("Trade"))return false;
            if(ALIASES.containsKey(receiver))return method.startsWith("get")||method.startsWith("is")||method.equals("iterator")||method.equals("saveToNBT")||method.equals("saveToJSON");
            return method.startsWith("get")||method.startsWith("is")||method.startsWith("set")||method.startsWith("update")||method.startsWith("load")||method.startsWith("save")||method.equals("iterator");
        }
        if(receiver!=s.adapter())return false;
        if(!(method.equals("save")||method.equals("load")||method.equals("provide")))return false;
        for(Object arg:args){
            if(arg instanceof File file){Path p=file.toPath().toAbsolutePath().normalize();String base=s.current().toString();if(!p.toString().equals(base)&&!p.toString().equals(base+".tmp"))return false;}
            if((arg instanceof CompoundTag||arg instanceof JsonObject)&&!arg.equals(s.payload()))return false;
        }
        return true;
    }
    /** No public bypass scope. HandoffCoordinator is the only internal scope caller. */
    static Object inspect(UUID owner,Call call)throws Exception{return scope(new Scope(owner,null,null,null,true),call);}
    static Object write(UUID owner,Object adapter,Path current,Object payload,Call call)throws Exception{return scope(new Scope(owner,adapter,current.toAbsolutePath().normalize(),payload,false),call);}
    private static Object scope(Scope scope,Call call)throws Exception {
        LOCK.lock();try {
            ensure();entry(scope.owner());if(SCOPE.get()!=null)throw new IOException("Nested privileged scope refused");
            SCOPE.set(scope);try{return call.run();}catch(Exception e){throw e;}catch(Throwable e){throw new IOException("Native operation failed",e);}finally{SCOPE.remove();}
        }finally{LOCK.unlock();}
    }
    /** Lock the entire enrollment/handoff, not just the method entry check. */
    static Object exclusive(Call call)throws Exception {
        LOCK.lock();try{ensure();try{return call.run();}catch(Exception e){throw e;}catch(Throwable e){throw new IOException(e);}}finally{LOCK.unlock();}
    }
    private static UUID affected(Object value) {
        if(value==null)return null;
        UUID alias=ALIASES.get(value);if(alias!=null)return alias;
        if(value instanceof UUID id)return OWNERS.containsKey(id)?id:IDS.get(id);
        if(value instanceof Pokemon p)return IDS.get(p.getUuid());
        if(value instanceof PokemonStore<?> store)return OWNERS.containsKey(store.getUuid())?store.getUuid():null;
        if(value instanceof PCBox box)return affected(box.getPc());
        if(value instanceof File file){
            String name=file.getName();if(name.length()>=36)try{return affected(UUID.fromString(name.substring(0,36)));}catch(IllegalArgumentException ignored){}return null;
        }
        if(value instanceof CompoundTag tag){for(String key:tag.getAllKeys()){UUID found=affected(tag.get(key));if(found!=null)return found;}}
        else if(value instanceof ListTag list){for(Tag tag:list){UUID found=affected(tag);if(found!=null)return found;}}
        else if(value instanceof StringTag tag){try{return affected(UUID.fromString(tag.getAsString()));}catch(IllegalArgumentException ignored){}}
        else if(value instanceof IntArrayTag tag&&tag.getAsIntArray().length==4){int[] a=tag.getAsIntArray();return affected(new UUID(((long)a[0]<<32)|(a[1]&0xffffffffL),((long)a[2]<<32)|(a[3]&0xffffffffL)));}
        else if(value instanceof JsonObject json){for(var entry:json.entrySet()){UUID found=affected(entry.getValue());if(found!=null)return found;}}
        else if(value instanceof JsonArray array){for(JsonElement item:array){UUID found=affected(item);if(found!=null)return found;}}
        else if(value instanceof JsonPrimitive p&&p.isString()){try{return affected(UUID.fromString(p.getAsString()));}catch(IllegalArgumentException ignored){}}
        return null;
    }
    static String hash(PartyCustodyGuard.Snapshot s) {
        try{var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(s.party());digest.update(s.pc());return HexFormat.of().formatHex(digest.digest());}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static byte[] encode(List<Entry> entries)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeInt(MAGIC);out.writeInt(entries.size());for(Entry e:entries){uuid(out,e.owner());uuid(out,e.transaction());out.writeUTF(e.provider());out.writeUTF(e.sourceHash());out.writeInt(e.ids().size());for(UUID id:new TreeSet<>(e.ids()))uuid(out,id);}}return bytes.toByteArray();
    }
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
}
