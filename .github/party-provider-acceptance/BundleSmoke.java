package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.essentials.*;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.*;
import dev.unyielding.quests.spin.DailyLogin;
import dev.unyielding.quests.team.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.handler.*;
import net.neoforged.neoforge.server.permission.nodes.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Public synthetic permission-provider contract, actual clients and assembled production JAR. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final ResourceLocation PROVIDER=ResourceLocation.parse("uq_bundle_smoke:meeting_contract");
    static final Path CONTROL=Path.of("/tmp/uq-meeting-provider-controls"),RESULT=Path.of("/tmp/uq-meeting-provider-result");
    static final Set<String> RECEIPTS=ConcurrentHashMap.newKeySet();
    static final Map<UUID,Mode> modes=new HashMap<>();
    static final Map<UUID,Integer> queries=new HashMap<>();
    static final List<String> trace=new ArrayList<>(),outcomes=new ArrayList<>();
    static final Map<String,Object> diagnostics=new LinkedHashMap<>();
    static final Map<String,String> requests=new HashMap<>();
    static final Map<String,Integer> processedAt=new HashMap<>();
    static final Map<UUID,ServerPlayer> known=new HashMap<>();
    static final double DEST_X=132.5,DEST_Y=81,DEST_Z=132.5,START_X=138.5;
    static UUID ownerId,visitorId,partyId,unrelatedId;
    static MinecraftServer server;
    static ServerPlayer oldSetter;
    static Entity mount;
    static String oldReady,leaveResult;
    static boolean initialized,done,callbackFired,disconnectSeen;
    static int tick,step,start,stageTick,sequence,initializedAt,setup;
    static Snapshot before;
    static final Deque<Case> cases=new ArrayDeque<>();
    enum Mode {ALLOW,DENY,RUNTIME,LINKAGE,SECOND_DENY,LEAVE_VISITOR}
    interface Body{void tick()throws Exception;}
    record Case(String name,Body body){}
    record PlayerState(List<String> inventory,int xp,float xpProgress,int xpTotal,long[] spins,Set<Long> quests,Set<Long> tasks,Set<Long> claimed,Map<String,Loc> homes,List<Loc> back){}
    record Snapshot(Map<UUID,PlayerState> players,CompoundTag points,CompoundTag unrelated,Map<String,Long> cooldowns,Loc visitorPosition,Loc ownerPosition){}
    public record Barrier(String token)implements CustomPacketPayload {
        public static final Type<Barrier> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:meeting_barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Barrier> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Barrier::token,Barrier::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Receipt(String token)implements CustomPacketPayload {
        public static final Type<Receipt> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:meeting_receipt"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Receipt> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Receipt::token,Receipt::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public BundleSmoke(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Handler event)->event.addPermissionHandler(PROVIDER,ContractHandler::new));
        bus.addListener((RegisterPayloadHandlersEvent event)->{
            var r=event.registrar("1");
            r.playToServer(Barrier.TYPE,Barrier.CODEC,(payload,ctx)->{
                var p=(ServerPlayer)ctx.player();String name=p.getGameProfile().getName();
                require(name.equals("Dev")||name.equals("Visitor"),"Unexpected fixture client");
                try{write(CONTROL.resolve(name+".processed"),payload.token());}catch(Exception bad){throw new IllegalStateException(bad);}
                Net.sendTo(p,new Receipt(payload.token()));
            });
            r.playToClient(Receipt.TYPE,Receipt.CODEC,(payload,ctx)->RECEIPTS.add(payload.token()));
        });
    }
    public static final class ContractHandler implements IPermissionHandler {
        final DefaultPermissionHandler fallback;
        public ContractHandler(Collection<PermissionNode<?>> nodes){fallback=new DefaultPermissionHandler(nodes);}
        public ResourceLocation getIdentifier(){return PROVIDER;}
        public Set<PermissionNode<?>> getRegisteredNodes(){return fallback.getRegisteredNodes();}
        @SuppressWarnings("unchecked") public <T>T getPermission(ServerPlayer p,PermissionNode<T> node,PermissionDynamicContext<?>... context) {
            if(!node.equals(PartyMeetings.USE))return fallback.getPermission(p,node,context);
            int count=queries.merge(p.getUUID(),1,Integer::sum);Mode mode=modes.getOrDefault(p.getUUID(),Mode.ALLOW);
            trace.add(p.getGameProfile().getName()+":"+node.getNodeName()+":"+count+":"+mode);
            if(mode==Mode.RUNTIME)throw new IllegalStateException("Synthetic public provider unavailable");
            if(mode==Mode.LINKAGE)throw new LinkageError("Synthetic public provider linkage failure");
            if(mode==Mode.LEAVE_VISITOR&&!callbackFired) {
                callbackFired=true;leaveResult=TeamManager.leave(visitor());
                trace.add("public TeamManager.leave(Visitor)="+leaveResult);
            }
            return (T)Boolean.valueOf(mode!=Mode.DENY&&(mode!=Mode.SECOND_DENY||count<2));
        }
        public <T>T getOfflinePermission(UUID p,PermissionNode<T> node,PermissionDynamicContext<?>... context){return fallback.getOfflinePermission(p,node,context);}
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static Path evidence(){return Path.of(System.getenv("UQ_MEETING_PROVIDER_EVIDENCE"));}
    static ServerPlayer dev(){return server.getPlayerList().getPlayerByName("Dev");}
    static ServerPlayer visitor(){return server.getPlayerList().getPlayerByName("Visitor");}
    static ServerPlayer ref(UUID id){var p=server.getPlayerList().getPlayer(id);return p==null?known.get(id):p;}
    static Team party(){return TeamManager.get(partyId);}
    static void ok(String error){require(error==null,"Native party action failed: "+error);}
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    @SuppressWarnings("unchecked") static Map<String,Long> cooldowns()throws Exception{var f=Essentials.class.getDeclaredField("COOLDOWNS");f.setAccessible(true);return(Map<String,Long>)f.get(null);}
    static PartyMeetingData meeting()throws Exception{var m=PartyMeetingData.class.getDeclaredMethod("get",MinecraftServer.class);m.setAccessible(true);return(PartyMeetingData)m.invoke(null,server);}
    static CompoundTag points()throws Exception{return meeting().save(new CompoundTag(),server.registryAccess()).copy();}
    static DailyLogin.Data spins(){return server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(),DailyLogin.Data.NAME);}
    static QuestData quests(){return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(),QuestData.NAME);}
    static PlayerState playerState(ServerPlayer p) {
        List<String> items=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++)items.add(i+":"+p.getInventory().getItem(i).saveOptional(server.registryAccess()));
        var progress=QuestManager.progressOf(p);var data=Essentials.data();
        return new PlayerState(List.copyOf(items),p.experienceLevel,p.experienceProgress,p.totalExperience,spins().players.get(p.getUUID()).clone(),Set.copyOf(progress.completedQuests),Set.copyOf(progress.completedTasks),Set.copyOf(quests().claimedRewards.getOrDefault(p.getUUID(),Set.of())),Map.copyOf(data.homes.getOrDefault(p.getUUID(),Map.of())),List.copyOf(data.back.getOrDefault(p.getUUID(),List.of())));
    }
    static Snapshot snapshot()throws Exception {
        Map<UUID,PlayerState> players=new HashMap<>();for(UUID id:List.of(ownerId,visitorId))players.put(id,playerState(ref(id)));
        return new Snapshot(players,points(),TeamManager.get(unrelatedId).toTag(true).copy(),Map.copyOf(cooldowns()),Loc.of(ref(visitorId)),Loc.of(ref(ownerId)));
    }
    static boolean near(ServerPlayer p,Loc old){return p.level().dimension().location().toString().equals(old.dim())&&p.distanceToSqr(old.x(),old.y(),old.z())<0.0001;}
    static void preserved(boolean keepPoints)throws Exception {
        var now=snapshot();
        for(UUID id:List.of(ownerId,visitorId)) {
            var a=before.players().get(id);var b=now.players().get(id);
            require(a.inventory().equals(b.inventory()),"Native inventory/components/currency changed for "+id);
            preservedPlayer(a,b,id);
        }
        require(before.unrelated().equals(now.unrelated()),"Unrelated party changed");
        if(keepPoints)require(before.points().equals(now.points()),"Stored meeting points changed");
    }
    static void preservedPlayer(PlayerState a,PlayerState b,UUID id) {
        require(a.xp()==b.xp()&&a.xpProgress()==b.xpProgress()&&a.xpTotal()==b.xpTotal(),"XP changed for "+id);
        require(Arrays.equals(a.spins(),b.spins()),"Earned/daily spin balance changed for "+id);
        require(a.quests().equals(b.quests())&&a.tasks().equals(b.tasks())&&a.claimed().equals(b.claimed()),"Personal quest progress or claims changed for "+id);
        require(a.homes().equals(b.homes())&&a.back().equals(b.back()),"Private home/back history changed for "+id);
    }
    static void denied(String actor)throws Exception {
        preserved(true);require(before.cooldowns().equals(cooldowns()),"Rejected command consumed/changed cooldown");
        require(near(actor.equals("Dev")?dev():visitor(),actor.equals("Dev")?before.ownerPosition():before.visitorPosition()),"Rejected command moved "+actor);
        diagnostics.put(cases.peek().name()+" step "+step,Map.of("provider_queries",List.copyOf(trace),"visitor_position",Loc.of(visitor()).toString(),"cooldown_keys",new TreeSet<>(cooldowns().keySet()),"point_unchanged",true));
    }
    static void arrived()throws Exception {
        preserved(true);require(visitor().distanceToSqr(DEST_X,DEST_Y,DEST_Z)<0.0001,"Allowed real client did not arrive");
        require(Essentials.cooldownRemaining(visitor(),"partymeet")>0,"Successful meeting travel did not start cooldown");
    }
    static void request(String who,String action,String argument)throws Exception{String id=""+(++sequence);requests.put(who,id);write(CONTROL.resolve(who+".txt"),id+"\t"+action+(argument.isEmpty()?"":"\t"+argument));}
    static void command(String who,String commands)throws Exception{request(who,"commands",commands);}
    static boolean ack(String who)throws Exception {
        String id=requests.get(who);Path p=CONTROL.resolve(who+".ack");if(id==null||!Files.exists(p))return false;String value=Files.readString(p);if(!value.startsWith(id+"\t"))return false;
        require(value.equals(id+"\tpassed"),"Client action failed: "+value);Path done=CONTROL.resolve(who+".processed");if(!Files.exists(done)||!Files.readString(done).equals(id))return false;
        return tick-processedAt.computeIfAbsent(id,k->tick)>=2;
    }
    static void next(){step++;stageTick=tick;}
    static void finish(){outcomes.add("passed: "+cases.peek().name());System.out.println("MEETING PROVIDER "+outcomes.getLast());cases.remove();start=0;step=0;}
    static void add(String name,Body body){cases.add(new Case(name,body));}
    static void transfer(ServerPlayer owner,ServerPlayer target){ok(TeamManager.setRank(owner,target.getGameProfile().getName(),Team.Rank.OWNER));ok(TeamManager.confirm(owner,partyId.toString(),target.getUUID().toString()));}
    static void restoreMembership() {
        require(dev()!=null&&visitor()!=null,"Both real clients must be online before a case");
        if(!TeamManager.teamIdOf(ownerId).equals(partyId)){ok(TeamManager.invite(visitor(),"Dev"));ok(TeamManager.join(dev(),partyId.toString()));}
        if(!TeamManager.teamIdOf(visitorId).equals(partyId)){ok(TeamManager.invite(dev(),"Visitor"));ok(TeamManager.join(visitor(),partyId.toString()));}
        if(!party().owner.equals(ownerId))transfer(visitor(),dev());
        if(party().rankOf(visitorId)!=Team.Rank.MEMBER)ok(TeamManager.setRank(dev(),"Visitor",Team.Rank.MEMBER));
    }
    static void base()throws Exception {
        modes.clear();queries.clear();trace.clear();callbackFired=false;leaveResult="not fired";
        restoreMembership();EssConfig.PARTY_MEET_ENABLED.set(true);EssConfig.PARTY_MEET_PUBLIC.set(true);EssConfig.PARTY_MEET_COOLDOWN.set(60);EssConfig.ADMINS_EXEMPT_COOLDOWNS.set(false);
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());
        if(mount!=null){visitor().stopRiding();mount.discard();mount=null;}
        for(UUID id:List.of(ownerId,visitorId)){var p=ref(id);p.setInvulnerable(true);p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(20);cooldowns().remove("partymeet/"+id);}
        dev().teleportTo(server.overworld(),DEST_X,DEST_Y,DEST_Z,0,0);visitor().teleportTo(server.overworld(),START_X,DEST_Y,DEST_Z,0,0);before=snapshot();
    }
    static void cases() {
        add("provider DENY overrides operator and public defaults for go, set and clear",()->{
            if(step==0){base();server.getPlayerList().op(visitor().getGameProfile());require(visitor().hasPermissions(2),"Operator-denial precondition was not established");modes.put(visitorId,Mode.DENY);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")){denied("Visitor");require(queries.getOrDefault(visitorId,0)>0,"Caller provider was not queried");modes.put(ownerId,Mode.DENY);queries.clear();trace.clear();before=snapshot();command("Dev","partymeet set;partymeet clear");next();}
            else if(step==2&&ack("Dev")){denied("Dev");require(queries.getOrDefault(ownerId,0)>=2,"Both owner commands did not query the provider");finish();}
        });
        add("caller RuntimeException and LinkageError fail closed without reward or cooldown effects",()->{
            if(step==0){base();modes.put(visitorId,Mode.RUNTIME);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")){denied("Visitor");require(queries.getOrDefault(visitorId,0)>0,"Runtime-failure provider not queried");modes.put(visitorId,Mode.LINKAGE);queries.clear();trace.clear();before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==2&&ack("Visitor")){denied("Visitor");require(queries.getOrDefault(visitorId,0)>0,"Linkage-failure provider not queried");finish();}
        });
        add("setter DENY and provider exceptions refuse an otherwise permitted caller",()->{
            if(step==0){base();modes.put(ownerId,Mode.DENY);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step>=1&&step<=3&&ack("Visitor")) {
                denied("Visitor");require(queries.getOrDefault(visitorId,0)>=2&&queries.getOrDefault(ownerId,0)>0,"Caller/setter execution permissions were not both checked");
                if(step==3){finish();return;}
                modes.put(ownerId,step==1?Mode.RUNTIME:Mode.LINKAGE);queries.clear();trace.clear();before=snapshot();command("Visitor","partymeet go");next();
            }
        });
        add("explicit provider ALLOW grants a nonoperator access while public default is false",()->{
            if(step==0){base();EssConfig.PARTY_MEET_PUBLIC.set(false);require(!visitor().hasPermissions(2),"Positive contract caller unexpectedly operator");before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")){arrived();require(queries.getOrDefault(visitorId,0)>=2&&queries.getOrDefault(ownerId,0)>0,"Allowed path skipped actual provider queries");diagnostics.put(cases.peek().name(),List.copyOf(trace));finish();}
        });
        add("provider revocation between first check and execution denies travel",()->{
            if(step==0){base();modes.put(visitorId,Mode.SECOND_DENY);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")){denied("Visitor");require(queries.getOrDefault(visitorId,0)>=2,"Execution revalidation never queried the provider again");finish();}
        });
        add("ordered synthetic setter-provider callback removes caller before movement",()->{
            if(step==0){base();modes.put(ownerId,Mode.LEAVE_VISITOR);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")) {
                require(callbackFired&&leaveResult==null,"Native TeamManager.leave callback was not successful: "+leaveResult);
                require(!party().isMember(visitorId)&&TeamManager.teamIdOf(visitorId).equals(visitorId),"Callback did not actually remove caller membership");
                denied("Visitor");finish();
            }
        });
        add("actual setter logout and reconnect preserve point while revalidating online permission",()->{
            if(step==0){base();oldSetter=dev();oldReady=Files.readString(CONTROL.resolve("Dev.ready"));request("Dev","reconnect","");next();}
            else if(step==1&&dev()==null){disconnectSeen=true;before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==2&&ack("Visitor")){require(dev()==null,"Setter reconnected before offline refusal was exercised");require(queries.getOrDefault(visitorId,0)>0,"Offline-setter command did not reach permission handling");denied("Visitor");write(CONTROL.resolve("Dev.resume"),"offline refusal processed");next();}
            else if(step==3) {
                if(dev()==null||dev()==oldSetter||dev().tickCount<100)return;
                Path ready=CONTROL.resolve("Dev.ready");if(!Files.exists(ready)||Files.readString(ready).equals(oldReady))return;
                require(disconnectSeen,"No server-observed setter disconnect");preserved(true);before=snapshot();command("Visitor","partymeet go");next();
            }
            else if(step==4&&ack("Visitor")){arrived();require(queries.getOrDefault(ownerId,0)>0,"Reconnected setter permission was not queried");finish();}
        });
        add("native setter leave and rejoin as member require restored officer rights",()->{
            if(step==0){base();transfer(dev(),visitor());ok(TeamManager.leave(dev()));ok(TeamManager.invite(visitor(),"Dev"));ok(TeamManager.join(dev(),partyId.toString()));require(party().rankOf(ownerId)==Team.Rank.MEMBER,"Setter did not rejoin as a member");before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")){require(queries.getOrDefault(visitorId,0)>0,"Rejoined-setter command did not reach permission handling");denied("Visitor");ok(TeamManager.setRank(visitor(),"Dev",Team.Rank.OFFICER));command("Dev","partymeet set");next();}
            else if(step==2&&ack("Dev")){require(party().rankOf(ownerId)==Team.Rank.OFFICER,"Setter officer rights not restored");before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==3&&ack("Visitor")){arrived();transfer(visitor(),dev());ok(TeamManager.setRank(dev(),"Visitor",Team.Rank.MEMBER));finish();}
        });
        add("disabled feature preserves stored point and re-enabling revalidates access",()->{
            if(step==0){base();EssConfig.PARTY_MEET_ENABLED.set(false);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==1&&ack("Visitor")){denied("Visitor");EssConfig.PARTY_MEET_ENABLED.set(true);before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==2&&ack("Visitor")){arrived();finish();}
        });
        add("native passenger cannot travel or consume a meeting cooldown",()->{
            if(step==0){base();mount=EntityType.BOAT.create(server.overworld());require(mount!=null,"Native boat could not be created");mount.setPos(START_X,DEST_Y,DEST_Z);mount.setNoGravity(true);require(server.overworld().addFreshEntity(mount),"Native boat did not enter the world");require(visitor().startRiding(mount,true),"Native passenger relationship was not established");next();}
            else if(step==1&&tick-stageTick>=20){require(visitor().isPassenger(),"Native passenger relationship did not persist");before=snapshot();command("Visitor","partymeet go");next();}
            else if(step==2&&ack("Visitor")){require(queries.getOrDefault(visitorId,0)>0,"Passenger command did not reach permission handling");denied("Visitor");require(visitor().isPassenger(),"Refused meeting command dismounted the passenger");visitor().stopRiding();mount.discard();mount=null;finish();}
        });
    }
    static void initialize()throws Exception {
        require("1".equals(System.getenv().getOrDefault("UQ_MEETING_PROVIDER_PHASE","1")),"This bounded fixture uses one phase");
        Files.createDirectories(CONTROL);Files.createDirectories(evidence());
        Path jar=Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        require(sha.equals(System.getenv("UQ_BUNDLE_SHA256")),"Candidate SHA-256 differs from prepared file");
        require(jar.equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"Production was not loaded from assembled JAR");
        require(PROVIDER.equals(PermissionAPI.getActivePermissionHandler()),"Synthetic provider was not selected normally: "+PermissionAPI.getActivePermissionHandler());
        require(PermissionAPI.getRegisteredNodes().contains(PartyMeetings.USE),"Production permission node was not registered");
        require(ModList.get().isLoaded("openpartiesandclaims"),"Actual public OPAC is required");
        require(!ModList.get().isLoaded("minecraftjail")&&!ModList.get().isLoaded("minecraft_jail")&&server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"Jail must remain separate");
        try{Class.forName("dev.unyielding.quests.jail.JailData");throw new IllegalStateException("Integrated Jail was loaded");}catch(ClassNotFoundException expected){}
        diagnostics.put("jars",Map.of(jar.getFileName().toString(),sha));diagnostics.put("jail_loaded",false);diagnostics.put("provider_identifier",PermissionAPI.getActivePermissionHandler().toString());
        diagnostics.put("policy_scope","Public synthetic NeoForge IPermissionHandler contract; generated policy is not authoritative live policy. Actual OPAC is loaded, no reflected provider replacement.");
        Config.SPIN_ENABLED.set(false);Config.DAILY_ENABLED.set(false);Config.SHARE_QUEST_PROGRESS.set(false);QuestManager.onSharingChanged(false);
        ownerId=dev().getUUID();visitorId=visitor().getUUID();known.put(ownerId,dev());known.put(visitorId,visitor());
        ok(TeamManager.createParty(dev(),"Meeting contract fixture"));partyId=TeamManager.teamIdOf(ownerId);ok(TeamManager.invite(dev(),"Visitor"));ok(TeamManager.join(visitor(),partyId.toString()));
        var unrelated=new Team();unrelated.id=UUID.randomUUID();unrelated.type=Team.Type.PARTY;unrelated.name="Unrelated meeting sentinel";unrelated.owner=UUID.randomUUID();unrelated.members.put(unrelated.owner,Team.Rank.OWNER);unrelatedId=unrelated.id;TeamManager.data().teams.put(unrelatedId,unrelated);TeamManager.data().setDirty();
        var book=new QuestFile();
        for(int i=1;i<=2;i++) {
            var quest=new QuestFile.Quest();quest.id=9_100_000L+i;quest.title="Disposable preserved progress "+i;
            var task=new QuestFile.Task();task.id=9_200_000L+i;task.quest=quest.id;task.type=QuestFile.TaskType.CHECKMARK;
            var reward=new QuestFile.Reward();reward.id=9_300_000L+i;reward.quest=quest.id;reward.type=QuestFile.RewardType.XP_LEVELS;reward.amount=1;
            quest.tasks.add(task);quest.rewards.add(reward);book.quests.put(quest.id,quest);book.tasks.put(task.id,task);book.rewards.put(reward.id,reward);
        }
        QuestManager.file=book;
        var coin=BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("lightmanscurrency:coin_gold")).orElseThrow();
        for(UUID id:List.of(ownerId,visitorId)) {
            var p=ref(id);p.getInventory().clearContent();var diamond=new ItemStack(Items.DIAMOND,id.equals(ownerId)?3:5);diamond.set(DataComponents.CUSTOM_NAME,Component.literal("Meeting preservation sentinel "+p.getGameProfile().getName()));p.getInventory().setItem(2,diamond);p.getInventory().setItem(11,new ItemStack(coin,id.equals(ownerId)?11:7));p.inventoryMenu.broadcastFullState();p.giveExperienceLevels(id.equals(ownerId)?3:1);
            spins().players.put(id,new long[]{20_000,4,2,id.equals(ownerId)?11:4,123_456_789});
            var progress=QuestManager.progressOf(p);progress.completedQuests.add(id.equals(ownerId)?9_100_001L:9_100_002L);progress.completedTasks.add(id.equals(ownerId)?9_200_001L:9_200_002L);quests().markClaimed(id,id.equals(ownerId)?9_300_001L:9_300_002L);
            Essentials.data().homes.put(id,new LinkedHashMap<>(Map.of("private-sentinel",new Loc("minecraft:overworld",42.5,81,51.5,17,3))));
            Essentials.data().back.put(id,new ArrayList<>(List.of(new Loc("minecraft:overworld",38.5,81,47.5,29,5))));
        }
        spins().setDirty();Essentials.data().setDirty();quests().setDirty();
        for(int x=128;x<=144;x++)for(int z=128;z<=140;z++){server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);for(int y=81;y<=84;y++)server.overworld().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        base();cases();require(cases.size()==10,"Bounded case count drifted");
        diagnostics.put("readiness",readiness());diagnostics.put("preservation_scope","Exact native inventory including physical gold coins, XP, seeded daily/earned spins, personal completed quests/tasks/claims, private homes/back and unrelated party. Expected lifecycle changes to membership and lastSeen are not asserted unchanged. This does not certify every external bank currency.");
    }
    static Map<String,Object> readiness() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("server_tick",tick);
        for(String name:List.of("Dev","Visitor")){var p=server.getPlayerList().getPlayerByName(name);Map<String,Object> data=new LinkedHashMap<>();data.put("server_present",p!=null);data.put("server_player_ticks",p==null?-1:p.tickCount);Path f=CONTROL.resolve(name+".ready");try{data.put("client_ready",Files.exists(f)?Files.readString(f):"missing");}catch(Exception bad){data.put("client_ready_error",bad.toString());}result.put(name,data);}return result;
    }
    static Map<String,Object> observed()throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();result.put("provider_trace",List.copyOf(trace));result.put("leave_callback_fired",callbackFired);result.put("leave_callback_result",String.valueOf(leaveResult));result.put("meeting_points",points().toString());result.put("cooldowns",new TreeMap<>(cooldowns()));
        List<Object> players=new ArrayList<>();for(UUID id:List.of(ownerId,visitorId)){var p=ref(id);Map<String,Object> state=new LinkedHashMap<>();state.put("name",p.getGameProfile().getName());state.put("position",Loc.of(p).toString());state.put("team",TeamManager.teamIdOf(id).toString());state.put("inventory",playerState(p).inventory());state.put("spins",spins().players.get(id).clone());state.put("passenger",p.isPassenger());players.add(state);}result.put("players",players);return result;
    }
    static void completed()throws Exception {
        done=true;Files.createDirectories(evidence());long failed=outcomes.stream().filter(s->s.startsWith("failed:")).count();long executed=outcomes.stream().filter(s->!s.startsWith("failed: fixture =>")).count();
        boolean passed=failed==0&&executed==10&&disconnectSeen;
        try{diagnostics.put("final_native_state",observed());}catch(Exception bad){diagnostics.put("final_native_state_error",bad.toString());}
        Map<String,Object> report=new LinkedHashMap<>();report.put("commit",System.getenv("GITHUB_SHA"));report.put("passed",passed);report.put("case_count",executed);report.put("failures",failed);report.put("cases",outcomes);report.put("jars",diagnostics.get("jars"));report.put("diagnostics",diagnostics);report.put("real_setter_disconnect_seen",disconnectSeen);report.put("private_or_live_data_used",false);
        Files.writeString(evidence().resolve("party-provider-phase-1.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");Files.writeString(evidence().resolve("cases-phase-1.txt"),String.join("\n",outcomes)+"\n");
        if(Files.isDirectory(CONTROL)){Path copy=evidence().resolve("client-controls");Files.createDirectories(copy);try(var files=Files.list(CONTROL)){for(var f:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().endsWith(".tmp")).toList())Files.copy(f,copy.resolve(f.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
        Files.writeString(RESULT,(passed?"passed: ":"failed: ")+executed+" party provider cases, "+failed+" failures\n");
    }
    void tick(ServerTickEvent.Post event) {
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        server=event.getServer();tick++;
        try {
            if(!initialized) {
                if(tick>=6000){diagnostics.put("startup_readiness",readiness());throw new IllegalStateException("Native clients not ready within 6000 server ticks");}
                if(dev()==null||visitor()==null||dev().tickCount<100||visitor().tickCount<100)return;
                for(var p:List.of(dev(),visitor())){Path ready=CONTROL.resolve(p.getGameProfile().getName()+".ready");if(!Files.exists(ready)||!Files.readString(ready).startsWith(p.getUUID()+"\t"))return;}
                initialize();initialized=true;initializedAt=tick;return;
            }
            for(var p:List.of("Dev","Visitor")){var online=server.getPlayerList().getPlayerByName(p);if(online!=null)known.put(online.getUUID(),online);}
            if(setup==0) {if(tick-initializedAt<80)return;command("Dev","partymeet set");setup=1;return;}
            if(setup==1) {require(tick-initializedAt<1200,"Initial native meeting setup did not finish");if(!ack("Dev"))return;require(points().getList("points",10).stream().anyMatch(t->((CompoundTag)t).getUUID("party").equals(partyId)),"Native client did not save initial meeting point");setup=2;return;}
            if(cases.isEmpty()){completed();return;}
            var current=cases.peek();
            if(start==0){start=tick;stageTick=tick;step=0;System.out.println("MEETING PROVIDER START "+current.name());}
            try{require(tick-start<1200,"Case timed out at step "+step);current.body().tick();}
            catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: "+current.name()+" at step "+step+" => "+bad);try{diagnostics.put("failure "+current.name(),observed());}catch(Exception failure){diagnostics.put("failure "+current.name(),failure.toString());}cases.remove();start=0;step=0;}
        } catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: fixture => "+bad);try{completed();}catch(Exception ignored){done=true;}}
    }
}
