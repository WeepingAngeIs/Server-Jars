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
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Five bounded selector checks using native clients and the actual assembled production JAR. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final Path CONTROL=Path.of("/tmp/uq-destination-controls"),RESULT=Path.of("/tmp/uq-destination-result");
    static final Set<String> RECEIPTS=ConcurrentHashMap.newKeySet();
    static final List<String> outcomes=new ArrayList<>();
    static final Map<String,Object> diagnostics=new LinkedHashMap<>();
    static final Map<String,String> requests=new HashMap<>();
    static final Map<String,Integer> processedAt=new HashMap<>();
    static final Loc DEV_HOME=loc(132.5,132.5),VISITOR_HOME=loc(140.5,132.5),PUBLIC_WARP=loc(148.5,132.5);
    static final Loc DEV_START=loc(132.5,140.5),VISITOR_START=loc(140.5,140.5);
    static UUID devId,visitorId,unrelatedId;
    static MinecraftServer server;
    static boolean initialized,done;
    static int tick,step,start,sequence,initializedAt;
    static Snapshot before;
    static final Deque<Case> cases=new ArrayDeque<>();
    interface Body{void tick()throws Exception;}
    record Case(String name,Body body){}
    record PlayerState(List<String> inventory,int xp,float xpProgress,int xpTotal,long[] spins,Set<Long> quests,Set<Long> tasks,Set<Long> claimed,Map<String,Loc> homes,List<Loc> back,Loc position){}
    record Snapshot(Map<UUID,PlayerState> players,Map<String,Loc> warps,CompoundTag unrelated,Map<String,Long> cooldowns){}
    public record Barrier(String token)implements CustomPacketPayload {
        public static final Type<Barrier> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:destination_barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Barrier> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Barrier::token,Barrier::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Receipt(String token)implements CustomPacketPayload {
        public static final Type<Receipt> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:destination_receipt"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Receipt> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Receipt::token,Receipt::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public BundleSmoke(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::tick);
        bus.addListener((RegisterPayloadHandlersEvent event)->{
            var r=event.registrar("1");
            r.playToServer(Barrier.TYPE,Barrier.CODEC,(payload,ctx)->{
                var p=(ServerPlayer)ctx.player();String name=p.getGameProfile().getName();
                require(name.equals("Dev")||name.equals("Visitor"),"Unexpected destination fixture client");
                try{write(CONTROL.resolve(name+".processed"),payload.token());}catch(Exception bad){throw new IllegalStateException(bad);}
                Net.sendTo(p,new Receipt(payload.token()));
            });
            r.playToClient(Receipt.TYPE,Receipt.CODEC,(payload,ctx)->RECEIPTS.add(payload.token()));
        });
    }
    static Loc loc(double x,double z){return new Loc("minecraft:overworld",x,81,z,0,0);}
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static Path evidence(){return Path.of(System.getenv("UQ_DESTINATION_EVIDENCE"));}
    static ServerPlayer dev(){return server.getPlayerList().getPlayerByName("Dev");}
    static ServerPlayer visitor(){return server.getPlayerList().getPlayerByName("Visitor");}
    static List<ServerPlayer> players(){return List.of(dev(),visitor());}
    static ServerPlayer player(String name){return name.equals("Dev")?dev():visitor();}
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    @SuppressWarnings("unchecked") static Map<String,Long> cooldowns()throws Exception{var f=Essentials.class.getDeclaredField("COOLDOWNS");f.setAccessible(true);return(Map<String,Long>)f.get(null);}
    static DailyLogin.Data spins(){return server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(),DailyLogin.Data.NAME);}
    static QuestData quests(){return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(),QuestData.NAME);}
    static PlayerState playerState(ServerPlayer p) {
        List<String> items=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++)items.add(i+":"+p.getInventory().getItem(i).saveOptional(server.registryAccess()));
        var progress=QuestManager.progressOf(p);var data=Essentials.data();
        return new PlayerState(List.copyOf(items),p.experienceLevel,p.experienceProgress,p.totalExperience,spins().players.get(p.getUUID()).clone(),Set.copyOf(progress.completedQuests),Set.copyOf(progress.completedTasks),Set.copyOf(quests().claimedRewards.getOrDefault(p.getUUID(),Set.of())),Map.copyOf(data.homes.getOrDefault(p.getUUID(),Map.of())),List.copyOf(data.back.getOrDefault(p.getUUID(),List.of())),Loc.of(p));
    }
    static Snapshot snapshot()throws Exception {
        Map<UUID,PlayerState> p=new HashMap<>();for(var player:players())p.put(player.getUUID(),playerState(player));
        return new Snapshot(p,Map.copyOf(Essentials.data().warps),TeamManager.get(unrelatedId).toTag(true).copy(),Map.copyOf(cooldowns()));
    }
    static boolean near(ServerPlayer player,Loc loc){return player.level().dimension().location().toString().equals(loc.dim())&&player.distanceToSqr(loc.x(),loc.y(),loc.z())<0.0001;}
    static Snapshot conserved()throws Exception {
        var now=snapshot();
        for(var p:players()) {
            var a=before.players().get(p.getUUID());var b=now.players().get(p.getUUID());
            require(a.inventory().equals(b.inventory()),"Native inventory/components/physical coins changed for "+p.getGameProfile().getName());
            require(a.xp()==b.xp()&&a.xpProgress()==b.xpProgress()&&a.xpTotal()==b.xpTotal(),"XP changed");
            require(Arrays.equals(a.spins(),b.spins()),"Earned/daily spin balance changed");
            require(a.quests().equals(b.quests())&&a.tasks().equals(b.tasks())&&a.claimed().equals(b.claimed()),"Personal quest progress or claims changed");
            require(a.homes().equals(b.homes()),"Private saved home data changed");
        }
        require(before.warps().equals(now.warps()),"Saved warp data changed");
        require(before.unrelated().equals(now.unrelated()),"Unrelated party changed");
        return now;
    }
    static void denied()throws Exception {
        var now=conserved();require(before.cooldowns().equals(now.cooldowns()),"Refused command changed a cooldown");
        for(var p:players()) {
            var a=before.players().get(p.getUUID());var b=now.players().get(p.getUUID());
            require(a.back().equals(b.back()),"Refused command changed private back history");
            require(near(p,a.position()),"Refused command moved "+p.getGameProfile().getName());
        }
        record("denied",now);
    }
    static void allowed(String actor,Loc target,String kind)throws Exception {
        var now=conserved();var actorId=player(actor).getUUID();String key=kind+"/"+actorId;
        require(!before.cooldowns().containsKey(key),"Allowed fixture precondition already had this cooldown");
        for(var p:players()) {
            var a=before.players().get(p.getUUID());var b=now.players().get(p.getUUID());List<Loc> expected=new ArrayList<>(a.back());
            if(p.getUUID().equals(actorId)){expected.add(a.position());while(expected.size()>EssConfig.BACK_MAX.get())expected.remove(0);require(near(p,target),"Native row did not reach caller's authoritative destination");}
            else require(near(p,a.position()),"Other player moved during selection");
            require(expected.equals(b.back()),"Native back history did not match the one allowed actor departure");
        }
        Map<String,Long> expected=new HashMap<>(before.cooldowns());var until=now.cooldowns().get(key);
        require(until!=null&&until>System.currentTimeMillis()&&Essentials.cooldownRemaining(player(actor),kind)>0,"Normal native selection did not start its cooldown");
        expected.put(key,until);require(expected.equals(now.cooldowns()),"Selection changed another cooldown");record("allowed "+actor+" "+kind,now);
    }
    static void record(String action,Snapshot now) {
        diagnostics.put(cases.peek().name()+" step "+step,Map.of("action",action,"positions",players().stream().map(p->p.getGameProfile().getName()+":"+Loc.of(p)).toList(),"cooldowns",new TreeMap<>(now.cooldowns()),"saved_home_warp_and_unrelated_data_unchanged",true));
    }
    static void request(String who,String action,String... arguments)throws Exception {
        String id=""+(++sequence);requests.put(who,id);write(CONTROL.resolve(who+".txt"),id+"\t"+action+(arguments.length==0?"":"\t"+String.join("\t",arguments)));
    }
    static void list(String who,String kind,String label)throws Exception{request(who,"list",kind,label);}
    static void click(String who,String label,String name)throws Exception{request(who,"click",label,name);}
    static boolean ack(String who)throws Exception {
        String id=requests.get(who);Path p=CONTROL.resolve(who+".ack");if(id==null||!Files.exists(p))return false;String value=Files.readString(p);if(!value.startsWith(id+"\t"))return false;
        require(value.equals(id+"\tpassed"),"Client action failed: "+value);Path done=CONTROL.resolve(who+".processed");if(!Files.exists(done)||!Files.readString(done).equals(id))return false;
        return tick-processedAt.computeIfAbsent(id,k->tick)>=2;
    }
    static CompoundTag screen(String who,String label)throws Exception {
        var data=TagParser.parseTag(Files.readString(CONTROL.resolve(who+".screen."+label+".snbt")));
        require(data.getString("fixtureScreen").equals("dev.unyielding.quests.client.DestinationScreen"),"Not the actual production destination screen");return data;
    }
    static void screenMatches(String who,String label,String kind,Set<String> expected)throws Exception {
        var data=screen(who,label);
        require(data.getString("kind").equals(kind)&&data.getBoolean("allowed"),"Native menu has wrong kind or access flag");
        require(data.getAllKeys().equals(Set.of("kind","allowed","remaining","cooldown","exempt","omitted","entries","fixtureScreen")),"Unexpected private fields in destination payload: "+data.getAllKeys());
        require(data.getInt("cooldown")==60&&!data.getBoolean("exempt")&&data.getInt("omitted")==0,"Native cooldown/list metadata differs");
        Set<String> actual=new HashSet<>();for(Tag tag:data.getList("entries",Tag.TAG_COMPOUND)) {
            var row=(CompoundTag)tag;require(row.getAllKeys().equals(Set.of("name","dimension")),"Destination row leaked coordinates/owner/extra data");
            require(row.getString("dimension").equals("minecraft:overworld"),"Native dimension display differs from seeded destination");require(actual.add(row.getString("name")),"Duplicate native destination row");
        }
        require(actual.equals(expected),who+" received wrong/private destination names: "+actual+" expected "+expected);
        diagnostics.put("captured "+who+" "+label,data.toString());
    }
    static void next(){step++;}
    static void finish(){outcomes.add("passed: "+cases.peek().name());System.out.println("DESTINATION "+outcomes.getLast());cases.remove();start=0;step=0;}
    static void add(String name,Body body){cases.add(new Case(name,body));}
    static void base()throws Exception {
        EssConfig.HOME_ENABLED.set(true);EssConfig.WARP_ENABLED.set(true);EssConfig.ADMINS_EXEMPT_COOLDOWNS.set(false);
        EssConfig.HOME_MAX.set(2);
        EssConfig.HOME_COOLDOWN.set(60);EssConfig.WARP_COOLDOWN.set(60);EssConfig.BACK_ONLY_ON_DEATH.set(false);EssConfig.BACK_MAX.set(10);
        for(var p:players()) {
            p.setInvulnerable(true);p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(20);
            cooldowns().remove("home/"+p.getUUID());cooldowns().remove("warp/"+p.getUUID());
            Essentials.data().homes.put(p.getUUID(),new LinkedHashMap<>(Map.of("shared",p==dev()?DEV_HOME:VISITOR_HOME,p==dev()?"dev-only":"visitor-only",p==dev()?loc(132.5,136.5):loc(140.5,136.5))));
            Essentials.data().back.put(p.getUUID(),new ArrayList<>(List.of(new Loc("minecraft:overworld",42.5,81,51.5,17,3))));
            Loc target=p==dev()?DEV_START:VISITOR_START;p.teleportTo(server.overworld(),target.x(),target.y(),target.z(),target.yaw(),target.pitch());
        }
        Essentials.data().warps.clear();Essentials.data().warps.put("public-warp",PUBLIC_WARP);Essentials.data().setDirty();before=snapshot();
    }
    static void cases() {
        add("two native home selectors expose only the requester and do not broadcast private rows",()->{
            if(step==0){base();request("Visitor","clear");next();}
            else if(step==1&&ack("Visitor")){list("Dev","home","privacy-dev");next();}
            else if(step==2&&ack("Dev")){screenMatches("Dev","privacy-dev","home",Set.of("shared","dev-only"));request("Visitor","absent");next();}
            else if(step==3&&ack("Visitor")){list("Visitor","home","privacy-visitor");next();}
            else if(step==4&&ack("Visitor")){screenMatches("Visitor","privacy-visitor","home",Set.of("shared","visitor-only"));request("Dev","inspect","home","privacy-dev-after-visitor");next();}
            else if(step==5&&ack("Dev")){screenMatches("Dev","privacy-dev-after-visitor","home",Set.of("shared","dev-only"));denied();finish();}
        });
        add("same-name native home buttons resolve each caller and current cooldown refuses a repeat",()->{
            if(step==0){base();list("Dev","home","same-dev");next();}
            else if(step==1&&ack("Dev")){screenMatches("Dev","same-dev","home",Set.of("shared","dev-only"));list("Visitor","home","same-visitor");next();}
            else if(step==2&&ack("Visitor")){screenMatches("Visitor","same-visitor","home",Set.of("shared","visitor-only"));click("Dev","same-dev","shared");next();}
            else if(step==3&&ack("Dev")){allowed("Dev",DEV_HOME,"home");before=snapshot();click("Visitor","same-visitor","shared");next();}
            else if(step==4&&ack("Visitor")){allowed("Visitor",VISITOR_HOME,"home");before=snapshot();list("Dev","home","cooldown-repeat");next();}
            else if(step==5&&ack("Dev")){screenMatches("Dev","cooldown-repeat","home",Set.of("shared","dev-only"));require(screen("Dev","cooldown-repeat").getLong("remaining")>0,"Native screen did not report the current cooldown");click("Dev","cooldown-repeat","shared");next();}
            else if(step==6&&ack("Dev")){denied();finish();}
        });
        add("another player's private home name cannot select that player's location",()->{
            if(step==0){base();request("Visitor","commands","home dev-only");next();}
            else if(step==1&&ack("Visitor")){denied();finish();}
        });
        add("an open allowed home menu cannot bypass a later disabled feature",()->{
            if(step==0){base();list("Visitor","home","home-before-disable");next();}
            else if(step==1&&ack("Visitor")){screenMatches("Visitor","home-before-disable","home",Set.of("shared","visitor-only"));EssConfig.HOME_ENABLED.set(false);click("Visitor","home-before-disable","shared");next();}
            else if(step==2&&ack("Visitor")){denied();EssConfig.HOME_ENABLED.set(true);before=snapshot();list("Visitor","home","home-reenabled");next();}
            else if(step==3&&ack("Visitor")){screenMatches("Visitor","home-reenabled","home",Set.of("shared","visitor-only"));click("Visitor","home-reenabled","shared");next();}
            else if(step==4&&ack("Visitor")){allowed("Visitor",VISITOR_HOME,"home");finish();}
        });
        add("public warp selector uses native commands and stale allowed rows obey the disabled feature",()->{
            if(step==0){base();list("Visitor","warp","warp-allowed");next();}
            else if(step==1&&ack("Visitor")){screenMatches("Visitor","warp-allowed","warp",Set.of("public-warp"));click("Visitor","warp-allowed","public-warp");next();}
            else if(step==2&&ack("Visitor")) {
                allowed("Visitor",PUBLIC_WARP,"warp");
                visitor().teleportTo(server.overworld(),VISITOR_START.x(),VISITOR_START.y(),VISITOR_START.z(),0,0);cooldowns().remove("warp/"+visitorId);
                before=snapshot();list("Visitor","warp","warp-before-disable");next();
            }
            else if(step==3&&ack("Visitor")){screenMatches("Visitor","warp-before-disable","warp",Set.of("public-warp"));EssConfig.WARP_ENABLED.set(false);click("Visitor","warp-before-disable","public-warp");next();}
            else if(step==4&&ack("Visitor")){denied();EssConfig.WARP_ENABLED.set(true);before=snapshot();list("Visitor","warp","warp-reenabled");next();}
            else if(step==5&&ack("Visitor")){screenMatches("Visitor","warp-reenabled","warp",Set.of("public-warp"));click("Visitor","warp-reenabled","public-warp");next();}
            else if(step==6&&ack("Visitor")){allowed("Visitor",PUBLIC_WARP,"warp");finish();}
        });
    }
    static void initialize()throws Exception {
        require("1".equals(System.getenv().getOrDefault("UQ_DESTINATION_PHASE","1")),"This bounded fixture uses one phase");
        Files.createDirectories(CONTROL);Files.createDirectories(evidence());
        Path jar=Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        require(sha.equals(System.getenv("UQ_BUNDLE_SHA256")),"Candidate SHA-256 differs from prepared file");
        require(jar.equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"Production was not loaded from assembled JAR");
        require(!ModList.get().isLoaded("minecraftjail")&&!ModList.get().isLoaded("minecraft_jail")&&server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"Jail must remain separate");
        try{Class.forName("dev.unyielding.quests.jail.JailData");throw new IllegalStateException("Integrated Jail was loaded");}catch(ClassNotFoundException expected){}
        diagnostics.put("jars",Map.of(jar.getFileName().toString(),sha));diagnostics.put("jail_loaded",false);
        diagnostics.put("contract","DestinationMenu is S2C only. Real list commands open production screens; actual currently open row buttons send normal home/warp commands. No invented selector packet or home permission provider.");
        Config.SPIN_ENABLED.set(false);Config.DAILY_ENABLED.set(false);Config.SHARE_QUEST_PROGRESS.set(false);QuestManager.onSharingChanged(false);QuestManager.file=new QuestFile();
        devId=dev().getUUID();visitorId=visitor().getUUID();
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());
        var unrelated=new Team();unrelated.id=UUID.randomUUID();unrelated.type=Team.Type.PARTY;unrelated.name="Unrelated destination sentinel";unrelated.owner=UUID.randomUUID();unrelated.members.put(unrelated.owner,Team.Rank.OWNER);unrelatedId=unrelated.id;TeamManager.data().teams.put(unrelatedId,unrelated);TeamManager.data().setDirty();
        var coin=BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("lightmanscurrency:coin_gold")).orElseThrow();
        for(var p:players()) {
            UUID id=p.getUUID();boolean owner=id.equals(devId);
            p.getInventory().clearContent();var diamond=new ItemStack(Items.DIAMOND,owner?3:5);diamond.set(DataComponents.CUSTOM_NAME,Component.literal("Destination preservation sentinel "+p.getGameProfile().getName()));
            p.getInventory().setItem(2,diamond);p.getInventory().setItem(11,new ItemStack(coin,owner?11:7));p.inventoryMenu.broadcastFullState();
            p.giveExperienceLevels(owner?7:9);spins().players.put(id,new long[]{20_000,4,2,owner?11:4,123_456_789});
            var progress=QuestManager.progressOf(p);progress.completedQuests.add(owner?9_100_001L:9_100_002L);progress.completedTasks.add(owner?9_200_001L:9_200_002L);quests().markClaimed(id,owner?9_300_001L:9_300_002L);
        }
        spins().setDirty();quests().setDirty();
        for(int x=128;x<=154;x++)for(int z=128;z<=144;z++){server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);for(int y=81;y<=84;y++)server.overworld().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        base();cases();require(cases.size()==5,"Bounded case count drifted");
        diagnostics.put("readiness",readiness());diagnostics.put("preservation_scope","Full native inventory/components including physical gold coins, XP, seeded daily/earned spins, personal completed quests/tasks/claims, homes, global warps, private back histories and an unrelated party. Allowed commands change only actor location, one expected back origin and actor cooldown. This is not every external bank currency or live rank policy.");
    }
    static Map<String,Object> readiness() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("server_tick",tick);
        for(String name:List.of("Dev","Visitor")){var p=server.getPlayerList().getPlayerByName(name);Map<String,Object> data=new LinkedHashMap<>();data.put("server_present",p!=null);data.put("server_player_ticks",p==null?-1:p.tickCount);Path f=CONTROL.resolve(name+".ready");try{data.put("client_ready",Files.exists(f)?Files.readString(f):"missing");}catch(Exception bad){data.put("client_ready_error",bad.toString());}result.put(name,data);}return result;
    }
    static Map<String,Object> observed()throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();result.put("cooldowns",new TreeMap<>(cooldowns()));result.put("warps",Essentials.data().warps.toString());
        List<Object> states=new ArrayList<>();for(var p:players()){var state=playerState(p);Map<String,Object> row=new LinkedHashMap<>();row.put("name",p.getGameProfile().getName());row.put("position",Loc.of(p).toString());row.put("homes",state.homes().toString());row.put("back",state.back().toString());row.put("inventory",state.inventory());row.put("spins",state.spins());states.add(row);}result.put("players",states);return result;
    }
    static void completed()throws Exception {
        done=true;Files.createDirectories(evidence());long failed=outcomes.stream().filter(s->s.startsWith("failed:")).count();long executed=outcomes.stream().filter(s->!s.startsWith("failed: fixture =>")).count();
        boolean passed=failed==0&&executed==5;
        try{diagnostics.put("final_native_state",observed());}catch(Exception bad){diagnostics.put("final_native_state_error",bad.toString());}
        Map<String,Object> report=new LinkedHashMap<>();report.put("commit",System.getenv("GITHUB_SHA"));report.put("passed",passed);report.put("case_count",executed);report.put("failures",failed);report.put("cases",outcomes);report.put("jars",diagnostics.get("jars"));report.put("diagnostics",diagnostics);report.put("private_or_live_data_used",false);
        Files.writeString(evidence().resolve("destination-phase-1.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");Files.writeString(evidence().resolve("cases-phase-1.txt"),String.join("\n",outcomes)+"\n");
        if(Files.isDirectory(CONTROL)){Path copy=evidence().resolve("client-controls");Files.createDirectories(copy);try(var files=Files.list(CONTROL)){for(var f:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().endsWith(".tmp")).toList())Files.copy(f,copy.resolve(f.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
        Files.writeString(RESULT,(passed?"passed: ":"failed: ")+executed+" destination cases, "+failed+" failures\n");
    }
    void tick(ServerTickEvent.Post event) {
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        server=event.getServer();tick++;
        try {
            if(!initialized) {
                if(tick>=6000){diagnostics.put("startup_readiness",readiness());throw new IllegalStateException("Native clients not ready within 6000 server ticks");}
                if(dev()==null||visitor()==null||dev().tickCount<100||visitor().tickCount<100)return;
                for(var p:players()){Path ready=CONTROL.resolve(p.getGameProfile().getName()+".ready");if(!Files.exists(ready)||!Files.readString(ready).startsWith(p.getUUID()+"\t"))return;}
                initialize();initialized=true;initializedAt=tick;return;
            }
            if(tick-initializedAt<80)return;
            if(cases.isEmpty()){completed();return;}
            var current=cases.peek();
            if(start==0){start=tick;step=0;System.out.println("DESTINATION START "+current.name());}
            try{require(tick-start<1200,"Case timed out at step "+step);current.body().tick();}
            catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: "+current.name()+" at step "+step+" => "+bad);try{diagnostics.put("failure "+current.name(),observed());}catch(Exception failure){diagnostics.put("failure "+current.name(),failure.toString());}cases.remove();start=0;step=0;}
        } catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: fixture => "+bad);try{completed();}catch(Exception ignored){done=true;}}
    }
}
