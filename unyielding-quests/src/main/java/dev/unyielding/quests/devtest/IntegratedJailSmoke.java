package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.essentials.Loc;
import dev.unyielding.quests.jail.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual /jail + /unjail calls, production ports only; destructive disposable-world fixture. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class IntegratedJailSmoke {
    static boolean done,waiting;static ServerPlayer admin,target;static Path world;static int phase;static byte[] source;
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static int command(String text)throws Exception{return admin.server.getCommands().getDispatcher().execute(text,admin.server.createCommandSourceStack());}
    static int staff(String text)throws Exception{return admin.server.getCommands().getDispatcher().execute(text,admin.createCommandSourceStack());}
    static JailCustodyLedger.Binding record()throws IOException {var records=JailCustodyService.ledger(admin.server).list().stream().filter(e->e.owner().equals(target.getUUID())).toList();return records.getLast();}
    static byte[] inventory(UUID tx,UUID retired)throws IOException{return new JailVanillaPort(target,tx,retired).current().inventory();}
    static void saved()throws Exception{admin.server.saveEverything(true,true,true);net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}
    @SuppressWarnings("unchecked") static void human()throws Exception {
        Object data=OriginCustodySmoke.data(target);OriginCustodyPort.call(OriginCustodyPort.DATA,data,"clear");OriginCustodyPort.call(OriginCustodyPort.DATA,data,"resetEvolution");
        for(String method:List.of("setPendingOrbCommit","setPendingLayerPickerCommit","setPendingAdminReselect"))OriginCustodyPort.call(OriginCustodyPort.DATA,data,method,new Class<?>[]{boolean.class},false);
        Map<ResourceLocation,?> origins=(Map<ResourceLocation,?>)OriginCustodyPort.call(OriginCustodyPort.ORIGINS,OriginCustodyPort.singleton(OriginCustodyPort.ORIGINS),"getOrigins");ResourceLocation human=null;
        for(var id:origins.keySet()){var raw=(com.google.gson.JsonObject)OriginCustodyPort.call(OriginCustodyPort.ORIGINS,OriginCustodyPort.singleton(OriginCustodyPort.ORIGINS),"getRawOriginJson",new Class<?>[]{ResourceLocation.class},id);if(raw!=null&&raw.has("name")&&raw.get("name").toString().contains("origins.neoorigins.human.name")){require(human==null,"multiple Humans");human=id;}}
        require(human!=null,"native Human absent");ResourceLocation layer=OriginCustodySmoke.layerFor(human,Map.of());
        Object config=OriginCustodyPort.type("com.cyberday1.neoorigins.config.AdminConfig").getField("UNIQUE_ORIGIN_LAYERS").get(null);((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>>)config).set(List.of(layer.toString()));((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>)config).save();
        OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setOrigin",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},layer,human);OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setHadAllOrigins",new Class<?>[]{boolean.class},true);
        Object claims=OriginCustodyPort.call(OriginCustodyPort.CLAIMS,null,"get",new Class<?>[]{net.minecraft.server.MinecraftServer.class},target.server);OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},layer,human,target.getUUID());
        VanillaCustodySmoke.seed(target);target.getInventory().setItem(10,target.getInventory().getItem(36).copy());target.getInventory().setItem(36,ItemStack.EMPTY);target.getInventory().setItem(11,target.getInventory().getItem(40).copy());target.getInventory().setItem(40,ItemStack.EMPTY);target.getInventory().selected=0;saved();
    }
    static BlockPos site(){var level=admin.serverLevel();BlockPos around=admin.blockPosition();for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){BlockPos min=new BlockPos(around.getX()+dx*16,around.getY(),around.getZ()+dz*16);boolean clear=true;
        for(int x=0;x<13&&clear;x++)for(int z=0;z<7&&clear;z++){if(!level.getBlockState(min.offset(x,-1,z)).is(Blocks.GRASS_BLOCK)){clear=false;break;}for(int y=0;y<7;y++)if(!level.getBlockState(min.offset(x,y,z)).isAir()){clear=false;break;}}if(clear)return min;}throw new IllegalStateException("No pristine fixture site");}
    static void returned(UUID id)throws Exception {
        require(Arrays.equals(source,inventory(id,null)),"source slots/components or nested contents not conserved");require(JailData.get(admin.server).assignment(target.getUUID())==null,"assignment retained after completed release");require(record().state()==JailCustodyLedger.State.RELEASED&&!JailCustodyService.quarantined(target),"release left quarantine");
        byte[] before=inventory(id,null);require(command("unjail Visitor")==0,"repeat unjail succeeded");require(Arrays.equals(before,inventory(id,null)),"repeat unjail changed inventory");
        require(target.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,target.getBoundingBox().inflate(128)).isEmpty(),"custody produced ground drops/overflow");
    }
    static void confirmReturn(UUID id)throws Exception {require(command("jailcustody recover "+id)==1,"explicit saved-phase confirmation failed: "+id);require(command("jailcustody review "+id)==1&&command("jailcustody restore "+id)==1,"confirmed release failed");returned(id);}
    static void faults()throws Exception {
        for(var at:List.of(JailIntegrated.Boundary.ITEMS_DURABLE,JailIntegrated.Boundary.HUMAN_DURABLE,JailIntegrated.Boundary.ASSIGNMENT_DURABLE,JailIntegrated.Boundary.CONFINED)){
            JailIntegrated.disposableCheckpoint(target,b->{if(b==at)throw new IOException("command admission fault "+at);});require(command("jail Visitor")==0,"admission boundary not reached: "+at);UUID id=JailIntegrated.activeTransaction(target.server,target.getUUID());require(target.getInventory().isEmpty()&&JailCustodyService.quarantined(target),"fault lost source/quarantine: "+at);
            JailIntegrated.disposableCheckpoint(target,b->{});require(command("jailcustody recover "+id)==1,"admission saved-phase confirmation failed: "+at);require(command("unjail Visitor")==1,"confirmed command return failed: "+at);returned(id);
        }
        for(var at:List.of(JailIntegrated.Boundary.ORIGIN_RETURNED,JailIntegrated.Boundary.RETURN_POSITION_DURABLE,JailIntegrated.Boundary.ASSIGNMENT_REMOVED,JailIntegrated.Boundary.COMPLETE)){
            require(command("jail Visitor")==1,"release fault setup failed");UUID id=record().id();JailIntegrated.disposableCheckpoint(target,b->{if(b==at)throw new IOException("command return fault "+at);});require(command("unjail Visitor")==0,"release boundary not reached: "+at);require(JailCustodyService.quarantined(target),"fault lifted quarantine early: "+at);
            JailIntegrated.disposableCheckpoint(target,b->{});confirmReturn(id);
        }
        Path file=world.resolve("playerdata/"+target.getUUID()+".dat"),directory=file.getParent();var oldDirectory=Files.getPosixFilePermissions(directory);var oldFile=Files.getPosixFilePermissions(file);boolean[] reached={false};
        try{
            JailIntegrated.disposableCheckpoint(target,b->{if(b==JailIntegrated.Boundary.ITEMS_MUTATED){reached[0]=true;Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));Files.setPosixFilePermissions(directory,java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));}});
            require(command("jail Visitor")==0&&reached[0]&&target.getInventory().isEmpty(),"actual player-save write failure did not hold custody");
        }finally{Files.setPosixFilePermissions(directory,oldDirectory);Files.setPosixFilePermissions(file,oldFile);JailIntegrated.disposableCheckpoint(target,b->{});}
        UUID id=record().id();require(command("jailcustody recover "+id)==0,"unsaved removal falsely confirmed");saved();require(command("jailcustody recover "+id)==1,"explicit persisted receipt not recovered");require(command("unjail Visitor")==1,"write-fault return failed");returned(id);
    }
    static void phase1()throws Exception {
        admin.server.getPlayerList().op(admin.getGameProfile());target.setInvulnerable(true);admin.setInvulnerable(true);BlockPos min=site();admin.teleportTo(admin.serverLevel(),min.getX()+3.5,min.getY(),min.getZ()-7.5,0,0);
        require(staff("jailbuild preview")>0&&staff("jailbuild place")>0,"real jail placement failed");target.teleportTo(target.serverLevel(),min.getX()+9.5,min.getY()+1,min.getZ()+5.5,0,0);human();
        require(command("jailcustody init")==1&&command("jailintegrated init")==1,"history init failed");UUID dummy=UUID.randomUUID();source=inventory(dummy,null);Files.write(world.resolve("expected-items.nbt"),source);
        JailIntegrated.disposableCheckpoint(target,b->{if(b==JailIntegrated.Boundary.PLAN_DURABLE)throw new IOException("prepared source interruption");});require(command("jail Visitor")==0,"prepared interruption missing");UUID abandoned=JailIntegrated.activeTransaction(target.server,target.getUUID());require(abandoned!=null&&JailCustodyService.quarantined(target)&&Arrays.equals(source,inventory(dummy,null)),"prepared interruption changed source/lost quarantine");require(command("jailintegrated cancel "+abandoned)==1&&!JailCustodyService.quarantined(target),"unchanged prepared cancellation failed");JailIntegrated.disposableCheckpoint(target,b->{});
        Object nativeData=OriginCustodySmoke.data(target);OriginCustodyPort.call(OriginCustodyPort.DATA,nativeData,"setEvolutionTier",new Class<?>[]{int.class},1);require(command("jail Visitor")==0,"higher tier accepted");OriginCustodyPort.call(OriginCustodyPort.DATA,nativeData,"setEvolutionTier",new Class<?>[]{int.class},0);
        var boots=target.getInventory().getItem(10).copy();target.getInventory().setItem(36,boots);require(command("jail Visitor")==0,"equipped item accepted");target.getInventory().setItem(36,ItemStack.EMPTY);require(Arrays.equals(source,inventory(dummy,null)),"refusal altered source");
        require(command("jail Visitor")==1,"actual managed admission failed");var first=record();require(target.getInventory().isEmpty()&&JailCustodyService.quarantined(target)&&JailData.get(admin.server).assignment(target.getUUID())!=null,"admission did not retain custody/confinement");require(Arrays.equals(source,JailCustodyService.items(admin.server,first).snapshot()),"authoritative source mismatch");require(command("jail Visitor")==0,"repeat admission accepted");
        try(var items=new JailEscrowJournal(JailCustodyService.journalPath(admin.server,first.items()))){try{JailVanillaCustody.restoreForDisposableTest(items,first.id(),new JailVanillaPort(admin,first.id(),null),at->{});throw new IllegalStateException("cross-player return accepted");}catch(IOException expected){}require(items.read(first.id()).state()==JailEscrowJournal.State.HELD&&target.getInventory().isEmpty(),"cross-player refusal changed custody");}
        // Deliberate administrator-side destination fixture; the mod must retain it, not overwrite/drop it.
        target.getInventory().setItem(0,new ItemStack(Items.DIRT));saved();require(command("unjail Visitor")==0&&target.getInventory().getItem(0).is(Items.DIRT),"occupied destination overwritten");target.getInventory().setItem(0,ItemStack.EMPTY);saved();
        AccessoryCustodySmoke.resize(target,1);saved();require(command("unjail Visitor")==0&&target.getInventory().isEmpty(),"changed accessory slots accepted");
        Object cap=((Optional<?>)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesCapability",null,"getOptionally",new Class<?>[]{net.minecraft.world.entity.LivingEntity.class},target)).orElseThrow();Map<?,?> containers=(Map<?,?>)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesCapability",cap,"getContainers");Object ring=containers.get("ring");AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",ring,"removeModifier",new Class<?>[]{ResourceLocation.class},AccessoryCustodySmoke.RESIZE);AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",ring,"update");saved();
        admin.server.getPlayerList().deop(admin.getGameProfile());require(!admin.server.getCommands().getDispatcher().getRoot().getChild("unjail").canUse(admin.createCommandSourceStack()),"lost permission retained command");admin.server.getPlayerList().op(admin.getGameProfile());
        BlockPos anchor=new BlockPos(first.anchor().x(),first.anchor().y(),first.anchor().z());admin.serverLevel().setBlock(anchor,Blocks.AIR.defaultBlockState(),3);require(command("unjail Visitor")==0&&target.getInventory().isEmpty(),"lost interface allowed normal release");require(command("jailcustody reattach "+first.id()+" "+anchor.getX()+" "+anchor.getY()+" "+anchor.getZ())==1,"logical interface reattach failed");
        require(command("unjail Visitor")==1,"actual managed release failed");returned(first.id());
        faults();
        JailIntegrated.disposableCheckpoint(target,b->{if(b==JailIntegrated.Boundary.HUMAN_DURABLE)throw new IOException("injected persisted admission before confinement");});require(command("jail Visitor")==0,"admission fault was not injected");var held=record();require(target.getInventory().isEmpty()&&JailCustodyService.quarantined(target)&&JailData.get(admin.server).assignment(target.getUUID())==null,"interrupted admission lost quarantine/source");
        require(command("unjail Visitor")==0&&command("unjail pardonuuid "+target.getUUID())==0,"legacy recovery bypassed custody");Files.writeString(world.resolve("active-transaction"),held.id().toString());saved();target.connection.disconnect(Component.literal("Disposable integrated disconnect"));waiting=true;
    }
    static void phase2()throws Exception {
        require(target.getInventory().isEmpty()&&JailCustodyService.quarantined(target),"restart lost custody");UUID id=UUID.fromString(Files.readString(world.resolve("active-transaction")));require(record().id().equals(id),"restart changed binding");
        require(command("jailcustody recover "+id)==1,"saved admission confirmation failed");require(JailData.get(admin.server).assignment(target.getUUID())!=null,"confirmation did not finish confinement");
        JailIntegrated.disposableCheckpoint(target,b->{if(b==JailIntegrated.Boundary.ITEMS_RETURNED)throw new IOException("injected persisted items before release metadata");});require(command("unjail Visitor")==0,"return save fault missing");require(Arrays.equals(source,inventory(id,null))&&JailCustodyService.quarantined(target)&&JailData.get(admin.server).assignment(target.getUUID())!=null,"return interruption lost item conservation or restrictions");require(command("unjail Visitor")==0,"ambiguous repeat was accepted");saved();pass();
    }
    static void phase3()throws Exception {
        UUID id=UUID.fromString(Files.readString(world.resolve("active-transaction")));require(Arrays.equals(source,inventory(id,null))&&JailCustodyService.quarantined(target),"saved return lost across restart");
        require(command("jailcustody recover "+id)==1,"saved return confirmation failed");require(command("jailcustody review "+id)==1&&command("jailcustody restore "+id)==1,"verified logical release failed");returned(id);require(command("jailcustody restore "+id)==1,"terminal restore no-op rejected");require(Arrays.equals(source,inventory(id,null)),"terminal restore duplicated items");
        Path root=world.resolve("integrated-jail-v1"),displaced=world.resolve("integrated-jail-v1-displaced");Files.move(root,displaced);try{require(JailCustodyService.quarantined(target)&&command("jailintegrated init")==0,"missing command history silently reset or unquarantined players");}finally{Files.move(displaced,root);}require(!JailCustodyService.quarantined(target),"verified command history restoration did not clear unavailable-history hold");saved();pass();
    }
    static void dependencyProbe()throws Exception {
        UUID id=UUID.fromString(Files.readString(world.resolve("active-transaction")));require(net.neoforged.fml.ModList.get().isLoaded("uq_uncertified_runtime"),"extra dependency did not actually load");byte[] before=inventory(id,null);require(Arrays.equals(source,before)&&JailCustodyService.quarantined(target),"dependency probe lost persisted custody");
        require(command("unjail Visitor")==0&&command("jailcustody recover "+id)==0&&command("jailcustody restore "+id)==0,"changed dependency allowed recovery");require(Arrays.equals(before,inventory(id,null))&&Arrays.equals(source,JailCustodyService.items(admin.server,record()).snapshot())&&JailCustodyService.quarantined(target),"dependency refusal changed/granted inventory");saved();pass();
    }
    static void pass()throws IOException {Files.writeString(Path.of("/tmp/uq-integrated-result"),"passed: actual /jail /unjail production Human/empty-accessory ports phase "+phase+"; exact packed slots and nested conservation, no repeated grant, persisted interruption, disconnect/restart, permissions and interface recovery\n");}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.integratedJail"))return;try{
            if(waiting){if(!target.hasDisconnected()||event.getServer().getPlayerList().getPlayer(target.getUUID())!=null)return;waiting=false;require(command("jailcustody review "+record().id())==0,"offline target accepted");pass();return;}
            if(done)return;admin=event.getServer().getPlayerList().getPlayerByName("Dev");target=event.getServer().getPlayerList().getPlayerByName("Visitor");if(admin==null||target==null||event.getServer().getTickCount()<120)return;done=true;
            world=event.getServer().getWorldPath(LevelResource.ROOT);require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))&&world.toRealPath().getFileName().toString().equals("jail_integrated_smoke"),"wrong runtime/world");require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"not final assembled JAR");
            phase=Integer.getInteger("uq.integratedPhase",1);if(phase==1)phase1();else{source=Files.readAllBytes(world.resolve("expected-items.nbt"));if(Boolean.getBoolean("uq.integratedDependencyProbe"))dependencyProbe();else if(phase==2)phase2();else if(phase==3)phase3();else throw new IOException("unknown phase");}
        }catch(Throwable bad){done=true;waiting=false;UnyieldingQuests.LOGGER.error("Integrated jail commands failed",bad);try{Files.writeString(Path.of("/tmp/uq-integrated-result"),"failed: "+bad+"\n");}catch(IOException ignored){}}
    }
}
