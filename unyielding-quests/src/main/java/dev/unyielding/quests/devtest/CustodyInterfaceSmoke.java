package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.Registration;
import dev.unyielding.quests.essentials.Loc;
import dev.unyielding.quests.jail.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real packet/menu/block tests; synthetic users and records only, never in release. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class CustodyInterfaceSmoke {
    static final UUID A=UUID.fromString("d007f85a-5304-4d79-945e-c00000000001"),B=UUID.fromString("d007f85a-5304-4d79-945e-c00000000002"),OTHER=UUID.fromString("d007f85a-5304-4d79-945e-c00000000003");
    static final BlockPos FAR=new BlockPos(1000,6,1000),FAR_B=FAR.offset(3,0,0),NEAR=new BlockPos(8,6,8);
    static boolean done;static int waitUntil,restores;static ItemEntity pickup;static JailCustodyMenu savedMenu;static ServerPlayer admin,target;static Path world;
    static boolean failOrigin,failLedger;
    static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    static int command(net.minecraft.server.MinecraftServer server,String text)throws com.mojang.brigadier.exceptions.CommandSyntaxException{return server.getCommands().getDispatcher().execute(text,server.createCommandSourceStack());}
    static JailCustodyLedger.Binding record(UUID id)throws IOException{return JailCustodyService.ledger(admin.server).get(id);}
    static OriginCustodyPort port()throws Exception{return OriginCustodySmoke.reopen(world.resolve("custody-fixtures/visitor"),A,target);}
    static void install()throws IOException {
        JailCustodyService.installDisposableFixture(admin.server,(server,b)->{
            if(!b.id().equals(A)||!b.owner().equals(target.getUUID()))throw new IOException("Uncertified fixture provider; refused");
            final OriginCustodyPort p;try{p=port();}catch(Exception bad){throw new IOException(bad);}
            return new JailCustodyService.VerifiedAdapter(){
                public String descriptor(){return "pinned-native-origin-and-vanilla-v1";}
                public JailCustodyService.Health inspect()throws IOException {
                    try(var items=new JailEscrowJournal(JailCustodyService.journalPath(server,b.items()));var origins=new JailEscrowJournal(JailCustodyService.journalPath(server,b.origins()))){
                        var live=p.current();var disk=p.durable();if(!p.online()||!Arrays.equals(live.state(),disk.state()))return JailCustodyService.Health.REVIEW_REQUIRED;
                        byte[] intent=JailOriginTransaction.intentForReview(origins,A,p.owner());var item=items.read(A);
                        if(item.state()==JailEscrowJournal.State.COMPLETE)return origins.read(A).state()==JailEscrowJournal.State.COMPLETE&&p.isOriginal(intent,A,live)?JailCustodyService.Health.COMPLETE:JailCustodyService.Health.REVIEW_REQUIRED;
                        if(JailVanillaCustody.inspect(items,A,p.inventory)!=JailVanillaCustody.Inspection.HELD_VERIFIED)return JailCustodyService.Health.REVIEW_REQUIRED;
                        var state=JailOriginTransaction.inspect(origins,items,A,p);if(state==JailOriginTransaction.Inspection.TEMPORARY_VERIFIED)return JailCustodyService.Health.READY;
                        if(state==JailOriginTransaction.Inspection.COMPLETE_RECORDED){JailOriginTransaction.assertInventoryReleaseReady(origins,items,A,p,false);return JailCustodyService.Health.READY;}
                        return JailCustodyService.Health.REVIEW_REQUIRED;
                    }
                }
                public void restore()throws IOException {
                    restores++;
                    try(var items=new JailEscrowJournal(JailCustodyService.journalPath(server,b.items()));var origins=new JailEscrowJournal(JailCustodyService.journalPath(server,b.origins()))){
                        JailOriginTransaction.originalForDisposableTest(origins,items,A,p,at->{if(failOrigin&&at==JailOriginTransaction.Boundary.PLAYER_AND_CLAIMS_DURABLE)throw new IOException("Injected saved-origin interruption; explicit confirmation required");});
                        JailVanillaCustody.restoreForDisposableTest(items,A,new OriginCustodySmoke.Guarded(p,origins,items,A),at->{});
                    }
                }
                public void confirmSaved()throws IOException {
                    try(var items=new JailEscrowJournal(JailCustodyService.journalPath(server,b.items()));var origins=new JailEscrowJournal(JailCustodyService.journalPath(server,b.origins()))){JailOriginTransaction.confirmSavedForDisposableTest(origins,items,A,p);}
                }
            };
        },()->{if(failLedger)throw new IOException("Injected interruption after verified return before ledger completion");});
    }
    static void teleport(ServerPlayer p,BlockPos pos){p.teleportTo((net.minecraft.server.level.ServerLevel)p.level(),pos.getX()+0.5,pos.getY(),pos.getZ()+2.5,Set.of(),180,0);}
    static JailCustodyMenu open(BlockPos pos){
        admin.closeContainer();teleport(admin,pos);admin.getInventory().setItem(admin.getInventory().selected,ItemStack.EMPTY);
        var result=admin.gameMode.useItemOn(admin,(net.minecraft.server.level.ServerLevel)admin.level(),ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        require(result.consumesAction()&&admin.containerMenu instanceof JailCustodyMenu,"actual block use did not open protected menu");return (JailCustodyMenu)admin.containerMenu;
    }
    static void click(JailCustodyMenu menu,int slot,ClickType type){admin.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),slot,0,type,ItemStack.EMPTY,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));}
    static void controls(BlockPos pos,int slot){click(open(pos),slot,ClickType.PICKUP);}
    static void fakeOther()throws Exception {
        Path root=world.resolve("custody-fixtures/other");Files.createDirectory(root);JailEscrowJournal.provisionNew(root.resolve("items"));JailEscrowJournal.provisionNew(root.resolve("origins"));
        CompoundTag vanilla=new CompoundTag();vanilla.putInt("version",1);vanilla.putInt("vanillaSlots",41);vanilla.put("items",new ListTag());
        CompoundTag body=new CompoundTag();body.putInt("version",2);body.putByteArray("vanilla",OriginCustodyPort.encode(vanilla));CompoundTag holder=new CompoundTag(),stores=new CompoundTag(),store=new CompoundTag();ListTag entries=new ListTag();for(int i=0;i<140;i++){CompoundTag entry=new CompoundTag();entry.putInt("Slot",i);entries.add(new ItemStack(Items.PAPER,i%32+1).save(admin.registryAccess(),entry));}store.put("items",entries);store.put("cosmetics",new ListTag());stores.put("ring",store);holder.put("accessories_containers",stores);body.put("holder",holder);
        for(String kind:List.of("items","origins"))try(var journal=new JailEscrowJournal(root.resolve(kind))){journal.begin(B,OTHER,kind.equals("items")?OriginCustodyPort.encode(body):new byte[]{1,2,3});journal.transition(B,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);journal.transition(B,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);}
    }
    static void bind(UUID id,UUID owner,String folder,BlockPos anchor){require(command(admin.server,"jailcustody bind "+id+" "+owner+" \"custody-fixtures/"+folder+"/items\" \"custody-fixtures/"+folder+"/origins\" "+anchor.getX()+" "+anchor.getY()+" "+anchor.getZ())==1,"binding command failed");}
    static void phase1()throws Exception {
        var server=admin.server;var level=(net.minecraft.server.level.ServerLevel)admin.level();server.getPlayerList().op(admin.getGameProfile());admin.setInvulnerable(true);teleport(admin,FAR);level.getChunk(FAR.getX()>>4,FAR.getZ()>>4);level.setBlock(FAR,Blocks.AIR.defaultBlockState(),3);level.setBlock(FAR_B,Blocks.AIR.defaultBlockState(),3);
        Files.createDirectory(world.resolve("custody-fixtures"));var p=OriginCustodySmoke.seed(target,OriginCustodyPort.ALPHA);OriginCustodySmoke.prepare(world.resolve("custody-fixtures/visitor"),A,p);OriginCustodySmoke.temporary(world.resolve("custody-fixtures/visitor"),A,p,at->{});
        Loc legacy=Loc.of(target);JailData.get(server).assign(target.getUUID(),new JailData.Assignment(legacy,legacy));fakeOther();
        require(command(server,"jailcustody init")==1,"index init failed");bind(A,target.getUUID(),"visitor",FAR);bind(B,OTHER,"other",FAR_B);require(JailCustodyService.quarantined(target),"import did not quarantine prisoner");
        require(command(server,"unjail pardonuuid "+target.getUUID())==0&&JailData.get(server).assignment(target.getUUID())!=null,"legacy pardon bypassed outstanding custody");
        // No factory yet: a loaded, syntactically valid snapshot is not an audited adapter.
        require(command(server,"jailcustody review "+A)==0&&record(A).state()==JailCustodyLedger.State.QUARANTINED,"production unsupported adapter accepted");install();
        var menu=open(FAR);require(menu.owner().equals(target.getUUID())&&menu.pages()==1,"wrong prisoner selected");byte[] source=JailCustodyService.items(server,record(A)).snapshot();
        for(ClickType type:ClickType.values()){click(menu,0,type);require(admin.containerMenu==menu&&admin.containerMenu.getCarried().isEmpty()&&target.getInventory().isEmpty(),"menu click transferred a preview "+type);}
        savedMenu=menu;var other=open(FAR_B);require(other.transaction().equals(B)&&other.pages()==4,"capacious view lost stacks");click(other,JailCustodyMenu.NEXT,ClickType.PICKUP);require(other.page()==1,"pagination failed");long before=record(B).revision();savedMenu.clicked(JailCustodyMenu.RESTORE,0,ClickType.PICKUP,admin);require(record(B).revision()==before&&restores==0,"stale cross-prisoner menu restored another record");
        controls(FAR_B,JailCustodyMenu.REVIEW);require(record(B).state()==JailCustodyLedger.State.QUARANTINED,"unsupported accessory source became transferable");
        menu=open(FAR);server.getPlayerList().deop(admin.getGameProfile());click(menu,JailCustodyMenu.RESTORE,ClickType.PICKUP);require(admin.containerMenu==admin.inventoryMenu&&restores==0,"permission loss allowed stale action");server.getPlayerList().op(admin.getGameProfile());menu.clicked(JailCustodyMenu.RESTORE,0,ClickType.PICKUP,admin);require(restores==0,"restored permission revived stale menu");
        var denied=target.gameMode.useItemOn(target,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(FAR),Direction.UP,FAR,false));require(!(target.containerMenu instanceof JailCustodyMenu),"prisoner opened custody");
        target.setGameMode(GameType.CREATIVE);target.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(2,new ItemStack(Items.DIAMOND,64)));require(target.getInventory().isEmpty(),"quarantine creative packet bypassed item fence");target.setGameMode(GameType.SURVIVAL);
        require(!admin.gameMode.destroyBlock(FAR)&&level.getBlockState(FAR).is(Registration.CUSTODY_VAULT.get()),"protected block was broken");
        level.explode(null,FAR.getX()+0.5,FAR.getY()+0.5,FAR.getZ()+0.5,4,Level.ExplosionInteraction.BLOCK);require(level.getBlockState(FAR).is(Registration.CUSTODY_VAULT.get()),"explosion removed interface");
        level.getEntitiesOfClass(ItemEntity.class,new AABB(FAR).inflate(8)).forEach(ItemEntity::discard);
        require(level.getBlockEntity(FAR)==null&&level.getCapability(Capabilities.ItemHandler.BLOCK,FAR,Direction.DOWN)==null,"physical block exposes item storage");
        level.setBlock(FAR.above(),Blocks.HOPPER.defaultBlockState(),3);level.setBlock(FAR.below(),Blocks.HOPPER.defaultBlockState(),3);((HopperBlockEntity)level.getBlockEntity(FAR.above())).setItem(0,new ItemStack(Items.DIAMOND,7));
        pickup=new ItemEntity(level,target.getX(),target.getY(),target.getZ(),new ItemStack(Items.EMERALD));pickup.setNoPickUpDelay();level.addFreshEntity(pickup);
        require(Arrays.equals(source,JailCustodyService.items(server,record(A)).snapshot()),"UI modified authoritative snapshot");waitUntil=server.getTickCount()+60;
    }
    static void finish1()throws Exception {
        var server=admin.server;var level=(net.minecraft.server.level.ServerLevel)admin.level();require(((HopperBlockEntity)level.getBlockEntity(FAR.below())).isEmpty()&&((HopperBlockEntity)level.getBlockEntity(FAR.above())).getItem(0).getCount()==7,"hopper extracted or inserted custody previews");require(target.getInventory().isEmpty()&&pickup.isAlive(),"quarantine allowed ground pickup");pickup.discard();
        failOrigin=true;controls(FAR,JailCustodyMenu.REVIEW);require(record(A).state()==JailCustodyLedger.State.REVIEWED,"native review did not verify");controls(FAR,JailCustodyMenu.RESTORE);require(record(A).state()==JailCustodyLedger.State.QUARANTINED&&target.getInventory().isEmpty()&&restores==1,"interrupted native return escaped quarantine");
        int calls=OriginCustodySmoke.callbacks;controls(FAR,JailCustodyMenu.RESTORE);require(restores==1&&OriginCustodySmoke.callbacks==calls,"repeated ambiguous request replayed callbacks");
        savedMenu=open(FAR);level.setBlock(FAR,Blocks.AIR.defaultBlockState(),3);require(!savedMenu.stillValid(admin),"missing block left stale screen valid");savedMenu.clicked(JailCustodyMenu.RESTORE,0,ClickType.PICKUP,admin);require(admin.containerMenu==admin.inventoryMenu&&target.getInventory().isEmpty(),"missing interface returned inventory");
        teleport(admin,BlockPos.ZERO.above(5));require(JailCustodyService.items(server,record(A)).snapshot().length>100&&record(B)!=null,"lost block discarded escrow");server.saveEverything(true,true,true);passed(1);
    }
    static void phase2()throws Exception {
        var server=admin.server;var level=(net.minecraft.server.level.ServerLevel)admin.level();server.getPlayerList().op(admin.getGameProfile());require(record(A).state()==JailCustodyLedger.State.QUARANTINED&&JailCustodyService.quarantined(target)&&JailData.get(server).assignment(target.getUUID())!=null,"restart lost quarantine or legacy assignment");
        require(!level.hasChunkAt(FAR_B),"fixture anchor chunk unexpectedly loaded");require(record(B).state()==JailCustodyLedger.State.QUARANTINED&&JailCustodyService.items(server,record(B)).snapshot().length>100,"unloaded interface lost record");
        var p=port();p.persistAndVerify();install();require(command(server,"jailcustody inspect "+A)==1&&command(server,"jailcustody init")==0,"recovery inspection or no-reset rule failed");
        teleport(admin,NEAR);level.setBlock(NEAR,Blocks.AIR.defaultBlockState(),3);require(command(server,"jailcustody reattach "+A+" "+NEAR.getX()+" "+NEAR.getY()+" "+NEAR.getZ())==1,"reattach failed");
        require(command(server,"jailcustody review "+A)==0,"ambiguous origin was silently accepted");int callbacks=OriginCustodySmoke.callbacks;controls(NEAR,JailCustodyMenu.CONFIRM);require(OriginCustodySmoke.callbacks==callbacks&&record(A).state()==JailCustodyLedger.State.QUARANTINED,"confirmation replayed native callbacks or released quarantine");
        controls(NEAR,JailCustodyMenu.REVIEW);failLedger=true;controls(NEAR,JailCustodyMenu.RESTORE);require(restores==1&&record(A).state()==JailCustodyLedger.State.QUARANTINED,"post-return interruption not retained");
        require(Arrays.equals(JailCustodyService.items(server,record(A)).snapshot(),p.inventory.current().inventory()),"native return lost components or source slots");require(JailCustodyService.quarantined(target)&&JailData.get(server).assignment(target.getUUID())!=null,"return interruption cleared restrictions");server.saveEverything(true,true,true);passed(2);
    }
    static void phase3()throws Exception {
        var server=admin.server;server.getPlayerList().op(admin.getGameProfile());var p=port();p.persistAndVerify();install();require(JailCustodyService.quarantined(target)&&record(A).state()==JailCustodyLedger.State.QUARANTINED,"restart discarded incomplete ledger state");byte[] saved=p.inventory.current().inventory();require(Arrays.equals(saved,JailCustodyService.items(server,record(A)).snapshot()),"completed native inventory lost across restart");
        controls(NEAR,JailCustodyMenu.REVIEW);controls(NEAR,JailCustodyMenu.RESTORE);require(restores==0&&OriginCustodySmoke.callbacks==0&&record(A).state()==JailCustodyLedger.State.RELEASED&&!JailCustodyService.quarantined(target),"completion confirmation replayed a grant or callback");
        require(command(server,"unjail pardonuuid "+target.getUUID())==1,"verified custody completion did not unblock legacy recovery");target.getInventory().setItem(2,ItemStack.EMPTY);p.inventory.persistAndVerify();byte[] consumed=p.inventory.current().inventory();for(int i=0;i<3;i++)controls(NEAR,JailCustodyMenu.RESTORE);
        require(restores==0&&Arrays.equals(consumed,p.inventory.current().inventory())&&JailCustodyService.items(server,record(A)).snapshot().length>100,"repeated request duplicated consumed item or deleted history");require(record(B).state()==JailCustodyLedger.State.QUARANTINED&&JailCustodyService.ledger(server).audit(A).size()>=10,"outstanding other record or audit lost");
        require(command(server,"jail "+target.getGameProfile().getName())==0,"production admissions were enabled");passed(3);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){if(!Boolean.getBoolean("uq.custodyInterface"))return;
        try{if(waitUntil>0){if(event.getServer().getTickCount()<waitUntil)return;waitUntil=0;finish1();return;}if(done)return;admin=event.getServer().getPlayerList().getPlayerByName("Dev");target=event.getServer().getPlayerList().getPlayerByName("Visitor");if(admin==null||target==null||event.getServer().getTickCount()<120)return;done=true;
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")),"hosted fixtures only");world=event.getServer().getWorldPath(LevelResource.ROOT).toRealPath();require(world.getFileName().toString().equals("jail_interface_smoke"),"not disposable interface world");require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"not final assembled JAR");OriginCustodySmoke.listeners();int phase=Integer.getInteger("uq.interfacePhase",1);if(phase==1)phase1();else if(phase==2)phase2();else if(phase==3)phase3();else throw new IOException("bad phase");
        }catch(Throwable bad){done=true;waitUntil=0;UnyieldingQuests.LOGGER.error("Protected custody interface smoke failed",bad);try{Files.writeString(Path.of("/tmp/uq-interface-result"),"failed: "+bad+"\n");}catch(IOException ignored){}}
    }
    static void passed(int phase)throws IOException{Files.writeString(Path.of("/tmp/uq-interface-result"),"passed: assembled custody interface phase "+phase+"; actual menus/packets, permissions, block/explosion/hoppers, quarantine, native confirmation, retained escrow, restart and no replay\n");}
}
