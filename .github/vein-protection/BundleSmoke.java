package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.essentials.EssConfig;
import dev.unyielding.quests.settings.AdminClaimBypass;
import dev.unyielding.quests.ultimine.UltimineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Disposable two-client assembled-JAR checks. Never packaged in production. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final Path ROOT=Path.of("/tmp/uq-vein-controls"),RESULT=Path.of("/tmp/uq-vein-protection-result");
    static MinecraftServer server;static boolean initialized,done;static int tick,start,sequence;
    static String cancel="";static BlockPos origin=new BlockPos(527,80,520);
    record Case(String name,Runnable setup,String action,String expected,Runnable check){}
    static final Deque<Case> cases=new ArrayDeque<>();static final List<String> outcomes=new ArrayList<>();
    public BundleSmoke(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.register(new LateProtection());}
    public static final class LateProtection {
        @SubscribeEvent(priority=EventPriority.LOW)
        public void deny(BlockEvent.BreakEvent e){if(cancel.equals("origin")&&e.getPos().equals(origin)||cancel.equals("extra")&&!e.getPos().equals(origin))e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOW)
        public void denyUse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){if(cancel.equals("use_origin")&&e.getPos().equals(origin)||cancel.equals("use_extra")&&!e.getPos().equals(origin))e.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void observe(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e){if(e.getEntity() instanceof ServerPlayer player&&player.getGameProfile().getName().equals("Dev"))System.out.println("VEIN USE TRACE: pos="+e.getPos()+" held="+held()+" item="+player.getMainHandItem()+" cancelled="+e.isCanceled()+" useItem="+e.getUseItem());}
    }
    static ServerPlayer p(){return server.getPlayerList().getPlayerByName("Dev");}
    static ServerPlayer v(){return server.getPlayerList().getPlayerByName("Visitor");}
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static boolean held(){try{var f=dev.unyielding.quests.ultimine.Ultimine.class.getDeclaredField("STATES");f.setAccessible(true);return ((Map<?,?>)f.get(null)).containsKey(p().getUUID());}catch(Exception bad){throw new IllegalStateException(bad);}}
    static Object api()throws Exception {return Class.forName("xaero.pac.common.server.api.OpenPACServerAPI").getMethod("get",MinecraftServer.class).invoke(null,server);}
    static Object manager()throws Exception{return Class.forName("xaero.pac.common.server.api.OpenPACServerAPI").getMethod("getServerClaimsManager").invoke(api());}
    static void claim(UUID id){try{Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI").getMethod("claim",ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class).invoke(manager(),ResourceLocation.parse("minecraft:overworld"),id,0,33,32,false);}catch(Exception bad){throw new IllegalStateException(bad);}}
    static void unclaim(){try{Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI").getMethod("unclaim",ResourceLocation.class,int.class,int.class).invoke(manager(),ResourceLocation.parse("minecraft:overworld"),33,32);}catch(Exception bad){throw new IllegalStateException(bad);}}
    static boolean access(){try{var a=api();Object protection=a.getClass().getMethod("getChunkProtection").invoke(a);return (boolean)Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI").getMethod("hasChunkAccess",net.minecraft.world.entity.Entity.class,ResourceLocation.class,int.class,int.class).invoke(protection,p(),ResourceLocation.parse("minecraft:overworld"),33,32);}catch(Exception bad){throw new IllegalStateException(bad);}}
    static void seed(boolean logs){
        cancel="";unclaim();origin=new BlockPos(527,80,520);
        UltimineConfig.ENABLED.set(true);UltimineConfig.REQUIRE_TOOL.set(false);UltimineConfig.COMPACT_FEEDBACK.set(true);UltimineConfig.MAX_BLOCKS.set(8);UltimineConfig.PREVENT_TOOL_BREAK.set(0);
        p().setGameMode(GameType.CREATIVE);p().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(logs?Items.IRON_AXE:Items.IRON_PICKAXE));
        p().getFoodData().setFoodLevel(20);
        for(int x=526;x<=530;x++)for(int y=79;y<=83;y++)for(int z=519;z<=521;z++)server.overworld().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        for(int x=0;x<3;x++)server.overworld().setBlock(origin.offset(x,0,0),(logs?Blocks.OAK_LOG:Blocks.IRON_ORE).defaultBlockState(),3);
        p().teleportTo(server.overworld(),origin.getX()+.5,81,origin.getZ()+2.5,180,75);
        p().getAbilities().flying=true;p().onUpdateAbilities();
    }
    static void blocks(boolean a,boolean b,boolean c){boolean[] expected={a,b,c};for(int i=0;i<3;i++)require(server.overworld().getBlockState(origin.offset(i,0,0)).isAir()==expected[i],"unexpected block change at "+origin.offset(i,0,0));}
    static void stripped(boolean a,boolean b,boolean c){boolean[] expected={a,b,c};for(int i=0;i<3;i++)require(server.overworld().getBlockState(origin.offset(i,0,0)).is(Blocks.STRIPPED_OAK_LOG)==expected[i],"unexpected log transformation at "+origin.offset(i,0,0));}
    static void ground(boolean hoe){seed(false);p().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(hoe?Items.IRON_HOE:Items.IRON_SHOVEL));for(int i=0;i<3;i++)server.overworld().setBlock(origin.offset(i,0,0),Blocks.DIRT.defaultBlockState(),3);}
    static void groundChanged(boolean hoe,boolean a,boolean b,boolean c){boolean[] expected={a,b,c};for(int i=0;i<3;i++)require(server.overworld().getBlockState(origin.offset(i,0,0)).is(hoe?Blocks.FARMLAND:Blocks.DIRT_PATH)==expected[i],"unexpected ground transformation at "+origin.offset(i,0,0));}
    static void survivalStop(boolean hungry){seed(false);for(int i=0;i<3;i++)server.overworld().setBlock(origin.offset(i,0,0),Blocks.SLIME_BLOCK.defaultBlockState(),3);p().setGameMode(GameType.SURVIVAL);if(hungry)p().getFoodData().setFoodLevel(0);else p().getMainHandItem().setDamageValue(249);server.overworld().setBlock(new BlockPos(527,80,522),Blocks.BEDROCK.defaultBlockState(),3);}
    static void adventure(boolean mining,boolean permitted){
        if(mining){seed(false);for(int i=0;i<3;i++)server.overworld().setBlock(origin.offset(i,0,0),Blocks.SLIME_BLOCK.defaultBlockState(),3);}else ground(false);
        p().setGameMode(GameType.ADVENTURE);server.overworld().setBlock(new BlockPos(527,80,522),Blocks.BEDROCK.defaultBlockState(),3);
        if(permitted){String component=mining?"can_break":"can_place_on",item=mining?"iron_pickaxe":"iron_shovel",block=mining?"slime_block":"dirt";
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"item replace entity Dev weapon.mainhand with minecraft:"+item+"[minecraft:"+component+"={predicates:[{blocks:\"minecraft:"+block+"\"}]}]");
            require(p().getMainHandItem().has(mining?net.minecraft.core.component.DataComponents.CAN_BREAK:net.minecraft.core.component.DataComponents.CAN_PLACE_ON),"adventure permission component missing");}
    }
    static void add(String name,Runnable setup,String action,String expected,Runnable check){cases.add(new Case(name,setup,action,expected,check));}
    static void setup(){
        require(!UltimineConfig.COMPACT_FEEDBACK.get(),"compact feedback default changed");
        require(ModList.get().isLoaded("openpartiesandclaims"),"OPAC missing");
        EssConfig.ADMIN_CLAIM_BYPASS.set(false);server.getPlayerList().deop(p().getGameProfile());server.getPlayerList().op(v().getGameProfile());
        server.overworld().setDefaultSpawnPos(new BlockPos(0,80,0),0);
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        add("loaded area and preview warmup",()->seed(false),"preview","NONE",()->blocks(false,false,false));
        add("unclaimed allowed feedback",()->seed(false),"mine","2 extra blocks",()->blocks(true,true,true));
        add("feedback off",()->{seed(false);UltimineConfig.COMPACT_FEEDBACK.set(false);},"mine","NONE",()->blocks(true,true,true));
        add("OPAC foreign boundary denied extras",()->{seed(false);claim(v().getUUID());require(!access(),"foreign claim fixture not protected");},"mine","0 extra blocks",()->blocks(true,false,false));
        add("OPAC foreign origin denied",()->{seed(false);claim(v().getUUID());require(!access(),"foreign origin fixture not protected");origin=origin.east();},"mine","NONE",()->{require(server.overworld().getBlockState(origin.west()).is(Blocks.IRON_ORE)&&server.overworld().getBlockState(origin).is(Blocks.IRON_ORE)&&server.overworld().getBlockState(origin.east()).is(Blocks.IRON_ORE),"denied origin changed connected blocks");});
        add("OPAC owner allowed",()->{seed(false);claim(p().getUUID());require(access(),"owner fixture denied");},"mine","2 extra blocks",()->blocks(true,true,true));
        add("OPAC admin full pass allowed",()->{seed(false);claim(v().getUUID());EssConfig.ADMIN_CLAIM_BYPASS.set(true);server.getPlayerList().op(p().getGameProfile());AdminClaimBypass.reconcile(server);},"mine","2 extra blocks",()->blocks(true,true,true));
        add("OPAC full pass revoked",()->{server.getPlayerList().deop(p().getGameProfile());AdminClaimBypass.reconcile(server);EssConfig.ADMIN_CLAIM_BYPASS.set(false);seed(false);claim(v().getUUID());require(!access(),"revocation fixture still has access");},"mine","0 extra blocks",()->blocks(true,false,false));
        add("late origin event denied",()->{seed(false);cancel="origin";},"mine","NONE",()->blocks(false,false,false));
        add("extra block events denied",()->{seed(false);cancel="extra";},"mine","0 extra blocks",()->blocks(true,false,false));
        add("spawn boundary denied extras",()->{seed(false);origin=new BlockPos(17,80,0);for(int i=0;i<3;i++)server.overworld().setBlock(origin.offset(-i,0,0),Blocks.IRON_ORE.defaultBlockState(),3);p().teleportTo(server.overworld(),17.5,81,2.5,180,75);},"mine","0 extra blocks",()->{require(server.overworld().getBlockState(origin).isAir(),"outside spawn origin denied");require(server.overworld().getBlockState(origin.west()).is(Blocks.IRON_ORE)&&server.overworld().getBlockState(origin.west(2)).is(Blocks.IRON_ORE),"spawn-protected extras were mined");});
        add("connected stripping allowed",()->seed(true),"use","NONE",()->stripped(true,true,true));
        add("OPAC connected stripping denied extras",()->{seed(true);claim(v().getUUID());require(!access(),"claim fixture unprotected");},"use","NONE",()->stripped(true,false,false));
        add("connected tilling allowed",()->ground(true),"use","NONE",()->groundChanged(true,true,true,true));
        add("OPAC connected tilling denied extras",()->{ground(true);claim(v().getUUID());},"use","NONE",()->groundChanged(true,true,false,false));
        add("connected paths allowed",()->ground(false),"use","NONE",()->groundChanged(false,true,true,true));
        add("OPAC connected paths denied extras",()->{ground(false);claim(v().getUUID());},"use","NONE",()->groundChanged(false,true,false,false));
        add("late item-use origin denied",()->{ground(false);cancel="use_origin";},"use","NONE",()->groundChanged(false,false,false,false));
        add("item-use extras denied",()->{ground(false);cancel="use_extra";},"use","NONE",()->groundChanged(false,true,false,false));
        add("spectator tool use denied",()->{seed(true);p().setGameMode(GameType.SPECTATOR);},"use","NONE",()->stripped(false,false,false));
        add("adventure tool use denied",()->adventure(false,false),"use","NONE",()->groundChanged(false,false,false,false));
        add("adventure permitted tool use",()->adventure(false,true),"use","NONE",()->groundChanged(false,true,true,true));
        add("adventure mining denied",()->adventure(true,false),"mine","NONE",()->blocks(false,false,false));
        add("adventure permitted mining",()->adventure(true,true),"mine","2 extra blocks",()->blocks(true,true,true));
        add("master switch off",()->{seed(false);UltimineConfig.ENABLED.set(false);},"disabled","NONE",()->blocks(true,false,false));
        add("require item empty hand",()->{seed(false);UltimineConfig.REQUIRE_TOOL.set(true);p().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);},"empty","NONE",()->blocks(true,false,false));
        add("durability stop display",()->survivalStop(false),"mine","tool durability",()->blocks(true,false,false));
        add("hunger stop display",()->survivalStop(true),"mine","out of hunger",()->blocks(true,false,false));
        add("held display and shape preview",()->seed(false),"preview","NONE",()->blocks(false,false,false));
        add("hidden HUD display",()->seed(false),"preview_hidden","NONE",()->blocks(false,false,false));
        add("inventory stops held display",()->seed(false),"menu","NONE",()->blocks(false,false,false));
    }
    static void send(Case c)throws Exception{
        dev.unyielding.quests.settings.AdminSettings.sync(p(),false,"",false);
        sequence++;Files.writeString(ROOT.resolve("Dev.txt"),sequence+"\t"+c.action()+"\t"+origin.getX()+","+origin.getY()+","+origin.getZ()+"\t"+c.expected()+"\t"+c.name().replace(' ','_')+"\t"+net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p().getMainHandItem().getItem())+"\t"+p().gameMode.getGameModeForPlayer().name()+"\t"+p().getX()+","+p().getY()+","+p().getZ());
    }
    void tick(ServerTickEvent.Post e){
        if(done||!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))return;
        server=e.getServer();tick++;
        try{
            if(!initialized){if(p()==null||v()==null||tick<100)return;initialized=true;Files.createDirectories(ROOT);
                require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"wrong production JAR");
                require(server.getCommands().getDispatcher().getRoot().getChild("jail")==null,"integrated Jail returned");setup();}
            if(cases.isEmpty()){done=true;Files.writeString(Path.of(System.getenv("UQ_VEIN_EVIDENCE")).resolve("cases.txt"),String.join("\n",outcomes)+"\n");long failures=outcomes.stream().filter(x->x.startsWith("failed:")).count();Files.writeString(RESULT,(failures==0?"passed: ":"failed: ")+outcomes.size()+" cases, "+failures+" failures\n");return;}
            Case c=cases.peek();
            if(start==0){System.out.println("VEIN CASE START: "+c.name());c.setup().run();send(c);start=tick;}
            if(tick-start>900)throw new IllegalStateException("client timeout: "+c.name());
            Path holding=ROOT.resolve("Dev.holding");boolean needsHeld=!c.action().equals("disabled")&&!c.action().equals("empty")&&!c.action().equals("menu");
            if(Files.exists(holding)&&Files.readString(holding).equals(""+sequence)&&(!needsHeld||held()))Files.writeString(ROOT.resolve("Dev.ready"),""+sequence);
            Path ack=ROOT.resolve("Dev.ack");if(tick-start<40||!Files.exists(ack)||!Files.readString(ack).startsWith(sequence+"\t"))return;
            List<String> failures=new ArrayList<>();String client=Files.readString(ack);if(!client.equals(sequence+"\tpassed"))failures.add(client);
            try{c.check().run();}catch(Throwable bad){failures.add(bad.toString());}
            String result=(failures.isEmpty()?"passed: ":"failed: ")+c.name()+(failures.isEmpty()?"":" => "+String.join("; ",failures));outcomes.add(result);System.out.println("VEIN CASE "+result);cancel="";cases.remove();start=0;
        }catch(Throwable bad){done=true;bad.printStackTrace();try{Files.writeString(RESULT,"failed: fixture "+bad+"\n");}catch(Exception ignored){}}
    }
}
