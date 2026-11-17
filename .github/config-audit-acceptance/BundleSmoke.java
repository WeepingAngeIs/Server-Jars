package dev.unyielding.bundlesmoke;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.essentials.*;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.*;
import dev.unyielding.quests.settings.ConfigAudit;
import dev.unyielding.quests.settings.ConfigEditor;
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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Eight bounded native audit/undo checks. No production helper or private live input. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final Path CONTROL=Path.of("/tmp/uq-config-audit-controls"),RESULT=Path.of("/tmp/uq-config-audit-result");
    static final Set<String> RECEIPTS=ConcurrentHashMap.newKeySet();
    static final Gson JSON=new Gson();
    static final String MAP=key("quests",Config.CUSTOM_MAP_ICON_SCALE),HOME=key("essentials",EssConfig.HOME_COOLDOWN),TOASTS=key("quests",Config.QUEST_TOASTS),ZONE=key("quests",Config.DAILY_TIMEZONE);
    static final String OMITTED="[text or list omitted]",TEST_ZONE="Pacific/Honolulu";
    static final List<String> outcomes=new ArrayList<>();
    static final Map<String,Object> diagnostics=new LinkedHashMap<>();
    static final Map<String,String> requests=new HashMap<>();
    static final Map<String,Integer> processedAt=new HashMap<>();
    static final Deque<Case> cases=new ArrayDeque<>();
    static Map<String,ModConfigSpec.ConfigValue<?>> values;
    static MinecraftServer server;
    static UUID devId,visitorId,unrelatedId;
    static boolean initialized,done;
    static int tick,step,start,sequence,initializedAt;
    static Snapshot before;
    interface Body{void tick()throws Exception;}
    record Case(String name,Body body){}
    record PlayerState(List<String> inventory,int xp,float xpProgress,int xpTotal,long[] spins,Set<Long> quests,Set<Long> tasks,Set<Long> claimed,Map<String,Loc> homes,List<Loc> back,Loc position){}
    record Snapshot(Map<String,String> config,Map<String,String> disk,CompoundTag audit,Map<UUID,PlayerState> players,Map<String,Loc> warps,CompoundTag unrelated,Map<String,Long> cooldowns,long time){}
    public record Barrier(String token)implements CustomPacketPayload {
        public static final Type<Barrier> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:config_audit_barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Barrier> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Barrier::token,Barrier::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Receipt(String token)implements CustomPacketPayload {
        public static final Type<Receipt> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:config_audit_receipt"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Receipt> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Receipt::token,Receipt::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public BundleSmoke(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::tick);
        bus.addListener((RegisterPayloadHandlersEvent event)->{
            var r=event.registrar("1");
            r.playToServer(Barrier.TYPE,Barrier.CODEC,(payload,ctx)->{
                var p=(ServerPlayer)ctx.player();String name=p.getGameProfile().getName();
                require(name.equals("Dev")||name.equals("Visitor"),"Unexpected config audit fixture client");
                try{write(CONTROL.resolve(name+".processed"),payload.token());}catch(Exception bad){throw new IllegalStateException(bad);}
                Net.sendTo(p,new Receipt(payload.token()));
            });
            r.playToClient(Receipt.TYPE,Receipt.CODEC,(payload,ctx)->RECEIPTS.add(payload.token()));
        });
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static String key(String group,ModConfigSpec.ConfigValue<?> value){return group+"."+String.join(".",value.getPath());}
    static String json(Object value){return JSON.toJson(value);}
    static Path evidence(){return Path.of(System.getenv("UQ_CONFIG_AUDIT_EVIDENCE"));}
    static ServerPlayer dev(){return server.getPlayerList().getPlayerByName("Dev");}
    static ServerPlayer visitor(){return server.getPlayerList().getPlayerByName("Visitor");}
    static ServerPlayer player(String name){return name.equals("Dev")?dev():visitor();}
    static List<ServerPlayer> players(){return List.of(dev(),visitor());}
    static void write(Path path,String value)throws Exception {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    @SuppressWarnings("unchecked") static Map<String,ModConfigSpec.ConfigValue<?>> editorValues()throws Exception {
        var f=ConfigEditor.class.getDeclaredField("VALUES");f.setAccessible(true);return(Map<String,ModConfigSpec.ConfigValue<?>>)f.get(null);
    }
    @SuppressWarnings("unchecked") static Map<String,Long> cooldowns()throws Exception {
        var f=Essentials.class.getDeclaredField("COOLDOWNS");f.setAccessible(true);return(Map<String,Long>)f.get(null);
    }
    static CompoundTag audit()throws Exception {
        var get=ConfigAudit.class.getDeclaredMethod("get",ServerPlayer.class);get.setAccessible(true);
        return((ConfigAudit)get.invoke(null,dev())).save(new CompoundTag(),server.registryAccess()).copy();
    }
    static ListTag entries(CompoundTag data){return data.getList("entries",Tag.TAG_COMPOUND);}
    static Map<String,String> config() {
        Map<String,String> result=new TreeMap<>();values.forEach((k,v)->result.put(k,json(v.getRaw())));return result;
    }
    static Map<String,String> disk()throws Exception {
        Map<String,UnmodifiableConfig> files=new HashMap<>();
        for(String filename:List.of("unyieldingquests-common.toml","unyieldingquests-essentials.toml","unyieldingquests-ultimine.toml")) {
            Path path=FMLPaths.CONFIGDIR.get().resolve(filename);require(Files.isRegularFile(path),"Generated config file missing: "+filename);
            try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){files.put(filename,new TomlParser().parse(reader));}
        }
        Map<String,String> result=new TreeMap<>();
        values.forEach((k,v)->{
            String filename=k.startsWith("quests.")?"unyieldingquests-common.toml":k.startsWith("essentials.")?"unyieldingquests-essentials.toml":k.startsWith("vein_mining.")?"unyieldingquests-ultimine.toml":null;
            if(filename!=null)result.put(k,json(files.get(filename).getRaw(v.getPath())));
        });
        return result;
    }
    static DailyLogin.Data spins(){return server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(),DailyLogin.Data.NAME);}
    static QuestData quests(){return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(),QuestData.NAME);}
    static PlayerState playerState(ServerPlayer p) {
        List<String> items=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++)items.add(i+":"+p.getInventory().getItem(i).saveOptional(server.registryAccess()));
        var progress=QuestManager.progressOf(p);var data=Essentials.data();
        return new PlayerState(List.copyOf(items),p.experienceLevel,p.experienceProgress,p.totalExperience,spins().players.get(p.getUUID()).clone(),Set.copyOf(progress.completedQuests),Set.copyOf(progress.completedTasks),Set.copyOf(quests().claimedRewards.getOrDefault(p.getUUID(),Set.of())),Map.copyOf(data.homes.getOrDefault(p.getUUID(),Map.of())),List.copyOf(data.back.getOrDefault(p.getUUID(),List.of())),Loc.of(p));
    }
    static Snapshot snapshot()throws Exception {
        Map<UUID,PlayerState> players=new HashMap<>();for(var p:players())players.put(p.getUUID(),playerState(p));
        return new Snapshot(config(),disk(),audit(),players,Map.copyOf(Essentials.data().warps),TeamManager.get(unrelatedId).toTag(true).copy(),Map.copyOf(cooldowns()),System.currentTimeMillis());
    }
    static void preserved(Snapshot now)throws Exception {
        for(var p:players()) {
            var a=before.players().get(p.getUUID());var b=now.players().get(p.getUUID());
            require(a.inventory().equals(b.inventory()),"Native inventory/components/physical coins changed for "+p.getGameProfile().getName());
            require(a.xp()==b.xp()&&a.xpProgress()==b.xpProgress()&&a.xpTotal()==b.xpTotal(),"XP changed");
            require(Arrays.equals(a.spins(),b.spins()),"Daily or earned spin balances changed");
            require(a.quests().equals(b.quests())&&a.tasks().equals(b.tasks())&&a.claimed().equals(b.claimed()),"Personal quest/task/claim data changed");
            require(a.homes().equals(b.homes())&&a.back().equals(b.back()),"Private homes/back changed");
            require(p.level().dimension().location().toString().equals(a.position().dim())&&p.distanceToSqr(a.position().x(),a.position().y(),a.position().z())<0.0001,"Config action moved a player");
        }
        require(before.warps().equals(now.warps()),"Global warps changed");
        require(before.unrelated().equals(now.unrelated()),"Unrelated party changed");
        require(before.cooldowns().equals(now.cooldowns()),"Teleport cooldowns changed");
    }
    static void unchanged()throws Exception {
        var now=snapshot();preserved(now);
        require(before.config().equals(now.config()),"Refused/read-only action changed a config value");
        require(before.disk().equals(now.disk()),"Refused/read-only action changed generated on-disk config values");
        require(before.audit().equals(now.audit()),"Refused/read-only action changed audit history");record("unchanged",now);
    }
    static CompoundTag changed(String actor,String key,String expectedAfter,String recordedBefore,String recordedAfter)throws Exception {
        var now=snapshot();preserved(now);
        Map<String,String> expected=new TreeMap<>(before.config());expected.put(key,expectedAfter);
        require(expected.equals(now.config()),"Accepted edit changed the wrong in-memory config value(s)");
        Map<String,String> expectedDisk=new TreeMap<>(before.disk());expectedDisk.put(key,expectedAfter);
        require(expectedDisk.equals(now.disk()),"Accepted edit did not persist exactly its generated config value(s)");
        var old=entries(before.audit());var current=entries(now.audit());require(current.size()==old.size()+1,"Accepted edit did not append exactly one audit entry");
        for(int i=0;i<old.size();i++)require(old.get(i).equals(current.get(i)),"Prior audit entry changed");
        var entry=current.getCompound(current.size()-1);
        require(entry.getAllKeys().equals(Set.of("actor","actorId","key","time","before","after")),"Unexpected audit entry fields");
        require(entry.getString("actor").equals(actor)&&entry.getUUID("actorId").equals(player(actor).getUUID()),"Audit actor identity differs from actual network caller");
        require(entry.getString("key").equals(key)&&entry.getString("before").equals(recordedBefore)&&entry.getString("after").equals(recordedAfter),"Audit key/value pair differs from accepted edit");
        require(entry.getLong("time")>=before.time()&&entry.getLong("time")<=System.currentTimeMillis(),"Audit time is outside this actual edit");
        record("changed "+key,now);return entry;
    }
    static void record(String action,Snapshot now) {
        diagnostics.put(cases.peek().name()+" step "+step,Map.of("action",action,"target_values",Map.of(MAP,now.config().get(MAP),HOME,now.config().get(HOME),TOASTS,now.config().get(TOASTS),ZONE,now.config().get(ZONE)),"audit_entries",entries(now.audit()).size(),"latest",entries(now.audit()).isEmpty()?"none":entries(now.audit()).getCompound(entries(now.audit()).size()-1).toString(),"unrelated_config_and_player_state_preserved",true));
    }
    static void request(String who,String action,String... arguments)throws Exception {
        String id=""+(++sequence);requests.put(who,id);write(CONTROL.resolve(who+".txt"),id+"\t"+action+(arguments.length==0?"":"\t"+String.join("\t",arguments)));
    }
    static void edit(String who,String key,String expected,String value)throws Exception{request(who,"edit",key,expected,value);}
    static void command(String who,String value)throws Exception{request(who,"commands",value);}
    static boolean ack(String who)throws Exception {
        String id=requests.get(who);Path p=CONTROL.resolve(who+".ack");if(id==null||!Files.exists(p))return false;String value=Files.readString(p);if(!value.startsWith(id+"\t"))return false;
        require(value.equals(id+"\tpassed"),"Native client action failed: "+value);Path processed=CONTROL.resolve(who+".processed");if(!Files.exists(processed)||!Files.readString(processed).equals(id))return false;
        return tick-processedAt.computeIfAbsent(id,k->tick)>=2;
    }
    static CompoundTag observation(String who)throws Exception {
        return TagParser.parseTag(Files.readString(CONTROL.resolve(who+".observation."+requests.get(who)+".snbt")));
    }
    static List<String> messages(String who)throws Exception {
        var rows=observation(who).getList("messages",Tag.TAG_STRING);List<String> result=new ArrayList<>();for(int i=0;i<rows.size();i++)result.add(rows.getString(i));return result;
    }
    static void noAuditDisclosure(String who)throws Exception {
        var o=observation(who);require(o.contains("screen",Tag.TAG_STRING),"Client did not capture its actual screen after the receipt");
        require(!o.getString("screen").contains("ConfigValuesScreen"),"Unprivileged client opened the config editor");
        require(messages(who).stream().noneMatch(s->s.startsWith("Recent configuration edits")||s.contains(MAP)||s.contains(HOME)),"Unprivileged client received private audit details");
        diagnostics.put("authorization observation "+who+" "+requests.get(who),o.toString());
    }
    static void next(){step++;}
    static void finish(){outcomes.add("passed: "+cases.peek().name());System.out.println("CONFIG AUDIT "+outcomes.getLast());cases.remove();start=0;step=0;}
    static void add(String name,Body body){cases.add(new Case(name,body));}
    static void base()throws Exception {
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());
        Config.CUSTOM_MAP_ICON_SCALE.set(1.0);EssConfig.HOME_COOLDOWN.set(60);Config.QUEST_TOASTS.set(true);Config.DAILY_TIMEZONE.set("UTC");
        Config.SPEC.save();EssConfig.SPEC.save();
        before=snapshot();
    }
    static void cases() {
        add("empty global audit can be viewed by an operator and undo changes nothing",()->{
            if(step==0){base();require(entries(before.audit()).isEmpty(),"Expected fresh disposable audit history");command("Dev","configaudit;configaudit undo");next();}
            else if(step==1&&ack("Dev")){unchanged();var text=messages("Dev");require(text.stream().anyMatch(s->s.startsWith("Recent configuration edits")),"Operator did not receive the native audit heading");require(text.stream().anyMatch(s->s.contains("No config edit is available to undo")),"Empty native undo did not refuse explicitly");diagnostics.put("empty audit native output",observation("Dev").toString());finish();}
        });
        add("numeric Double edit and undo persist exact values and each reverse becomes the new latest",()->{
            if(step==0){base();edit("Dev",MAP,"1.0","1.5");next();}
            else if(step==1&&ack("Dev")){changed("Dev",MAP,"1.5","1.0","1.5");before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==2&&ack("Dev")){changed("Dev",MAP,"1.0","1.5","1.0");before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==3&&ack("Dev")){changed("Dev",MAP,"1.5","1.0","1.5");finish();}
        });
        add("representative Int cooldown edit and native undo preserve other settings and readable audit output",()->{
            if(step==0){base();edit("Dev",HOME,"60","65");next();}
            else if(step==1&&ack("Dev")){changed("Dev",HOME,"65","60","65");before=snapshot();command("Dev","configaudit");next();}
            else if(step==2&&ack("Dev")){unchanged();require(messages("Dev").stream().anyMatch(s->s.contains(HOME)&&s.contains("1m 5s")),"Native audit did not format the edited cooldown");diagnostics.put("readable cooldown native output",observation("Dev").toString());before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==3&&ack("Dev")){changed("Dev",HOME,"60","65","60");finish();}
        });
        add("nonoperator and revoked operator cannot edit open audit details or undo an eligible latest value",()->{
            if(step==0){base();require(!visitor().hasPermissions(2),"Visitor must be a native nonoperator");edit("Dev",MAP,"1.0","1.5");next();}
            else if(step==1&&ack("Dev")){changed("Dev",MAP,"1.5","1.0","1.5");before=snapshot();edit("Visitor",MAP,"1.5","2.0");next();}
            else if(step==2&&ack("Visitor")){unchanged();noAuditDisclosure("Visitor");edit("Visitor","__open","","");next();}
            else if(step==3&&ack("Visitor")){unchanged();noAuditDisclosure("Visitor");command("Visitor","configaudit;configaudit undo");next();}
            else if(step==4&&ack("Visitor")){unchanged();noAuditDisclosure("Visitor");server.getPlayerList().deop(dev().getGameProfile());require(!dev().hasPermissions(2),"Dev operator permission was not revoked");before=snapshot();edit("Dev",MAP,"1.5","2.0");next();}
            else if(step==5&&ack("Dev")){unchanged();noAuditDisclosure("Dev");command("Dev","configaudit undo;configaudit");next();}
            else if(step==6&&ack("Dev")){unchanged();noAuditDisclosure("Dev");require(Config.CUSTOM_MAP_ICON_SCALE.get()==1.5,"Eligible latest value was changed by a revoked actor");server.getPlayerList().op(dev().getGameProfile());finish();}
        });
        add("stale expected edits and unmanaged conflicting values refuse without overwriting or extending audit",()->{
            if(step==0){base();edit("Dev",MAP,"1.0","1.5");next();}
            else if(step==1&&ack("Dev")){changed("Dev",MAP,"1.5","1.0","1.5");server.getPlayerList().op(visitor().getGameProfile());before=snapshot();edit("Visitor",MAP,"1.5","2.0");next();}
            else if(step==2&&ack("Visitor")){changed("Visitor",MAP,"2.0","1.5","2.0");before=snapshot();edit("Dev",MAP,"1.5","2.5");next();}
            else if(step==3&&ack("Dev")) {
                unchanged();
                // Declared unmanaged edit only in this new disposable config. It is not an audited action.
                Config.CUSTOM_MAP_ICON_SCALE.set(1.75);Config.CUSTOM_MAP_ICON_SCALE.save();before=snapshot();
                require(before.config().get(MAP).equals("1.75")&&before.disk().get(MAP).equals("1.75"),"Unmanaged fixture conflict was not in memory and on disk");
                command("Dev","configaudit undo");next();
            }
            else if(step==4&&ack("Dev")){unchanged();require(messages("Dev").stream().anyMatch(s->s.contains("cannot be safely undone")),"Conflicting native undo did not refuse explicitly");finish();}
        });
        add("unsupported Boolean and no-op latest entries block undo instead of skipping older eligible edits",()->{
            if(step==0){base();edit("Dev",MAP,"1.0","1.5");next();}
            else if(step==1&&ack("Dev")){changed("Dev",MAP,"1.5","1.0","1.5");before=snapshot();edit("Dev",TOASTS,"true","false");next();}
            else if(step==2&&ack("Dev")){changed("Dev",TOASTS,"false","true","false");before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==3&&ack("Dev")){unchanged();require(Config.CUSTOM_MAP_ICON_SCALE.get()==1.5&&!Config.QUEST_TOASTS.get(),"Undo skipped an unsupported latest entry");before=snapshot();edit("Dev",MAP,"1.5","1.5");next();}
            else if(step==4&&ack("Dev")){changed("Dev",MAP,"1.5","1.5","1.5");before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==5&&ack("Dev")){unchanged();require(Config.CUSTOM_MAP_ICON_SCALE.get()==1.5,"Undo skipped a no-op latest entry");finish();}
        });
        add("two administrators undo the global latest key while the earlier independent key remains changed",()->{
            if(step==0){base();edit("Dev",MAP,"1.0","1.25");next();}
            else if(step==1&&ack("Dev")){changed("Dev",MAP,"1.25","1.0","1.25");server.getPlayerList().op(visitor().getGameProfile());require(visitor().hasPermissions(2),"Second native operator was not granted permission");before=snapshot();edit("Visitor",HOME,"60","90");next();}
            else if(step==2&&ack("Visitor")){changed("Visitor",HOME,"90","60","90");before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==3&&ack("Dev")){changed("Dev",HOME,"60","90","60");require(Config.CUSTOM_MAP_ICON_SCALE.get()==1.25,"Global latest undo reverted an older independent key");finish();}
        });
        add("native text edits are minimized in audit storage and display and remain ineligible for undo",()->{
            if(step==0){base();edit("Dev",ZONE,json("UTC"),json(TEST_ZONE));next();}
            else if(step==1&&ack("Dev")) {
                var entry=changed("Dev",ZONE,json(TEST_ZONE),OMITTED,OMITTED);
                require(!entry.toString().contains(TEST_ZONE)&&!entry.toString().contains("UTC"),"Native audit retained the raw synthetic text value");
                before=snapshot();command("Dev","configaudit");next();
            }
            else if(step==2&&ack("Dev")){unchanged();var text=messages("Dev");require(text.stream().anyMatch(s->s.startsWith("Recent configuration edits")),"Operator audit heading was absent");require(text.stream().anyMatch(s->s.contains(ZONE)&&s.contains(OMITTED)),"Native audit did not display omitted text markers");require(text.stream().noneMatch(s->s.contains(TEST_ZONE)),"Native audit display leaked the raw synthetic text value");diagnostics.put("redacted audit native output",observation("Dev").toString());before=snapshot();command("Dev","configaudit undo");next();}
            else if(step==3&&ack("Dev")){unchanged();require(Config.DAILY_TIMEZONE.get().equals(TEST_ZONE),"Unsupported text undo altered current configuration");finish();}
        });
    }
    static void initialize()throws Exception {
        require("1".equals(System.getenv().getOrDefault("UQ_CONFIG_AUDIT_PHASE","1")),"This bounded fixture uses one phase");
        Files.createDirectories(CONTROL);Files.createDirectories(evidence());
        Path game=FMLPaths.GAMEDIR.get().toRealPath();
        require(game.getFileName().toString().equals("run-server")&&game.getParent().getParent().getFileName().toString().equals("dist"),"Only the disposable generated harness may run this fixture");
        Path jar=Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        require(sha.equals(System.getenv("UQ_BUNDLE_SHA256")),"Candidate SHA-256 differs from the prepared file");
        require(jar.equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"Production was not loaded from the assembled candidate");
        require(!ModList.get().isLoaded("minecraftjail")&&!ModList.get().isLoaded("minecraft_jail")&&server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"Jail must remain separate");
        try{Class.forName("dev.unyielding.quests.jail.JailData");throw new IllegalStateException("Integrated Jail was loaded");}catch(ClassNotFoundException expected){}
        diagnostics.put("jars",Map.of(jar.getFileName().toString(),sha));diagnostics.put("jail_loaded",false);
        diagnostics.put("contract","Native ConfigEditor.Action and configaudit commands. Undo targets the global latest entry and a successful undo appends a reverse entry; repeat undo is not one-use.");
        diagnostics.put("disk_scope","Pure read-only TomlParser loads generated common, essentials and ultimine config files; compare native value semantics, not formatting/comment bytes.");
        values=editorValues();require(values.containsKey(MAP)&&values.containsKey(HOME)&&values.containsKey(TOASTS)&&values.containsKey(ZONE),"Expected native config targets are missing");
        require(entries(audit()).isEmpty(),"Expected fresh disposable audit history; existing history must not be replaced");
        Config.SPIN_ENABLED.set(false);Config.DAILY_ENABLED.set(false);Config.SHARE_QUEST_PROGRESS.set(false);QuestManager.onSharingChanged(false);QuestManager.file=new QuestFile();
        devId=dev().getUUID();visitorId=visitor().getUUID();
        var unrelated=new Team();unrelated.id=UUID.randomUUID();unrelated.type=Team.Type.PARTY;unrelated.name="Unrelated audit sentinel";unrelated.owner=UUID.randomUUID();unrelated.members.put(unrelated.owner,Team.Rank.OWNER);unrelatedId=unrelated.id;TeamManager.data().teams.put(unrelatedId,unrelated);TeamManager.data().setDirty();
        var coin=BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("lightmanscurrency:coin_gold")).orElseThrow();
        int offset=0;for(var p:players()) {
            UUID id=p.getUUID();boolean owner=id.equals(devId);p.setInvulnerable(true);p.setGameMode(GameType.SURVIVAL);p.getFoodData().setFoodLevel(20);
            p.getInventory().clearContent();var diamond=new ItemStack(Items.DIAMOND,owner?3:5);diamond.set(DataComponents.CUSTOM_NAME,Component.literal("Audit preservation sentinel "+p.getGameProfile().getName()));
            p.getInventory().setItem(2,diamond);p.getInventory().setItem(11,new ItemStack(coin,owner?11:7));p.inventoryMenu.broadcastFullState();p.giveExperienceLevels(owner?7:9);
            spins().players.put(id,new long[]{20_000,4,2,owner?11:4,123_456_789});
            var progress=QuestManager.progressOf(p);progress.completedQuests.add(owner?9_100_001L:9_100_002L);progress.completedTasks.add(owner?9_200_001L:9_200_002L);quests().markClaimed(id,owner?9_300_001L:9_300_002L);
            Essentials.data().homes.put(id,new LinkedHashMap<>(Map.of("private-sentinel",new Loc("minecraft:overworld",42.5+offset,81,51.5,17,3))));
            Essentials.data().back.put(id,new ArrayList<>(List.of(new Loc("minecraft:overworld",38.5+offset,81,47.5,29,5))));
            p.teleportTo(server.overworld(),132.5+offset,81,132.5,0,0);offset+=4;
        }
        Essentials.data().warps.put("unrelated-warp-sentinel",new Loc("minecraft:overworld",48.5,81,53.5,11,7));
        spins().setDirty();quests().setDirty();Essentials.data().setDirty();
        for(int x=128;x<=144;x++)for(int z=128;z<=140;z++){server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);for(int y=81;y<=84;y++)server.overworld().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        base();cases();require(cases.size()==8,"Bounded case count drifted");diagnostics.put("readiness",readiness());
        diagnostics.put("preservation_scope","All editor config values semantically, generated native config-file values, prior audit entries, full native inventory/components including physical gold coins, XP, seeded daily/earned spins, personal completed quests/tasks/claims, homes, warps, private back, positions, cooldowns and an unrelated party. No every-bank-currency claim.");
        diagnostics.put("durability_scope","One running disposable server. Audit uses ordinary dirty SavedData and config files save separately. No physical crash, atomic transaction, saved restart or real old-save certification.");
    }
    static Map<String,Object> readiness() {
        Map<String,Object> result=new LinkedHashMap<>();result.put("server_tick",tick);
        for(String name:List.of("Dev","Visitor")){var p=server.getPlayerList().getPlayerByName(name);Map<String,Object> state=new LinkedHashMap<>();state.put("server_present",p!=null);state.put("server_player_ticks",p==null?-1:p.tickCount);Path f=CONTROL.resolve(name+".ready");try{state.put("client_ready",Files.exists(f)?Files.readString(f):"missing");}catch(Exception bad){state.put("client_ready_error",bad.toString());}result.put(name,state);}return result;
    }
    static Map<String,Object> observed()throws Exception {
        Map<String,Object> result=new LinkedHashMap<>();var cfg=config();
        result.put("target_memory",Map.of(MAP,cfg.get(MAP),HOME,cfg.get(HOME),TOASTS,cfg.get(TOASTS),ZONE,cfg.get(ZONE)));
        var persisted=disk();result.put("target_disk",Map.of(MAP,persisted.get(MAP),HOME,persisted.get(HOME),TOASTS,persisted.get(TOASTS),ZONE,persisted.get(ZONE)));
        result.put("audit",audit().toString());result.put("cooldowns",new TreeMap<>(cooldowns()));
        result.put("players",players().stream().map(p->Map.of("name",p.getGameProfile().getName(),"position",Loc.of(p).toString(),"inventory",playerState(p).inventory(),"spins",spins().players.get(p.getUUID()).clone(),"operator",p.hasPermissions(2))).toList());
        return result;
    }
    static void completed()throws Exception {
        done=true;Files.createDirectories(evidence());long failed=outcomes.stream().filter(s->s.startsWith("failed:")).count();long executed=outcomes.stream().filter(s->!s.startsWith("failed: fixture =>")).count();boolean passed=failed==0&&executed==8;
        try{diagnostics.put("final_native_state",observed());}catch(Exception bad){diagnostics.put("final_native_state_error",bad.toString());}
        Map<String,Object> report=new LinkedHashMap<>();report.put("commit",System.getenv("GITHUB_SHA"));report.put("passed",passed);report.put("case_count",executed);report.put("failures",failed);report.put("cases",outcomes);report.put("jars",diagnostics.get("jars"));report.put("diagnostics",diagnostics);report.put("private_or_live_data_used",false);
        Files.writeString(evidence().resolve("config-audit-phase-1.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");Files.writeString(evidence().resolve("cases-phase-1.txt"),String.join("\n",outcomes)+"\n");
        if(Files.isDirectory(CONTROL)){Path copy=evidence().resolve("client-controls");Files.createDirectories(copy);try(var files=Files.list(CONTROL)){for(var f:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().endsWith(".tmp")).toList())Files.copy(f,copy.resolve(f.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
        Files.writeString(RESULT,(passed?"passed: ":"failed: ")+executed+" config audit cases, "+failed+" failures\n");
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
            var current=cases.peek();if(start==0){start=tick;step=0;System.out.println("CONFIG AUDIT START "+current.name());}
            try{require(tick-start<1200,"Case timed out at step "+step);current.body().tick();}
            catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: "+current.name()+" at step "+step+" => "+bad);try{diagnostics.put("failure "+current.name(),observed());}catch(Exception failure){diagnostics.put("failure "+current.name(),failure.toString());}cases.remove();start=0;step=0;}
        } catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: fixture => "+bad);try{completed();}catch(Exception ignored){done=true;}}
    }
}
