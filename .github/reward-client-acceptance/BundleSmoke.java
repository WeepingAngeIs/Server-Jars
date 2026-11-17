package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.google.gson.GsonBuilder;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.*;
import dev.unyielding.quests.spin.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Hosted, disposable, connected-client authorization checks. No release code or live data. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final Path CONTROL=Path.of("/tmp/uq-reward-client-controls"),RESULT=Path.of("/tmp/uq-reward-client-result");
    static final Set<String> RECEIPTS=ConcurrentHashMap.newKeySet();
    static final String MARKER="uq_reward_client_marker";
    static final long QUEST=8_410_001L,COMMON=8_410_002L,ALL_A=8_410_003L,ALL_B=8_410_004L,INVALID=8_410_005L,REWARD=8_420_001L,INVALID_REWARD=8_420_002L;
    static MinecraftServer server;
    static int tick,initializedAt,start,step,stageTick,sequence;
    static boolean initialized,done,realDisconnect;
    static ServerPlayer oldPlayer;
    static String oldReady;
    static Snapshot before;
    static final Map<String,String> commands=new HashMap<>();
    static final Map<String,Integer> processedTicks=new HashMap<>();
    static final Map<String,Object> diagnostics=new LinkedHashMap<>();
    static final List<String> outcomes=new ArrayList<>();
    interface Body {void tick()throws Exception;}
    record Case(String name,Body body) {}
    record Debit(String player,int slot) {}
    record Credit(String player,long quest) {}
    record Account(long[] balance,int stars,int markers,List<String> other,int party) {}
    record Snapshot(Map<UUID,Account> players,Map<UUID,RewardJournal.Entry> payouts,Map<UUID,RewardJournal.QuestCredit> credits,Set<UUID> debits,Set<UUID> appliedCredits) {}
    static final Deque<Case> cases=new ArrayDeque<>();
    public record Barrier(String token) implements CustomPacketPayload {
        public static final Type<Barrier> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:reward_barrier"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Barrier> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Barrier::token,Barrier::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Receipt(String token) implements CustomPacketPayload {
        public static final Type<Receipt> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:reward_receipt"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Receipt> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Receipt::token,Receipt::new);
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public BundleSmoke(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener(this::tick);
        bus.addListener((RegisterPayloadHandlersEvent event)->{
            var r=event.registrar("1");
            r.playToServer(Barrier.TYPE,Barrier.CODEC,(payload,ctx)->{
                var p=(ServerPlayer)ctx.player();String name=p.getGameProfile().getName();
                require(name.equals("Dev")||name.equals("Visitor"),"Unexpected fixture connection");
                try {write(CONTROL.resolve(name+".processed"),payload.token());}
                catch(java.io.IOException bad){throw new IllegalStateException(bad);}
                Net.sendTo(p,new Receipt(payload.token()));
            });
            // Shared record/set avoid loading client-only classes on the dedicated server.
            r.playToClient(Receipt.TYPE,Receipt.CODEC,(payload,ctx)->RECEIPTS.add(payload.token()));
        });
    }
    static void require(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
    static void write(Path path,String value)throws java.io.IOException {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,value);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    static ServerPlayer player(String name){return server.getPlayerList().getPlayerByName(name);}
    static ServerPlayer dev(){return player("Dev");}
    static ServerPlayer visitor(){return player("Visitor");}
    static List<ServerPlayer> players(){return List.of(dev(),visitor());}
    static Path evidence(){return Path.of(System.getenv("UQ_REWARD_CLIENT_EVIDENCE"));}
    static DailyLogin.Data daily(){return server.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(),DailyLogin.Data.NAME);}
    static QuestData quests(){return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(),QuestData.NAME);}
    static void starter(ServerPlayer p,boolean selected){var data=Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p);data.setStarterSelected(selected);require(data.getStarterSelected()==selected,"Native starter setter did not take effect");require(LuckySpin.starterReady(p)==selected,"Production starter gate differs from native flag");}
    static int party(ServerPlayer p){return Cobblemon.INSTANCE.getStorage().getParty(p).occupied();}
    static boolean marked(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);return data!=null&&data.copyTag().getBoolean(MARKER);}
    static ItemStack markerStack(){var s=new ItemStack(Items.DIAMOND,2);var data=new CompoundTag();data.putBoolean(MARKER,true);s.set(DataComponents.CUSTOM_DATA,CustomData.of(data));return s;}
    static Account account(ServerPlayer p){
        int stars=0,markers=0;List<String> other=new ArrayList<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var stack=p.getInventory().getItem(i);if(stack.isEmpty())continue;
            if(stack.is(Items.NETHER_STAR)){require(ItemStack.isSameItemSameComponents(stack,new ItemStack(Items.NETHER_STAR)),"Native item prize components changed");stars+=stack.getCount();}
            else if(marked(stack)){require(ItemStack.isSameItemSameComponents(stack,markerStack()),"Native quest marker components changed");markers+=stack.getCount();}
            else other.add(i+":"+stack.save(server.registryAccess()));
        }
        var balance=daily().players.get(p.getUUID());require(balance!=null&&balance.length==5,"Missing synthetic account");
        return new Account(balance.clone(),stars,markers,List.copyOf(other),party(p));
    }
    static Snapshot snapshot()throws Exception {
        Map<UUID,Account> accounts=new HashMap<>();for(var p:players())accounts.put(p.getUUID(),account(p));
        Map<UUID,RewardJournal.Entry> payouts=new LinkedHashMap<>();for(var e:DailyLogin.journal().list())payouts.put(e.id(),e);
        Map<UUID,RewardJournal.QuestCredit> credits=new LinkedHashMap<>();for(var c:DailyLogin.journal().questCredits())credits.put(c.id(),c);
        return new Snapshot(accounts,payouts,credits,Set.copyOf(daily().appliedDebits),Set.copyOf(daily().appliedQuestCredits));
    }
    static void check(List<Debit> expectedDebits,List<Credit> expectedCredits,String markerOwner,int markerCount)throws Exception {
        var now=snapshot();
        for(var p:players()) {
            var old=before.players().get(p.getUUID());var current=now.players().get(p.getUUID());long[] expected=old.balance().clone();String name=p.getGameProfile().getName();
            long awards=expectedDebits.stream().filter(d->d.player().equals(name)).count();
            for(var d:expectedDebits)if(d.player().equals(name))expected[d.slot()]--;
            expected[3]+=expectedCredits.stream().filter(c->c.player().equals(name)).count();
            require(Arrays.equals(current.balance(),expected),name+" balance changed incorrectly: expected "+Arrays.toString(expected)+" actual "+Arrays.toString(current.balance()));
            require(current.stars()==old.stars()+3*awards,name+" native prize item delta is not "+3*awards);
            require(current.markers()==old.markers()+(name.equals(markerOwner)?markerCount:0),name+" native quest marker delivery changed");
            require(current.other().equals(old.other()),name+" unrelated inventory changed");
            require(current.party()==old.party(),name+" received/lost a Pokemon during item-only tests");
        }
        require(now.payouts().keySet().containsAll(before.payouts().keySet()),"Prior payout history disappeared");
        var fresh=now.payouts().values().stream().filter(e->!before.payouts().containsKey(e.id())).toList();
        require(fresh.size()==expectedDebits.size(),"Payout transaction count: expected "+expectedDebits.size()+" actual "+fresh.size());
        List<Debit> remaining=new ArrayList<>(expectedDebits);Set<UUID> debitMarkers=new HashSet<>(before.debits());
        for(var entry:before.payouts().values()) {
            var current=now.payouts().get(entry.id());require(current.player().equals(entry.player())&&current.slot()==entry.slot()&&current.state()==entry.state()&&Arrays.equals(current.payload(),entry.payload()),"Prior payout record changed");
        }
        for(var entry:fresh) {
            var p=server.getPlayerList().getPlayer(entry.player());require(p!=null,"Payout owner is not a real connected player");
            require(remaining.remove(new Debit(p.getGameProfile().getName(),entry.slot())),"Payout used wrong owner or balance slot");
            require(entry.state()==RewardJournal.State.RETURNED,"Delivery did not return successfully: "+entry.state());
            require(DailyLogin.journal().deliveryAttempted(entry.id()),"Native payout attempt marker missing");
            var selected=TagParser.parseTag(new String(entry.payload(),StandardCharsets.UTF_8));
            require(selected.getString("kind").equals("item")&&selected.getInt("tier")==0,"Item-only selection changed");
            var stack=ItemStack.parse(server.registryAccess(),selected.getCompound("item")).orElseThrow();
            require(stack.getCount()==3&&ItemStack.isSameItemSameComponents(stack,new ItemStack(Items.NETHER_STAR)),"Journal prize differs from native configured stack");
            debitMarkers.add(entry.id());
        }
        require(now.debits().equals(debitMarkers),"Applied debit identity history differs from accepted requests");
        require(now.credits().keySet().containsAll(before.credits().keySet()),"Prior quest credit history disappeared");
        List<Credit> creditsLeft=new ArrayList<>(expectedCredits);Set<UUID> creditMarkers=new HashSet<>(before.appliedCredits());
        for(var credit:now.credits().values()) {
            if(before.credits().containsKey(credit.id())){require(before.credits().get(credit.id()).equals(credit),"Prior credit identity changed");continue;}
            var p=server.getPlayerList().getPlayer(credit.player());require(p!=null,"Quest credit owner is not a connected player");
            Credit expected=null;for(var c:creditsLeft)if(c.player().equals(p.getGameProfile().getName())&&LuckySpin.spinId(c.quest())==credit.spinId()){expected=c;break;}
            require(expected!=null,"Unexpected/repeated/private quest credit "+credit);
            creditsLeft.remove(expected);require(credit.generation().equals(quests().rewardEpochs.generation(credit.player(),credit.spinId())),"Credit reset generation differs");
            require(quests().isClaimed(credit.player(),credit.spinId()),"Quest credit has no personal claim marker");creditMarkers.add(credit.id());
        }
        require(creditsLeft.isEmpty(),"Expected quest credits were not recorded: "+creditsLeft);
        require(now.appliedCredits().equals(creditMarkers),"Applied quest credit identities differ");
        diagnostics.put(cases.peek().name(),Map.of("balances",players().stream().map(p->p.getGameProfile().getName()+":"+Arrays.toString(daily().players.get(p.getUUID()))).toList(),"new_payouts",fresh.stream().map(e->e.id()+":"+e.player()+":"+e.slot()+":"+e.state()).toList(),"expected_credit_count",expectedCredits.size(),"native_nether_star_counts",players().stream().map(p->p.getGameProfile().getName()+":"+account(p).stars()).toList()));
    }
    static void unchanged()throws Exception{check(List.of(),List.of(),"",0);}
    static void payout(String who,int slot)throws Exception{check(List.of(new Debit(who,slot)),List.of(),"",0);}
    static void base()throws Exception {
        Config.SPIN_ENABLED.set(true);Config.DAILY_ENABLED.set(false);Config.SPIN_BASIC.set(100);Config.SPIN_GOOD.set(0);Config.SPIN_PARADOX.set(0);Config.SPIN_MYTHICAL.set(0);Config.SPIN_LEGENDARY.set(0);
        Config.DAILY_BASIC.set(100);Config.DAILY_GOOD.set(0);Config.DAILY_PARADOX.set(0);Config.DAILY_MYTHICAL.set(0);Config.DAILY_LEGENDARY.set(0);Config.SPIN_BASIC_ITEMS.set(List.of("minecraft:nether_star 3"));
        QuestManager.file=new QuestFile();
        long today=LocalDate.now(ZoneId.of(Config.DAILY_TIMEZONE.get())).toEpochDay();
        for(var p:players()) {
            starter(p,true);p.setInvulnerable(true);p.getFoodData().setFoodLevel(20);p.setGameMode(GameType.SURVIVAL);
            for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(Items.NETHER_STAR)||marked(stack))p.getInventory().setItem(i,ItemStack.EMPTY);}
            p.inventoryMenu.broadcastFullState();daily().players.put(p.getUUID(),new long[]{today,4,0,p==dev()?6:5,System.currentTimeMillis()});
        }
        daily().setDirty();before=snapshot();
    }
    static void addQuest(long id,boolean devComplete,boolean visitorComplete,boolean reward) {
        var q=new QuestFile.Quest();q.id=id;q.title="Disposable authorization quest "+id;
        var task=new QuestFile.Task();task.id=id+100_000;task.quest=id;task.type=QuestFile.TaskType.CHECKMARK;q.tasks.add(task);QuestManager.file.tasks.put(task.id,task);
        if(reward) {
            var r=new QuestFile.Reward();r.id=id==INVALID?INVALID_REWARD:REWARD;r.quest=id;r.type=QuestFile.RewardType.ITEM;r.item=QuestFile.ItemRef.of("minecraft:diamond");r.amount=2;
            r.item.components=new CompoundTag();var data=new CompoundTag();data.putBoolean(MARKER,true);r.item.components.put("minecraft:custom_data",data);
            q.rewards.add(r);QuestManager.file.rewards.put(r.id,r);
        }
        QuestManager.file.quests.put(id,q);QuestManager.file.spinQuests.put(LuckySpin.spinId(id),q);
        for(var p:players()) {
            var progress=QuestManager.progressOf(p);progress.completedQuests.remove(id);progress.completedTasks.remove(task.id);
            if(p==dev()?devComplete:visitorComplete){progress.completedQuests.add(id);progress.completedTasks.add(task.id);}
        }
        quests().setDirty();
    }
    static void request(String who,String action,String... args)throws Exception {
        String token="reward-"+(++sequence);commands.put(who,token);
        write(CONTROL.resolve(who+".txt"),token+"\t"+action+(args.length==0?"":"\t"+String.join("\t",args)));
    }
    static boolean ack(String who)throws Exception {
        String token=commands.get(who);Path ack=CONTROL.resolve(who+".ack");if(token==null||!Files.exists(ack))return false;
        String value=Files.readString(ack);if(!value.startsWith(token+"\t"))return false;
        require(value.equals(token+"\tpassed"),"Client action failed: "+value);
        Path receipt=CONTROL.resolve(who+".processed");if(!Files.exists(receipt)||!Files.readString(receipt).equals(token))return false;
        return tick-processedTicks.computeIfAbsent(token,k->tick)>=2;
    }
    static CompoundTag screen(String who,String label)throws Exception {
        var state=TagParser.parseTag(Files.readString(CONTROL.resolve(who+".screen."+label+".snbt")));
        require(state.getString("fixtureScreen").equals("dev.unyielding.quests.client.LuckySpinScreen"),"Not the production screen");
        require(!state.getString("token").isEmpty(),"Native client did not capture server token");return state;
    }
    static void status(String who,String label)throws Exception{request(who,"status",label);}
    static void spin(String who,String owner,String label,int copies)throws Exception{request(who,"spin",owner,label,""+copies);}
    static String claim(long id,int mode){return id+","+mode;}
    static void claims(String who,String... pairs)throws Exception{request(who,"claims",String.join(";",pairs));}
    static void next(){step++;stageTick=tick;}
    static boolean elapsed(int n){return tick-stageTick>=n;}
    static void add(String name,Body body){cases.add(new Case(name,body));}
    static void finish(){outcomes.add("passed: "+cases.peek().name());System.out.println("REWARD CLIENT "+outcomes.getLast());cases.remove();start=0;step=0;}
    static void cases() {
        add("starter false blocks native UI and forced token with nonempty party",()->{
            if(step==0){base();require(party(dev())>0,"Starter gate precondition needs a nonempty real party");starter(dev(),false);before=snapshot();status("Dev","no-starter");next();}
            else if(step==1&&ack("Dev")){var s=screen("Dev","no-starter");require(!s.getBoolean("ready")&&!s.getBoolean("fixtureButtonActive"),"Native screen enabled a pre-starter spin");spin("Dev","Dev","no-starter",1);next();}
            else if(step==2&&ack("Dev")){unchanged();require(!Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(dev()).getStarterSelected(),"Forced spin changed starter flag");finish();}
        });
        add("native Spin button consumes one earned balance and delivers exact stack",()->{
            if(step==0){base();status("Dev","accepted");next();}
            else if(step==1&&ack("Dev")){var s=screen("Dev","accepted");require(s.getBoolean("ready")&&s.getBoolean("fixtureButtonActive")&&s.getLong("count")==6,"Native status/balance incorrect");request("Dev","click");next();}
            else if(step==2&&ack("Dev")){payout("Dev",3);finish();}
        });
        add("used native token replay cannot debit or deliver again",()->{
            if(step==0){before=snapshot();spin("Dev","Dev","accepted",1);next();}
            else if(step==1&&ack("Dev")){unchanged();finish();}
        });
        add("rapid duplicate valid-token packets produce one payout",()->{
            if(step==0){base();status("Dev","burst");next();}
            else if(step==1&&ack("Dev")){screen("Dev","burst");spin("Dev","Dev","burst",4);next();}
            else if(step==2&&ack("Dev")){payout("Dev",3);finish();}
        });
        add("foreign captured token cannot spend either player's balance",()->{
            if(step==0){base();status("Visitor","foreign");next();}
            else if(step==1&&ack("Visitor")){screen("Visitor","foreign");spin("Dev","Visitor","foreign",1);next();}
            else if(step==2&&ack("Dev")){unchanged();spin("Visitor","Visitor","foreign",1);next();}
            else if(step==3&&ack("Visitor")){payout("Visitor",3);finish();}
        });
        add("forged and empty tokens never authorize payout",()->{
            if(step==0){base();status("Dev","before-forgery");next();}
            else if(step==1&&ack("Dev")){request("Dev","literal","definitely-not-a-server-token","2");next();}
            else if(step==2&&ack("Dev")){unchanged();request("Dev","literal","","2");next();}
            else if(step==3&&ack("Dev")){unchanged();finish();}
        });
        add("status refresh rotates token and invalidates old status token",()->{
            if(step==0){base();status("Dev","refresh-old");next();}
            else if(step==1&&ack("Dev")){status("Dev","refresh-new");next();}
            else if(step==2&&ack("Dev")){require(!screen("Dev","refresh-old").getString("token").equals(screen("Dev","refresh-new").getString("token")),"Status did not rotate token");spin("Dev","Dev","refresh-old",1);next();}
            else if(step==3&&ack("Dev")){unchanged();finish();}
        });
        add("actual disconnect and reconnect invalidates previously issued token",()->{
            if(step==0){base();status("Dev","before-reconnect");next();}
            else if(step==1&&ack("Dev")){oldPlayer=dev();oldReady=Files.readString(CONTROL.resolve("Dev.ready"));request("Dev","reconnect");next();}
            else if(step==2){if(dev()==null){realDisconnect=true;next();}else require(!elapsed(160),"Native client did not actually disconnect");}
            else if(step==3){
                if(dev()==null||dev()==oldPlayer||dev().tickCount<100)return;
                Path ready=CONTROL.resolve("Dev.ready");if(!Files.exists(ready)||Files.readString(ready).equals(oldReady))return;
                require(realDisconnect,"No server-observed disconnect");require(Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(dev()).getStarterSelected(),"Native starter flag lost on reconnect");
                spin("Dev","Dev","before-reconnect",1);next();
            }
            else if(step==4&&ack("Dev")){unchanged();finish();}
        });
        add("closing a live reel then reopening cannot reaward or lose item",()->{
            if(step==0){base();status("Dev","close-before");next();}
            else if(step==1&&ack("Dev")){request("Dev","click_close");next();}
            else if(step==2&&ack("Dev")){payout("Dev",3);status("Dev","close-after");next();}
            else if(step==3&&ack("Dev")){payout("Dev",3);require(screen("Dev","close-after").getLong("count")==5,"Reopened native screen has wrong balance");finish();}
        });
        add("daily slot is debited before earned balance with native item payout",()->{
            if(step==0){base();Config.DAILY_ENABLED.set(true);daily().players.get(dev().getUUID())[2]=2;daily().setDirty();before=snapshot();status("Dev","daily");next();}
            else if(step==1&&ack("Dev")){var s=screen("Dev","daily");require(s.getBoolean("dailyEnabled")&&s.getLong("count")==8,"Native daily status not updated");spin("Dev","Dev","daily",1);next();}
            else if(step==2&&ack("Dev")){payout("Dev",2);finish();}
        });
        add("both disabled refuses real valid token despite stored balances",()->{
            if(step==0){base();daily().players.get(dev().getUUID())[2]=2;Config.SPIN_ENABLED.set(false);Config.DAILY_ENABLED.set(false);daily().setDirty();before=snapshot();status("Dev","disabled");next();}
            else if(step==1&&ack("Dev")){var s=screen("Dev","disabled");require(!s.getBoolean("enabled")&&!s.getBoolean("fixtureButtonActive")&&s.getLong("count")==0,"Disabled native screen offered spin");spin("Dev","Dev","disabled",1);next();}
            else if(step==2&&ack("Dev")){unchanged();finish();}
        });
        add("zero balance refuses valid captured token without negative debit",()->{
            if(step==0){base();daily().players.get(dev().getUUID())[3]=0;daily().setDirty();before=snapshot();status("Dev","empty");next();}
            else if(step==1&&ack("Dev")){var s=screen("Dev","empty");require(s.getLong("count")==0&&!s.getBoolean("fixtureButtonActive"),"Empty native screen offered spin");spin("Dev","Dev","empty",1);next();}
            else if(step==2&&ack("Dev")){unchanged();finish();}
        });
        add("Net Claim cannot claim another player's completed quest or unknown id",()->{
            if(step==0){base();addQuest(QUEST,true,false,true);before=snapshot();claims("Visitor",claim(LuckySpin.spinId(QUEST),0),claim(REWARD,0),claim(QUEST,1),claim(0,2),claim(Long.MAX_VALUE,0),claim(Long.MAX_VALUE,1));next();}
            else if(step==1&&ack("Visitor")){unchanged();require(!QuestManager.progressOf(visitor()).questDone(QUEST)&&QuestManager.progressOf(dev()).questDone(QUEST),"Incomplete/private completion state changed");require(!quests().isClaimed(visitor().getUUID(),LuckySpin.spinId(QUEST))&&!quests().isClaimed(visitor().getUUID(),REWARD),"Incomplete sender got personal claim flag");finish();}
        });
        add("Net Claim mode zero and duplicate cross-mode requests grant one private credit",()->{
            if(step==0){base();addQuest(QUEST,true,false,true);before=snapshot();claims("Dev",claim(REWARD,0),claim(REWARD,0),claim(LuckySpin.spinId(QUEST),0),claim(LuckySpin.spinId(QUEST),0),claim(QUEST,1),claim(0,2));next();}
            else if(step==1&&ack("Dev")){check(List.of(),List.of(new Credit("Dev",QUEST)),"Dev",2);require(quests().isClaimed(dev().getUUID(),REWARD),"Native item reward not marked claimed");finish();}
        });
        add("unknown Net Claim modes minus one and three have no reward effects",()->{
            if(step==0){base();addQuest(INVALID,true,false,true);before=snapshot();claims("Dev",claim(INVALID,-1),claim(INVALID,3));next();}
            else if(step==1&&ack("Dev")){unchanged();require(!quests().isClaimed(dev().getUUID(),LuckySpin.spinId(INVALID))&&!quests().isClaimed(dev().getUUID(),INVALID_REWARD),"Unknown mode mutated personal claim ledger");finish();}
        });
        add("Net Claim modes one and two keep same-quest credits personal",()->{
            if(step==0){base();addQuest(COMMON,true,true,false);addQuest(ALL_A,true,false,false);addQuest(ALL_B,true,false,false);before=snapshot();claims("Visitor",claim(COMMON,1),claim(COMMON,1));next();}
            else if(step==1&&ack("Visitor")){check(List.of(),List.of(new Credit("Visitor",COMMON)),"",0);require(!quests().isClaimed(dev().getUUID(),LuckySpin.spinId(COMMON)),"Visitor credit marked Dev claimed");claims("Dev",claim(0,2),claim(0,2));next();}
            else if(step==2&&ack("Dev")){check(List.of(),List.of(new Credit("Visitor",COMMON),new Credit("Dev",COMMON),new Credit("Dev",ALL_A),new Credit("Dev",ALL_B)),"",0);require(!quests().isClaimed(visitor().getUUID(),LuckySpin.spinId(ALL_A))&&!quests().isClaimed(visitor().getUUID(),LuckySpin.spinId(ALL_B)),"Claim-all leaked private claims");finish();}
        });
    }
    static Map<String,Object> observed()throws Exception {
        var state=new LinkedHashMap<String,Object>();
        state.put("players",players().stream().map(p->{var values=new LinkedHashMap<String,Object>();values.put("name",p.getGameProfile().getName());values.put("uuid",p.getUUID().toString());values.put("balance",daily().players.get(p.getUUID()).clone());values.put("nether_stars",account(p).stars());values.put("marked_diamonds",account(p).markers());values.put("party",party(p));values.put("claimed",Set.copyOf(quests().claimedRewards.getOrDefault(p.getUUID(),Set.of())));return values;}).toList());
        state.put("payout_journal",DailyLogin.journal().list().stream().map(e->Map.of("id",e.id().toString(),"player",e.player().toString(),"slot",e.slot(),"state",e.state().toString(),"payload",new String(e.payload(),StandardCharsets.UTF_8))).toList());
        state.put("quest_credit_journal",DailyLogin.journal().questCredits().stream().map(c->Map.of("id",c.id().toString(),"player",c.player().toString(),"spin_id",c.spinId(),"generation",c.generation().toString())).toList());
        state.put("applied_debits",Set.copyOf(daily().appliedDebits));state.put("applied_quest_credits",Set.copyOf(daily().appliedQuestCredits));return state;
    }
    static void initialize()throws Exception {
        require("1".equals(System.getenv().getOrDefault("UQ_REWARD_CLIENT_PHASE","1")),"This bounded fixture is one phase");
        Files.createDirectories(CONTROL);Files.createDirectories(evidence());
        Path jar=Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        require(hash.equals(System.getenv("UQ_BUNDLE_SHA256")),"Wrong candidate SHA-256");
        require(jar.equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"Production did not load assembled candidate JAR");
        require(ModList.get().isLoaded("cobblemon"),"Native Cobblemon dependency missing");
        require(!ModList.get().isLoaded("minecraftjail")&&!ModList.get().isLoaded("minecraft_jail")&&server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"Jail must not be loaded");
        try{Class.forName("dev.unyielding.quests.jail.JailData");throw new IllegalStateException("Integrated Jail present");}catch(ClassNotFoundException expected){}
        diagnostics.put("jars",Map.of(jar.getFileName().toString(),hash));diagnostics.put("jail_loaded",false);
        require(daily().appliedDebits.isEmpty()&&daily().appliedQuestCredits.isEmpty(),"Expected fresh disposable reward history");
        if(DailyLogin.journal()==null){require(DailyLogin.journalForReview()==null,"Existing journal requires review; fixture must not replace it");DailyLogin.provisionNewJournal(server);}
        require(DailyLogin.journal()!=null&&DailyLogin.journal().list().isEmpty()&&DailyLogin.journal().questCredits().isEmpty(),"Expected new journal");
        Config.SHARE_QUEST_PROGRESS.set(false);QuestManager.onSharingChanged(false);
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());
        for(int x=124;x<=134;x++)for(int z=124;z<=132;z++)server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);
        int offset=0;for(var p:players()) {
            p.getInventory().clearContent();p.teleportTo(server.overworld(),128.5+offset,81,128.5,0,0);offset+=3;
            var party=Cobblemon.INSTANCE.getStorage().getParty(p);
            require(party.occupied()==0,"Unexpected existing Pokemon in disposable account");var props=new PokemonProperties();props.setSpecies("eevee");props.setLevel(5);require(party.add(props.create(p)),"Cannot seed native nonempty party");party.sendTo(p);
        }
        base();cases();require(cases.size()==16,"Bounded acceptance case count drifted");
        diagnostics.put("scope","Real connected-client in-process authorization and native delivery; RETURNED is not a cross-store durable receipt. No power-cut or prize-durability claim.");
        diagnostics.put("status_refresh_contract","Every status rotates the caller token; stale and invalid attempts also consume/rotate that caller token.");
    }
    static void completed()throws Exception {
        done=true;Files.createDirectories(evidence());long failed=outcomes.stream().filter(s->s.startsWith("failed:")).count();boolean passed=failed==0&&outcomes.size()==16&&realDisconnect;
        try{diagnostics.put("final_native_state",observed());}catch(Exception bad){diagnostics.put("final_native_state_error",bad.toString());}
        var report=new LinkedHashMap<String,Object>();report.put("commit",System.getenv("GITHUB_SHA"));report.put("phase",1);report.put("passed",passed);report.put("case_count",outcomes.size());report.put("failures",failed);report.put("cases",outcomes);report.put("diagnostics",diagnostics);report.put("real_disconnect_seen",realDisconnect);report.put("private_or_live_data_used",false);report.put("jars",diagnostics.get("jars"));
        Files.writeString(evidence().resolve("reward-client-phase-1.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        Files.writeString(evidence().resolve("cases-phase-1.txt"),String.join("\n",outcomes)+"\n");
        if(Files.isDirectory(CONTROL)){Path copy=evidence().resolve("client-controls");Files.createDirectories(copy);try(var files=Files.list(CONTROL)){for(var file:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().endsWith(".tmp")).toList())Files.copy(file,copy.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);}}
        Files.writeString(RESULT,(passed?"passed: ":"failed: ")+outcomes.size()+" reward client cases, "+failed+" failures\n");
    }
    void tick(ServerTickEvent.Post event) {
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        server=event.getServer();tick++;
        try {
            if(!initialized) {
                require(tick<1800,"Real clients did not become loaded-world ready");
                if(dev()==null||visitor()==null||tick<120||dev().tickCount<100||visitor().tickCount<100)return;
                for(var p:players()){var ready=CONTROL.resolve(p.getGameProfile().getName()+".ready");if(!Files.exists(ready)||!Files.readString(ready).startsWith(p.getUUID()+"\t"))return;}
                initialized=true;initialize();initializedAt=tick;return;
            }
            // No ground entities are required. Let teleports/chunk delivery and party sync settle.
            if(tick-initializedAt<60)return;
            if(cases.isEmpty()){completed();return;}
            var current=cases.peek();
            if(start==0){require(dev()!=null&&visitor()!=null,"Client missing before case");start=tick;stageTick=tick;step=0;System.out.println("REWARD CLIENT START "+current.name());}
            try {require(tick-start<900,"Case timed out at step "+step);current.body().tick();}
            catch(Throwable bad){bad.printStackTrace();try{diagnostics.put("failure "+current.name(),observed());}catch(Exception capture){diagnostics.put("failure "+current.name(),"State capture also failed: "+capture);}String result="failed: "+current.name()+" at step "+step+" => "+bad;outcomes.add(result);System.out.println("REWARD CLIENT "+result);cases.remove();start=0;step=0;}
        } catch(Throwable bad) {
            bad.printStackTrace();outcomes.add("failed: fixture => "+bad);
            try{completed();}catch(Exception ignored){done=true;}
        }
    }
}
