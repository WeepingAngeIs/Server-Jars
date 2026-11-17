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

/** Disposable test-mod policy only. Publishes a READ-ONLY native graph, never a playable custody release. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class PublicationGate {
    public enum Step { PREPARED_DURABLE, CACHE_SWAPPED, RETIREMENT_DURABLE }
    @FunctionalInterface public interface Fault { void hit(Step step)throws Exception; }
    public enum Phase { PREPARED, READ_ONLY, SUSPENDED, QUARANTINED }
    private static final boolean ENABLED=Boolean.getBoolean("uq.partyHandback")&&FMLEnvironment.dist==Dist.DEDICATED_SERVER;
    private static final String FACTORY="com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory#";
    private static final String MANAGER="com.cobblemon.mod.common.api.storage.PokemonStoreManager#";
    private static final IdentityHashMap<Object,Context> CONTEXTS=new IdentityHashMap<>();
    private static final Map<UUID,Session> ACTIVE=new HashMap<>();
    private static final IdentityHashMap<Object,Session> OBJECTS=new IdentityHashMap<>();
    private static final ThreadLocal<Construction> CONSTRUCT=new ThreadLocal<>();
    private static final ThreadLocal<Session> VERIFY=new ThreadLocal<>();
    private static final Map<String,Long> COUNTS=new TreeMap<>();
    private static final int MAGIC=0x48504231;
    private PublicationGate(){}
    public static final class Context {
        final PokemonStoreManager manager; final FileBackedPokemonStoreFactory factory; final OneToOneFileStoreAdapter adapter;
        final RegistryAccess registries; final Thread thread; final String provider; boolean closed;
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
        final GenerationJournal.Receipt custodyReceipt;final long epoch;final Set<Object> graph=Collections.newSetFromMap(new IdentityHashMap<>());
        final CobblemonSnapshotCodec.Stores stores;GenerationJournal.Receipt receipt;Phase phase;boolean live,closed;
        Session(Context c,HandoffCoordinator h,GenerationJournal j,PartyCustodyGuard.Snapshot s,long e,CobblemonSnapshotCodec.Stores stores)throws Exception {
            context=c;custody=h;journal=j;original=s;epoch=e;this.stores=stores;custodyReceipt=h.receipt();
            graph.add(stores.party());graph.add(stores.pc());for(Pokemon p:stores.party())graph.add(p);
            graph.addAll(stores.pc().getBoxes());graph.add(stores.pc().getBackupStore());
        }
        public long epoch(){return epoch;}
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
    private static void deny(String why){count("denied:"+why);throw new NativeFence.Denied("Read-only publication denied: "+why);}
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
            ||Set.of("loadFromNBT","loadFrom","saveToNBT","saveTo","removeDuplicates","initialize","iterator","size","get","set","remove","rebuildPokemonByUuid","updateAspects","updateForm","updateAbility","checkGender","validateMoveset","recalculateCharacteristic","initializeScale","onChange","notify","onPokemonChanged","self","heldItem","heldItemNoCopy$common","cosmeticItem").contains(name);
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
            if(!ids.equals(s.original.partyIds())||s.stores.pc().getBoxes().size()!=1||s.stores.pc().iterator().hasNext()||s.stores.pc().getBackupStore().iterator().hasNext())throw new IOException("Identity or PC topology changed");
        }finally{VERIFY.remove();}
    }
    private static void validate(Session s,boolean caches)throws Exception {
        try {
            if(s.closed)throw new IOException("Closed publication journal");topology(s.context);s.journal.verify(s.receipt);
            if(!s.custody.receipt().equals(s.custodyReceipt)||s.custody.phase()!=PartyCustodyGuard.Phase.RESTORED)throw new IOException("Custody generation changed");
            NativeFence.verifySource(NativeFence.entry(s.original.owner()).transaction(),s.original);s.custody.verifyNative(s.original);exactGraph(s);
            if(caches&&(cache(s.context,PlayerPartyStore.class).get(s.original.owner())!=s.stores.party()||cache(s.context,PCStore.class).get(s.original.owner())!=s.stores.pc()))throw new IOException("Native cache ownership changed");
        }catch(Exception fail){withdraw(s);count("integrity-quarantine");throw fail;}
    }
    private static void withdraw(Session s){s.live=false;ACTIVE.remove(s.original.owner(),s);for(Object o:s.graph)OBJECTS.remove(o,s);}
    private static byte[] state(PartyCustodyGuard.Snapshot source,GenerationJournal.Receipt custody,long epoch,Phase phase)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(MAGIC);out.writeUTF(source.owner().toString());out.writeUTF(source.provider());out.writeUTF(NativeFence.hash(source));
            out.writeUTF(custody.journal().toString());out.writeLong(custody.generation());out.writeUTF(custody.recordHash());out.writeUTF(custody.stateHash());out.writeLong(epoch);out.writeUTF(phase.name());
        }return bytes.toByteArray();
    }
    private static long prior(GenerationJournal j,PartyCustodyGuard.Snapshot source,GenerationJournal.Receipt custody)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(j.state()))){
            if(in.readInt()!=MAGIC||!in.readUTF().equals(source.owner().toString())||!in.readUTF().equals(source.provider())||!in.readUTF().equals(NativeFence.hash(source))
                ||!in.readUTF().equals(custody.journal().toString())||in.readLong()!=custody.generation()||!in.readUTF().equals(custody.recordHash())||!in.readUTF().equals(custody.stateHash()))throw new IOException("Publication/source generation mismatch");
            long epoch=in.readLong();Phase phase=Phase.valueOf(in.readUTF());if(epoch<1||epoch>=64||in.available()!=0||!(phase==Phase.READ_ONLY||phase==Phase.SUSPENDED))throw new IOException("Ambiguous or terminal publication record; no replay");return epoch;
        }catch(IllegalArgumentException e){throw new IOException("Bad publication phase",e);}
    }
    public static Session publish(Path directory,String binding,Context c,HandoffCoordinator custody,boolean fresh,Fault fault)throws Exception {
        return (Session)NativeFence.exclusive(()->{
            topology(c);var source=custody.owned();if(custody.phase()!=PartyCustodyGuard.Phase.RESTORED||!source.pcIds().isEmpty())throw new IOException("Only exact staged return with empty PC can be prepared");
            NativeFence.verifySource(NativeFence.entry(source.owner()).transaction(),source);custody.verifyNative(source);
            if(ACTIVE.containsKey(source.owner()))throw new IOException("An accessible successor already owns the identity");
            for(Class<?> type:List.of(PlayerPartyStore.class,PCStore.class)){Object current=cache(c,type).get(source.owner());if(current!=null&&!revoked(current))throw new IOException("Occupied or unrecognized native cache destination");}
            GenerationJournal j=fresh?GenerationJournal.create(directory,binding):GenerationJournal.open(directory,binding);Session s=null;
            try {
                long epoch=fresh?1:prior(j,source,custody.receipt())+1;
                j.commit(j.receipt(),state(source,custody.receipt(),epoch,Phase.PREPARED));fault.hit(Step.PREPARED_DURABLE);
                if(CONSTRUCT.get()!=null||VERIFY.get()!=null)throw new IOException("Nested construction refused");Construction build=new Construction(c);CONSTRUCT.set(build);
                CobblemonSnapshotCodec.Stores stores;
                try {
                    stores=new CobblemonSnapshotCodec(c.registries).decode(source);stores.party().initialize();stores.pc().initialize();
                    s=new Session(c,custody,j,source,epoch,stores);
                    if(!build.born.containsAll(s.graph)||!stores.party().getObserverUUIDs().isEmpty())throw new IOException("Graph was not freshly constructed or has observers");
                    c.factory.track(stores.party());c.factory.track(stores.pc());
                }finally{CONSTRUCT.remove();}
                s.phase=Phase.PREPARED;s.receipt=j.receipt();exactGraph(s);
                cache(c,PlayerPartyStore.class).put(source.owner(),stores.party());cache(c,PCStore.class).put(source.owner(),stores.pc());fault.hit(Step.CACHE_SWAPPED);
                validate(s,true);s.receipt=j.commit(s.receipt,state(source,s.custodyReceipt,epoch,Phase.READ_ONLY));s.phase=Phase.READ_ONLY;
                fault.hit(Step.RETIREMENT_DURABLE);validate(s,true);
                s.live=true;ACTIVE.put(source.owner(),s);for(Object o:s.graph)OBJECTS.put(o,s);count("published-read-only");return s;
            }catch(Throwable failure){
                if(s!=null){withdraw(s);cache(c,PlayerPartyStore.class).remove(source.owner(),s.stores.party());cache(c,PCStore.class).remove(source.owner(),s.stores.pc());}
                j.close();throw failure;
            }
        });
    }
    private static void suspend(Session s)throws Exception {
        validate(s,true);s.receipt=s.journal.commit(s.receipt,state(s.original,s.custodyReceipt,s.epoch,Phase.SUSPENDED));s.phase=Phase.SUSPENDED;
        withdraw(s);cache(s.context,PlayerPartyStore.class).remove(s.original.owner(),s.stores.party());cache(s.context,PCStore.class).remove(s.original.owner(),s.stores.pc());count("suspended");
    }
    public static Access requestParty(Context c,UUID owner)throws Exception {
        try{return new Access(true,c.manager.getParty(owner,c.registries),"");}
        catch(NativeFence.Denied denied){count("request-denied-without-fallback");return new Access(false,null,"quarantined or unsupported");}
    }
    private static Object read(Session s,Object receiver,String name,Object[] args,NativeFence.Call operation)throws Throwable {
        validate(s,true);if(!s.live||Thread.currentThread()!=s.context.thread)deny("inactive-or-wrong-thread");
        if(receiver==s.stores.party()&&(name.equals("get")||name.equals("size")||name.equals("isEmpty"))){
            if(args.length>1||args.length==1&&!(args[0] instanceof Integer||args[0] instanceof PartyPosition))deny("unsupported-read-arguments");return operation.run();
        }
        if(receiver instanceof Pokemon&&(name.equals("getLevel")||name.equals("getShiny")||name.equals("isShiny"))&&args.length==0)return operation.run();
        if(receiver==s.stores.party()&&name.equals("iterator")&&args.length==0){
            List<Pokemon> copy=new ArrayList<>();VERIFY.set(s);try{for(Pokemon p:s.stores.party())copy.add(p);}finally{VERIFY.remove();}
            Iterator<Pokemon> it=List.copyOf(copy).iterator();return new Iterator<Pokemon>(){
                private void check(){try{NativeFence.exclusive(()->{if(!s.live)deny("revoked-iterator");validate(s,true);return null;});}catch(RuntimeException e){throw e;}catch(Exception e){throw new NativeFence.Denied("Iterator authority failed: "+e);}}
                public boolean hasNext(){check();return it.hasNext();}public Pokemon next(){check();return it.next();}
            };
        }
        deny("unsupported-gameplay-or-mutation:"+name);return null;
    }
    private static Object route(Object receiver,String key,Object[] args,NativeFence.Call operation)throws Throwable {
        String name=method(key);Construction build=CONSTRUCT.get();Session verify=VERIFY.get();
        if(build!=null){
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
                validate(s,true);dirty(c).remove(store);count("unchanged-read-only-save-verified");return null;
            }
        }
        if(c!=null&&(key.startsWith(FACTORY)||key.startsWith(MANAGER))&&args.length>=1&&args[0] instanceof UUID owner&&fenced(owner)){
            Session s=ACTIVE.get(owner);if(s==null||s.context!=c||!s.live)deny("owner-unpublished");validate(s,true);
            if(args.length!=2||args[1]!=c.registries)deny("unbound-registry");
            if(name.equals("getParty")||name.equals("getPlayerParty"))return s.stores.party();if(name.equals("getPC"))return s.stores.pc();deny("unsupported-manager-access");
        }
        Session s=OBJECTS.get(receiver);if(s!=null)return read(s,receiver,name,args,operation);
        return NativeFence.invoke(receiver,key,args,operation);
    }
    public static Object invoke(Object receiver,String key,Object[] args,NativeFence.Call operation)throws Throwable {
        if(!ENABLED)return NativeFence.invoke(receiver,key,args,operation);NativeFence.boot();return NativeFence.exclusive(()->route(receiver,key,args,operation));
    }
    /** Partition, do not catch a whole batch and accidentally skip an unrelated owner. */
    private static void mixedSave(Context c)throws Exception {
        topology(c);for(PokemonStore<?> store:new ArrayList<>(dirty(c))){
            if(fenced(store.getUuid())){
                Session s=ACTIVE.get(store.getUuid());if(s==null||s.context!=c||!s.graph.contains(store)){count("quarantined-dirty-retained");continue;}
                try{validate(s,true);dirty(c).remove(store);count("unchanged-read-only-save-verified");}catch(Exception failure){count("changed-read-only-dirty-retained");}
            }else c.factory.save(store,c.registries);
        }count("mixed-save-complete");
    }
    /** A denied old queued write has a rejection outcome, never a successful save receipt. */
    public static void worker(FileBackedPokemonStoreFactory factory,List<SerializedStore> writes,NativeFence.Call fallback)throws Throwable {
        if(!ENABLED||!CONTEXTS.containsKey(factory)){fallback.run();return;}Context c=CONTEXTS.get(factory);
        for(SerializedStore write:writes){try{c.adapter.save(write.getStoreClass(),write.getUuid(),write.getSerializedForm());}
            catch(NativeFence.Denied denied){NativeFence.exclusive(()->{count("queued-write-rejected-no-receipt");return null;});}}
    }
    /** Never hold the native method lock while the native executor is being joined. */
    public static void shutdown(FileBackedPokemonStoreFactory factory,NativeFence.Call original)throws Throwable {
        if(!ENABLED||!CONTEXTS.containsKey(factory)){original.run();return;}Context c=CONTEXTS.get(factory);
        NativeFence.exclusive(()->{for(Session s:new ArrayList<>(ACTIVE.values()))if(s.context==c)suspend(s);return null;});
        try{original.run();}finally{c.closed=true;}
        ExecutorService e=(ExecutorService)HandoffCoordinator.field(factory,"saveExecutor");if(!e.isTerminated())throw new IOException("Native executor still running");
        NativeFence.exclusive(()->{count("mixed-native-shutdown-complete");return null;});
    }
}
