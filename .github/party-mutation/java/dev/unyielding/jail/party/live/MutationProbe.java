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
public final class MutationProbe {
    static Path root;static ServerPlayer player;static String backend;static int phase,stage,ticks;static boolean done;
    static final Map<String,Object> proof=new LinkedHashMap<>();
    static Fixture shared;static MutationGate.Context context;static HandoffCoordinator primary,quarantine;static MutationGate.Session session;
    static MutationGate.Lease staleLease;static MutationGate.Session retired;static byte[] originalPartyBytes;
    static PlayerPartyStore oldParty;static Pokemon oldMon,untrackedClone;static CompoundTag controlExpected;static long autoBefore;
    interface Action{void run()throws Exception;}
    record Fixture(OneToOneFileStoreAdapter adapter,FileBackedPokemonStoreFactory factory,PokemonStoreManager manager){}
    record Stores(PlayerPartyStore party,PCStore pc){}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static void flag(String name){proof.put(name,true);}
    static UUID owner(String label){return UUID.nameUUIDFromBytes(("mutation:"+backend+":"+label).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
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
    static MutationGate.Context bind(Fixture f)throws Exception{return MutationGate.bind(f.manager,f.factory,f.adapter,player.registryAccess());}
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
    static void requestDenied(MutationGate.Context c,String label,String observation)throws Exception {var a=MutationGate.requestParty(c,owner(label));require(!a.available()&&a.party()==null&&!a.reason().isEmpty(),"Denial made a fallback party");flag(observation);}
    static Pokemon current(MutationGate.Session s)throws Exception{return shared.manager.getParty(s.original().owner(),player.registryAccess()).get(0);}
    static void exact(MutationGate.Session s,String nickname)throws Exception {
        var p=shared.manager.getParty(s.original().owner(),player.registryAccess());require(p==shared.factory.getPlayerParty(s.original().owner(),player.registryAccess()),"Two cache owners");
        require(p.get(0).getNickname().getString().equals(nickname)&&p.get(0).getLevel()==17&&p.get(5).getLevel()==23&&p.get(1)==null,"Nickname or original slot state changed");s.assertIntegrity();
    }
    static void rejected(String flag,MutationGate.Session s,MutationGate.Lease lease,Pokemon target,UUID action,String name)throws Exception {
        var result=MutationGate.requestRename(s,lease,target,action,name);require(!result.accepted()&&result.current()==null&&!result.reason().isEmpty(),"Unsafe mutation accepted");flag(flag);
    }
    static MutationGate.Session rename(MutationGate.Session s,String action,String value)throws Exception {
        var result=MutationGate.requestRename(s,s.lease(),current(s),owner(action),value);require(result.accepted()&&result.current()!=null,"Safe rename refused: "+result.reason());return result.current();
    }
    static HandoffCoordinator staged(String label)throws Exception {var x=seed(shared,label);var h=hold(shared,x,label);h.stageReturn(step->{});return h;}
    static MutationGate.Session publish(HandoffCoordinator h,String label,boolean fresh)throws Exception{return MutationGate.publish(publication(label),binding(),context,h,fresh,step->{});}
    static void firstStart()throws Exception {
        shared=fixture("shared");var source=seed(shared,"primary");var q=seed(shared,"quarantine");var control=seed(shared,"control");oldParty=source.party;oldMon=oldParty.get(0);
        untrackedClone=Pokemon.Companion.loadFromNBT(player.registryAccess(),oldMon.saveToNBT(player.registryAccess(),new CompoundTag()));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);executor(shared).execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});require(entered.await(10,TimeUnit.SECONDS),"Queue blocker absent");
        try {
            oldMon.setNickname(Component.literal("stale-queued-payload"));control.party.get(0).setNickname(Component.literal("queued-control-preserved"));dirty(shared).add(oldParty);dirty(shared).add(control.party);shared.factory.saveAll(player.registryAccess());
            oldMon.setNickname(Component.literal("frozen-primary"));context=bind(shared);primary=hold(shared,source,"primary");primary.stageReturn(step->{});quarantine=hold(shared,q,"quarantine");
            session=publish(primary,"primary",true);retired=session;staleLease=session.lease();var oldFresh=current(session);var oldIterator=shared.manager.getParty(owner("primary"),player.registryAccess()).iterator();
            session=rename(session,"edit-one","Returned One");exact(session,"Returned One");require(session.epoch()==2&&!retired.live(),"Epoch not advanced or old owner active");flag("native_nickname_change_exact_full_state");flag("edit_receipt_precedes_new_access");
            denied("old_graph_revoked_after_edit",oldFresh::getLevel);denied("old_iterator_revoked_after_edit",oldIterator::hasNext);denied("original_pokemon_still_revoked",oldMon::getLevel);
            denied("untracked_clone_still_revoked",untrackedClone::getLevel);
            rejected("stale_session_request_refused",retired,staleLease,oldFresh,owner("stale-action"),"Not Applied");
            rejected("stale_generation_request_refused",session,staleLease,current(session),owner("stale-generation"),"Not Applied");
            rejected("foreign_object_request_refused",session,session.lease(),untrackedClone,owner("clone-request"),"Not Applied");
            rejected("command_replay_refused",session,session.lease(),current(session),owner("edit-one"),"Replay");
            rejected("invalid_literal_refused",session,session.lease(),current(session),owner("invalid"),"x".repeat(33));
            rejected("no_op_refused",session,session.lease(),current(session),owner("noop"),"Returned One");
            denied("direct_nickname_setter_refused",()->current(session).setNickname(Component.literal("Bypass")));denied("unapproved_level_change_refused",()->current(session).setLevel(90));
            var party=shared.manager.getParty(owner("primary"),player.registryAccess());var component=current(session).getNickname();component.append("Mutated Alias");exact(session,"Returned One");flag("nickname_read_returns_detached_component");
            var alternate=new PokemonStoreManager();alternate.registerFactory(Priority.HIGHEST,shared.factory);denied("alternate_manager_refused",()->alternate.getParty(owner("primary"),player.registryAccess()));
            denied("direct_current_loader_refused",()->shared.adapter.load(PlayerPartyStore.class,owner("primary"),player.registryAccess()));
            var snapshotBefore=retired.original();require(Arrays.equals(primary.owned().party(),snapshotBefore.party()),"Original custody snapshot rewritten");flag("original_custody_snapshot_unchanged");
            retired.close();session.assertIntegrity();flag("old_session_close_cannot_close_current_journal");
            var oldLease=session.lease();var oldTarget=current(session);retired=session;session=rename(session,"edit-two","Returned Two");exact(session,"Returned Two");
            rejected("queued_old_action_refused_after_second_edit",retired,oldLease,oldTarget,owner("queued-action"),"Must Not Apply");flag("second_edit_chains_full_state_and_new_epoch");
        }finally{release.countDown();}
        drain(shared);require(MutationGate.counters().getOrDefault("queued-write-rejected-no-receipt",0L)>=1,"Stale native write did not reject");exact(session,"Returned Two");flag("pre_enrollment_queued_write_cannot_revert_edit");
        controlExpected=control.party.saveToNBT(new CompoundTag(),player.registryAccess()).copy();checkedCurrent(shared,"control",controlExpected);flag("same_native_batch_unrelated_owner_preserved");
        var sessionNow=session;var leaseNow=session.lease();var targetNow=current(session);
        var worker=executor(shared).submit(()->{try{return MutationGate.requestRename(sessionNow,leaseNow,targetNow,owner("worker"),"Worker").accepted();}catch(Exception e){throw new RuntimeException(e);}});
        require(!worker.get(15,TimeUnit.SECONDS),"Worker got mutation capability");flag("worker_thread_cannot_mutate_current_session");
        dirty(shared).add(shared.manager.getParty(owner("primary"),player.registryAccess()));dirty(shared).add(q.party);control.party.get(0).setNickname(Component.literal("autosave-control-preserved"));controlExpected=control.party.saveToNBT(new CompoundTag(),player.registryAccess()).copy();dirty(shared).add(control.party);
        autoBefore=MutationGate.counters().getOrDefault("mixed-save-complete",0L);shared.factory.setPassedTicks(Integer.MAX_VALUE-1);stage=1;ticks=0;
    }
    static void finishFirstAfterAutosave()throws Exception {
        drain(shared);require(MutationGate.counters().getOrDefault("mixed-save-complete",0L)>autoBefore,"Real mixed autosave did not fire");checkedCurrent(shared,"control",controlExpected);exact(session,"Returned Two");
        require(dirty(shared).stream().anyMatch(p->p.getUuid().equals(owner("quarantine"))),"Quarantine was acknowledged saved");flag("real_autosave_preserves_mutated_current_and_unrelated");flag("quarantine_dirty_remains_unacknowledged");
        var previous=session;var lease=session.lease();var mon=current(session);shared.factory.onPlayerDisconnect(disconnected(owner("primary")));require(!session.live(),"Disconnect left authority open");session.close();
        session=publish(primary,"primary",false);exact(session,"Returned Two");require(session.epoch()>previous.epoch(),"Rejoin reused epoch");rejected("disconnect_stale_request_refused",previous,lease,mon,owner("disconnect-stale"),"No");flag("native_disconnect_republication_preserves_edits");
        shared.factory.save(shared.manager.getParty(owner("primary"),player.registryAccess()),player.registryAccess());exact(session,"Returned Two");flag("native_save_ack_requires_exact_durable_mutated_state");session.close();primary.close();quarantine.close();
        try(var h=staged("after-write")){
            var s=publish(h,"after-write",true);try{MutationGate.rename(s,s.lease(),current(s),owner("after-write-action"),"Written No Receipt",step->{if(step==MutationGate.EditStep.NATIVE_WRITTEN)throw new IOException("synthetic failure after verified write");});throw new IllegalStateException("Injected interruption ignored");}catch(IOException expected){flag("post_write_exception_does_not_acknowledge_success");}
            refused("post_write_incomplete_record_stays_quarantined",()->publish(h,"after-write",false));requestDenied(context,"after-write","post_write_denial_has_no_fallback");
        }
        try(var h=staged("temp-conflict")){
            var s=publish(h,"temp-conflict",true);Path p=current(shared,"temp-conflict"),temp=Path.of(p+".temp");Files.writeString(temp,"do-not-overwrite");byte[] before=Files.readAllBytes(p);
            var r=MutationGate.requestRename(s,s.lease(),current(s),owner("temp-action"),"No Overwrite");require(!r.accepted()&&Arrays.equals(before,Files.readAllBytes(p))&&Files.readString(temp).equals("do-not-overwrite"),"Uncertain temporary state overwritten");flag("temporary_destination_conflict_preserved");requestDenied(context,"temp-conflict","temp_conflict_quarantines_without_empty_fallback");
        }
        var h=staged("pending-halt");var s=publish(h,"pending-halt",true);byte[] before=Files.readAllBytes(current(shared,"pending-halt"));
        MutationGate.rename(s,s.lease(),current(s),owner("pending-action"),"Never Acknowledged",step->{if(step==MutationGate.EditStep.INTENT_DURABLE){require(Arrays.equals(before,Files.readAllBytes(current(shared,"pending-halt"))),"Disk changed before intent barrier");requestDenied(context,"pending-halt","intent_committed_access_closed");flag("hard_halt_after_intent_before_native_write");finish(96);}});
        throw new IllegalStateException("Expected halt96");
    }
    static void secondStart()throws Exception {
        shared=fixture("shared");context=bind(shared);primary=recovered(shared,"primary");session=publish(primary,"primary",false);exact(session,"Returned Two");flag("restart_loads_latest_edit_not_original_snapshot");
        rejected("replay_refused_after_restart",session,session.lease(),current(session),owner("edit-one"),"Replay");session.close();primary.close();
        for(String label:List.of("pending-halt","after-write","temp-conflict"))try(var h=recovered(shared,label)){refused("restart_quarantines_"+label,()->publish(h,label,false));requestDenied(context,label,"no_fallback_"+label);}
        var h=staged("committed-halt");var s=publish(h,"committed-halt",true);
        MutationGate.rename(s,s.lease(),current(s),owner("committed-action"),"Committed Before Stop",step->{if(step==MutationGate.EditStep.COMMIT_DURABLE){requestDenied(context,"committed-halt","edit_commit_not_exposed_before_validation");flag("hard_halt_after_edit_commit_before_publication");finish(97);}});
        throw new IllegalStateException("Expected halt97");
    }
    static void thirdStart()throws Exception {
        shared=fixture("shared");context=bind(shared);
        try(var h=recovered(shared,"committed-halt");var s=publish(h,"committed-halt",false)){
            exact(s,"Committed Before Stop");flag("complete_committed_edit_revalidated_as_fresh_graph");
            rejected("committed_command_not_replayed",s,s.lease(),current(s),owner("committed-action"),"Replay");
            denied("sendout_still_unsupported",()->current(s).sendOut(player.serverLevel(),player.position(),null,p->kotlin.Unit.INSTANCE));
        }
        try(var h=staged("changed-current")){
            var s=publish(h,"changed-current",true);var lease=s.lease();var target=current(s);Path file=current(shared,"changed-current");
            Files.writeString(file,"synthetic-changed-destination");force(file);byte[] changed=Files.readAllBytes(file);
            var result=MutationGate.requestRename(s,lease,target,owner("changed-request"),"Do Not Write");
            require(!result.accepted()&&s.receipt().equals(lease.generation())&&Arrays.equals(changed,Files.readAllBytes(file)),"Changed destination overwritten or generation advanced");
            flag("changed_current_file_blocks_edit_without_advancing_receipt");requestDenied(context,"changed-current","changed_current_withdraws_access");s.close();
        }
        primary=recovered(shared,"primary");session=publish(primary,"primary",false);exact(session,"Returned Two");
        session=rename(session,"edit-three","Final Durable Name");exact(session,"Final Durable Name");flag("post_restart_native_mutation_commits_new_generation");
        var control=shared.factory.getPlayerParty(owner("control"),player.registryAccess());control.get(0).setNickname(Component.literal("final-control"));
        var expected=control.saveToNBT(new CompoundTag(),player.registryAccess()).copy();dirty(shared).add(control);
        shared.factory.shutdown(player.registryAccess());require(executor(shared).isTerminated()&&!session.live(),"Mixed shutdown incomplete");
        session.close();primary.close();checkedCurrent(shared,"control",expected);flag("mixed_native_shutdown_preserves_mutated_and_unrelated");finish(0);done=true;
    }
    static void fourthStart()throws Exception {
        shared=fixture("shared");context=bind(shared);primary=recovered(shared,"primary");session=publish(primary,"primary",false);exact(session,"Final Durable Name");flag("graceful_restart_preserves_final_full_state");
        for(String command:List.of("edit-one","edit-two","edit-three"))rejected("persistent_replay_refusal_"+command,session,session.lease(),current(session),owner(command),"Replay");
        for(String label:List.of("pending-halt","after-write","temp-conflict"))try(var h=recovered(shared,label)){
            refused("later_restart_quarantine_"+label,()->publish(h,label,false));var access=MutationGate.requestParty(context,owner(label));require(!access.available()&&access.party()==null,"Ambiguous owner exposed");
        }
        var control=shared.factory.getPlayerParty(owner("control"),player.registryAccess());require(control.get(0).getNickname().getString().equals("final-control")&&control.get(5).getLevel()==23,"Unrelated final save missing");flag("unrelated_owner_survives_final_restart");
        denied("returned_direct_native_mutations_remain_blocked",()->current(session).setNickname(Component.literal("Bypass")));
        shared.factory.shutdown(player.registryAccess());require(executor(shared).isTerminated()&&!session.live(),"Final executor/access remains active");session.close();primary.close();
        flag("no_admission_or_playable_release_enabled");finish(0);done=true;
    }
    static void force(Path path)throws IOException{try(var channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.force(true);}}
    static void finish(int halt)throws Exception {
        proof.put("commit",System.getenv("GITHUB_SHA"));proof.put("backend",backend);proof.put("phase",phase);proof.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));proof.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));proof.put("counters",MutationGate.counters());
        proof.put("admission_enabled",false);proof.put("playable_handback_verified",false);proof.put("bounded_mutation_verified",true);proof.put("production_changed",false);proof.put("expected_exit",halt);
        Path report=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/mutation-"+backend+"-phase"+phase+".json");Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(proof));force(report);
        Path marker=Path.of("/tmp/uq-mutation-result");Files.writeString(marker,"passed: bounded mutation "+backend+" phase "+phase+"\n");force(marker);
        if(halt!=0){System.out.println("MUTATION_PLANNED_HALT_"+halt);System.out.flush();Runtime.getRuntime().halt(halt);}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.partyMutation")||done)return;
        try {
            if(stage==1){if(++ticks>=5){stage=2;finishFirstAfterAutosave();}return;}
            if(stage!=0)return;
            var p=event.getServer().getPlayerList().getPlayerByName("Visitor");var admin=event.getServer().getPlayerList().getPlayerByName("Dev");if(p==null||admin==null||p.tickCount<100||event.getServer().getTickCount()<120)return;
            stage=2;player=p;backend=System.getProperty("uq.liveBackend");phase=Integer.getInteger("uq.livePhase");require(List.of("nbt","json").contains(backend),"Unknown backend");
            Path world=event.getServer().getWorldPath(LevelResource.ROOT).toRealPath();require(world.getFileName().toString().equals("jail_integrated_smoke"),"Not disposable world");root=world.resolve("party-live");Files.createDirectories(root);NativeFence.boot();
            require(!JailConfig.JAIL_PACKED_WALLETS.get(),"Wallet default changed");flag("wallet_default_off");
            if(phase==1)firstStart();else if(phase==2)secondStart();else if(phase==3)thirdStart();else if(phase==4)fourthStart();else throw new IOException("Unknown phase");
        }catch(Throwable failure){done=true;failure.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-mutation-result"),"failed: "+failure+"\n");}catch(IOException ignored){}}
    }
}
