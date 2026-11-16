package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.*;
import dev.unyielding.quests.mixin.JailEquipmentAccess;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Destructive independent test mod around the final JAR; no mock custody or native-power adapters. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class ExpandedJailSmoke {
    static boolean done,failUnequip,failEquip,waiting;static ServerPlayer admin,target;static Path world;static int phase;static byte[] original;static UUID last;
    static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    static int command(String text)throws Exception{return admin.server.getCommands().getDispatcher().execute(text,admin.server.createCommandSourceStack());}
    static JailCustodyLedger.Binding record()throws IOException{return JailCustodyService.ledger(admin.server).list().stream().filter(b->b.owner().equals(target.getUUID())).toList().getLast();}
    static JailEquipmentPort items(UUID id,UUID retired)throws IOException{return new JailEquipmentPort(target,id,retired);}
    static void saved()throws Exception{admin.server.saveEverything(true,true,true);net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}
    static Object data()throws IOException{return OriginCustodySmoke.data(target);}
    static void flush()throws IOException {((JailEquipmentAccess)target).uq$detectEquipmentUpdates();AccessoryCustodyPort.call("io.wispforest.accessories.impl.AccessoriesEventHandler",null,"onLivingEntityTick",new Class<?>[]{LivingEntity.class},target);}
    @SuppressWarnings("unchecked") static Map<ResourceLocation,ResourceLocation> choices()throws IOException{return Map.copyOf((Map<ResourceLocation,ResourceLocation>)OriginCustodyPort.call(OriginCustodyPort.DATA,data(),"getOrigins"));}
    static ResourceLocation selected()throws IOException {return choices().entrySet().stream().filter(e->!e.getValue().equals(OriginCustodyPort.KEPT_CLASS)).findFirst().orElseThrow().getValue();}
    static void held()throws Exception {
        require(items(last,null).current().empty()&&selected().getPath().equals("human"),"custody did not clear all stores or temporarily select native Human");
        require(target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK).getModifiers().isEmpty(),"original native origin effect remained active");
        require(choices().containsValue(OriginCustodyPort.KEPT_CLASS)&&JailCustodyService.quarantined(target),"other origin layer or quarantine lost");
    }
    static void returned()throws Exception {
        require(Arrays.equals(original,items(last,null).current().inventory()),"equipment/accessory slots, components or nested contents changed");require(selected().equals(OriginCustodyPort.ALPHA)&&choices().containsValue(OriginCustodyPort.KEPT_CLASS),"original main/other layer not restored");
        require(target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK).getValue()==1&&JailData.get(admin.server).assignment(target.getUUID())==null&&!JailCustodyService.quarantined(target),"original effect or release did not verify");
        require(target.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,target.getBoundingBox().inflate(128)).isEmpty(),"equipment/accessories dropped or overflowed");byte[] before=items(last,null).current().inventory();require(command("unjail Visitor")==0&&Arrays.equals(before,items(last,null).current().inventory()),"repeated release changed/granted inventory");
    }
    static void listeners(){NeoForge.EVENT_BUS.addListener(LivingEquipmentChangeEvent.class,event->{if(event.getEntity()==target&&((failUnequip&&!event.getFrom().isEmpty()&&event.getTo().isEmpty())||(failEquip&&event.getFrom().isEmpty()&&!event.getTo().isEmpty())))throw new IllegalStateException("injected native equipment lifecycle interruption");});}
    @SuppressWarnings("unchecked") static void seed()throws Exception {
        OriginCustodySmoke.listeners();OriginCustodySmoke.seed(target,OriginCustodyPort.ALPHA);target.getInventory().selected=2;
        Object capability=((Optional<?>)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesCapability",null,"getOptionally",new Class<?>[]{LivingEntity.class},target)).orElseThrow();Map<String,Object> containers=(Map<String,Object>)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesCapability",capability,"getContainers",new Class<?>[0]);Object ring=containers.get("ring");require(ring!=null,"real ring slot absent");
        Container normal=(Container)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",ring,"getAccessories",new Class<?>[0]),cosmetic=(Container)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",ring,"getCosmeticAccessories",new Class<?>[0]);
        for(Container channel:List.of(normal,cosmetic)){ItemStack stack=new ItemStack(channel==normal?Items.GOLD_INGOT:Items.AMETHYST_SHARD);CompoundTag custom=new CompoundTag();custom.putString("unique",UUID.randomUUID().toString());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));stack.set(DataComponents.CUSTOM_NAME,Component.literal(channel==normal?"Default native accessory":"Default cosmetic accessory"));channel.setItem(0,stack);}
        flush();saved();UUID probe=UUID.randomUUID();items(probe,null).validateRemoval();original=items(probe,null).current().inventory();Files.write(world.resolve("expected-equipment.nbt"),original);
    }
    static void phase1()throws Exception {
        admin.server.getPlayerList().op(admin.getGameProfile());admin.setInvulnerable(true);target.setInvulnerable(true);IntegratedJailSmoke.admin=admin;var min=IntegratedJailSmoke.site();admin.teleportTo(admin.serverLevel(),min.getX()+3.5,min.getY(),min.getZ()-7.5,0,0);require(admin.server.getCommands().getDispatcher().execute("jailbuild preview",admin.createCommandSourceStack())>0&&admin.server.getCommands().getDispatcher().execute("jailbuild place",admin.createCommandSourceStack())>0,"jail placement failed");target.teleportTo(target.serverLevel(),min.getX()+9.5,min.getY()+1,min.getZ()+5.5,0,0);seed();listeners();
        require(command("jailcustody init")==1&&command("jailintegrated init")==1,"metadata initialization failed");
        Object claims=OriginCustodyPort.call(OriginCustodyPort.CLAIMS,null,"get",new Class<?>[]{net.minecraft.server.MinecraftServer.class},admin.server);var layer=choices().entrySet().stream().filter(e->e.getValue().equals(OriginCustodyPort.ALPHA)).findFirst().orElseThrow().getKey();var human=ResourceLocation.fromNamespaceAndPath("neoorigins","human");
        OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},layer,human,admin.getUUID());saved();
        require(command("jail Visitor")==0&&Arrays.equals(original,items(UUID.randomUUID(),null).current().inventory())&&JailCustodyService.ledger(admin.server).list().isEmpty(),"foreign Human claim removed equipment or created custody");
        require(Boolean.TRUE.equals(OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"releaseIfOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},layer,human,admin.getUUID())),"fixture Human claim cleanup failed");saved();
        require(command("jail Visitor")==1,"actual equipment/Luck-origin admission failed");last=record().id();held();
        OriginCustodyPort.call(OriginCustodyPort.DATA,data(),"setEssenceKills",new Class<?>[]{int.class},123);OriginCustodyPort.call(OriginCustodyPort.DATA,data(),"setCustomFloat",new Class<?>[]{String.class,float.class},"kept",66f);
        saved(); // Release requires matching native live and durable progress, not an unsaved fixture edit.
        require(command("unjail Visitor")==1,"actual equipment/Luck-origin release failed");returned();require((int)OriginCustodyPort.call(OriginCustodyPort.DATA,data(),"getEssenceKills")==123,"origin progress was rewound");
        failUnequip=true;require(command("jail Visitor")==0,"native unequip fault did not interrupt");last=record().id();failUnequip=false;require(command("jail Visitor")==0&&command("jailcustody recover "+last)==0,"uncertain native unequip was automatically replayed");flush();saved();require(command("jailcustody recover "+last)==1,"explicit verified unequip recovery failed");held();require(command("unjail Visitor")==1,"unequip-fault return failed");returned();
        require(command("jail Visitor")==1,"equip fault setup failed");last=record().id();held();failEquip=true;require(command("unjail Visitor")==0,"native equip fault did not interrupt");failEquip=false;require(command("unjail Visitor")==0,"uncertain native equip was replayed");flush();saved();require(command("jailcustody confirm "+last)==1,"saved native equip effects not confirmed");require(command("jailcustody recover "+last)==1&&command("jailcustody review "+last)==1&&command("jailcustody restore "+last)==1,"verified equip-fault completion failed");returned();
        require(command("jail Visitor")==1,"restart custody setup failed");last=record().id();held();Files.writeString(world.resolve("active-expanded"),last.toString());saved();target.connection.disconnect(Component.literal("Disposable equipped custody disconnect"));waiting=true;
    }
    static void phase2()throws Exception {
        last=UUID.fromString(Files.readString(world.resolve("active-expanded")));held();JailIntegrated.disposableCheckpoint(target,at->{if(at==JailIntegrated.Boundary.ITEMS_RETURNED)throw new IOException("persisted equipped return before metadata completion");});require(command("unjail Visitor")==0,"equipped return persistence fault missing");require(Arrays.equals(original,items(last,null).current().inventory())&&JailCustodyService.quarantined(target),"equipped return was lost or released early");require(command("unjail Visitor")==0,"repeated pending equipped release granted again");saved();pass();
    }
    static void cross()throws Exception {
        var jail=JailData.get(admin.server).assignment(target.getUUID()).jail();var field=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");field.setAccessible(true);
        var pending=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");pending.setAccessible(true);
        if(pending.get(target.connection)!=null)target.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(field.getInt(target.connection)));
        require(!target.hasDisconnected(),"movement fixture lost its actual player connection");
        target.setPos(jail.x()+2,jail.y(),jail.z());target.connection.resetPosition();target.noPhysics=true;
        try{target.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(jail.x()+4,jail.y(),jail.z(),true));}finally{target.noPhysics=false;}
    }
    static void phase3()throws Exception {
        last=UUID.fromString(Files.readString(world.resolve("active-expanded")));require(Arrays.equals(original,items(last,null).current().inventory())&&JailCustodyService.quarantined(target),"saved equipped return lost across restart");require(command("jailcustody recover "+last)==1&&command("jailcustody review "+last)==1&&command("jailcustody restore "+last)==1,"equipped restart release failed");returned();
        require(command("jail Visitor")==1,"escape custody setup failed");last=record().id();held();Files.writeString(world.resolve("active-expanded"),last.toString());target.setInvulnerable(false);
        var a=JailData.get(admin.server).assignment(target.getUUID());var corner=net.minecraft.core.BlockPos.containing(a.jail().x()-3.5,a.jail().y()-1,a.jail().z()-3.5);var block=target.serverLevel().getBlockState(corner);
        target.serverLevel().setBlock(corner,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);cross();require(target.isAlive(),"unsafe jail caused escape death");target.serverLevel().setBlock(corner,block,3);
        target.totalExperience=3;cross();require(target.isAlive()&&target.totalExperience==3,"unsupported experience was dropped/lost");target.totalExperience=0;
        cross();require(!target.hasDisconnected()&&target.getHealth()<=0&&!target.isAlive()&&items(last,null).current().empty(),"verified escape did not die with empty native stores and active connection");
        try(var attempts=new JailEscrowJournal(world.resolve("integrated-jail-v1").resolve(last.toString()).resolve("escapes"))){require(attempts.list().size()==1&&attempts.list().getFirst().state()==JailEscrowJournal.State.HELD,"no verified durable escape death receipt");}
        require(target.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,target.getBoundingBox().inflate(128)).isEmpty(),"escape death dropped custody items");
        saved();target.connection.disconnect(Component.literal("Disposable dead jail restart"));pass();
    }
    static void phase4()throws Exception {
        last=UUID.fromString(Files.readString(world.resolve("active-expanded")));require(!target.hasDisconnected()&&target.getHealth()<=0&&!target.isAlive()&&items(last,null).current().empty(),"durable dead custody lost across restart");
        JailEscapeCustody.disposableCheckpoint(target,()->{throw new IOException("saved native respawn before escape completion");});
        var id=target.getUUID();target.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));target=admin.server.getPlayerList().getPlayer(id);
        require(target!=null&&target.connection.player==target&&target.isAlive(),"actual native respawn did not rebind replacement player");held();var jail=JailData.get(admin.server).assignment(id).jail();require(target.distanceToSqr(jail.x(),jail.y(),jail.z())<0.0001,"replacement escaped safe jail");
        require(command("unjail Visitor")==0,"unconfirmed escape respawn released custody");saved();pass();
    }
    static void phase5()throws Exception {
        last=UUID.fromString(Files.readString(world.resolve("active-expanded")));held();require(target.isAlive()&&command("jailcustody recover "+last)==1,"saved native respawn could not be explicitly confirmed");require(command("unjail Visitor")==1,"escape custody return failed");returned();
        var advancement=admin.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("unyieldingquests","jail_escape"));require(advancement!=null&&target.getAdvancements().getOrStartProgress(advancement).isDone()&&!target.getAdvancements().award(advancement,"escape"),"escape advancement missing or repeated");
        require(command("jail Visitor")==1,"cancelled death setup failed");last=record().id();held();Files.writeString(world.resolve("active-expanded"),last.toString());final int[] deaths={0};
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class,event->{if(event.getEntity()==target){deaths[0]++;target.setHealth(20);event.setCanceled(true);}});
        cross();require(target.isAlive()&&deaths[0]==1,"death cancellation was bypassed");cross();require(target.isAlive()&&deaths[0]==1,"pending escape replayed lethal damage");require(command("unjail Visitor")==0&&command("jailcustody recover "+last)==0,"uncertain protected death was automatically released/replayed");held();saved();pass();
    }
    static void pass()throws IOException{String proof=switch(phase){case 1->"equipped native custody, exact components, Luck/claims/progress, callback faults and offline refusal";case 2->"restart held custody and saved return interruption without replay";case 3->"explicit return recovery, unsafe jail/XP refusal, actual empty-custody escape death";case 4->"restart on death screen, actual native respawn and saved completion interruption";case 5->"restart respawn confirmation, exact native return, one-time advancement and cancelled death no replay";default->throw new IOException("Unknown proof phase");};Files.writeString(Path.of("/tmp/uq-expanded-result"),"passed: final-JAR phase "+phase+"; "+proof+"\n");}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.expandedJail"))return;try{
            if(waiting){if(!target.hasDisconnected()||event.getServer().getPlayerList().getPlayer(target.getUUID())!=null)return;waiting=false;require(command("jailcustody review "+last)==0,"offline equipped restore accepted");pass();return;}
            if(done)return;admin=event.getServer().getPlayerList().getPlayerByName("Dev");target=event.getServer().getPlayerList().getPlayerByName("Visitor");if(admin==null||target==null||event.getServer().getTickCount()<120||target.isAlive()&&target.tickCount<100)return;done=true;world=event.getServer().getWorldPath(LevelResource.ROOT);
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))&&world.toRealPath().getFileName().toString().equals("jail_expanded_smoke"),"wrong disposable runtime");require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"not final assembled JAR");phase=Integer.getInteger("uq.expandedPhase",1);if(phase==1)phase1();else{original=Files.readAllBytes(world.resolve("expected-equipment.nbt"));if(phase==2)phase2();else if(phase==3)phase3();else if(phase==4)phase4();else if(phase==5)phase5();else throw new IOException("unknown phase");}
        }catch(Throwable bad){done=true;waiting=false;UnyieldingQuests.LOGGER.error("Expanded jail commands failed",bad);try{Files.writeString(Path.of("/tmp/uq-expanded-result"),"failed: "+bad+"\n");}catch(IOException ignored){}}
    }
}
