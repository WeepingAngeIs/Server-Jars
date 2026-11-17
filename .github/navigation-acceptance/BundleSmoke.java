package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.*;
import dev.unyielding.quests.spin.DailyLogin;
import dev.unyielding.quests.team.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Disposable native navigation acceptance; not included in the production artifact. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final Path CONTROL=Path.of("/tmp/uq-navigation-controls"),RESULT=Path.of("/tmp/uq-navigation-result");
    static final Set<String> RECEIPTS=ConcurrentHashMap.newKeySet();
    static final List<String> outcomes=new ArrayList<>();
    static final Map<String,Object> diagnostics=new LinkedHashMap<>();
    static final Map<String,String> requests=new HashMap<>();
    static final Map<String,Integer> processedAt=new HashMap<>();
    static final Deque<Case> cases=new ArrayDeque<>();
    static MinecraftServer server;
    static Team party;
    static UUID devId,visitorId;
    static boolean initialized,done;
    static int tick,step,start,sequence,initializedAt;
    static Snapshot baseline;
    interface Body{void tick()throws Exception;}
    record Case(String name,Body body){}
    record PlayerState(List<String> inventory,int xp,float xpProgress,int xpTotal){}
    record Snapshot(CompoundTag quests,CompoundTag spins,CompoundTag teams,Map<UUID,PlayerState> players){}
    public record Barrier(String token)implements CustomPacketPayload {
        public static final Type<Barrier> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:navigation_barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Barrier> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Barrier::token,Barrier::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Receipt(String token)implements CustomPacketPayload {
        public static final Type<Receipt> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:navigation_receipt"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Receipt> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Receipt::token,Receipt::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public BundleSmoke(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::tick);
        bus.addListener((RegisterPayloadHandlersEvent event)->{
            var r=event.registrar("1");
            r.playToServer(Barrier.TYPE,Barrier.CODEC,(payload,ctx)->{
                var p=(ServerPlayer)ctx.player();String name=p.getGameProfile().getName();
                require(name.equals("Dev")||name.equals("Visitor"),"Unexpected navigation client");
                try{write(CONTROL.resolve(name+".processed"),payload.token());}catch(Exception bad){throw new IllegalStateException(bad);}
                Net.sendTo(p,new Receipt(payload.token()));
            });
            r.playToClient(Receipt.TYPE,Receipt.CODEC,(payload,ctx)->RECEIPTS.add(payload.token()));
        });
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static Path evidence(){return Path.of(System.getenv("UQ_NAVIGATION_EVIDENCE"));}
    static ServerPlayer dev(){return server.getPlayerList().getPlayerByName("Dev");}
    static ServerPlayer visitor(){return server.getPlayerList().getPlayerByName("Visitor");}
    static List<ServerPlayer> players(){return List.of(dev(),visitor());}
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static QuestData quests(){return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(),QuestData.NAME);}
    static DailyLogin.Data spins(){return server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(),DailyLogin.Data.NAME);}
    static Snapshot snapshot() {
        Map<UUID,PlayerState> states=new HashMap<>();
        for(var p:players()) {
            List<String> items=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++)items.add(i+":"+p.getInventory().getItem(i).saveOptional(server.registryAccess()));
            states.put(p.getUUID(),new PlayerState(List.copyOf(items),p.experienceLevel,p.experienceProgress,p.totalExperience));
        }
        CompoundTag teams=TeamManager.data().save(new CompoundTag(),server.registryAccess()).copy();
        // Only this informational field is permitted to change in the eight UI cases.
        for(Tag t:teams.getList("teams",Tag.TAG_COMPOUND)){var tag=(CompoundTag)t;if(tag.getUUID("id").equals(party.id))tag.remove("pinnedQuest");}
        return new Snapshot(quests().save(new CompoundTag(),server.registryAccess()).copy(),spins().save(new CompoundTag(),server.registryAccess()).copy(),teams,states);
    }
    static void preserved() {
        var now=snapshot();require(baseline.equals(now),"Navigation mutated personal progress/tasks/task counters/keys/claim ledger/epochs, spins/debit history, inventory/XP or party membership/settings");
        require(!Config.SHARE_QUEST_PROGRESS.get()&&!quests().sharingEnabled&&QuestManager.sharingModeMatches(false),"Navigation enabled progress sharing");
        require(QuestManager.progressOf(dev())!=QuestManager.progressOf(visitor()),"Personal progress records became shared");
    }
    static void request(String who,String action)throws Exception {
        String id=""+(++sequence);requests.put(who,id);write(CONTROL.resolve(who+".txt"),id+"\t"+action);
    }
    static boolean ack(String who)throws Exception {
        String id=requests.get(who);Path p=CONTROL.resolve(who+".ack");if(id==null||!Files.exists(p))return false;
        String value=Files.readString(p);if(!value.startsWith(id+"\t"))return false;
        require(value.equals(id+"\tpassed"),"Native action failed: "+value);
        Path processed=CONTROL.resolve(who+".processed");if(!Files.exists(processed)||!Files.readString(processed).equals(id))return false;
        if(tick-processedAt.computeIfAbsent(id,k->tick)<2)return false;
        var o=TagParser.parseTag(Files.readString(CONTROL.resolve(who+".observation."+id+".snbt")));
        require(!o.getBoolean("shareQuestProgress"),"Client presentation enabled shared quests");
        diagnostics.put("native observation "+who+" "+id,o.toString());preserved();return true;
    }
    static void next(){step++;}
    static void finish(){preserved();outcomes.add("passed: "+cases.peek().name());System.out.println("NAVIGATION "+outcomes.getLast());cases.remove();start=0;step=0;}
    static void add(String name,Body body){cases.add(new Case(name,body));}
    static void cases() {
        add("party roster native first/middle/last pages and disabled boundary arrows",()->{
            if(step==0){request("Dev","roster_pages");next();}else if(ack("Dev"))finish();
        });
        add("member search trims and ignores case resets page and clears to the original roster",()->{
            if(step==0){request("Dev","roster_search");next();}else if(ack("Dev"))finish();
        });
        add("ordinary member roster has no management controls and a readable empty search result",()->{
            if(step==0){request("Visitor","roster_empty");next();}else if(ack("Visitor"))finish();
        });
        add("visible quest title search and Next cross chapters wrap and skip hidden personal quests",()->{
            if(step==0){request("Visitor","quest_search");next();}else if(ack("Visitor"))finish();
        });
        add("native status selector covers All Locked Available Started Done Claim and resets navigation",()->{
            if(step==0){request("Visitor","quest_status");next();}else if(ack("Visitor"))finish();
        });
        add("owner pins a visible informational goal and ordinary member opens its synchronized detail",()->{
            if(step==0){request("Dev","pin_available");next();}
            else if(step==1&&ack("Dev")){require(party.pinnedQuest==102,"Owner Pin did not synchronize server metadata");request("Visitor","open_available_goal");next();}
            else if(step==2&&ack("Visitor")){require(party.pinnedQuest==102,"Viewing changed the goal");finish();}
        });
        add("ordinary party member can replace and unpin the shared informational goal",()->{
            if(step==0){request("Visitor","member_repin");next();}
            else if(step==1&&ack("Visitor")){require(party.pinnedQuest==103,"Normal member was not allowed to pin");request("Dev","observe_member_goal");next();}
            else if(step==2&&ack("Dev")){request("Visitor","member_unpin");next();}
            else if(step==3&&ack("Visitor")){require(party.pinnedQuest==0,"Normal member was not allowed to unpin");request("Dev","observe_no_goal");next();}
            else if(step==4&&ack("Dev"))finish();
        });
        add("goal visibility is personal hidden viewer cannot open or forge its pin and owner can unpin",()->{
            if(step==0){request("Dev","pin_hidden");next();}
            else if(step==1&&ack("Dev")){require(party.pinnedQuest==107,"Owner with prerequisite could not pin visible hidden quest");request("Visitor","hidden_goal");next();}
            else if(step==2&&ack("Visitor")){require(party.pinnedQuest==107,"Hidden-for-actor pin packet changed metadata");request("Dev","unpin_hidden");next();}
            else if(step==3&&ack("Dev")){require(party.pinnedQuest==0,"Final goal did not unpin");finish();}
        });
    }
    static QuestFile.Chapter chapter(QuestFile f,long id,String title) {
        var c=new QuestFile.Chapter();c.id=id;c.title=title;c.order=f.chapters.size();c.progressionMode="linear";
        f.chapters.add(c);f.chapterMap.put(id,c);return c;
    }
    static QuestFile.Quest quest(QuestFile f,QuestFile.Chapter c,long id,String title) {
        var q=new QuestFile.Quest();q.id=id;q.chapter=c.id;q.title=title;q.x=(c.quests.size()%3)*2;q.y=(c.quests.size()/3)*2;q.icon=QuestFile.ItemRef.of("minecraft:book");
        q.desc.add("Synthetic navigation fixture. Viewing, searching and pinning do not award or complete this quest.");
        var t=new QuestFile.Task();t.id=1000+id;t.quest=id;t.type=QuestFile.TaskType.KILL;t.target="minecraft:wither";t.amount=10;t.title="Defeat ten Withers";q.tasks.add(t);f.tasks.put(t.id,t);
        c.quests.add(q);f.quests.put(id,q);return q;
    }
    static void reward(QuestFile f,QuestFile.Quest q) {
        var r=new QuestFile.Reward();r.id=2000+q.id;r.quest=q.id;r.type=QuestFile.RewardType.XP;r.amount=37;r.title="Unclaimed test XP";q.rewards.add(r);f.rewards.put(r.id,r);
    }
    static void initialize()throws Exception {
        Files.createDirectories(CONTROL);Files.createDirectories(evidence());
        Path game=FMLPaths.GAMEDIR.get().toRealPath();
        require(game.getFileName().toString().equals("run-server")&&game.getParent().getParent().getFileName().toString().equals("dist"),"Only disposable generated harness may run");
        Path jar=Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        require(sha.equals(System.getenv("UQ_BUNDLE_SHA256")),"Candidate hash differs");
        require(jar.equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"Loaded production artifact differs from candidate");
        require(!ModList.get().isLoaded("minecraftjail")&&!ModList.get().isLoaded("minecraft_jail")&&server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"Jail must remain absent");
        diagnostics.put("jars",Map.of(jar.getFileName().toString(),sha));
        Config.SPIN_ENABLED.set(false);Config.DAILY_ENABLED.set(false);Config.SHARE_QUEST_PROGRESS.set(false);QuestManager.onSharingChanged(false);
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());
        devId=dev().getUUID();visitorId=visitor().getUUID();
        // Declared synthetic server state: no live party/save/profile is read or changed.
        party=new Team();party.id=UUID.nameUUIDFromBytes("navigation-fixture-party".getBytes(StandardCharsets.UTF_8));party.type=Team.Type.PARTY;party.name="Navigation";party.description="Two online and twelve synthetic offline members";party.owner=devId;
        party.members.put(devId,Team.Rank.OWNER);party.names.put(devId,"Dev");party.members.put(visitorId,Team.Rank.MEMBER);party.names.put(visitorId,"Visitor");
        for(int i=1;i<=12;i++){String name=String.format(Locale.ROOT,"Offline%02d",i);UUID id=UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));party.members.put(id,i==1?Team.Rank.OFFICER:Team.Rank.MEMBER);party.names.put(id,name);TeamManager.data().playerTeam.put(id,party.id);}
        TeamManager.data().teams.put(party.id,party);TeamManager.data().playerTeam.put(devId,party.id);TeamManager.data().playerTeam.put(visitorId,party.id);TeamManager.data().setDirty();
        var f=new QuestFile();f.detectionDelay=1000;var c=chapter(f,11,"Navigation Alpha");var other=chapter(f,12,"Navigation Beta");
        var locked=quest(f,c,101,"Navigation Locked");locked.deps=new long[]{108};
        quest(f,c,102,"Navigation Available");quest(f,c,103,"Navigation Started");var completed=quest(f,c,104,"Navigation Done");reward(f,completed);
        var claim=quest(f,c,105,"Navigation Claim");reward(f,claim);
        quest(f,other,106,"Navigation Available Elsewhere");var hidden=quest(f,other,107,"Navigation Hidden");hidden.deps=new long[]{108};hidden.hideUntilDeps=true;
        quest(f,other,108,"Visibility prerequisite");QuestManager.file=f;
        var dirty=QuestManager.class.getDeclaredField("chunksDirty");dirty.setAccessible(true);dirty.setBoolean(null,true);
        var sendFile=QuestManager.class.getDeclaredMethod("sendFile",ServerPlayer.class);sendFile.setAccessible(true);
        for(var p:players()) {
            boolean owner=p==dev();p.setInvulnerable(true);p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(20);p.getInventory().clearContent();
            var item=new ItemStack(owner?Items.DIAMOND:Items.EMERALD,owner?3:5);item.set(DataComponents.CUSTOM_NAME,Component.literal("Navigation sentinel "+p.getGameProfile().getName()));p.getInventory().setItem(2,item);p.inventoryMenu.broadcastFullState();p.giveExperienceLevels(owner?7:9);
            var progress=QuestManager.progressOf(p);progress.completedQuests.add(104L);progress.completedQuests.add(105L);progress.completedTasks.add(1104L);progress.completedTasks.add(1105L);progress.taskProgress.put(1103L,owner?4L:2L);progress.keys.put("navigation-sentinel",owner?11L:17L);
            if(owner){progress.completedQuests.add(108L);progress.completedTasks.add(1108L);}
            quests().markClaimed(p.getUUID(),2104L);quests().markClaimed(p.getUUID(),owner?9_300_001L:9_300_002L);
            spins().players.put(p.getUUID(),new long[]{20_000,4,owner?2:3,owner?11:4,123_456_789});
            p.teleportTo(server.overworld(),132.5+(owner?0:4),81,132.5,0,0);
            sendFile.invoke(null,p);QuestManager.sendSpinInfo(p);QuestManager.sendProgress(p);
        }
        for(int x=128;x<=144;x++)for(int z=128;z<=140;z++){server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);for(int y=81;y<=84;y++)server.overworld().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        TeamManager.syncAllPlayers();quests().setDirty();spins().setDirty();baseline=snapshot();cases();require(cases.size()==8,"Case count drifted");
        diagnostics.put("fixture_seed","Disposable direct server seed: two real clients, one 14-member party with 12 offline synthetic entries; two chapters, five visible personal statuses, cross-chapter match and actor-specific hidden goal.");
        diagnostics.put("contract","Any PARTY member may pin/unpin. Goal is informational only. Filters are All/LOCKED/AVAILABLE/STARTED/DONE/CLAIM. Status selection changes the filter; Next navigates. Hidden personal quests are excluded.");
        diagnostics.put("preservation_scope","Entire native QuestData including separate personal progress, taskProgress, keys, per-player claim ledger/epochs; entire DailyLogin data including balances and debit/credit history; inventories/components and XP; entire TeamData except pinnedQuest; sharing remains false after every ordered action and case. No claim/reward/task-completion UI button is pressed.");
        diagnostics.put("readiness",readiness());
    }
    static Map<String,Object> readiness() {
        Map<String,Object> r=new LinkedHashMap<>();r.put("server_tick",tick);
        for(String name:List.of("Dev","Visitor")){var p=server.getPlayerList().getPlayerByName(name);Path f=CONTROL.resolve(name+".ready");try{r.put(name,Map.of("server_ticks",p==null?-1:p.tickCount,"client_ready",Files.exists(f)?Files.readString(f):"missing"));}catch(Exception bad){r.put(name,bad.toString());}}return r;
    }
    static void completed()throws Exception {
        done=true;Files.createDirectories(evidence());long failures=outcomes.stream().filter(s->s.startsWith("failed:")).count(),executed=outcomes.stream().filter(s->!s.startsWith("failed: fixture =>")).count();boolean passed=failures==0&&executed==8;
        diagnostics.put("final_goal",party==null?-1:party.pinnedQuest);diagnostics.put("final_readiness",readiness());
        Map<String,Object> report=new LinkedHashMap<>();report.put("commit",System.getenv("GITHUB_SHA"));report.put("passed",passed);report.put("case_count",executed);report.put("failures",failures);report.put("cases",outcomes);report.put("jars",diagnostics.get("jars"));report.put("diagnostics",diagnostics);report.put("private_or_live_data_used",false);
        Files.writeString(evidence().resolve("navigation-phase-1.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");Files.writeString(evidence().resolve("cases-phase-1.txt"),String.join("\n",outcomes)+"\n");
        if(Files.isDirectory(CONTROL)){Path copy=evidence().resolve("client-controls");Files.createDirectories(copy);try(var files=Files.list(CONTROL)){for(var f:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().endsWith(".tmp")).toList())Files.copy(f,copy.resolve(f.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
        Files.writeString(RESULT,(passed?"passed: ":"failed: ")+executed+" navigation cases, "+failures+" failures\n");
    }
    void tick(ServerTickEvent.Post event) {
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;server=event.getServer();tick++;
        try {
            if(!initialized) {
                if(tick>=6000){diagnostics.put("startup_readiness",readiness());throw new IllegalStateException("Native clients not ready within 6000 server ticks");}
                if(dev()==null||visitor()==null||dev().tickCount<100||visitor().tickCount<100)return;
                for(var p:players()){Path ready=CONTROL.resolve(p.getGameProfile().getName()+".ready");if(!Files.exists(ready)||!Files.readString(ready).startsWith(p.getUUID()+"\t"))return;}
                initialize();initialized=true;initializedAt=tick;return;
            }
            if(tick-initializedAt<80)return;if(cases.isEmpty()){completed();return;}
            var current=cases.peek();if(start==0){start=tick;step=0;System.out.println("NAVIGATION START "+current.name());}
            try{require(tick-start<1200,"Case timed out at step "+step);current.body().tick();}
            catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: "+current.name()+" at step "+step+" => "+bad);diagnostics.put("failure "+current.name(),Map.of("goal",party.pinnedQuest,"readiness",readiness()));cases.remove();start=0;step=0;}
        }catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: fixture => "+bad);try{completed();}catch(Exception ignored){done=true;}}
    }
}
