package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.minecraft.world.scores.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Synthetic hosted item-safety acceptance. No fake players, Jail or live data. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final Path CONTROL=Path.of("/tmp/uq-ground-controls"),RESULT=Path.of("/tmp/uq-ground-result");
    static final String PREFIX="UQ ground ";
    static final BlockPos PLOT=new BlockPos(128,80,128);
    static final AABB AREA=new AABB(95,70,115,150,95,145);
    static MinecraftServer server;static int tick,start,step,stageTick,sequence,phase;
    static boolean initialized,done,realDisconnect;
    static String marker;static ItemEntity a,b;static UUID oldA,oldB;static CompoundTag expected,expectedSecond;
    static int rememberedEpoch;static ServerPlayer oldPlayer;static long reconnectDeadline;
    static final Map<String,String> commands=new HashMap<>();static final Map<String,Integer> commandTicks=new HashMap<>();
    static final Map<String,Integer> processedTicks=new HashMap<>();
    static final Map<String,Object> diagnostics=new LinkedHashMap<>();static final List<String> outcomes=new ArrayList<>();
    static final List<ItemEntity> joined=new ArrayList<>();
    interface Body{void tick()throws Exception;}
    record Case(String name,Body body){}
    static final Deque<Case> cases=new ArrayDeque<>();
    public record Barrier(String token) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<Barrier> TYPE=new Type<>(ResourceLocation.parse("uq_bundle_smoke:ground_barrier"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,Barrier> CODEC=net.minecraft.network.codec.StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,Barrier::token,Barrier::new);
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type(){return TYPE;}
    }
    public BundleSmoke(net.neoforged.bus.api.IEventBus modBus){
        NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::joined);
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e)->e.registrar("1").playToServer(Barrier.TYPE,Barrier.CODEC,(payload,context)->{
            try{Files.writeString(CONTROL.resolve(context.player().getGameProfile().getName()+".processed"),payload.token());}catch(java.io.IOException bad){throw new IllegalStateException(bad);}
        }));
    }
    void joined(EntityJoinLevelEvent event){if(!event.getLevel().isClientSide&&event.getEntity() instanceof ItemEntity item&&fixture(item.getItem()))joined.add(item);}
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static ServerPlayer player(String name){return server.getPlayerList().getPlayerByName(name);}
    static ServerPlayer dev(){return player("Dev");}static ServerPlayer visitor(){return player("Visitor");}
    static Path evidence(){return Path.of(System.getenv("UQ_GROUND_EVIDENCE"));}
    static Path world(){return server.getWorldPath(LevelResource.ROOT);}
    static Objective objective(String name){var v=server.getScoreboard().getObjective(name);require(v!=null,"Missing objective "+name);return v;}
    static int score(String key){return score("cui_items",key);}
    static int score(String obj,String key){var v=server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly("#"+key),objective(obj));return v==null?0:v.value();}
    static void set(String key,int value){set("cui_items",key,value);}
    static void set(String obj,String key,int value){server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#"+key),objective(obj)).set(value);}
    static int stamp(ItemEntity item){var v=server.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(item.getStringUUID()),objective("cui_candidate"));return v==null?-1:v.value();}
    static boolean fixture(ItemStack stack){var name=stack.get(DataComponents.CUSTOM_NAME);return name!=null&&name.getString().startsWith(PREFIX);}
    static boolean named(ItemStack stack){var name=stack.get(DataComponents.CUSTOM_NAME);return name!=null&&name.getString().equals(PREFIX+marker);}
    static List<ItemEntity> items(){return server.overworld().getEntities(net.minecraft.world.entity.EntityType.ITEM,AREA,e->fixture(e.getItem()));}
    static List<ItemEntity> matching(){return items().stream().filter(e->named(e.getItem())).toList();}
    static int groundCount(){return matching().stream().mapToInt(e->e.getItem().getCount()).sum();}
    static int heldCount(ServerPlayer p){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(named(p.getInventory().getItem(i)))n+=p.getInventory().getItem(i).getCount();return n;}
    static CompoundTag encoded(ItemStack stack){return (CompoundTag)stack.save(server.registryAccess());}
    static ItemStack stack(Item item,int count){var s=new ItemStack(item,count);s.set(DataComponents.CUSTOM_NAME,Component.literal(PREFIX+marker));return s;}
    static Item item(String id){var key=ResourceLocation.parse(id);require(BuiltInRegistries.ITEM.containsKey(key),"Missing actual item "+id);return BuiltInRegistries.ITEM.get(key);}
    static ItemEntity spawn(ItemStack stack,int age,double dx){var e=new ItemEntity(server.overworld(),PLOT.getX()+.5+dx,81.2,PLOT.getZ()+.5,stack);e.setNoGravity(true);e.setDeltaMovement(Vec3.ZERO);e.setPickUpDelay(1000);require(server.overworld().addFreshEntity(e),"Cannot spawn item");age(e,age);return e;}
    static void age(ItemEntity entity,int value){var nbt=entity.saveWithoutId(new CompoundTag());nbt.putShort("Age",(short)value);entity.load(nbt);}
    static void away(ServerPlayer p,double x){p.teleportTo(server.overworld(),x,81,128.5,0,0);}
    static void reset()throws Exception{
        for(var e:items())e.discard();
        for(var p:List.of(dev(),visitor())){for(int i=0;i<p.getInventory().getContainerSize();i++)if(fixture(p.getInventory().getItem(i)))p.getInventory().setItem(i,ItemStack.EMPTY);p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.getFoodData().setFoodLevel(20);p.inventoryMenu.broadcastFullState();}
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());away(dev(),102.5);away(visitor(),105.5);
        set("enabled",0);set("interval",12000);set("last_interval",12000);set("timer",0);set("grace",1200);set("clear_food",1);set("clear_gear",0);set("clear_other",0);
        set("dim_overworld",1);set("dim_nether",0);set("dim_end",0);set("dim_custom",0);
        int epoch=score("cui_candidate","preview_epoch");set("cui_candidate","preview_epoch",epoch==Integer.MAX_VALUE?0:epoch+1);
        a=null;b=null;expected=null;expectedSecond=null;joined.clear();marker=phase+" "+sequence+" "+UUID.randomUUID();
    }
    static void next(){step++;stageTick=tick;}
    static boolean elapsed(int n){return tick-stageTick>=n;}
    static void request(String who,String action,String... args)throws Exception{String token=phase+"-"+(++sequence);commands.put(who,token);commandTicks.put(who,tick);Files.writeString(CONTROL.resolve(who+".txt"),token+"\t"+action+(args.length==0?"":"\t"+String.join("\t",args)));}
    static void action(String who,String key,int value)throws Exception{request(who,"ground_"+key,""+value);}
    static boolean ack(String who)throws Exception{var path=CONTROL.resolve(who+".ack");String token=commands.get(who);if(token==null||!Files.exists(path))return false;String text=Files.readString(path);if(!text.startsWith(token+"\t"))return false;require(text.equals(token+"\tpassed"),"Client action failed: "+text);var processed=CONTROL.resolve(who+".processed");if(!Files.exists(processed)||!Files.readString(processed).equals(token))return false;return tick-processedTicks.computeIfAbsent(token,k->tick)>=2;}
    static void preview(String who)throws Exception{action(who,"preview",0);}
    static void confirm(String who)throws Exception{action(who,"confirm",0);}
    static void function(String name){var function=server.getFunctions().get(ResourceLocation.parse("cui_cleanup:"+name));require(function.isPresent(),"Missing cleanup function "+name);server.getFunctions().execute(function.get(),server.getFunctions().getGameLoopSender());}
    static void finish(){outcomes.add("passed: "+cases.peek().name());System.out.println("GROUND "+outcomes.getLast());cases.remove();start=0;step=0;}
    static void add(String name,Body body){cases.add(new Case(name,body));}
    static void basicFood()throws Exception{reset();a=spawn(stack(Items.COOKED_BEEF,2),1300,0);}
    static void conservation(int total){require(groundCount()+heldCount(dev())+heldCount(visitor())==total,"Fixture item count changed: expected "+total+" actual "+(groundCount()+heldCount(dev())+heldCount(visitor())));}
    static void giveHand(ServerPlayer p,ItemStack stack){int slot=-1;for(int i=0;i<9;i++)if(p.getInventory().getItem(i).isEmpty()){slot=i;break;}require(slot>=0,"No empty synthetic hotbar slot");p.getInventory().selected=slot;p.getInventory().setItem(slot,stack);p.inventoryMenu.broadcastFullState();p.connection.send(new ClientboundSetCarriedItemPacket(slot));}
    static void drop(String who,int count)throws Exception{var p=player(who);request(who,"drop_all",PREFIX+marker,""+count,""+p.getX(),""+p.getY(),""+p.getZ());}
    static long pendingExpiry(UUID owner)throws Exception{var f=dev.unyielding.quests.settings.GroundSettings.class.getDeclaredField("PENDING");f.setAccessible(true);Object pending=((Map<?,?>)f.get(null)).get(owner);require(pending!=null,"Native server did not retain preview");var method=pending.getClass().getDeclaredMethod("expires");method.setAccessible(true);return (long)method.invoke(pending);}
    static void mergeCase(Item mergeItem,int oldCount,int freshCount,boolean oldWins,boolean partial,boolean bothOld)throws Exception{
        if(step==0){reset();a=spawn(stack(mergeItem,oldCount),1300,0);oldA=a.getUUID();expected=encoded(a.getItem());expected.remove("count");preview("Dev");next();return;}
        if(step==1&&ack("Dev")){require(stamp(a)==score("cui_candidate","preview_epoch"),"Original not previewed");b=spawn(stack(mergeItem,freshCount),bothOld?1300:0,0);oldB=b.getUUID();next();return;}
        if(step==2){
            int total=oldCount+freshCount;boolean merged=partial?(!a.isRemoved()&&!b.isRemoved()&&a.getItem().getCount()==64&&b.getItem().getCount()==total-64):(oldWins?(!a.isRemoved()&&b.isRemoved()):(!b.isRemoved()&&a.isRemoved()));
            if(!merged){require(!elapsed(140),"Real tick merge did not reach expected survivor/partial state");return;}
            conservation(total);for(var e:matching()){if(!bothOld)require(e.getAge()<1200,"Native merge lost fresh grace");var current=encoded(e.getItem());current.remove("count");require(current.equals(expected),"Native merge changed item/components");}
            diagnostics.put(cases.peek().name(),Map.of("original_uuid",oldA.toString(),"added_uuid",oldB.toString(),"survivors",matching().stream().map(e->e.getUUID()+":"+e.getItem().getCount()+":"+e.getAge()).toList()));confirm("Dev");next();return;
        }
        if(step==3&&ack("Dev")){conservation(oldCount+freshCount);require(groundCount()==oldCount+freshCount,"Manual confirmation removed changed merge");finish();}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) static ItemStack wallet()throws Exception{
        var wallet=stack(item("lightmanscurrency:wallet_leather"),1);List<ItemStack> inner=new ArrayList<>();for(int i=0;i<6;i++)inner.add(ItemStack.EMPTY);inner.set(0,new ItemStack(item("lightmanscurrency:coin_gold"),11));inner.set(5,new ItemStack(item("lightmanscurrency:coin_gold"),7));
        Object data=Class.forName("io.github.lightman314.lightmanscurrency.common.items.data.WalletData").getConstructor(List.class,boolean.class,int.class).newInstance(List.copyOf(inner),false,0);
        wallet.set((DataComponentType)BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("lightmanscurrency:wallet_data")),data);return wallet;
    }
    static void phaseOne(){
        add("native merge original UUID survives",()->mergeCase(Items.COOKED_MUTTON,5,1,true,false,false));
        add("native Nether burger merge fresh UUID survives",()->mergeCase(item("mynethersdelight:nether_burger"),1,5,false,false,false));
        add("native overfull pair refuses merge and protects fresh stack",()->{
            if(step==0){reset();a=spawn(stack(Items.COOKED_MUTTON,60),1300,0);preview("Dev");next();}
            else if(step==1&&ack("Dev")){require(stamp(a)==score("cui_candidate","preview_epoch"),"Original overfull stack not previewed");b=spawn(stack(Items.COOKED_MUTTON,8),0,0);next();}
            else if(step==2&&elapsed(100)){require(!a.isRemoved()&&!b.isRemoved()&&a.getItem().getCount()==60&&b.getItem().getCount()==8,"Native overfull merge refusal changed");conservation(68);confirm("Dev");next();}
            else if(step==3&&ack("Dev")){require(a.isRemoved()&&!b.isRemoved()&&b.getItem().getCount()==8&&b.getAge()<1200,"Overfull pair confirmation deleted fresh items or retained approved old stack");finish();}
        });
        add("native old merge invalidates preview count",()->mergeCase(Items.COOKED_MUTTON,5,1,true,false,true));
        add("two admin previews supersede safely",()->{
            if(step==0){basicFood();server.getPlayerList().op(visitor().getGameProfile());preview("Dev");next();}
            else if(step==1&&ack("Dev")){rememberedEpoch=score("cui_candidate","preview_epoch");preview("Visitor");next();}
            else if(step==2&&ack("Visitor")){require(score("cui_candidate","preview_epoch")==rememberedEpoch+1,"New administrator preview did not supersede epoch");confirm("Dev");next();}
            else if(step==3&&ack("Dev")){require(!a.isRemoved(),"Superseded administrator cleared items");confirm("Visitor");next();}
            else if(step==4&&ack("Visitor")){require(a.isRemoved(),"Latest administrator could not confirm");finish();}
        });
        add("nonoperator forged actions preserve owner preview",()->{
            if(step==0){basicFood();preview("Dev");next();}
            else if(step==1&&ack("Dev")){rememberedEpoch=score("cui_candidate","preview_epoch");preview("Visitor");next();}
            else if(step==2&&ack("Visitor")){action("Visitor","clear_gear",1);next();}
            else if(step==3&&ack("Visitor")){confirm("Visitor");next();}
            else if(step==4&&ack("Visitor")){require(!a.isRemoved()&&score("clear_gear")==0&&score("cui_candidate","preview_epoch")==rememberedEpoch,"Forged nonoperator action mutated cleanup");confirm("Dev");next();}
            else if(step==5&&ack("Dev")){require(a.isRemoved(),"Unauthorized preview destroyed authorized pending confirmation");finish();}
        });
        add("revoked operator cannot confirm",()->{
            if(step==0){basicFood();preview("Dev");next();}
            else if(step==1&&ack("Dev")){server.getPlayerList().deop(dev().getGameProfile());confirm("Dev");next();}
            else if(step==2&&ack("Dev")){require(!a.isRemoved(),"Revoked operator cleared items");server.getPlayerList().op(dev().getGameProfile());finish();}
        });
        add("other administrator setting invalidates fingerprint",()->{
            if(step==0){basicFood();server.getPlayerList().op(visitor().getGameProfile());preview("Dev");next();}
            else if(step==1&&ack("Dev")){action("Visitor","clear_other",1);next();}
            else if(step==2&&ack("Visitor")){require(score("clear_other")==1,"Setting change not received");confirm("Dev");next();}
            else if(step==3&&ack("Dev")){require(!a.isRemoved(),"Changed settings reused old preview");finish();}
        });
        add("two native player drops merge and keep scheduled fresh grace",()->{
            if(step==0){reset();away(dev(),128.5);giveHand(dev(),stack(Items.COOKED_BEEF,5));drop("Dev",5);next();}
            else if(step==1&&ack("Dev")){if(matching().isEmpty())return;require(matching().size()==1&&groundCount()==5,"First native drop count wrong");a=matching().getFirst();oldA=a.getUUID();expected=encoded(a.getItem());expected.remove("count");a.setNoGravity(true);a.setDeltaMovement(Vec3.ZERO);a.setPos(128.5,81.2,128.5);a.setPickUpDelay(1000);age(a,1300);away(dev(),102.5);away(visitor(),128.5);giveHand(visitor(),stack(Items.COOKED_BEEF,1));drop("Visitor",1);next();}
            else if(step==2&&ack("Visitor")){var added=joined.stream().filter(e->!e.getUUID().equals(oldA)).findFirst();if(added.isEmpty())return;b=added.get();oldB=b.getUUID();away(visitor(),105.5);if(!b.isRemoved()){b.setNoGravity(true);b.setDeltaMovement(Vec3.ZERO);b.setPos(128.7,81.2,128.5);b.setPickUpDelay(1000);}next();}
            else if(step==3){if(a.isRemoved()||!b.isRemoved()||a.getItem().getCount()!=6){require(!elapsed(140),"Actual two-player drops did not merge into expected original UUID");return;}require(a.getAge()<1200,"Actual fresh drop did not rejuvenate merged age");var current=encoded(a.getItem());current.remove("count");require(current.equals(expected),"Actual player drop merge changed components");conservation(6);diagnostics.put(cases.peek().name(),Map.of("original_drop_uuid",oldA.toString(),"fresh_drop_uuid",oldB.toString(),"merged_uuid",a.getUUID().toString(),"merged_age",a.getAge(),"count",6));set("interval",100);set("last_interval",100);set("timer",99);set("enabled",1);next();}
            else if(step==4&&elapsed(5)){set("enabled",0);require(score("timer")<20,"Automatic cleanup did not run");require(!a.isRemoved()&&groundCount()==6,"Scheduled cleanup removed actual fresh merged drops");conservation(6);finish();}
        });
        add("native pickup and redrop replace preview identity",()->{
            if(step==0){basicFood();expected=encoded(a.getItem());oldA=a.getUUID();preview("Dev");next();}
            else if(step==1&&ack("Dev")){a.setPickUpDelay(0);away(visitor(),128.5);next();}
            else if(step==2){if(!a.isRemoved()||heldCount(visitor())!=2){require(!elapsed(140),"Native player pickup did not complete");return;}int slot=-1;for(int i=0;i<9;i++)if(named(visitor().getInventory().getItem(i)))slot=i;require(slot>=0,"Picked-up item not in hotbar");visitor().getInventory().selected=slot;visitor().inventoryMenu.broadcastFullState();visitor().connection.send(new ClientboundSetCarriedItemPacket(slot));require(encoded(visitor().getMainHandItem()).equals(expected),"Native pickup changed stack/components");drop("Visitor",2);next();}
            else if(step==3&&ack("Visitor")){if(matching().isEmpty())return;require(matching().size()==1,"Redrop duplicated entity");b=matching().getFirst();require(!b.getUUID().equals(oldA)&&b.getAge()<1200,"Redrop reused old UUID/age");require(encoded(b.getItem()).equals(expected),"Native redrop changed stack/components");away(visitor(),105.5);confirm("Dev");next();}
            else if(step==4&&ack("Dev")){require(!b.isRemoved()&&groundCount()==2,"Old preview cleared redropped stack");conservation(2);finish();}
        });
        add("native addon foods and protected wallet edible gear",()->{
            if(step==0){reset();var burger=stack(item("farmersdelight:hamburger"),2);var nether=stack(item("mynethersdelight:nether_burger"),3);require(burger.has(DataComponents.FOOD)&&nether.has(DataComponents.FOOD),"Actual addon foods lack native food component");a=spawn(burger,1300,0);b=spawn(nether,1300,3);var gear=stack(Items.IRON_SWORD,1);gear.set(DataComponents.FOOD,new ItemStack(Items.COOKED_BEEF).get(DataComponents.FOOD));var packed=wallet();expected=encoded(gear);expectedSecond=encoded(packed);spawn(gear,1300,6);spawn(packed,1300,9);preview("Dev");next();}
            else if(step==1&&ack("Dev")){require(score("preview_stacks")==2&&score("preview_items")==5,"Native food/category preview counts wrong");confirm("Dev");next();}
            else if(step==2&&ack("Dev")){require(a.isRemoved()&&b.isRemoved(),"Actual addon food was not cleared");var survivors=items();require(survivors.size()==2,"Protected wallet/gear removed or extra stack appeared");require(survivors.stream().anyMatch(e->encoded(e.getItem()).equals(expected))&&survivors.stream().anyMatch(e->encoded(e.getItem()).equals(expectedSecond)),"Wallet/nested coins or edible gear components changed");finish();}
        });
        add("cancel consumes preview authority",()->{
            if(step==0){basicFood();preview("Dev");next();}
            else if(step==1&&ack("Dev")){action("Dev","cancel_preview",0);next();}
            else if(step==2&&ack("Dev")){confirm("Dev");next();}
            else if(step==3&&ack("Dev")){require(!a.isRemoved(),"Cancelled preview still cleared");finish();}
        });
        add("reconnected administrator cannot reuse old confirmation",()->{
            if(step==0){basicFood();preview("Dev");next();}
            else if(step==1&&ack("Dev")){oldPlayer=dev();reconnectDeadline=pendingExpiry(oldPlayer.getUUID());request("Dev","reconnect");next();}
            else if(step==2){if(dev()==null)realDisconnect=true;if(realDisconnect&&dev()!=null&&dev()!=oldPlayer){require(System.nanoTime()<reconnectDeadline,"Fixture reconnect exceeded original preview expiry");require(server.getPlayerList().isOp(dev().getGameProfile()),"Reconnect lost intended operator permission");confirm("Dev");next();}else require(!elapsed(400),"Real reconnect not observed");}
            else if(step==3&&ack("Dev")){require(System.nanoTime()<reconnectDeadline,"Fixture confirm exceeded original preview expiry");require(realDisconnect,"No actual disconnect observed");require(!a.isRemoved(),"Logout retained destructive pending confirmation");finish();}
        });
        add("preview epoch exhaustion refuses deletion",()->{
            if(step==0){basicFood();set("cui_candidate","preview_epoch",Integer.MAX_VALUE);preview("Dev");next();}
            else if(step==1&&ack("Dev")){require(score("cui_candidate","preview_epoch")==Integer.MAX_VALUE,"Preview epoch wrapped");confirm("Dev");next();}
            else if(step==2&&ack("Dev")){require(!a.isRemoved(),"Exhausted preview cleared items");finish();}
        });
        add("stage saved restart preview",()->{
            if(step==0){basicFood();preview("Dev");next();}
            else if(step==1&&ack("Dev")){require(stamp(a)==score("cui_candidate","preview_epoch"),"Restart item not marked");var nbt=new CompoundTag();nbt.putUUID("item",a.getUUID());nbt.putString("marker",marker);nbt.put("stack",encoded(a.getItem()));net.minecraft.nbt.NbtIo.writeCompressed(nbt,world().resolve("ground-restart.nbt"));finish();}
        });
    }
    static void phaseTwo(){
        add("saved server restart refuses stale confirmation",()->{
            if(step==0){if(expected==null){var nbt=net.minecraft.nbt.NbtIo.readCompressed(world().resolve("ground-restart.nbt"),net.minecraft.nbt.NbtAccounter.unlimitedHeap());marker=nbt.getString("marker");oldA=nbt.getUUID("item");expected=nbt.getCompound("stack");server.overworld().getChunkAt(PLOT);}var entity=server.overworld().getEntity(oldA);if(entity==null){require(!elapsed(140),"Saved restart entity did not load after chunk wait");return;}require(entity instanceof ItemEntity,"Saved restart UUID is not an item");a=(ItemEntity)entity;require(encoded(a.getItem()).equals(expected),"Restart changed saved stack/components");confirm("Dev");next();}
            else if(step==1&&ack("Dev")){require(!a.isRemoved()&&encoded(a.getItem()).equals(expected),"Fresh server reused old preview authority");finish();}
        });
        add("new postrestart preview remains usable",()->{
            if(step==0){preview("Dev");next();}
            else if(step==1&&ack("Dev")){confirm("Dev");next();}
            else if(step==2&&ack("Dev")){require(a.isRemoved(),"New postrestart preview could not clear eligible stack");finish();}
        });
    }
    static void initialize()throws Exception{
        phase=Integer.parseInt(System.getenv().getOrDefault("UQ_GROUND_PHASE","1"));require(phase==1||phase==2,"Unknown phase");Files.createDirectories(CONTROL);Files.createDirectories(evidence());
        Path jar=Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();require(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath().equals(jar),"Wrong production JAR");require(!ModList.get().isLoaded("minecraftjail")&&server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"Jail must not be loaded");
        try{Class.forName("dev.unyielding.quests.jail.JailData");throw new IllegalStateException("Integrated Jail present");}catch(ClassNotFoundException expected){}
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));diagnostics.put("jars",Map.of(jar.getFileName().toString(),sha));diagnostics.put("jail_loaded",false);
        for(int x=100;x<=142;x++)for(int z=120;z<=140;z++)server.overworld().setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),2);
        server.getPlayerList().op(dev().getGameProfile());server.getPlayerList().deop(visitor().getGameProfile());away(dev(),102.5);away(visitor(),105.5);for(var p:List.of(dev(),visitor())){p.setGameMode(GameType.SURVIVAL);p.setInvulnerable(true);p.getFoodData().setFoodLevel(20);}
        if(phase==1){require(score("enabled")==1&&score("interval")==12000&&score("clear_food")==1&&score("clear_gear")==0&&score("clear_other")==0&&score("grace")==1200,"Fresh cleanup defaults drifted");set("enabled",0);phaseOne();}else{require(score("enabled")==0,"Restart did not retain disabled automatic setting");phaseTwo();}
    }
    static void completed()throws Exception{
        done=true;long failed=outcomes.stream().filter(s->s.startsWith("failed:")).count();var report=new LinkedHashMap<String,Object>();report.put("commit",System.getenv("GITHUB_SHA"));report.put("phase",phase);report.put("passed",failed==0);report.put("case_count",outcomes.size());report.put("failures",failed);report.put("cases",outcomes);report.put("diagnostics",diagnostics);report.put("real_disconnect_seen",realDisconnect);report.put("private_or_live_data_used",false);report.put("jars",diagnostics.get("jars"));
        Files.writeString(evidence().resolve("ground-phase-"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");Files.writeString(evidence().resolve("cases-phase-"+phase+".txt"),String.join("\n",outcomes)+"\n");Files.writeString(RESULT,(failed==0?"passed: ":"failed: ")+outcomes.size()+" ground cases, "+failed+" failures; phase "+phase+"\n");
    }
    void tick(ServerTickEvent.Post event){
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;server=event.getServer();tick++;
        try{
            if(!initialized){if(dev()==null||visitor()==null||tick<120)return;initialized=true;initialize();}
            if(cases.isEmpty()){completed();return;}
            var current=cases.peek();if(start==0){if(dev()==null||visitor()==null)return;start=tick;stageTick=tick;step=0;System.out.println("GROUND START "+current.name());}
            try{require(tick-start<500,"Case timed out at step "+step);current.body().tick();}
            catch(Throwable bad){bad.printStackTrace();String result="failed: "+current.name()+" at step "+step+" => "+bad;outcomes.add(result);System.out.println("GROUND "+result);cases.remove();start=0;step=0;}
        }catch(Throwable bad){bad.printStackTrace();outcomes.add("failed: fixture => "+bad);try{completed();}catch(Exception ignored){done=true;}}
    }
}
