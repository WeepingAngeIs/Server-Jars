package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.*;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.*;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.google.gson.*;
import dev.unyielding.jail.JailConfig;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native interception tests over synthetic, initialized stores. Never calls jail with a nonempty party. */
@EventBusSubscriber(modid="uq_bundle_smoke")
@SuppressWarnings({"rawtypes","unchecked"})
public final class LiveFenceProbe {
    private static boolean done;
    private static Path root;
    private static ServerPlayer player;
    private static String backend;
    private static int phase;
    private static final Map<String,Object> proof=new LinkedHashMap<>();
    interface Action {void run()throws Exception;}
    static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    static UUID owner(String label){return UUID.nameUUIDFromBytes(("native-fence:"+backend+":"+label).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static UUID tx(String label){return owner(label+"-tx");}
    static String binding(){return System.getenv("UQ_JAIL_SHA256")+":"+System.getenv("UQ_BUNDLE_SHA256")+":"+backend;}
    static String provider(String label)throws Exception{return HandoffCoordinator.providerIdentity(adapter(label));}
    static void flag(String name){proof.put(name,true);}
    static void denied(String label,Action action)throws Exception {
        try{action.run();}catch(NativeFence.Denied expected){flag(label);return;}
        throw new IllegalStateException("Native hook allowed "+label);
    }
    static void ioDenied(String label,Action action)throws Exception {
        try{action.run();}catch(IOException expected){flag(label);return;}
        throw new IllegalStateException("Ordering check allowed "+label);
    }
    static OneToOneFileStoreAdapter adapter(String label)throws IOException {
        Path p=root.resolve(label);Files.createDirectories(p);
        return backend.equals("nbt")?new NBTStoreAdapter(p.toString(),false,true):new JSONStoreAdapter(p.toString(),false,true,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create());
    }
    static FileBackedPokemonStoreFactory factory(OneToOneFileStoreAdapter a)throws Exception {
        var f=new FileBackedPokemonStoreFactory(a,true,(kotlin.jvm.functions.Function1<UUID,PlayerPartyStore>)PlayerPartyStore::new,(kotlin.jvm.functions.Function1<UUID,PCStore>)PCStore::new);
        // Only this newly constructed scratch factory: its auto-save subscription must not run a second experiment during the test.
        Object subscription=HandoffCoordinator.field(f,"saveSubscription");subscription.getClass().getMethod("unsubscribe").invoke(subscription);
        return f;
    }
    static ExecutorService executor(FileBackedPokemonStoreFactory f)throws Exception{return (ExecutorService)HandoffCoordinator.field(f,"saveExecutor");}
    static void drain(FileBackedPokemonStoreFactory f)throws Exception{executor(f).submit(()->true).get(20,TimeUnit.SECONDS);}
    static void closeFactory(FileBackedPokemonStoreFactory f)throws Exception{executor(f).shutdown();require(executor(f).awaitTermination(20,TimeUnit.SECONDS),"Scratch writer not terminated");}
    record Fixture(OneToOneFileStoreAdapter adapter,FileBackedPokemonStoreFactory factory,PlayerPartyStore party,PCStore pc) {}
    static Fixture seed(String label)throws Exception {
        var a=adapter(label);var f=factory(a);var party=f.getPlayerParty(owner(label),player.registryAccess());var pc=f.getPC(owner(label),player.registryAccess());
        require(party.isEmpty(),"Refuse re-seeding existing native party");pc.resize(1,true,p->{throw new IllegalStateException("Unexpected overflow");});
        party.set(0,PokemonProperties.Companion.parse("eevee level=17 shiny=yes"," ","=").create(player));
        party.set(5,PokemonProperties.Companion.parse("pikachu level=23"," ","=").create(player));
        party.get(0).setScaleModifier(1.1f); // deterministic float32 wire/native equality regression
        f.save(party,player.registryAccess());f.save(pc,player.registryAccess());drain(f);
        f.save(party,player.registryAccess());drain(f);
        var s=new CobblemonSnapshotCodec(player.registryAccess()).capture(owner(label),HandoffCoordinator.providerIdentity(a),party,pc);
        Files.write(root.resolve(label+".party"),s.party());Files.write(root.resolve(label+".pc"),s.pc());
        Files.copy(a.getFile(PlayerPartyStore.class,owner(label)).toPath(),root.resolve(label+".original-native"));
        return new Fixture(a,f,party,pc);
    }
    static Path file(String label,OneToOneFileStoreAdapter a){return a.getFile(PlayerPartyStore.class,owner(label)).toPath();}
    static Path journal(String label){return root.resolve(label+"-custody");}
    static void full(String label,PartyCustodyGuard.Snapshot s)throws Exception {
        require(s.owner().equals(owner(label))&&s.provider().equals(provider(label))&&Arrays.equals(s.party(),Files.readAllBytes(root.resolve(label+".party")))&&Arrays.equals(s.pc(),Files.readAllBytes(root.resolve(label+".pc"))),"Complete original snapshot differs");
    }
    static void ordinaryControl(String label)throws Exception {
        var x=seed(label);var before=x.party.saveToNBT(new CompoundTag(),player.registryAccess());
        var manager=new PokemonStoreManager();manager.registerFactory(Priority.HIGHEST,x.factory);
        require(manager.getParty(owner(label),player.registryAccess())==x.party,"Unenrolled provider altered");
        x.factory.save(x.party,player.registryAccess());drain(x.factory);
        var loaded=(PlayerPartyStore)x.adapter.load(PlayerPartyStore.class,owner(label),player.registryAccess());
        require(before.equals(loaded.saveToNBT(new CompoundTag(),player.registryAccess())),"Unenrolled source changed");x.factory.shutdown(player.registryAccess());require(executor(x.factory).isTerminated(),"Native shutdown blocked by method lock");flag("unrelated_native_owner_unchanged");
    }
    static void payloadRegression(Fixture x)throws Exception {
        if(!backend.equals("json")){flag("payload_native_state_regression");return;}
        Gson gson=(Gson)HandoffCoordinator.field(x.adapter,"gson");
        JsonObject nativeTree=x.party.saveToJSON(new JsonObject(),player.registryAccess());
        JsonObject wire=JsonParser.parseString(gson.toJson(nativeTree)).getAsJsonObject();
        proof.put("json_tree_representation_differs",!nativeTree.equals(wire));
        HandoffCoordinator.requireJsonRoundTrip(x.party,wire,player.registryAccess());
        var foreign=new PlayerPartyStore(owner("json-negative"));
        ioDenied("json_changed_state_refused",()->HandoffCoordinator.requireJsonRoundTrip(x.party,foreign.saveToJSON(new JsonObject(),player.registryAccess()),player.registryAccess()));
        require(Float.floatToIntBits(x.party.get(0).getScaleModifier())==Float.floatToIntBits(1.1f),"Native float changed");
        flag("payload_native_state_regression");
    }
    static void scopeRegression(PartyCustodyGuard.Snapshot state,Fixture x)throws Exception {
        Path current=file("primary",x.adapter);byte[] before=Files.readAllBytes(current);
        Object exact=NativeFence.payload(state,false);
        Object changed;
        if(exact instanceof CompoundTag tag){var copy=tag.copy();copy.putString("unexpected","do not write");changed=copy;}
        else {var copy=((JsonObject)exact).deepCopy();copy.addProperty("unexpected","do not write");changed=copy;}
        ioDenied("unfrozen_payload_refused",()->NativeFence.write(state,x.adapter,current,changed,()->{throw new AssertionError("Unfrozen operation ran");}));
        var other=adapter("scope-other");AtomicReference<Future<?>> worker=new AtomicReference<>();
        NativeFence.write(state,x.adapter,current,exact,()->{
            denied("scope_does_not_allow_native_load",()->x.adapter.load(PlayerPartyStore.class,state.owner(),player.registryAccess()));
            denied("scope_refuses_wrong_temporary_path",()->x.adapter.save(new File(current+".tmp"),exact));
            denied("scope_refuses_backup_write",()->x.adapter.save(new File(current+".old"),exact));
            denied("scope_refuses_wrong_owner",()->x.adapter.save(PlayerPartyStore.class,UUID.randomUUID(),exact));
            denied("scope_refuses_mutated_payload",()->x.adapter.save(new File(current+".temp"),changed));
            denied("scope_refuses_other_adapter",()->other.save(new File(current+".temp"),exact));
            worker.set(executor(x.factory).submit(()->x.adapter.save(PlayerPartyStore.class,state.owner(),exact)));
            return null;
        });
        try{worker.get().get(20,TimeUnit.SECONDS);throw new IllegalStateException("Worker inherited IO privilege");}
        catch(ExecutionException expected){require(expected.getCause() instanceof NativeFence.Denied,"Wrong asynchronous failure");flag("scope_not_inherited_by_worker");}
        denied("scope_removed_after_use",()->x.adapter.save(PlayerPartyStore.class,state.owner(),exact));
        require(Arrays.equals(before,Files.readAllBytes(current))&&!Files.exists(Path.of(current+".temp"))&&!Files.exists(Path.of(current+".tmp")),"Negative scope probe changed source");
        flag("scope_probes_preserve_native_bytes");
    }
    static void first()throws Exception {
        var x=seed("primary");var oldMon=x.party.get(0);var iterator=x.party.iterator();var oldBox=x.pc.getBoxes().getFirst();
        CompoundTag rawData=(CompoundTag)oldMon.getClass().getMethod("getPersistentData").invoke(oldMon);
        var manager=new PokemonStoreManager();manager.registerFactory(Priority.HIGHEST,x.factory);
        payloadRegression(x);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),failed=new CountDownLatch(1);AtomicReference<Throwable> workerError=new AtomicReference<>();
        executor(x.factory).submit(()->Thread.currentThread().setUncaughtExceptionHandler((t,e)->{workerError.set(e);failed.countDown();})).get();
        executor(x.factory).execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        require(entered.await(10,TimeUnit.SECONDS),"Queue fixture did not block");
        x.factory.save(x.party,player.registryAccess()); // Real stale populated serialized write, queued BEFORE fencing.
        try(var handoff=HandoffCoordinator.start(journal("primary"),binding(),tx("primary"),player.registryAccess(),x.factory,x.adapter,x.party,x.pc,step->{})) {
            require(handoff.phase()==PartyCustodyGuard.Phase.SEALED&&handoff.receipt().generation()==2,"Removal ordering failed");
            require(handoff.owned().partyIds().isEmpty(),"Managed source not empty");handoff.verifyNative(handoff.owned());flag("fence_reserve_native_empty_then_seal_order");
            release.countDown();drain(x.factory);require(failed.await(10,TimeUnit.SECONDS)&&workerError.get() instanceof NativeFence.Denied,"Stale native worker did not hit actual fence");
            handoff.verifyNative(handoff.owned());flag("pre_enrollment_queued_write_denied_at_execution");
            scopeRegression(handoff.owned(),x);
            denied("manager_party_denied",()->manager.getParty(owner("primary"),player.registryAccess()));
            denied("manager_pc_denied",()->manager.getPC(owner("primary"),player.registryAccess()));
            denied("manager_parties_denied",()->manager.getParties(owner("primary"),player.registryAccess()));
            var converter=new com.cobblemon.mod.common.api.storage.adapter.conversions.ReforgedConversion(root.resolve("converter"));
            denied("direct_conversion_provider_load_denied",()->converter.load(PlayerPartyStore.class,owner("primary"),player.registryAccess()));
            denied("retained_identity_rewrite_denied",()->oldMon.setUuid(UUID.randomUUID()));
            denied("retained_sendout_denied",()->oldMon.sendOut(player.serverLevel(),player.position(),null,e->{throw new IllegalStateException("No send-out callback should run");}));
            denied("direct_cached_factory_denied",()->x.factory.getPlayerParty(owner("primary"),player.registryAccess()));
            denied("direct_native_current_load_denied",()->x.adapter.load(PlayerPartyStore.class,owner("primary"),player.registryAccess()));
            File backup=new File(file("primary",x.adapter)+".old");require(backup.isFile(),"Native backup absent");
            denied("direct_native_backup_load_denied",()->x.adapter.load(backup,PlayerPartyStore.class,owner("primary"),player.registryAccess()));
            var alternate=factory(adapter("alternate"));var altManager=new PokemonStoreManager();altManager.registerFactory(Priority.HIGHEST,alternate);
            denied("alternate_provider_manager_denied",()->altManager.getParty(owner("primary"),player.registryAccess()));
            denied("alternate_provider_direct_denied",()->alternate.getPlayerParty(owner("primary"),player.registryAccess()));closeFactory(alternate);
            denied("retained_party_reference_denied",()->x.party.get(0));
            denied("retained_pokemon_mutation_denied",()->oldMon.setLevel(99));
            denied("preacquired_iterator_pokemon_denied",()->iterator.next().setLevel(98));
            denied("retained_pc_box_denied",()->oldBox.setName("forbidden"));
            var foreign=new PlayerPartyStore(owner("foreign-live"));
            denied("reserved_identity_insert_elsewhere_denied",()->foreign.add(oldMon));
            // Raw nested mutable aliases cannot be revoked by Java method wrappers. They are never reused as authority.
            rawData.putString("synthetic_stale_alias_change","must not reach a save or return");
            denied("raw_alias_mutation_cannot_save",()->x.factory.save(x.party,player.registryAccess()));
            handoff.verifyNative(handoff.owned());flag("nested_alias_not_immutable_but_non_authoritative");
            byte[] clean=Files.readAllBytes(file("primary",x.adapter));Files.write(file("primary",x.adapter),new byte[]{1,2,3});
            try{denied("corrupt_current_cannot_trigger_backup_exposure",()->x.adapter.load(PlayerPartyStore.class,owner("primary"),player.registryAccess()));}
            finally{Files.write(file("primary",x.adapter),clean);}
        }finally{release.countDown();closeFactory(x.factory);}
        var failure=seed("io-failure");Path directory=file("io-failure",failure.adapter).getParent();Path stash=directory.resolveSibling("obstruction-stash");
        try {
            try{HandoffCoordinator.start(journal("io-failure"),binding(),tx("io-failure"),player.registryAccess(),failure.factory,failure.adapter,failure.party,failure.pc,step->{if(step==HandoffCoordinator.Step.RESERVATION_DURABLE){Files.move(directory,stash);Files.writeString(directory,"test-owned IO obstruction");}});throw new IllegalStateException("IO failure returned a successful handoff");}
            catch(IOException expected){flag("synchronous_native_io_failure_no_held_receipt");}
        }finally{if(Files.isRegularFile(directory)){Files.delete(directory);Files.move(stash,directory);}closeFactory(failure.factory);}
        try(var g=PartyCustodyGuard.open(journal("io-failure"),binding())){require(g.phase(tx("io-failure"))==PartyCustodyGuard.Phase.RESERVED,"Failed IO promoted ownership");}
        var uncertain=seed("empty-interrupted");
        try{HandoffCoordinator.start(journal("empty-interrupted"),binding(),tx("empty-interrupted"),player.registryAccess(),uncertain.factory,uncertain.adapter,uncertain.party,uncertain.pc,step->{if(step==HandoffCoordinator.Step.EMPTY_WRITTEN)throw new IOException("Synthetic stop before HELD receipt");});throw new IllegalStateException("Interrupted removal succeeded");}
        catch(IOException expected){flag("native_empty_without_held_receipt_stays_reserved");}finally{closeFactory(uncertain.factory);}
        ordinaryControl("control-one");finish();System.out.println("PARTY_LIVE_PLANNED_HALT_92");System.out.flush();Runtime.getRuntime().halt(92);
    }
    static void second()throws Exception {
        require(NativeFence.enrolled()==3,"Catalog did not recover before access");flag("durable_fence_recovered_before_native_lookup");
        ioDenied("same_class_different_root_recovery_refused",()->HandoffCoordinator.open(journal("primary"),binding(),owner("primary"),player.registryAccess(),adapter("alternate")));
        var a=adapter("primary");denied("restart_load_denied_before_fallback",()->a.load(PlayerPartyStore.class,owner("primary"),player.registryAccess()));
        try(var c=HandoffCoordinator.open(journal("primary"),binding(),owner("primary"),player.registryAccess(),a)) {
            require(c.phase()==PartyCustodyGuard.Phase.SEALED,"Unexpected phase after halt");c.verifyNative(c.owned());
            byte[] empty=Files.readAllBytes(file("primary",a));var receipt=c.receipt();Files.write(file("primary",a),Files.readAllBytes(root.resolve("primary.original-native")));
            try{ioDenied("occupied_native_destination_refused",()->c.stageReturn(step->{}));require(c.receipt().equals(receipt),"Conflict advanced generation");require(Arrays.equals(Files.readAllBytes(file("primary",a)),Files.readAllBytes(root.resolve("primary.original-native"))),"Conflict overwritten");}
            finally{Files.write(file("primary",a),empty);}
            Path pcFile=a.getFile(PCStore.class,owner("primary")).toPath();byte[] originalPC=Files.readAllBytes(pcFile);
            if(backend.equals("nbt")){var nbt=net.minecraft.nbt.NbtIo.readCompressed(pcFile,net.minecraft.nbt.NbtAccounter.unlimitedHeap());nbt.putString("synthetic_destination_conflict","must remain untouched");net.minecraft.nbt.NbtIo.writeCompressed(nbt,pcFile);}
            else {var json=JsonParser.parseString(Files.readString(pcFile)).getAsJsonObject();json.addProperty("synthetic_destination_conflict","must remain untouched");Files.writeString(pcFile,json.toString());}
            byte[] changedPC=Files.readAllBytes(pcFile);
            try{ioDenied("changed_native_pc_destination_refused",()->c.stageReturn(step->{}));require(c.receipt().equals(receipt)&&Arrays.equals(changedPC,Files.readAllBytes(pcFile)),"PC conflict overwritten or advanced");}finally{Files.write(pcFile,originalPC);}
            c.stageReturn(step->{});full("primary",c.owned());c.verifyNative(c.owned());require(c.receipt().generation()==3,"Return generation missing");flag("exact_return_staged_before_any_unlock");
            denied("staged_return_not_player_accessible",()->a.load(PlayerPartyStore.class,owner("primary"),player.registryAccess()));
            ioDenied("repeat_return_refused",()->c.stageReturn(step->{}));
        }
        for(String label:List.of("io-failure","empty-interrupted"))try(var c=HandoffCoordinator.open(journal(label),binding(),owner(label),player.registryAccess(),adapter(label))){
            require(c.phase()==PartyCustodyGuard.Phase.RESERVED,"Ambiguous removal changed phase");ioDenied("ambiguous_"+label+"_no_owned_view",c::owned);denied("ambiguous_"+label+"_native_denied",()->adapter(label).load(PlayerPartyStore.class,owner(label),player.registryAccess()));
        }
        var x=seed("return-interrupted");
        try(var c=HandoffCoordinator.start(journal("return-interrupted"),binding(),tx("return-interrupted"),player.registryAccess(),x.factory,x.adapter,x.party,x.pc,step->{})){
            closeFactory(x.factory);
            c.stageReturn(step->{if(step==HandoffCoordinator.Step.RETURN_DURABLE){flag("hard_halt_after_return_generation_before_native_write");finish();System.out.println("PARTY_LIVE_PLANNED_HALT_93");System.out.flush();Runtime.getRuntime().halt(93);}});
            throw new IllegalStateException("Intended interrupted return did not halt");
        }
    }
    static void third()throws Exception {
        require(NativeFence.enrolled()==4,"Interrupted return lost reservation");flag("all_fences_survived_second_halt");
        var a=adapter("primary");try(var c=HandoffCoordinator.open(journal("primary"),binding(),owner("primary"),player.registryAccess(),a)){
            require(c.phase()==PartyCustodyGuard.Phase.RESTORED,"Completed return phase changed");full("primary",c.owned());c.verifyNative(c.owned());flag("complete_staged_state_survived_restart_without_alias_change");ioDenied("terminal_return_no_replay",()->c.stageReturn(step->{}));
        }
        var interrupted=adapter("return-interrupted");try(var c=HandoffCoordinator.open(journal("return-interrupted"),binding(),owner("return-interrupted"),player.registryAccess(),interrupted)){
            require(c.phase()==PartyCustodyGuard.Phase.RESTORED,"Return intent lost");full("return-interrupted",c.owned());ioDenied("interrupted_return_native_mismatch_not_hidden",()->c.verifyNative(c.owned()));ioDenied("interrupted_return_not_replayed",()->c.stageReturn(step->{}));
        }
        for(String label:List.of("primary","io-failure","empty-interrupted","return-interrupted")){
            var adapter=adapter(label);denied("persistent_native_quarantine_"+label,()->adapter.load(PlayerPartyStore.class,owner(label),player.registryAccess()));
        }
        ordinaryControl("control-three");finish();
    }
    static void finish()throws Exception {
        proof.put("commit",System.getenv("GITHUB_SHA"));proof.put("backend",backend);proof.put("phase",phase);proof.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));proof.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));proof.put("native_denials",NativeFence.denials());
        proof.put("nonempty_admission_enabled",false);proof.put("player_handback_verified",false);proof.put("production_changed",false);
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/party-live-"+backend+"-phase"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        Files.writeString(Path.of("/tmp/uq-party-live-result"),"passed: native fence "+backend+" phase "+phase+"\n");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("uq.partyLiveFence")||done)return;
        try {
            var p=event.getServer().getPlayerList().getPlayerByName("Visitor");var admin=event.getServer().getPlayerList().getPlayerByName("Dev");
            if(p==null||admin==null||p.tickCount<100||event.getServer().getTickCount()<120)return;
            done=true;player=p;backend=System.getProperty("uq.liveBackend");phase=Integer.getInteger("uq.livePhase");
            require(List.of("nbt","json").contains(backend),"Unsupported scratch backend");
            Path world=event.getServer().getWorldPath(LevelResource.ROOT).toRealPath();require(world.getFileName().toString().equals("jail_integrated_smoke"),"Not disposable world");root=world.resolve("party-live");Files.createDirectories(root);
            require(!JailConfig.JAIL_PACKED_WALLETS.get(),"Packed wallet default changed");NativeFence.boot();require(NativeFence.ready(),"Fence not ready");flag("wallet_default_off");
            if(phase==1)first();else if(phase==2)second();else if(phase==3)third();else throw new IOException("Unknown phase");
        }catch(Throwable fail){fail.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-party-live-result"),"failed: "+fail+"\n");}catch(IOException ignored){} }
    }
}
