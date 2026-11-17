package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.api.storage.*;
import com.cobblemon.mod.common.api.storage.adapter.SerializedStore;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.*;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.*;
import com.cobblemon.mod.common.api.storage.pc.*;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Test-only transactional nickname edits on successor graphs. Not an end-to-end gameplay custody path. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class MutationGate {
    public enum Step { PREPARED_DURABLE, CACHE_SWAPPED, RETIREMENT_DURABLE }
    @FunctionalInterface public interface Fault { void hit(Step step)throws Exception; }
    public enum Phase { PREPARED, READ_ONLY, SUSPENDED, QUARANTINED, MUTATING }
    public enum EditStep { INTENT_DURABLE, NATIVE_WRITTEN, COMMIT_DURABLE }
    @FunctionalInterface public interface EditFault { void hit(EditStep step)throws Exception; }
    private static final boolean ENABLED=Boolean.getBoolean("uq.partyMutation")&&FMLEnvironment.dist==Dist.DEDICATED_SERVER;
    private static final String FACTORY="com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory#";
    private static final String MANAGER="com.cobblemon.mod.common.api.storage.PokemonStoreManager#";
    private static final Map<Object,Context> CONTEXTS=Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static final IdentityHashMap<Object,Session> OBJECTS=new IdentityHashMap<>();
    private static final ThreadLocal<Construction> CONSTRUCT=new ThreadLocal<>();
    private static final ThreadLocal<Session> VERIFY=new ThreadLocal<>();
    private static final Map<String,Long> COUNTS=new ConcurrentSkipListMap<>();
    private static final int MAGIC=0x4d555431;
    private static final ThreadLocal<WriteScope> WRITE=new ThreadLocal<>();
    private record WriteScope(Context context,UUID owner,java.nio.file.Path current,Object payload,GenerationJournal journal,GenerationJournal.Receipt receipt) {}
    public record Lease(UUID owner,long epoch,GenerationJournal.Receipt generation) {}
    public record EditResult(boolean accepted,Session current,String reason) {}
    private record Record(PartyCustodyGuard.Snapshot snapshot,long epoch,Phase phase,Set<UUID> commands,String previousHash) {}
    private MutationGate(){}
    public static final class Context {
        final PokemonStoreManager manager; final FileBackedPokemonStoreFactory factory; final OneToOneFileStoreAdapter adapter;
        final RegistryAccess registries; final Thread thread; final String provider; volatile boolean closed;
        private Context(PokemonStoreManager m,FileBackedPokemonStoreFactory f,OneToOneFileStoreAdapter a,RegistryAccess r)throws Exception {
            manager=m;factory=f;adapter=a;registries=r;thread=Thread.currentThread();provider=HandoffCoordinator.providerIdentity(a);
        }
    }
    private static final class Construction {
        final Context context;final Set<Object> born=Collections.newSetFromMap(new IdentityHashMap<>());
        Construction(Context c){context=c;}
    }
    public static final class Session implements AutoCloseable {
        final Context context;final HandoffCoordinator custody;final GenerationJournal journal;final PartyCustodyGuard.Snapshot original;
        final GenerationJournal.Receipt custodyReceipt;final PartyCustodyGuard.Snapshot origin;final Set<UUID> commands;final long epoch;final Set<Object> graph=Collections.newSetFromMap(new IdentityHashMap<>());
        final CobblemonSnapshotCodec.Stores stores;GenerationJournal.Receipt receipt;Phase phase;boolean live,closed;
        Session(Context c,HandoffCoordinator h,GenerationJournal j,PartyCustodyGuard.Snapshot s,long e,CobblemonSnapshotCodec.Stores stores,Set<UUID> commands)throws Exception {
            context=c;custody=h;journal=j;original=s;epoch=e;this.stores=stores;custodyReceipt=h.receipt();origin=h.owned();this.commands=Set.copyOf(commands);
            graph.add(stores.party());graph.add(stores.pc());for(Pokemon p:stores.party())graph.add(p);
            graph.addAll(stores.pc().getBoxes());graph.add(stores.pc().getBackupStore());
        }
        public long epoch(){return epoch;}
        public Lease lease(){return new Lease(original.owner(),epoch,receipt);}
        public GenerationJournal.Receipt receipt(){return receipt;}
        public boolean live(){return live;}
        public PartyCustodyGuard.Snapshot original(){return original;}
        public void assertIntegrity()throws Exception {NativeFence.exclusive(()->{validate(this,true);return null;});}
        public void close()throws IOException {
            try{NativeFence.exclusive(()->{if(live)suspend(this);if(!closed){journal.close();closed=true;}return null;});}
            catch(IOException e){throw e;}catch(Exception e){throw new IOException(e);}
        }
    }
    public record Access(boolean available,PlayerPartyStore party,String reason){}
    private static void count(String key){COUNTS.merge(key,1L,Long::sum);}
    public static Map<String,Long> counters()throws Exception {return (Map<String,Long>)NativeFence.exclusive(()->Map.copyOf(COUNTS));}
    private static void deny(String why){count("denied:"+why);throw new NativeFence.Denied("Bounded mutation authority denied: "+why);}
    private static Object staticField(String name)throws Exception {Field f=NativeFence.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static boolean fenced(UUID owner)throws Exception {return ((Map<?,?>)staticField("OWNERS")).containsKey(owner);}
    private static boolean revoked(Object object)throws Exception {return ((Map<?,?>)staticField("ALIASES")).containsKey(object);}
    private static Map<UUID,PokemonStore<?>> cache(Context c,Class<?> type)throws Exception {
        Map<?,?> caches=(Map<?,?>)HandoffCoordinator.field(c.factory,"storeCaches");Object cache=caches.get(type);
        if(cache==null)throw new IOException("Required native cache absent");return (Map<UUID,PokemonStore<?>>)HandoffCoordinator.field(cache,"cacheMap");
    }
    private static Set<PokemonStore<?>> dirty(Context c)throws Exception {return (Set<PokemonStore<?>>)HandoffCoordinator.field(c.factory,"dirtyStores");}
    private static void topology(Context c)throws Exception {
        if(c.closed||Thread.currentThread()!=c.thread||c.factory.getClass()!=FileBackedPokemonStoreFactory.class
                ||c.manager.getClass()!=PokemonStoreManager.class||HandoffCoordinator.field(c.factory,"adapter")!=c.adapter
                ||!c.provider.equals(HandoffCoordinator.providerIdentity(c.adapter)))throw new IOException("Closed, wrong-thread or changed concrete provider");
        List<Object> factories=new ArrayList<>();for(Object f:(Iterable<?>)HandoffCoordinator.field(c.manager,"factories"))factories.add(f);
        if(factories.size()!=1||factories.getFirst()!=c.factory)throw new IOException("Alternate or changed manager provider set");
    }
    public static Context bind(PokemonStoreManager m,FileBackedPokemonStoreFactory f,OneToOneFileStoreAdapter a,RegistryAccess r)throws Exception {
        return (Context)NativeFence.exclusive(()->{if(CONTEXTS.containsKey(m)||CONTEXTS.containsKey(f))throw new IOException("Duplicate native authority binding");
            Context c=new Context(m,f,a,r);topology(c);cache(c,PlayerPartyStore.class);cache(c,PCStore.class);CONTEXTS.put(m,c);CONTEXTS.put(f,c);return c;});
    }
    /** Called only by constructor-return hooks. Existing objects cannot enter the construction capability. */
    public static void born(Object object){Construction b=CONSTRUCT.get();if(ENABLED&&b!=null)b.born.add(object);}
    private static boolean constructionMethod(String name){
        return name.startsWith("get")||name.startsWith("set")||name.startsWith("is")||name.startsWith("has")
            ||Set.of("loadFromNBT","loadFromJSON","loadFrom","saveToNBT","saveToJSON","saveTo","removeDuplicates","initialize","iterator","size","get","set","remove","rebuildPokemonByUuid","updateAspects","updateForm","updateAbility","checkGender","validateMoveset","recalculateCharacteristic","initializeScale","refreshOriginalTrainer","onChange","notify","onPokemonChanged","self","heldItem","heldItemNoCopy$common","cosmeticItem").contains(name);
    }
    private static boolean inspectMethod(String name){return name.startsWith("get")||name.startsWith("is")||name.startsWith("has")
        ||Set.of("saveToNBT","saveTo","iterator","size","get","heldItem","heldItemNoCopy$common","cosmeticItem","self").contains(name);}
    private static String method(String key){return key.substring(key.indexOf('#')+1,key.indexOf('('));}
    private static void noForeignNative(Object value,Set<Object> graph)throws Exception {
        if(value instanceof Pokemon||value instanceof PokemonStore<?>||value instanceof PCBox){if(!graph.contains(value))deny("foreign-reference-in-capability");}
        else if(value instanceof Object[] a)for(Object x:a)noForeignNative(x,graph);
        else if(value instanceof Collection<?> a)for(Object x:a)noForeignNative(x,graph);
    }
    private static CompoundTag nbt(byte[] bytes)throws IOException {try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){CompoundTag t=NbtIo.read(in,NbtAccounter.create(4L*1024*1024));if(t==null||in.available()!=0)throw new IOException("Invalid frozen NBT");return t;}}
    private static void exactGraph(Session s)throws Exception {
        if(VERIFY.get()!=null)throw new IOException("Nested verification capability");VERIFY.set(s);
        try {
            if(!s.stores.party().getUuid().equals(s.original.owner())||!s.stores.pc().getUuid().equals(s.original.owner())
                ||!nbt(s.original.party()).equals(s.stores.party().saveToNBT(new CompoundTag(),s.context.registries))
                ||!nbt(s.original.pc()).equals(s.stores.pc().saveToNBT(new CompoundTag(),s.context.registries)))throw new IOException("Published native state changed");
            Set<UUID> ids=new HashSet<>();for(Pokemon p:s.stores.party())if(!s.graph.contains(p)||!ids.add(p.getUuid()))throw new IOException("Foreign or duplicate native object");
            if(!ids.equals(s.original.partyIds())||s.stores.pc().getBoxes().size()!=1||s.stores.pc().iterator().hasNext()||s.stores.pc().getBackupStore().iterator().hasNext()
                    ||!s.stores.party().getObserverUUIDs().equals(List.of(s.original.owner())))throw new IOException("Identity, observer or PC topology changed");
        }finally{VERIFY.remove();}
    }
    private static void validate(Session s,boolean caches)throws Exception {
        try {
            if(s.closed)throw new IOException("Closed publication journal");topology(s.context);s.journal.verify(s.receipt);
            Record current=record(s.journal,s.origin,s.custodyReceipt);if(current.epoch()!=s.epoch||current.phase()!=s.phase||!current.commands().equals(s.commands)||!current.snapshot().same(s.original))throw new IOException("Session differs from exact committed generation");
            if(!s.custody.receipt().equals(s.custodyReceipt)||s.custody.phase()!=PartyCustodyGuard.Phase.RESTORED)throw new IOException("Custody generation changed");
            NativeFence.verifySource(NativeFence.entry(s.origin.owner()).transaction(),s.origin);verifyCurrent(s.context,s.original);exactGraph(s);
            if(caches&&(cache(s.context,PlayerPartyStore.class).get(s.original.owner())!=s.stores.party()||cache(s.context,PCStore.class).get(s.original.owner())!=s.stores.pc()))throw new IOException("Native cache ownership changed");
        }catch(Exception fail){withdraw(s);count("integrity-quarantine");throw fail;}
    }
    private static void validateAccess(Session s)throws Exception {
        try{validate(s,true);}catch(IOException uncertain){deny("uncertain-generation-or-native-state");}
    }
    private static void withdraw(Session s){s.live=false;ACTIVE.remove(s.original.owner(),s);for(Object o:s.graph)OBJECTS.remove(o,s);}
    private static void bytes(DataOutputStream out,byte[] bytes)throws IOException {if(bytes.length>4*1024*1024)throw new IOException("Snapshot bound");out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] bytes(DataInputStream in)throws IOException {int n=in.readInt();if(n<1||n>4*1024*1024)throw new IOException("Snapshot bound");byte[] b=in.readNBytes(n);if(b.length!=n)throw new EOFException();return b;}
    private static void ids(DataOutputStream out,Set<UUID> ids)throws IOException {out.writeInt(ids.size());for(UUID id:new TreeSet<>(ids))out.writeUTF(id.toString());}
    private static Set<UUID> ids(DataInputStream in,int limit)throws IOException {int n=in.readInt();if(n<0||n>limit)throw new IOException("Identity bound");Set<UUID>s=new HashSet<>();for(int i=0;i<n;i++)if(!s.add(UUID.fromString(in.readUTF())))throw new IOException("Duplicate identity");return Set.copyOf(s);}
    private static byte[] state(PartyCustodyGuard.Snapshot source,PartyCustodyGuard.Snapshot origin,GenerationJournal.Receipt custody,long epoch,Phase phase,Set<UUID> commands,String previousHash)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeUTF(origin.owner().toString());out.writeUTF(origin.provider());out.writeUTF(NativeFence.hash(origin));
            out.writeUTF(custody.journal().toString());out.writeLong(custody.generation());out.writeUTF(custody.recordHash());out.writeUTF(custody.stateHash());out.writeLong(epoch);out.writeUTF(phase.name());
            bytes(out,source.party());bytes(out,source.pc());ids(out,source.partyIds());ids(out,source.pcIds());ids(out,commands);out.writeUTF(previousHash);
        }return bytes.toByteArray();
    }
    private static Record record(GenerationJournal j,PartyCustodyGuard.Snapshot origin,GenerationJournal.Receipt custody)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(j.state()))){
            if(in.readInt()!=MAGIC||!in.readUTF().equals(origin.owner().toString())||!in.readUTF().equals(origin.provider())||!in.readUTF().equals(NativeFence.hash(origin))
                ||!in.readUTF().equals(custody.journal().toString())||in.readLong()!=custody.generation()||!in.readUTF().equals(custody.recordHash())||!in.readUTF().equals(custody.stateHash()))throw new IOException("Publication/source generation mismatch");
            long epoch=in.readLong();Phase phase=Phase.valueOf(in.readUTF());
            byte[] party=bytes(in),pc=bytes(in);Set<UUID> pids=ids(in,6),cids=ids(in,30),commands=ids(in,32);String previous=in.readUTF();
            if(epoch<1||epoch>=64||in.available()!=0||!pids.equals(origin.partyIds())||!cids.equals(origin.pcIds())||!Arrays.equals(pc,origin.pc())||!previous.matches("[0-9a-f]{64}"))throw new IOException("Changed identity/topology or bad generation");
            return new Record(new PartyCustodyGuard.Snapshot(origin.owner(),origin.provider(),party,pids,pc,cids),epoch,phase,commands,previous);
        }catch(IllegalArgumentException e){throw new IOException("Bad mutation record",e);}
    }
    public static Session publish(Path directory,String binding,Context c,HandoffCoordinator custody,boolean fresh,Fault fault)throws Exception {
        return (Session)NativeFence.exclusive(()->{
            topology(c);var origin=custody.owned();var source=origin;if(custody.phase()!=PartyCustodyGuard.Phase.RESTORED||!source.pcIds().isEmpty())throw new IOException("Only exact staged return with empty PC can be prepared");
            NativeFence.verifySource(NativeFence.entry(origin.owner()).transaction(),origin);
            if(ACTIVE.containsKey(source.owner()))throw new IOException("An accessible successor already owns the identity");
            for(Class<?> type:List.of(PlayerPartyStore.class,PCStore.class)){Object current=cache(c,type).get(source.owner());if(current!=null&&!revoked(current))throw new IOException("Occupied or unrecognized native cache destination");}
            if(fresh)verifyCurrent(c,source);
            GenerationJournal j=fresh?GenerationJournal.create(directory,binding):GenerationJournal.open(directory,binding);Session s=null;
            try {
                Set<UUID> commands=Set.of();long epoch=1;
                if(!fresh){Record prior=record(j,origin,custody.receipt());if(!(prior.phase()==Phase.READ_ONLY||prior.phase()==Phase.SUSPENDED))throw new IOException("Ambiguous mutation/publication; no replay");source=prior.snapshot();commands=prior.commands();epoch=prior.epoch()+1;}
                if(epoch>=64)throw new IOException("Publication epoch limit; no implicit reset");
                verifyCurrent(c,source);
                j.commit(j.receipt(),state(source,origin,custody.receipt(),epoch,Phase.PREPARED,commands,NativeFence.hash(source)));fault.hit(Step.PREPARED_DURABLE);
                if(CONSTRUCT.get()!=null||VERIFY.get()!=null)throw new IOException("Nested construction refused");Construction build=new Construction(c);CONSTRUCT.set(build);
                CobblemonSnapshotCodec.Stores stores;
                try {
                    stores=new CobblemonSnapshotCodec(c.registries).decode(source);stores.party().initialize();stores.pc().initialize();
                    s=new Session(c,custody,j,source,epoch,stores,commands);
                    if(!build.born.containsAll(s.graph)||!stores.party().getObserverUUIDs().equals(List.of(source.owner())))throw new IOException("Graph was not freshly constructed or has foreign observers");
                    c.factory.track(stores.party());c.factory.track(stores.pc());
                }finally{CONSTRUCT.remove();}
                s.phase=Phase.PREPARED;s.receipt=j.receipt();exactGraph(s);
                cache(c,PlayerPartyStore.class).put(source.owner(),stores.party());cache(c,PCStore.class).put(source.owner(),stores.pc());fault.hit(Step.CACHE_SWAPPED);
                validate(s,true);s.receipt=j.commit(s.receipt,state(source,origin,s.custodyReceipt,epoch,Phase.READ_ONLY,commands,NativeFence.hash(source)));s.phase=Phase.READ_ONLY;
                fault.hit(Step.RETIREMENT_DURABLE);validate(s,true);
                s.live=true;ACTIVE.put(source.owner(),s);for(Object o:s.graph)OBJECTS.put(o,s);count("published-read-only");return s;
            }catch(Throwable failure){
                if(s!=null){withdraw(s);cache(c,PlayerPartyStore.class).remove(source.owner(),s.stores.party());cache(c,PCStore.class).remove(source.owner(),s.stores.pc());}
                j.close();throw failure;
            }
        });
    }
    private static void suspend(Session s)throws Exception {
        validate(s,true);s.receipt=s.journal.commit(s.receipt,state(s.original,s.origin,s.custodyReceipt,s.epoch,Phase.SUSPENDED,s.commands,NativeFence.hash(s.original)));s.phase=Phase.SUSPENDED;
        withdraw(s);cache(s.context,PlayerPartyStore.class).remove(s.original.owner(),s.stores.party());cache(s.context,PCStore.class).remove(s.original.owner(),s.stores.pc());count("suspended");
    }
    public static Access requestParty(Context c,UUID owner)throws Exception {
        try{return new Access(true,c.manager.getParty(owner,c.registries),"");}
        catch(NativeFence.Denied denied){count("request-denied-without-fallback");return new Access(false,null,"quarantined or unsupported");}
    }
    private static Object read(Session s,Object receiver,String name,Object[] args,NativeFence.Call operation)throws Throwable {
        validateAccess(s);if(!s.live||Thread.currentThread()!=s.context.thread)deny("inactive-or-wrong-thread");
        if(receiver==s.stores.party()&&(name.equals("get")||name.equals("size")||name.equals("isEmpty"))){
            if(args.length>1||args.length==1&&!(args[0] instanceof Integer||args[0] instanceof PartyPosition))deny("unsupported-read-arguments");return operation.run();
        }
        if(receiver instanceof Pokemon&&name.equals("getNickname")&&args.length==0){Object n=operation.run();return n==null?null:((net.minecraft.network.chat.Component)n).copy();}
        if(receiver instanceof Pokemon&&(name.equals("getLevel")||name.equals("getShiny")||name.equals("isShiny"))&&args.length==0)return operation.run();
        if(receiver==s.stores.party()&&name.equals("iterator")&&args.length==0){
            List<Pokemon> copy=new ArrayList<>();VERIFY.set(s);try{for(Pokemon p:s.stores.party())copy.add(p);}finally{VERIFY.remove();}
            Iterator<Pokemon> it=List.copyOf(copy).iterator();return new Iterator<Pokemon>(){
                private void check(){try{NativeFence.exclusive(()->{if(!s.live)deny("revoked-iterator");validateAccess(s);return null;});}catch(RuntimeException e){throw e;}catch(Exception e){throw new NativeFence.Denied("Iterator authority failed: "+e);}}
                public boolean hasNext(){check();return it.hasNext();}public Pokemon next(){check();return it.next();}
            };
        }
        deny("unsupported-gameplay-or-mutation:"+name);return null;
    }
    private static Object route(Object receiver,String key,Object[] args,NativeFence.Call operation)throws Throwable {
        String name=method(key);WriteScope io=WRITE.get();
        if(io!=null){io.journal().verify(io.receipt());if(!allowWrite(receiver,key,args,io))deny("unapproved-mutation-io");Object out=operation.run();io.journal().verify(io.receipt());if(!allowWrite(receiver,key,args,io))deny("changed-mutation-io");return out;}
        Construction build=CONSTRUCT.get();Session verify=VERIFY.get();
        if(build!=null){
            if(build.born.contains(receiver)&&receiver instanceof PCStore pc&&name.equals("tryRestoreBackedUpPokemon")&&args.length==0){
                if(pc.getBackupStore().iterator().hasNext())deny("nonempty-construction-backup");return operation.run();
            }
            if(build.born.contains(receiver)&&constructionMethod(name)){noForeignNative(args,build.born);return operation.run();}
            if(receiver==build.context.factory&&name.equals("track")&&args.length==1&&build.born.contains(args[0]))return operation.run();
            return NativeFence.invoke(receiver,key,args,operation);
        }
        if(verify!=null&&verify.graph.contains(receiver)&&inspectMethod(name)){noForeignNative(args,verify.graph);return operation.run();}
        Context c=CONTEXTS.get(receiver);
        if(c!=null&&key.startsWith(FACTORY)){
            if(name.equals("saveAll")){mixedSave(c);return null;}
            if(name.equals("onPlayerDisconnect")&&args.length==1&&args[0] instanceof ServerPlayer p&&fenced(p.getUUID())){
                Session s=ACTIVE.get(p.getUUID());if(s!=null&&s.context==c)suspend(s);else count("quarantined-disconnect-retained");return null;
            }
            if(name.equals("isCached")&&args.length==1&&args[0] instanceof PokemonStore<?> store&&fenced(store.getUuid())){
                Session s=ACTIVE.get(store.getUuid());return s!=null&&s.context==c&&s.graph.contains(store)&&cache(c,store.getClass()).get(store.getUuid())==store;
            }
            if(name.equals("save")&&args.length==2&&args[0] instanceof PokemonStore<?> store&&fenced(store.getUuid())){
                Session s=ACTIVE.get(store.getUuid());if(s==null||s.context!=c||!s.graph.contains(store))deny("revoked-save");
                validateAccess(s);dirty(c).remove(store);count("durable-current-save-verified");return null;
            }
        }
        if(c!=null&&(key.startsWith(FACTORY)||key.startsWith(MANAGER))&&args.length>=1&&args[0] instanceof UUID owner&&fenced(owner)){
            Session s=ACTIVE.get(owner);if(s==null||s.context!=c||!s.live)deny("owner-unpublished");validateAccess(s);
            if(args.length!=2||args[1]!=c.registries)deny("unbound-registry");
            if(name.equals("getParty")||name.equals("getPlayerParty"))return s.stores.party();if(name.equals("getPC"))return s.stores.pc();deny("unsupported-manager-access");
        }
        Session s=OBJECTS.get(receiver);if(s!=null)return read(s,receiver,name,args,operation);
        return NativeFence.invoke(receiver,key,args,operation);
    }
    public static Object invoke(Object receiver,String key,Object[] args,NativeFence.Call operation)throws Throwable {
        if(!ENABLED)return NativeFence.invoke(receiver,key,args,operation);NativeFence.boot();return NativeFence.exclusive(()->route(receiver,key,args,operation));
    }
    private static Object payload(Context c,PartyCustodyGuard.Snapshot expected,boolean pc)throws Exception {
        if(CONSTRUCT.get()!=null||VERIFY.get()!=null||WRITE.get()!=null)throw new IOException("Nested payload preparation");
        Construction build=new Construction(c);CONSTRUCT.set(build);
        try {
            var copy=new CobblemonSnapshotCodec(c.registries).decode(expected);PokemonStore<?> store=pc?copy.pc():copy.party();
            if(c.adapter.getClass()==NBTStoreAdapter.class)return store.saveToNBT(new CompoundTag(),c.registries).copy();
            com.google.gson.Gson gson=(com.google.gson.Gson)HandoffCoordinator.field(c.adapter,"gson");
            String wire=gson.toJson(store.saveToJSON(new com.google.gson.JsonObject(),c.registries));
            var json=com.google.gson.JsonParser.parseString(wire).getAsJsonObject();
            HandoffCoordinator.requireJsonRoundTrip(store,json,c.registries);
            if(!gson.toJson(json).equals(wire))throw new IOException("Unstable native JSON encoding");return json;
        }finally{CONSTRUCT.remove();}
    }
    private static java.nio.file.Path checkedPath(Context c,Class<?> type,UUID owner)throws Exception {
        java.nio.file.Path p=c.adapter.getFile(type,owner).toPath().toAbsolutePath();
        if(!p.equals(p.normalize())||!java.nio.file.Files.isRegularFile(p,java.nio.file.LinkOption.NOFOLLOW_LINKS)
            ||!p.equals(p.toRealPath())||!p.getParent().equals(p.getParent().toRealPath()))throw new IOException("Noncanonical or missing native file");return p;
    }
    private static void verifyFile(Context c,Class<?> type,UUID owner,Object expected)throws Exception {
        java.nio.file.Path p=checkedPath(c,type,owner);Object actual=c.adapter.getClass()==NBTStoreAdapter.class
            ?NbtIo.readCompressed(p,NbtAccounter.create(4L*1024*1024))
            :com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(p));
        if(!expected.equals(actual))throw new IOException("Native current generation differs: "+type.getSimpleName());
    }
    private static void verifyCurrent(Context c,PartyCustodyGuard.Snapshot expected)throws Exception {
        if(!c.provider.equals(expected.provider()))throw new IOException("Foreign native provider");
        verifyFile(c,PlayerPartyStore.class,expected.owner(),payload(c,expected,false));
        verifyFile(c,PCStore.class,expected.owner(),payload(c,expected,true));
    }
    private static boolean allowWrite(Object receiver,String key,Object[] args,WriteScope s){
        if(receiver!=s.context().adapter||Thread.currentThread()!=s.context().thread)return false;
        String prefix="com.cobblemon.mod.common.api.storage.adapter.flatfile.";
        if(key.equals(prefix+"OneToOneFileStoreAdapter#save(Ljava/lang/Class;Ljava/util/UUID;Ljava/lang/Object;)V"))
            return args.length==3&&args[0]==PlayerPartyStore.class&&s.owner().equals(args[1])&&s.payload().equals(args[2]);
        String adapter=receiver.getClass()==NBTStoreAdapter.class?"NBTStoreAdapter":"JSONStoreAdapter";
        String type=adapter.equals("NBTStoreAdapter")?"Lnet/minecraft/nbt/CompoundTag;":"Lcom/google/gson/JsonObject;";
        if(!key.equals(prefix+adapter+"#save(Ljava/io/File;Ljava/lang/Object;)V")&&!key.equals(prefix+adapter+"#save(Ljava/io/File;"+type+")V"))return false;
        if(args.length!=2||!(args[0] instanceof File f)||!s.payload().equals(args[1]))return false;
        var p=f.toPath().toAbsolutePath();return p.equals(p.normalize())&&p.equals(java.nio.file.Path.of(s.current()+".temp"));
    }
    private static void persistChange(Session old,Session next)throws Exception {
        Context c=old.context;verifyCurrent(c,old.original);
        var current=checkedPath(c,PlayerPartyStore.class,old.original.owner());
        var temp=java.nio.file.Path.of(current+".temp");var backup=java.nio.file.Path.of(current+".old");
        if(java.nio.file.Files.exists(temp,java.nio.file.LinkOption.NOFOLLOW_LINKS))throw new IOException("Uncertain native temporary write");
        if(java.nio.file.Files.exists(backup,java.nio.file.LinkOption.NOFOLLOW_LINKS)&&(!java.nio.file.Files.isRegularFile(backup,java.nio.file.LinkOption.NOFOLLOW_LINKS)||!backup.equals(backup.toRealPath())))throw new IOException("Unsafe native backup");
        Object frozen=payload(c,next.original,false); // frozen before scope; no native reconstruction inside IO privilege
        if(WRITE.get()!=null||CONSTRUCT.get()!=null||VERIFY.get()!=null)throw new IOException("Nested write capability");
        next.journal.verify(next.receipt);
        WRITE.set(new WriteScope(c,next.original.owner(),current,frozen,next.journal,next.receipt));
        Object input=frozen instanceof CompoundTag t?t.copy():((com.google.gson.JsonObject)frozen).deepCopy();
        try{c.adapter.save(PlayerPartyStore.class,next.original.owner(),input);}finally{WRITE.remove();}
        try(var file=java.nio.channels.FileChannel.open(current,java.nio.file.StandardOpenOption.WRITE)){file.force(true);}
        try(var directory=java.nio.channels.FileChannel.open(current.getParent(),java.nio.file.StandardOpenOption.READ)){directory.force(true);}
        verifyCurrent(c,next.original);
    }
    /** A plain-literal nickname is the only supported mutation, not a generic operation callback. */
    private static Session candidate(Session old,Pokemon target,UUID command,String nickname)throws Exception {
        if(CONSTRUCT.get()!=null||VERIFY.get()!=null||WRITE.get()!=null)throw new IOException("Nested edit construction");
        Construction build=new Construction(old.context);CONSTRUCT.set(build);
        try {
            var codec=new CobblemonSnapshotCodec(old.context.registries);var fresh=codec.decode(old.original);
            Pokemon selected=null;for(Pokemon p:fresh.party())if(p.getUuid().equals(target.getUuid()))selected=p;
            if(selected==null)throw new IOException("Target not in party");
            CompoundTag before=selected.saveToNBT(old.context.registries,new CompoundTag()).copy();
            selected.setNickname(net.minecraft.network.chat.Component.literal(nickname));
            CompoundTag after=selected.saveToNBT(old.context.registries,new CompoundTag()).copy();
            if(before.equals(after)||selected.getNickname()==null||!selected.getNickname().equals(net.minecraft.network.chat.Component.literal(nickname)))throw new IOException("No-op or changed native nickname");
            CompoundTag beforeRest=before.copy(),afterRest=after.copy();beforeRest.remove("Nickname");afterRest.remove("Nickname");
            if(!beforeRest.equals(afterRest)||!after.contains("Nickname"))throw new IOException("Native edit changed another Pokemon field");
            var changed=codec.capture(old.original.owner(),old.original.provider(),fresh.party(),fresh.pc());
            CompoundTag expectedParty=nbt(old.original.party());List<String> matches=new ArrayList<>();
            for(String k:expectedParty.getAllKeys())if(before.equals(expectedParty.get(k)))matches.add(k);
            if(matches.size()!=1)throw new IOException("Ambiguous native slot");expectedParty.put(matches.getFirst(),after);
            if(!expectedParty.equals(nbt(changed.party()))||!Arrays.equals(changed.pc(),old.original.pc())||!changed.partyIds().equals(old.original.partyIds())||!changed.pcIds().equals(old.original.pcIds()))throw new IOException("Edit changed full state, slot or identity");
            fresh.party().initialize();fresh.pc().initialize();Set<UUID>commands=new HashSet<>(old.commands);commands.add(command);
            Session next=new Session(old.context,old.custody,old.journal,changed,old.epoch+1,fresh,commands);
            if(!build.born.containsAll(next.graph)||!fresh.party().getObserverUUIDs().equals(List.of(changed.owner())))throw new IOException("Foreign successor graph");
            old.context.factory.track(fresh.party());old.context.factory.track(fresh.pc());return next;
        }finally{CONSTRUCT.remove();}
    }
    /** Compare-and-set request with object identity, session epoch, exact receipt and persistent command deduplication. */
    public static Session rename(Session old,Lease lease,Pokemon target,UUID command,String nickname,EditFault fault)throws Exception {
        return (Session)NativeFence.exclusive(()->{
            if(old==null||lease==null||target==null||command==null||nickname==null||fault==null)throw new IOException("Missing edit authority");
            if(!old.live||ACTIVE.get(old.original.owner())!=old||!old.lease().equals(lease)||!old.graph.contains(target))throw new IOException("Stale session, object or generation");
            if(Thread.currentThread()!=old.context.thread||old.epoch>=63||old.commands.size()>=32||old.commands.contains(command)||!nickname.matches("[A-Za-z0-9 _-]{1,32}"))throw new IOException("Replay, wrong thread, invalid name or bounded limit");
            validate(old,true);Session next=candidate(old,target,command,nickname);exactGraph(next);
            boolean began=false;
            try {
                // Persist intent before disk change or any new graph exposure. No asynchronous native saver receipt.
                began=true;next.receipt=old.journal.commit(old.receipt,state(next.original,old.origin,old.custodyReceipt,next.epoch,Phase.MUTATING,next.commands,NativeFence.hash(old.original)));next.phase=Phase.MUTATING;
                withdraw(old);old.closed=true;
                cache(old.context,PlayerPartyStore.class).remove(old.original.owner(),old.stores.party());cache(old.context,PCStore.class).remove(old.original.owner(),old.stores.pc());
                fault.hit(EditStep.INTENT_DURABLE);persistChange(old,next);fault.hit(EditStep.NATIVE_WRITTEN);
                cache(old.context,PlayerPartyStore.class).put(old.original.owner(),next.stores.party());cache(old.context,PCStore.class).put(old.original.owner(),next.stores.pc());
                validate(next,true);next.receipt=next.journal.commit(next.receipt,state(next.original,old.origin,old.custodyReceipt,next.epoch,Phase.READ_ONLY,next.commands,NativeFence.hash(old.original)));next.phase=Phase.READ_ONLY;
                fault.hit(EditStep.COMMIT_DURABLE);validate(next,true);
                next.live=true;ACTIVE.put(next.original.owner(),next);for(Object o:next.graph)OBJECTS.put(o,next);count("nickname-edit-durable");return next;
            }catch(Throwable failure){
                if(began){withdraw(old);old.closed=true;withdraw(next);cache(old.context,PlayerPartyStore.class).remove(old.original.owner(),next.stores.party());cache(old.context,PCStore.class).remove(old.original.owner(),next.stores.pc());next.journal.close();next.closed=true;count("edit-quarantined-no-replay");}throw failure;
            }
        });
    }
    public static EditResult requestRename(Session session,Lease lease,Pokemon target,UUID command,String nickname)throws Exception {
        try{return new EditResult(true,rename(session,lease,target,command,nickname,step->{}),"");}
        catch(IOException|NativeFence.Denied refusal){count("edit-request-refused-no-fallback");return new EditResult(false,null,"stale, conflicting, quarantined or unsupported");}
    }

    /** Partition, do not catch a whole batch and accidentally skip an unrelated owner. */
    private static void mixedSave(Context c)throws Exception {
        topology(c);for(PokemonStore<?> store:new ArrayList<>(dirty(c))){
            if(fenced(store.getUuid())){
                Session s=ACTIVE.get(store.getUuid());if(s==null||s.context!=c||!s.graph.contains(store)){count("quarantined-dirty-retained");continue;}
                try{validate(s,true);dirty(c).remove(store);count("durable-current-save-verified");}catch(Exception failure){count("changed-read-only-dirty-retained");}
            }else c.factory.save(store,c.registries);
        }count("mixed-save-complete");
    }
    /** A denied old queued write has a rejection outcome, never a successful save receipt. */
    public static void worker(FileBackedPokemonStoreFactory factory,List<SerializedStore> writes,NativeFence.Call fallback)throws Throwable {
        Context c=ENABLED?CONTEXTS.get(factory):null;if(c==null){fallback.run();return;}
        for(SerializedStore write:writes){try{c.adapter.save(write.getStoreClass(),write.getUuid(),write.getSerializedForm());}
            catch(NativeFence.Denied denied){NativeFence.exclusive(()->{count("queued-write-rejected-no-receipt");return null;});}}
    }
    /** Never hold the native method lock while the native executor is being joined. */
    public static void shutdown(FileBackedPokemonStoreFactory factory,NativeFence.Call original)throws Throwable {
        Context c=ENABLED?CONTEXTS.get(factory):null;if(c==null){original.run();return;}
        NativeFence.exclusive(()->{for(Session s:new ArrayList<>(ACTIVE.values()))if(s.context==c)suspend(s);return null;});
        try{original.run();}finally{c.closed=true;}
        ExecutorService e=(ExecutorService)HandoffCoordinator.field(factory,"saveExecutor");if(!e.isTerminated())throw new IOException("Native executor still running");
        NativeFence.exclusive(()->{count("mixed-native-shutdown-complete");return null;});
    }
}
