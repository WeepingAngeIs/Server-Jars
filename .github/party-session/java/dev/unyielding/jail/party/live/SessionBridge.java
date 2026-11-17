package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.*;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.*;
import dev.unyielding.jail.JailConfig;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Loopback integration only. Normal Cobblemon gameplay is not authorized by this transport. */
@EventBusSubscriber(modid="uq_bundle_smoke")
@SuppressWarnings({"rawtypes","unchecked"})
public final class SessionBridge {
    public static void trace(String event,ServerPlayer p){
        if(!Boolean.getBoolean("uq.sessionDiagnostic"))return;
        var c=p==null?null:connections.get(p.getUUID());
        System.out.println("JAIL_LIFECYCLE event="+event+" thread="+Thread.currentThread().getName()
            +" owner="+(p==null?"none":p.getUUID())+" player="+System.identityHashCode(p)
            +" listener="+(p==null?0:System.identityHashCode(p.connection))
            +" tick="+(p==null?-1:p.tickCount)+" prepared="+prepared
            +" leased="+(c!=null)+" samePlayer="+(c!=null&&c.player==p));
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void diagnosticSync(net.neoforged.neoforge.event.OnDatapackSyncEvent e){trace("datapack-event-highest",e.getPlayer());}
    @SubscribeEvent
    public static void diagnosticLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p)trace("logged-in-event",p);
    }
    static final String MANAGER="com.cobblemon.mod.common.api.storage.PokemonStoreManager#";
    static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    static final UUID VISITOR=UUID.nameUUIDFromBytes("OfflinePlayer:Visitor".getBytes(StandardCharsets.UTF_8));
    static final UUID CONTROL=UUID.nameUUIDFromBytes("OfflinePlayer:Dev".getBytes(StandardCharsets.UTF_8));
    static final UUID QUARANTINE=UUID.nameUUIDFromBytes("jail-session-quarantine".getBytes(StandardCharsets.UTF_8));
    static ServerPlayer callbackPlayer;static JsonObject callbackRequest;
    static final Map<String,Object> proof=new LinkedHashMap<>();
    static final Map<UUID,ConnectionLease> connections=new HashMap<>();
    static final List<Map<String,Object>> connectionHistory=new ArrayList<>();
    static MinecraftServer server;static Path root;static int phase,doneTick=-1;static boolean done,prepared;
    static MutationGate.Context context;static HandoffCoordinator custody,quarantine;static Pending pending;
    static PlayerPartyStore sourceParty;static Pokemon sourceMon;static CompoundTag controlExpected;static long autoBefore;
    static int visitorConnections,disconnects,nativeDenials,accepted;static ConnectionLease retired;
    static final class ConnectionLease {
        final ServerPlayer player;final Object listener;final UUID nonce=UUID.randomUUID();MutationGate.Session session;boolean valid=true;
        ConnectionLease(ServerPlayer p,MutationGate.Session s){player=p;listener=p.connection;session=s;}
    }
    record Pending(ConnectionLease connection,MutationGate.Session session,MutationGate.Lease lease,Pokemon target,UUID action,String name){}
    static boolean enabled(){return Boolean.getBoolean("uq.partySession");}
    static UUID action(String name){return UUID.nameUUIDFromBytes(("jail-session-action:"+name).getBytes(StandardCharsets.UTF_8));}
    static void require(boolean b,String why)throws IOException{if(!b)throw new IOException(why);}
    static void flag(String name){proof.put(name,true);}
    static String binding(){return System.getenv("UQ_JAIL_SHA256")+":"+System.getenv("UQ_BUNDLE_SHA256")+":global-session";}
    static Path state(String name){return root.resolve(name);}
    static ExecutorService executor()throws Exception{return (ExecutorService)HandoffCoordinator.field(GlobalProfile.factory(),"saveExecutor");}
    static Set<PokemonStore<?>> dirty()throws Exception{return (Set<PokemonStore<?>>)HandoffCoordinator.field(GlobalProfile.factory(),"dirtyStores");}
    static void drain()throws Exception{executor().submit(()->true).get(20,TimeUnit.SECONDS);}
    static boolean enrolled(UUID owner)throws Exception {
        var f=NativeFence.class.getDeclaredField("OWNERS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).containsKey(owner);
    }
    static boolean current(ConnectionLease c,ServerPlayer p){return c!=null&&c.valid&&c.player==p&&c.listener==p.connection&&server.getPlayerList().getPlayer(p.getUUID())==p;}
    static Pokemon target(ConnectionLease c)throws Exception{return GlobalProfile.manager().getParty(c.player.getUUID(),c.player.registryAccess()).get(0);}
    static String receipt(MutationGate.Session s){return s.receipt().journal()+":"+s.receipt().generation()+":"+s.receipt().recordHash()+":"+s.receipt().stateHash();}
    static void fail(Throwable failure){
        done=true;failure.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-session-result"),"failed: "+failure+"\n");}catch(IOException ignored){}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!enabled())return;
        try{
            server=event.getServer();phase=Integer.getInteger("uq.sessionPhase");require(phase==1||phase==2,"Unsupported session phase");
            Path world=server.getWorldPath(LevelResource.ROOT).toRealPath();require(world.getFileName().toString().equals("jail_integrated_smoke"),"Not disposable world");
            root=world.resolve("party-live/player-session");Files.createDirectories(root);NativeFence.boot();
            // Establish the actual configured cache types without replacing or unregistering a provider.
            var global=Cobblemon.INSTANCE.getStorage();UUID bootstrap=action("bootstrap");
            global.getParty(bootstrap,server.registryAccess());global.getPC(bootstrap,server.registryAccess());
            GlobalProfile.bind(server);flag("actual_global_singleton_and_configured_factory");
            proof.put("global_profile",GlobalProfile.description());
            require(!JailConfig.JAIL_PACKED_WALLETS.get(),"Packed-wallet default changed");flag("wallet_default_off");
            if(phase==2){
                prepared=true;flag("restart_opens_original_custody_receipt");
            }
        }catch(Throwable failure){fail(failure);}
    }
    static void prepare(ServerPlayer visitor)throws Exception {
        require(!prepared&&phase==1&&visitor.getUUID().equals(VISITOR),"Refuse implicit reseed");
        var f=GlobalProfile.factory();var a=GlobalProfile.adapter();var reg=visitor.registryAccess();
        sourceParty=GlobalProfile.manager().getParty(visitor);var pc=GlobalProfile.manager().getPC(visitor);
        require(sourceParty.isEmpty()&&!pc.iterator().hasNext(),"Nonempty preexisting test source");
        pc.resize(1,true,p->{throw new IllegalStateException("Unexpected PC overflow");});
        sourceParty.set(0,PokemonProperties.Companion.parse("eevee level=17 shiny=yes"," ","=").create(visitor));
        sourceParty.set(5,PokemonProperties.Companion.parse("pikachu level=23"," ","=").create(visitor));
        sourceMon=sourceParty.get(0);sourceMon.setNickname(Component.literal("Network Original"));sourceMon.setScaleModifier(1.1f);
        var control=GlobalProfile.manager().getParty(CONTROL,reg);require(control.isEmpty(),"Unexpected control party");
        control.set(0,PokemonProperties.Companion.parse("pikachu level=31"," ","=").create(visitor));control.get(0).setNickname(Component.literal("control-original"));
        var q=f.getPlayerParty(QUARANTINE,reg);var qp=f.getPC(QUARANTINE,reg);require(q.isEmpty()&&!qp.iterator().hasNext(),"Unexpected quarantine source");
        qp.resize(1,true,p->{throw new IllegalStateException("Overflow");});q.set(0,PokemonProperties.Companion.parse("eevee level=9"," ","=").create(visitor));
        if(Boolean.getBoolean("uq.callbackContract"))SessionCallbackProbe.baseline(visitor);
        for(PokemonStore<?> store:List.of(sourceParty,pc,control,q,qp))f.save(store,reg);drain();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        executor().execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        require(entered.await(10,TimeUnit.SECONDS),"Native executor did not enter blocker");
        try{
            control.get(0).setNickname(Component.literal("queued-control"));dirty().add(control);dirty().add(sourceParty);f.saveAll(reg);
            context=MutationGate.bind(GlobalProfile.manager(),f,a,reg);
            custody=HandoffCoordinator.start(state("custody"),binding(),action("custody"),reg,f,a,sourceParty,pc,step->{});
            custody.stageReturn(step->{});
            quarantine=HandoffCoordinator.start(state("quarantine"),binding(),action("quarantine"),reg,f,a,q,qp,step->{});dirty().add(q);
        }finally{release.countDown();}
        drain();require(MutationGate.counters().getOrDefault("queued-write-rejected-no-receipt",0L)>0,"Global stale native write not fenced");
        require(control.get(0).getNickname().getString().equals("queued-control"),"Unrelated mixed source changed");
        flag("global_pre_enrollment_queued_write_refused");flag("global_source_and_staged_return_exact");prepared=true;
    }
    static void hello(ServerPlayer p,JsonObject request)throws Exception {
        trace("custom-hello",p);
        if(!p.getUUID().equals(VISITOR)||!p.getGameProfile().getName().equals("Visitor")){flag("unrelated_connection_cannot_claim_target");send(p,"DENIED",label(request),null);return;}
        if(p.tickCount<100||GlobalProfile.manager()==null){send(p,"WAIT",label(request),null);return;}
        if(!prepared&&Boolean.getBoolean("uq.callbackContract")&&p.serverLevel().getGameTime()%20!=0){
            callbackPlayer=p;callbackRequest=request.deepCopy();send(p,"WAIT",label(request),null);return;
        }
        if(!prepared)prepare(p);
        if(context==null){context=MutationGate.bind(GlobalProfile.manager(),GlobalProfile.factory(),GlobalProfile.adapter(),p.registryAccess());custody=HandoffCoordinator.open(state("custody"),binding(),VISITOR,p.registryAccess(),GlobalProfile.adapter());}
        ConnectionLease c=connections.get(p.getUUID());
        if(c!=null){require(current(c,p),"Different connection cannot inherit session");snapshot(c,label(request));return;}
        var s=MutationGate.publish(state("publication"),binding(),context,custody,phase==1&&visitorConnections==0,step->{});
        c=new ConnectionLease(p,s);connections.put(p.getUUID(),c);visitorConnections++;
        connectionHistory.add(Map.of("index",visitorConnections,"nonce",c.nonce.toString(),"epoch",s.epoch(),"player_object",System.identityHashCode(p),"listener_object",System.identityHashCode(p.connection)));
        require(GlobalProfile.manager().getParty(p)==GlobalProfile.factory().getPlayerParty(p.getUUID(),p.registryAccess()),"Global manager/cache disagree");s.assertIntegrity();
        flag("global_player_overload_returns_only_current_graph");flag("actual_network_hello_received");
        if(Boolean.getBoolean("uq.callbackContract"))SessionCallbackProbe.enrolled(p,s);
        if(retired!=null){
            require(!retired.valid&&retired.player!=p&&retired.listener!=p.connection&&!retired.nonce.equals(c.nonce),"Network identity reused");
            try{GlobalProfile.manager().getParty(retired.player);throw new IOException("Old ServerPlayer acquired current party");}catch(NativeFence.Denied expected){flag("old_serverplayer_reference_refused");}
            var r=MutationGate.requestRename(retired.session,retired.session.lease(),(Pokemon)retired.session.graph.stream().filter(Pokemon.class::isInstance).findFirst().orElseThrow(),action("old-session"),"Must Not Apply");
            require(!r.accepted(),"Old session mutated successor");flag("real_reconnect_has_fresh_player_listener_nonce_epoch");
        }
        if(pending!=null){
            require(pending.connection()!=c&&!pending.connection().valid,"Queued request retargeted");
            require(!MutationGate.requestRename(pending.session(),pending.lease(),pending.target(),pending.action(),pending.name()).accepted(),"Deferred old mutation accepted");
            pending=null;flag("queued_prior_connection_action_refused");
        }
        snapshot(c,label(request));
    }
    static ConnectionLease authorize(ServerPlayer p,JsonObject r)throws Exception {
        ConnectionLease c=connections.get(p.getUUID());require(current(c,p),"No current connection");
        require(r.has("owner")&&r.get("owner").getAsString().equals(p.getUUID().toString()),"Claimed owner mismatch");
        require(r.get("nonce").getAsString().equals(c.nonce.toString())&&r.get("epoch").getAsLong()==c.session.epoch()&&r.get("receipt").getAsString().equals(receipt(c.session)),"Stale connection or exact generation");
        c.session.assertIntegrity();return c;
    }
    public static void request(ServerPlayer p,String wire){
        if(!enabled())return;
        JsonObject r=null;
        try{
            require(server!=null&&server.isSameThread()&&!done,"Session transport unavailable");r=JsonParser.parseString(wire).getAsJsonObject();String op=r.get("op").getAsString();
            if(op.equals("HELLO")){hello(p,r);return;}
            ConnectionLease c=authorize(p,r);String q=label(r);
            if(op.equals("ACK")){require(r.get("hash").getAsString().equals(NativeFence.hash(c.session.original())),"Client acknowledged a different full state");flag("client_acknowledged_exact_snapshot");send(p,"ACK",q,null);return;}
            if(op.equals("RENAME")){
                var before=c.session;var lease=before.lease();Pokemon mon=target(c);
                require(r.get("pokemon").getAsString().equals(mon.getUuid().toString()),"Foreign target identity");
                var result=MutationGate.requestRename(before,lease,mon,UUID.fromString(r.get("action").getAsString()),r.get("name").getAsString());
                if(!result.accepted()){require(before.receipt().equals(lease.generation()),"Refused request advanced generation");flag("duplicate_or_invalid_action_refused_without_advance");send(p,"DENIED",q,null);return;}
                c.session=result.current();accepted++;require(!before.live(),"Prior graph still authorized");
                try{mon.getLevel();throw new IOException("Old Pokemon still accessible");}catch(NativeFence.Denied expected){flag("stale_native_object_refused_after_network_edit");}
                require(GlobalProfile.manager().getParty(p)==c.session.stores.party(),"Edited global cache not current");
                flag("network_edit_receipt_precedes_current_cache");snapshot(c,q);return;
            }
            if(op.equals("QUEUE")){require(pending==null,"Queue already occupied");pending=new Pending(c,c.session,c.session.lease(),target(c),action("delayed"),"Must Not Apply");flag("network_action_captured_with_original_session");send(p,"QUEUED",q,null);return;}
            if(op.equals("RECONNECT")){require(phase==1&&visitorConnections==1&&pending!=null,"Unexpected reconnect request");send(p,"RECONNECT",q,null);return;}
            if(op.equals("DONE")){
                require(pending==null&&accepted==(phase==1?2:1),"Incomplete network edits");
                require(phase==2||visitorConnections==2&&disconnects>=1&&nativeDenials>=1,"Reconnect or native denial missing");
                require(target(c).getNickname().getString().equals(phase==1?"Network Two":"Network Three"),"Final network state wrong");
                var control=GlobalProfile.manager().getParty(CONTROL,p.registryAccess());control.get(0).setNickname(Component.literal("saved-control-"+phase));
                controlExpected=control.saveToNBT(new CompoundTag(),p.registryAccess()).copy();dirty().add(control);dirty().add(c.session.stores.party());
                autoBefore=MutationGate.counters().getOrDefault("mixed-save-complete",0L);GlobalProfile.factory().setPassedTicks(Integer.MAX_VALUE-1);doneTick=server.getTickCount();send(p,"DONE",q,null);return;
            }
            throw new IOException("Unsupported request");
        }catch(IOException|IllegalArgumentException|IllegalStateException|NullPointerException|JsonParseException|NativeFence.Denied refusal){
            flag("safe_request_denial_without_empty_fallback");try{send(p,"DENIED",r==null?"malformed":label(r),null);}catch(Throwable sendFailure){fail(sendFailure);}
        }catch(Throwable failure){fail(failure);}
    }
    static String label(JsonObject r){return r.has("q")?r.get("q").getAsString():"unknown";}
    static void send(ServerPlayer p,String code,String q,JsonObject data){
        JsonObject r=data==null?new JsonObject():data;r.addProperty("code",code);r.addProperty("q",q);r.addProperty("phase",phase);
        PacketDistributor.sendToPlayer(p,new SessionWire.Reply(GSON.toJson(r)));
    }
    static void snapshot(ConnectionLease c,String q)throws Exception {
        require(current(c,c.player),"Cannot sync detached connection");c.session.assertIntegrity();var s=c.session.original();
        JsonObject r=new JsonObject();r.addProperty("owner",s.owner().toString());r.addProperty("provider",s.provider());r.addProperty("nonce",c.nonce.toString());
        r.addProperty("epoch",c.session.epoch());r.addProperty("receipt",receipt(c.session));r.addProperty("hash",NativeFence.hash(s));
        r.addProperty("party",Base64.getEncoder().encodeToString(s.party()));r.addProperty("pc",Base64.getEncoder().encodeToString(s.pc()));r.addProperty("pokemon",target(c).getUuid().toString());
        r.addProperty("swap_other",c.session.stores.party().get(5).getUuid().toString());r.addProperty("name",target(c).getNickname().getString());r.addProperty("joins",visitorConnections);send(c.player,"SNAPSHOT",q,r);flag("full_state_snapshot_sent_after_receipt");
    }
    public static Object invoke(Object receiver,String key,Object[] args,NativeFence.Call original)throws Throwable {
        if(key.startsWith(MANAGER)&&args.length==1&&args[0] instanceof ServerPlayer p)trace(key,p);
        if(enabled()&&key.startsWith(MANAGER)&&args.length==1&&args[0] instanceof ServerPlayer p&&GlobalProfile.manager()!=null&&receiver==GlobalProfile.manager()&&enrolled(p.getUUID())){
            if(key.contains("#onPlayerDataSync(")&&!Boolean.getBoolean("uq.sessionDiagnostic")){flag("native_sync_deferred_no_empty_party_substitute");return null;}
            if(key.contains("#onPlayerDisconnect(")){
                ConnectionLease c=connections.remove(p.getUUID());
                if(c!=null){require(c.player==p&&c.listener==p.connection,"Stale disconnect cannot retire current connection");c.valid=false;retired=c;disconnects++;}
                Object result=MutationGate.invoke(receiver,key,args,original);
                if(c!=null){require(!c.session.live(),"Actual disconnect failed to suspend");c.session.close();flag("actual_native_disconnect_suspends_and_revokes");}
                return result;
            }
            if(key.contains("#getParty(" )||key.contains("#getPC(")){
                ConnectionLease c=connections.get(p.getUUID());if(!current(c,p))throw new NativeFence.Denied("Stale or unpublished network player");
                c.session.assertIntegrity();return key.contains("#getParty(")?GlobalProfile.manager().getParty(p.getUUID(),p.registryAccess()):GlobalProfile.manager().getPC(p.getUUID(),p.registryAccess());
            }
        }
        return MutationGate.invoke(receiver,key,args,original);
    }
    /** Exact native handler entry rejection. No gameplay mutation is granted by custom synchronization. */
    public static boolean blockNative(ServerPlayer p,String handler){
        if(!enabled())return false;
        try{if(!enrolled(p.getUUID()))return false;nativeDenials++;flag("native_gameplay_request_rejected_at_boundary");send(p,"NATIVE_DENIED",handler,null);return true;}catch(Throwable failure){fail(failure);return true;}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!enabled()||done||server==null)return;
        try{
            if(callbackPlayer!=null&&callbackPlayer.serverLevel().getGameTime()%20==0){
                var p=callbackPlayer;var r=callbackRequest;callbackPlayer=null;callbackRequest=null;
                SessionBoundary.request(p,r.toString());if(done)return;
            }
            if(server.getTickCount()>6000)throw new IOException("Network fixture timeout");
            if(doneTick<0||server.getTickCount()-doneTick<8)return;
            drain();require(MutationGate.counters().getOrDefault("mixed-save-complete",0L)>autoBefore,"Global native autosave did not run");
            var loaded=(PlayerPartyStore)GlobalProfile.adapter().load(PlayerPartyStore.class,CONTROL,server.registryAccess());
            require(controlExpected.equals(loaded.saveToNBT(new CompoundTag(),server.registryAccess())),"Unrelated global autosave mismatch");
            require(dirty().stream().anyMatch(s->s.getUuid().equals(QUARANTINE))||phase==2,"Quarantined dirty evidence lost");
            var c=connections.get(VISITOR);require(current(c,c.player),"Lost final connection");c.session.assertIntegrity();
            try{GlobalProfile.adapter().load(PlayerPartyStore.class,VISITOR,server.registryAccess());throw new IOException("Direct native fallback bypass");}catch(NativeFence.Denied expected){flag("direct_primary_adapter_load_stays_blocked");}
            flag("real_global_mixed_autosave_preserves_exact_state");flag("unrelated_global_owner_saved");flag("nonempty_admission_not_enabled");
            proof.put("commit",System.getenv("GITHUB_SHA"));proof.put("phase",phase);proof.put("connections",connectionHistory);proof.put("native_disconnects",disconnects);proof.put("accepted_edits",accepted);proof.put("mutation_counters",MutationGate.counters());
            proof.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));proof.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));proof.put("production_changed",false);proof.put("playable_custody_verified",false);proof.put("client_sync","test-only full snapshot, not ordinary Cobblemon UI");
            Path report=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/session-phase"+phase+".json");Files.writeString(report,GSON.toJson(proof));
            Files.writeString(Path.of("/tmp/uq-session-result"),"passed: actual global session phase "+phase+"\n");done=true;
        }catch(Throwable failure){fail(failure);}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(!enabled()||GlobalProfile.factory()==null)return;
        try{
            Map<String,Object> row=new LinkedHashMap<>();row.put("commit",System.getenv("GITHUB_SHA"));row.put("phase",phase);row.put("native_executor_terminated",executor().isTerminated());row.put("connections_revoked",connections.isEmpty());row.put("disconnects",disconnects);row.put("counters",MutationGate.counters());
            Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/session-phase"+phase+"-shutdown.json"),GSON.toJson(row));
        }catch(Throwable failure){failure.printStackTrace();}
    }
}
