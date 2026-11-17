package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.*;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.*;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import dev.unyielding.jail.JailConfig;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Synthetic scratch factory/manager only. Connected clients are never admitted with nonempty parties. */
@EventBusSubscriber(modid="uq_bundle_smoke")
@SuppressWarnings({"rawtypes","unchecked"})
public final class HandbackProbe {
    static Path root;static ServerPlayer player;static String backend;static int phase,stage,ticks;static boolean done;
    static final Map<String,Object> proof=new LinkedHashMap<>();
    static Fixture shared;static PublicationGate.Context context;static HandoffCoordinator primary,quarantine;static PublicationGate.Session session;
    static PlayerPartyStore oldParty;static Pokemon oldMon,untrackedClone;static CompoundTag controlExpected;static long autoBefore;
    interface Action{void run()throws Exception;}
    record Fixture(OneToOneFileStoreAdapter adapter,FileBackedPokemonStoreFactory factory,PokemonStoreManager manager){}
    record Stores(PlayerPartyStore party,PCStore pc){}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void flag(String name){proof.put(name,true);}
    static UUID owner(String label){return UUID.nameUUIDFromBytes(("handback:"+backend+":"+label).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static UUID tx(String label){return owner(label+"-transaction");}
    static String binding(){return System.getenv("UQ_JAIL_SHA256")+":"+System.getenv("UQ_BUNDLE_SHA256")+":"+backend;}
    static Path custody(String label){return root.resolve("custody-"+label);}
    static Path publication(String label){return root.resolve("publication-"+label);}
    static void denied(String label,Action action)throws Exception{try{action.run();}catch(NativeFence.Denied expected){flag(label);return;}throw new IllegalStateException("Unsafe access succeeded: "+label);}
    static void refused(String label,Action action)throws Exception{try{action.run();}catch(IOException expected){flag(label);return;}throw new IllegalStateException("Unsafe ordering succeeded: "+label);}
    static ExecutorService executor(Fixture f)throws Exception{return (ExecutorService)HandoffCoordinator.field(f.factory,"saveExecutor");}
    static void drain(Fixture f)throws Exception{executor(f).submit(()->true).get(20,TimeUnit.SECONDS);}
    static Set<PokemonStore<?>> dirty(Fixture f)throws Exception{return (Set<PokemonStore<?>>)HandoffCoordinator.field(f.factory,"dirtyStores");}
    static Fixture fixture(String label)throws Exception {
        Path p=root.resolve(label);Files.createDirectories(p);OneToOneFileStoreAdapter a=backend.equals("nbt")?new NBTStoreAdapter(p.toString(),false,true):new JSONStoreAdapter(p.toString(),false,true,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create());
        var f=new FileBackedPokemonStoreFactory(a,true,(kotlin.jvm.functions.Function1<UUID,PlayerPartyStore>)PlayerPartyStore::new,(kotlin.jvm.functions.Function1<UUID,PCStore>)PCStore::new);
        var m=new PokemonStoreManager();m.registerFactory(Priority.HIGHEST,f);
        // Keep the REAL native auto-save subscription enabled. Only initialize unrelated caches here.
        f.getPlayerParty(owner("bootstrap-"+label),player.registryAccess());f.getPC(owner("bootstrap-"+label),player.registryAccess());return new Fixture(a,f,m);
    }
    static PublicationGate.Context bind(Fixture f)throws Exception{return PublicationGate.bind(f.manager,f.factory,f.adapter,player.registryAccess());}
    static Stores seed(Fixture f,String label)throws Exception {
        var p=f.factory.getPlayerParty(owner(label),player.registryAccess());var pc=f.factory.getPC(owner(label),player.registryAccess());require(p.isEmpty(),"Refuse reseeding existing party");
        pc.resize(1,true,x->{throw new IllegalStateException("Unexpected overflow");});
        p.set(0,PokemonProperties.Companion.parse("eevee level=17 shiny=yes"," ","=").create(player));p.set(5,PokemonProperties.Companion.parse("pikachu level=23"," ","=").create(player));
        p.get(0).setNickname(Component.literal("frozen-"+label));p.get(0).setScaleModifier(1.1f);
        f.factory.save(p,player.registryAccess());f.factory.save(pc,player.registryAccess());drain(f);return new Stores(p,pc);
    }
    static HandoffCoordinator hold(Fixture f,Stores s,String label)throws Exception{return HandoffCoordinator.start(custody(label),binding(),tx(label),player.registryAccess(),f.factory,f.adapter,s.party,s.pc,step->{});}
    static HandoffCoordinator recovered(Fixture f,String label)throws Exception{return HandoffCoordinator.open(custody(label),binding(),owner(label),player.registryAccess(),f.adapter);}
    static Path current(Fixture f,String label){return f.adapter.getFile(PlayerPartyStore.class,owner(label)).toPath();}
    static void checkedCurrent(Fixture f,String label,CompoundTag expected)throws Exception {
        var loaded=(PlayerPartyStore)f.adapter.load(PlayerPartyStore.class,owner(label),player.registryAccess());require(loaded!=null&&expected.equals(loaded.saveToNBT(new CompoundTag(),player.registryAccess())),"Unrelated complete state changed");
    }
    static ServerPlayer disconnected(UUID id){return new ServerPlayer(player.server,player.serverLevel(),new GameProfile(id,"SyntheticCustody"),ClientInformation.createDefault());}
    static void requestDenied(PublicationGate.Context c,String label,String observation)throws Exception {var a=PublicationGate.requestParty(c,owner(label));require(!a.available()&&a.party()==null&&!a.reason().isEmpty(),"Denial made a fallback party");flag(observation);}
    static void basicPublished(Fixture f,PublicationGate.Context c,PublicationGate.Session s,String label)throws Exception {
        var p=f.manager.getParty(owner(label),player.registryAccess());require(p==f.factory.getPlayerParty(owner(label),player.registryAccess()),"Manager and factory expose different objects");
        require(p.get(0).getLevel()==17&&p.get(5).getLevel()==23&&p.get(1)==null,"Original native slots or values changed");
        require(p.get(0).getUuid().equals(s.original().partyIds().stream().filter(id->id.equals(p.get(0).getUuid())).findFirst().orElseThrow()),"Identity not preserved");
        s.assertIntegrity();flag("fresh_native_view_full_state_"+label);
        denied("direct_disk_load_still_denied_"+label,()->f.adapter.load(PlayerPartyStore.class,owner(label),player.registryAccess()));
        denied("fresh_gameplay_mutation_denied_"+label,()->p.get(0).setLevel(50));
    }
    static void firstStart()throws Exception {
        shared=fixture("shared");var source=seed(shared,"primary");var q=seed(shared,"quarantine");var control=seed(shared,"control");oldParty=source.party;oldMon=oldParty.get(0);
        untrackedClone=Pokemon.Companion.loadFromNBT(player.registryAccess(),oldMon.saveToNBT(player.registryAccess(),new CompoundTag()));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);executor(shared).execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});require(entered.await(10,TimeUnit.SECONDS),"Queue blocker absent");
        try {
            oldMon.setNickname(Component.literal("stale-queued-payload"));control.party.get(0).setNickname(Component.literal("queued-control-preserved"));
            dirty(shared).add(oldParty);dirty(shared).add(control.party);shared.factory.saveAll(player.registryAccess());
            oldMon.setNickname(Component.literal("frozen-primary"));context=bind(shared);
            primary=hold(shared,source,"primary");primary.stageReturn(step->{});quarantine=hold(shared,q,"quarantine");
            session=PublicationGate.publish(publication("primary"),binding(),context,primary,true,step->{
                if(step==PublicationGate.Step.RETIREMENT_DURABLE)requestDenied(context,"primary","durable_retirement_precedes_access_grant");
            });
            require(session.epoch()==1,"First epoch mismatch");basicPublished(shared,context,session,"primary");
            var fresh=shared.manager.getParty(owner("primary"),player.registryAccess());require(fresh!=oldParty&&fresh.get(0)!=oldMon,"Old objects reused");flag("fresh_cache_objects_not_old_aliases");
            denied("old_party_still_denied",()->oldParty.get(0));denied("old_pokemon_still_denied",oldMon::getLevel);denied("untracked_same_uuid_clone_denied",untrackedClone::getLevel);
            var alternate=new PokemonStoreManager();alternate.registerFactory(Priority.HIGHEST,shared.factory);denied("alternate_manager_cannot_expose_successor",()->alternate.getParty(owner("primary"),player.registryAccess()));
            refused("second_publication_refused",()->PublicationGate.publish(publication("duplicate"),binding(),context,primary,true,step->{}));
        }finally{release.countDown();}
        drain(shared);require(PublicationGate.counters().getOrDefault("queued-write-rejected-no-receipt",0L)>=1,"Old queued write not rejected");primary.verifyNative(primary.owned());flag("old_queued_payload_rejected_after_retirement");
        controlExpected=control.party.saveToNBT(new CompoundTag(),player.registryAccess()).copy();checkedCurrent(shared,"control",controlExpected);flag("same_queued_batch_unrelated_owner_saved");
        require(!shared.factory.isCached(oldParty),"Revoked callback still cached");flag("revoked_cache_predicate_returns_false");
        requestDenied(context,"quarantine","quarantine_request_no_throw_no_fallback");
        dirty(shared).add(q.party);dirty(shared).add(shared.manager.getParty(owner("primary"),player.registryAccess()));
        control.party.get(0).setNickname(Component.literal("autosave-control-preserved"));controlExpected=control.party.saveToNBT(new CompoundTag(),player.registryAccess()).copy();dirty(shared).add(control.party);
        autoBefore=PublicationGate.counters().getOrDefault("mixed-save-complete",0L);shared.factory.setPassedTicks(Integer.MAX_VALUE-1);stage=1;ticks=0;
    }
    static void finishFirstAfterAutosave()throws Exception {
        drain(shared);require(PublicationGate.counters().getOrDefault("mixed-save-complete",0L)>autoBefore,"Real native autosave callback did not execute");
        checkedCurrent(shared,"control",controlExpected);require(dirty(shared).stream().anyMatch(p->p.getUuid().equals(owner("quarantine"))),"Quarantine dirty evidence discarded");
        primary.verifyNative(primary.owned());flag("real_mixed_owner_autosave_passed");flag("quarantined_dirty_retained_not_acknowledged");
        var oldFresh=shared.manager.getParty(owner("primary"),player.registryAccess());var oldIterator=oldFresh.iterator();var oldFreshMon=oldFresh.get(0);long epoch=session.epoch();
        shared.factory.onPlayerDisconnect(disconnected(owner("primary")));require(!session.live(),"Disconnect did not revoke epoch");session.close();requestDenied(context,"primary","disconnected_epoch_not_accessible");
        denied("disconnected_fresh_reference_revoked",oldFreshMon::getLevel);denied("iterator_lease_revoked_on_disconnect",oldIterator::hasNext);
        session=PublicationGate.publish(publication("primary"),binding(),context,primary,false,step->{});require(session.epoch()==epoch+1&&shared.manager.getParty(owner("primary"),player.registryAccess())!=oldFresh,"Reconnect reused epoch or object");flag("disconnect_republication_uses_new_epoch");
        Future<?> stale=executor(shared).submit(()->oldFreshMon.getLevel());try{stale.get(10,TimeUnit.SECONDS);throw new IllegalStateException("Old queued action succeeded");}catch(ExecutionException e){require(e.getCause() instanceof NativeFence.Denied,"Wrong queued-action failure");flag("queued_old_epoch_action_denied");}
        basicPublished(shared,context,session,"primary");shared.factory.shutdown(player.registryAccess());require(executor(shared).isTerminated()&&!session.live(),"Native shutdown did not finish and revoke");flag("mixed_native_shutdown_drains_without_lock_deadlock");session.close();primary.close();quarantine.close();checkedCurrent(shared,"control",controlExpected);flag("unrelated_owner_survives_shared_shutdown");
        var crash=fixture("crash");var s=seed(crash,"prepare-halt");var c=bind(crash);var h=hold(crash,s,"prepare-halt");h.stageReturn(step->{});
        PublicationGate.publish(publication("prepare-halt"),binding(),c,h,true,step->{if(step==PublicationGate.Step.CACHE_SWAPPED){requestDenied(c,"prepare-halt","swapped_cache_without_receipt_not_accessible");flag("hard_halt_after_swap_before_retirement");finish(94);}});
        throw new IllegalStateException("Expected halt94 did not happen");
    }
    static void secondStart()throws Exception {
        shared=fixture("shared");context=bind(shared);primary=recovered(shared,"primary");session=PublicationGate.publish(publication("primary"),binding(),context,primary,false,step->{});
        require(session.epoch()>=3,"Restart did not advance epoch");basicPublished(shared,context,session,"primary");flag("restart_revalidates_before_fresh_publication");session.close();primary.close();
        var crash=fixture("crash");var c=bind(crash);try(var h=recovered(crash,"prepare-halt")){
            refused("interrupted_prepared_retirement_stays_quarantined",()->PublicationGate.publish(publication("prepare-halt"),binding(),c,h,false,step->{}));requestDenied(c,"prepare-halt","prepared_restart_has_no_native_fallback");
        }crash.factory.shutdown(player.registryAccess());
        var conflict=seed(shared,"conflict");try(var h=hold(shared,conflict,"conflict")){
            h.stageReturn(step->{});Path p=current(shared,"conflict");
            if(backend.equals("nbt")){CompoundTag tag=NbtIo.readCompressed(p,NbtAccounter.unlimitedHeap());tag.putString("unexpected","preserve-conflict");NbtIo.writeCompressed(tag,p);}else{var json=JsonParser.parseString(Files.readString(p)).getAsJsonObject();json.addProperty("unexpected","preserve-conflict");Files.writeString(p,json.toString());}
            byte[] changed=Files.readAllBytes(p);var before=h.receipt();refused("changed_native_destination_blocks_publication",()->PublicationGate.publish(publication("conflict"),binding(),context,h,true,step->{}));
            require(Arrays.equals(changed,Files.readAllBytes(p))&&before.equals(h.receipt())&&!Files.exists(publication("conflict")),"Conflict overwritten or journal advanced");flag("publication_conflict_preserves_bytes_and_generations");
        }
        var pending=seed(shared,"retire-halt");var h=hold(shared,pending,"retire-halt");h.stageReturn(step->{});
        PublicationGate.publish(publication("retire-halt"),binding(),context,h,true,step->{if(step==PublicationGate.Step.RETIREMENT_DURABLE){requestDenied(context,"retire-halt","committed_epoch_not_exposed_early");flag("hard_halt_after_retirement_before_access");finish(95);}});
        throw new IllegalStateException("Expected halt95 did not happen");
    }
    static void thirdStart()throws Exception {
        shared=fixture("shared");context=bind(shared);primary=recovered(shared,"primary");session=PublicationGate.publish(publication("primary"),binding(),context,primary,false,step->{});basicPublished(shared,context,session,"primary");
        try(var h=recovered(shared,"retire-halt");var s=PublicationGate.publish(publication("retire-halt"),binding(),context,h,false,step->{})){
            require(s.epoch()==2,"Interrupted committed epoch not replaced");basicPublished(shared,context,s,"retire-halt");flag("committed_but_unexposed_restart_revalidates_new_epoch");
            refused("retirement_record_cannot_publish_twice",()->PublicationGate.publish(publication("retire-halt"),binding(),context,h,false,step->{}));
            var fresh=shared.manager.getParty(owner("retire-halt"),player.registryAccess());denied("fresh_sendout_remains_unsupported",()->fresh.get(0).sendOut(player.serverLevel(),player.position(),null,p->kotlin.Unit.INSTANCE));
            shared.factory.save(fresh,player.registryAccess());h.verifyNative(h.owned());flag("unchanged_read_only_save_verified_without_native_queue");
        }
        requestDenied(context,"conflict","conflicting_owner_still_quarantined");
        var crash=fixture("crash");var c=bind(crash);try(var h=recovered(crash,"prepare-halt")){refused("prepared_record_not_replayed_on_later_restart",()->PublicationGate.publish(publication("prepare-halt"),binding(),c,h,false,step->{}));}crash.factory.shutdown(player.registryAccess());
        var control=shared.factory.getPlayerParty(owner("control"),player.registryAccess());control.get(0).setNickname(Component.literal("final-unrelated-preserved"));CompoundTag expected=control.saveToNBT(new CompoundTag(),player.registryAccess()).copy();dirty(shared).add(control);
        shared.factory.shutdown(player.registryAccess());require(executor(shared).isTerminated()&&!session.live(),"Final shutdown incomplete");session.close();primary.close();checkedCurrent(shared,"control",expected);flag("final_shared_shutdown_preserves_unrelated_owner");flag("no_player_admission_or_gameplay_release_enabled");finish(0);done=true;
    }
    static void force(Path p)throws IOException{try(var c=FileChannel.open(p,StandardOpenOption.WRITE)){c.force(true);}}
    static void finish(int halt)throws Exception {
        proof.put("commit",System.getenv("GITHUB_SHA"));proof.put("backend",backend);proof.put("phase",phase);proof.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));proof.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));proof.put("counters",PublicationGate.counters());
        proof.put("admission_enabled",false);proof.put("playable_handback_verified",false);proof.put("read_only_native_publication",true);proof.put("production_changed",false);proof.put("expected_exit",halt);
        Path report=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/handback-"+backend+"-phase"+phase+".json");Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(proof));force(report);
        Path marker=Path.of("/tmp/uq-handback-result");Files.writeString(marker,"passed: read-only handback "+backend+" phase "+phase+"\n");force(marker);
        if(halt!=0){System.out.println("HANDBACK_PLANNED_HALT_"+halt);System.out.flush();Runtime.getRuntime().halt(halt);}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.partyHandback")||done)return;
        try {
            if(stage==1){if(++ticks>=5){stage=2;finishFirstAfterAutosave();}return;}
            if(stage!=0)return;
            var p=event.getServer().getPlayerList().getPlayerByName("Visitor");var admin=event.getServer().getPlayerList().getPlayerByName("Dev");if(p==null||admin==null||p.tickCount<100||event.getServer().getTickCount()<120)return;
            stage=2;player=p;backend=System.getProperty("uq.liveBackend");phase=Integer.getInteger("uq.livePhase");require(List.of("nbt","json").contains(backend),"Unknown backend");
            Path world=event.getServer().getWorldPath(LevelResource.ROOT).toRealPath();require(world.getFileName().toString().equals("jail_integrated_smoke"),"Not disposable world");root=world.resolve("party-live");Files.createDirectories(root);NativeFence.boot();
            require(!JailConfig.JAIL_PACKED_WALLETS.get(),"Wallet default changed");flag("wallet_default_off");
            if(phase==1)firstStart();else if(phase==2)secondStart();else if(phase==3)thirdStart();else throw new IOException("Unknown phase");
        }catch(Throwable failure){done=true;failure.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-handback-result"),"failed: "+failure+"\n");}catch(IOException ignored){}}
    }
}
