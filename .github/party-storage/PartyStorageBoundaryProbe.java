package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.*;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.*;
import com.cobblemon.mod.common.api.storage.factory.*;
import com.cobblemon.mod.common.api.storage.party.*;
import com.cobblemon.mod.common.api.storage.pc.*;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.GsonBuilder;
import dev.unyielding.jail.JailConfig;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Independent synthetic native-API tests. Never part of a production JAR or a custody authorization. */
@EventBusSubscriber(modid="uq_bundle_smoke")
@SuppressWarnings({"rawtypes","unchecked"})
public final class PartyStorageBoundaryProbe {
    static final String MARKER="/tmp/uq-party-storage-result";
    static boolean done;
    static ServerPlayer player;
    static Path scratch;
    static String backend;
    static int phase;
    static final Map<String,Object> evidence=new LinkedHashMap<>();
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static Object field(Object object,String name)throws Exception{
        for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    static UUID owner(String label){return UUID.nameUUIDFromBytes(("jail-party-storage:"+backend+":"+label).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static OneToOneFileStoreAdapter adapter(String label)throws Exception{
        Path root=scratch.resolve(label).toAbsolutePath().normalize();require(root.startsWith(scratch.toAbsolutePath().normalize()),"Outside synthetic scratch directory");
        Files.createDirectories(root);
        return backend.equals("nbt")?new NBTStoreAdapter(root.toString(),false,true):new JSONStoreAdapter(root.toString(),false,true,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create());
    }
    static FileBackedPokemonStoreFactory factory(OneToOneFileStoreAdapter adapter,boolean create){
        return new FileBackedPokemonStoreFactory(adapter,create,(kotlin.jvm.functions.Function1<UUID,PlayerPartyStore>)PlayerPartyStore::new,(kotlin.jvm.functions.Function1<UUID,PCStore>)PCStore::new);
    }
    static ExecutorService executor(FileBackedPokemonStoreFactory f)throws Exception{return (ExecutorService)field(f,"saveExecutor");}
    static void drain(FileBackedPokemonStoreFactory f)throws Exception{executor(f).submit(()->"queue-position-reached").get(20,TimeUnit.SECONDS);}
    static CompoundTag snapshot(PokemonStore<?> store){return store.saveToNBT(new CompoundTag(),player.registryAccess()).copy();}
    static Pokemon pokemon(String spec){return PokemonProperties.Companion.parse(spec," ","=").create(player);}
    static void seed(PlayerPartyStore party){
        require(!party.iterator().hasNext(),"Expected fresh synthetic party");
        party.set(0,pokemon("eevee level=17 shiny=yes"));party.set(5,pokemon("pikachu level=23"));
    }
    static void recordNBT(String label,CompoundTag tag)throws Exception{
        Path file=scratch.resolve(label+".nbt");NbtIo.writeCompressed(tag,file);force(file);
    }
    static CompoundTag expected(String label)throws Exception{return NbtIo.readCompressed(scratch.resolve(label+".nbt"),NbtAccounter.unlimitedHeap());}
    static void force(Path file)throws Exception{try(FileChannel c=FileChannel.open(file,StandardOpenOption.WRITE)){c.force(true);}}
    static PlayerPartyStore loadParty(OneToOneFileStoreAdapter a,UUID id){return (PlayerPartyStore)a.load(PlayerPartyStore.class,id,player.registryAccess());}
    static void same(CompoundTag wanted,PokemonStore<?> actual,String label)throws Exception{
        require(actual!=null,"Native load returned null: "+label);
        CompoundTag got=snapshot(actual);
        if(!wanted.equals(got)){Path out=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke");Files.writeString(out.resolve(backend+"-"+label+"-expected.snbt"),wanted.toString());Files.writeString(out.resolve(backend+"-"+label+"-actual.snbt"),got.toString());throw new IllegalStateException("Complete native state differs: "+label);}
    }
    static void close(FileBackedPokemonStoreFactory f)throws Exception{f.shutdown(player.registryAccess());require(executor(f).isTerminated(),"Synthetic save executor did not terminate");}
    static void identity()throws Exception{
        require(!JailConfig.JAIL_PACKED_WALLETS.get(),"Wallet default-off setting changed");
        var manager=Cobblemon.INSTANCE.getStorage();var party=manager.getParty(player);var pc=manager.getPC(player);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Object raw:(Iterable<?>)field(manager,"factories")){
            PokemonStoreFactory f=(PokemonStoreFactory)raw;Map<String,Object> row=new LinkedHashMap<>();row.put("factory",f.getClass().getName());
            row.put("selected_party",f.getPlayerParty(player.getUUID(),player.registryAccess())==party);row.put("selected_pc",f.getPC(player.getUUID(),player.registryAccess())==pc);
            if(f instanceof FileBackedPokemonStoreFactory){Object a=field(f,"adapter");row.put("adapter",a.getClass().getName());row.put("adapter_children",((java.util.List<?>)field(a,"children")).stream().map(x->x.getClass().getName()).toList());}
            rows.add(row);
        }
        evidence.put("observed_runtime_factories",rows);evidence.put("wallet_default_off",true);
        require(FileBackedPokemonStoreFactory.class.getMethod("save",PokemonStore.class,net.minecraft.core.RegistryAccess.class).getReturnType()==void.class,"Native save API changed");
        evidence.put("native_save_returns_void",true);
    }
    static void queuedAndFailedSave()throws Exception{
        var a=adapter("failure");var f=factory(a,true);UUID id=owner("failure");var party=f.getPlayerParty(id,player.registryAccess());seed(party);f.save(party,player.registryAccess());drain(f);
        CompoundTag first=snapshot(party);same(first,loadParty(a,id),"ordinary-roundtrip");evidence.put("ordinary_full_state_roundtrip",true);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        executor(f).execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        require(entered.await(10,TimeUnit.SECONDS),"Queue blocker did not start");
        try{party.set(1,pokemon("bulbasaur level=12"));f.save(party,player.registryAccess());same(first,loadParty(a,id),"queued-old-source");require(!first.equals(snapshot(party)),"Queue probe did not change live source");evidence.put("save_returned_before_write",true);}finally{release.countDown();}
        drain(f);CompoundTag second=snapshot(party);same(second,loadParty(a,id),"queue-completion-control");
        // A real filesystem obstruction in this test-owned adapter, not a mocked success/failure response.
        Path dir=a.getFile(PlayerPartyStore.class,id).toPath().getParent();Path stash=dir.resolveSibling(dir.getFileName()+"-test-stash");
        AtomicReference<Throwable> workerFailure=new AtomicReference<>();CountDownLatch observed=new CountDownLatch(1);
        executor(f).submit(()->Thread.currentThread().setUncaughtExceptionHandler((t,e)->{workerFailure.set(e);observed.countDown();})).get(10,TimeUnit.SECONDS);
        party.set(2,pokemon("squirtle level=14"));evidence.put("dirty_before_failure",((Set<?>)field(f,"dirtyStores")).contains(party));
        Files.move(dir,stash);Files.writeString(dir,"Synthetic obstruction: parent is a file");
        try{
            f.save(party,player.registryAccess());drain(f);require(observed.await(10,TimeUnit.SECONDS),"Expected native worker IO failure absent");
            require(!((Set<?>)field(f,"dirtyStores")).contains(party),"Native failed-save dirty state differs from inspected code");
            evidence.put("queue_fence_succeeded_after_failed_write",true);evidence.put("native_worker_failure",workerFailure.get().toString());evidence.put("dirty_cleared_despite_failure",true);
        }finally{Files.delete(dir);Files.move(stash,dir);}
        f.saveAll(player.registryAccess());drain(f);same(second,loadParty(a,id),"failed-save-not-retried");require(!second.equals(snapshot(party)),"Failure probe lacks unsaved state");
        evidence.put("saveAll_did_not_retry_failed_dirty_store",true);close(f);
    }
    static void prepareFallback()throws Exception{
        var a=adapter("fallback");var f=factory(a,true);UUID id=owner("fallback");var party=f.getPlayerParty(id,player.registryAccess());seed(party);CompoundTag original=snapshot(party);recordNBT("fallback-original",original);
        f.save(party,player.registryAccess());drain(f);party.clearParty();f.save(party,player.registryAccess());drain(f);
        require(!loadParty(a,id).iterator().hasNext(),"Native empty-party save did not complete");
        Path current=a.getFile(PlayerPartyStore.class,id).toPath();List<String> backups=new ArrayList<>();
        try(var files=Files.list(current.getParent())){for(Path p:files.toList())if(!p.equals(current)&&Files.isRegularFile(p)){
            var old=(PlayerPartyStore)a.load(p.toFile(),PlayerPartyStore.class,id,player.registryAccess());if(old!=null&&snapshot(old).equals(original))backups.add(p.getFileName().toString());
        }}
        require(backups.size()==1,"Cannot identify exact native previous-generation backup: "+backups);evidence.put("previous_generation_backup",backups);
        close(f);Files.copy(current,scratch.resolve("acknowledged-empty-native-file"),StandardCopyOption.REPLACE_EXISTING);
        Files.write(current,new byte[]{1,2,3},StandardOpenOption.TRUNCATE_EXISTING);force(current);
        evidence.put("empty_source_saved_then_current_file_corrupted",true);
    }
    static int count(PokemonStore<?> store,UUID id){int n=0;for(Pokemon p:store)if(p!=null&&p.getUuid().equals(id))n++;return n;}
    static void ownership()throws Exception{
        var a=adapter("ownership");var f=factory(a,true);UUID id=owner("ownership");var party=f.getPlayerParty(id,player.registryAccess());var pc=f.getPC(id,player.registryAccess());seed(party);
        // A new native PCStore has zero boxes until resized. Give this synthetic store one real box.
        require(pc.getBoxes().isEmpty(),"Synthetic PC unexpectedly populated");pc.resize(1,true,p->{throw new IllegalStateException("Unexpected synthetic PC overflow");});require(pc.getBoxes().size()==1,"Synthetic native PC has no capacity");
        Pokemon original=party.get(0);Pokemon copy=Pokemon.Companion.loadFromNBT(player.registryAccess(),original.saveToNBT(player.registryAccess(),new CompoundTag()));
        require(copy!=original&&copy.getUuid().equals(original.getUuid()),"Native codec did not preserve UUID");require(pc.add(copy),"Native PC rejected fixture unexpectedly");
        require(count(party,original.getUuid())==1&&count(pc,original.getUuid())==1,"Expected independent same-UUID store entries absent");
        evidence.put("same_uuid_accepted_in_party_and_pc",true);Files.writeString(scratch.resolve("duplicate-uuid"),original.getUuid().toString());force(scratch.resolve("duplicate-uuid"));
        // Native set is deliberately not a safe restoration primitive: it replaces an occupied slot.
        Pokemon replacement=pokemon("charmander level=9");party.set(0,replacement);require(party.get(0)==replacement,"Native occupied-slot behavior changed");
        evidence.put("native_set_replaces_occupied_destination",true);party.set(0,original);
        recordNBT("ownership-party",snapshot(party));recordNBT("ownership-pc",snapshot(pc));f.save(party,player.registryAccess());f.save(pc,player.registryAccess());drain(f);close(f);
        // Exact native factories can legitimately resolve party and PC independently.
        var firstAdapter=adapter("selector-first");var secondAdapter=adapter("selector-second");UUID selected=owner("selector");
        var seedParty=new PlayerPartyStore(selected);seed(seedParty);firstAdapter.save(PlayerPartyStore.class,selected,firstAdapter.serialize(seedParty,player.registryAccess()));
        var first=factory(firstAdapter,false);var second=factory(secondAdapter,true);var manager=new PokemonStoreManager();manager.registerFactory(Priority.HIGHEST,first);manager.registerFactory(Priority.LOWEST,second);
        require(manager.getParty(selected,player.registryAccess())==first.getPlayerParty(selected,player.registryAccess()),"Primary party not selected");
        require(first.getPC(selected,player.registryAccess())==null&&manager.getPC(selected,player.registryAccess())==second.getPC(selected,player.registryAccess()),"Independent PC selection not demonstrated");
        evidence.put("party_and_pc_can_resolve_different_native_factories",true);close(first);close(second);
    }
    static void queuedHalt()throws Exception{
        var a=adapter("halt");var f=factory(a,true);UUID id=owner("halt");var party=f.getPlayerParty(id,player.registryAccess());seed(party);f.save(party,player.registryAccess());drain(f);recordNBT("halt-original",snapshot(party));
        CountDownLatch entered=new CountDownLatch(1);executor(f).execute(()->{entered.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        require(entered.await(10,TimeUnit.SECONDS),"Crash queue blocker did not start");party.clearParty();f.save(party,player.registryAccess());require(!party.iterator().hasNext(),"Live synthetic party not empty");same(expected("halt-original"),loadParty(a,id),"halt-disk-before");
        evidence.put("hard_halt_after_save_return_before_write",true);finish();System.out.println("PARTY_STORAGE_PLANNED_HALT_91");System.out.flush();Runtime.getRuntime().halt(91);
    }
    static void afterRestart()throws Exception{
        var a=adapter("halt");var f=factory(a,true);same(expected("halt-original"),f.getPlayerParty(owner("halt"),player.registryAccess()),"halt-restart-original");evidence.put("queued_empty_save_not_committed_at_halt",true);close(f);
        a=adapter("fallback");f=factory(a,true);same(expected("fallback-original"),f.getPlayerParty(owner("fallback"),player.registryAccess()),"backup-restored-original");evidence.put("native_backup_resurrected_pre_removal_party",true);close(f);
        a=adapter("ownership");f=factory(a,true);var party=f.getPlayerParty(owner("ownership"),player.registryAccess());var pc=f.getPC(owner("ownership"),player.registryAccess());UUID id=UUID.fromString(Files.readString(scratch.resolve("duplicate-uuid")));
        same(expected("ownership-party"),party,"ownership-party-restart");same(expected("ownership-pc"),pc,"ownership-pc-restart");require(count(party,id)==1&&count(pc,id)==1,"Cross-store identity conflict did not survive independent native reload");
        evidence.put("same_uuid_in_party_and_pc_survived_restart",true);evidence.put("complete_party_pc_snapshots_preserved_in_conflict",true);close(f);finish();
    }
    static void finish()throws Exception{
        evidence.put("commit",System.getenv("GITHUB_SHA"));evidence.put("phase",phase);evidence.put("backend",backend);evidence.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));evidence.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));
        evidence.put("nonempty_admission_enabled",false);evidence.put("scope","native storage counterexamples, NOT jail custody or a power-loss guarantee");
        Path out=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/party-storage-"+backend+"-phase"+phase+".json");Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(evidence));force(out);
        Path marker=Path.of(MARKER);Files.writeString(marker,"passed: native storage boundary "+backend+" phase "+phase+"\n");force(marker);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.partyStorageBoundary")||done)return;
        try{
            var p=event.getServer().getPlayerList().getPlayerByName("Visitor");var admin=event.getServer().getPlayerList().getPlayerByName("Dev");if(p==null||admin==null||event.getServer().getTickCount()<120||p.tickCount<100)return;
            done=true;player=p;Path world=event.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))&&world.getFileName().toString().equals("jail_integrated_smoke"),"Not isolated hosted world");
            backend=System.getProperty("uq.partyStorageBackend");phase=Integer.getInteger("uq.partyStoragePhase",0);require(Set.of("nbt","json").contains(backend),"Unknown backend");scratch=world.resolve("party-storage-probe");
            if(phase==1){require(!Files.exists(scratch),"Refuse reused synthetic state");Files.createDirectory(scratch);}else require(Files.isDirectory(scratch),"Missing first-phase synthetic state");
            identity();if(phase==1){queuedAndFailedSave();prepareFallback();ownership();queuedHalt();}else if(phase==2)afterRestart();else throw new IllegalStateException("Invalid phase");
        }catch(Throwable failure){done=true;failure.printStackTrace();try{evidence.put("completed",false);evidence.put("failure",failure.toString());evidence.put("commit",System.getenv("GITHUB_SHA"));evidence.put("backend",backend);evidence.put("phase",phase);Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/party-storage-"+backend+"-phase"+phase+"-partial.json"),new GsonBuilder().setPrettyPrinting().create().toJson(evidence));Files.writeString(Path.of(MARKER),"failed: "+failure+"\n");}catch(Exception ignored){}}
    }
}
