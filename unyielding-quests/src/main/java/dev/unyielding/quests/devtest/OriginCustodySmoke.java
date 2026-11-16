package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Destructive synthetic fixtures only, in a separate test mod around the final JAR. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class OriginCustodySmoke {
    static final UUID DEV=UUID.fromString("a79b2a2d-4579-48ba-9430-3ec2df120001"),VISITOR=UUID.fromString("a79b2a2d-4579-48ba-9430-3ec2df120002");
    static boolean finished,listeners;static int callbacks;static String mode="normal";
    static ServerPlayer disconnecting;static Path pendingDirectory;
    interface Action{void run()throws Exception;}
    static void require(boolean value,String why){if(!value)throw new IllegalStateException(why);}
    static void refuse(Action action)throws Exception{try{action.run();}catch(IOException expected){return;}throw new IllegalStateException("unsafe action was accepted");}
    static void callData(OriginCustodyPort p,String method,Class<?>[] types,Object...args)throws IOException{OriginCustodyPort.call(OriginCustodyPort.DATA,p.data(),method,types,args);}
    static Object data(ServerPlayer player)throws IOException{
        var type=(AttachmentType<?>)OriginCustodyPort.call("com.cyberday1.neoorigins.attachment.OriginAttachments",null,"originData");return player.getData(type);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) static void listeners()throws IOException {
        if(listeners)return;listeners=true;
        for(String name:List.of("PowerGrantedEvent","PowerRevokedEvent")){
            Class event=OriginCustodyPort.type("com.cyberday1.neoorigins.api.event."+name);
            NeoForge.EVENT_BUS.addListener(event,(Consumer)(e->{try{
                if(!OriginCustodyPort.POWER.equals(event.getMethod("getPowerId").invoke(e)))return;
                callbacks++;ServerPlayer player=(ServerPlayer)event.getMethod("getEntity").invoke(e);
                switch(mode){
                    case "throw"->throw new IllegalStateException("injected native event callback failure");
                    case "item"->player.getInventory().setItem(1,new ItemStack(Items.DIRT));
                    case "progress"->OriginCustodyPort.call(OriginCustodyPort.DATA,data(player),"setCustomFloat",new Class<?>[]{String.class,float.class},"callback",99f);
                    default->{}
                }
            }catch(IOException|ReflectiveOperationException error){throw new IllegalStateException(error);}}));
        }
    }
    @SuppressWarnings("unchecked") static ResourceLocation layerFor(ResourceLocation origin,Map<ResourceLocation,ResourceLocation> choices)throws IOException{
        var manager=OriginCustodyPort.singleton(OriginCustodyPort.LAYERS);
        Map<ResourceLocation,?> layers=(Map<ResourceLocation,?>)OriginCustodyPort.call(OriginCustodyPort.LAYERS,manager,"getLayers");
        List<ResourceLocation> found=new ArrayList<>();
        for(var e:layers.entrySet()){
            var available=(List<?>)OriginCustodyPort.call("com.cyberday1.neoorigins.api.origin.OriginLayer",e.getValue(),"getAvailableOriginIds",new Class<?>[]{Map.class},choices);
            if(available.contains(origin))found.add(e.getKey());
        }
        require(found.size()==1,"fixture must extend exactly one native layer: "+origin+" "+found);return found.getFirst();
    }
    @SuppressWarnings("unchecked") static OriginCustodyPort seed(ServerPlayer player,ResourceLocation original)throws Exception {
        mode="normal";Object data=data(player);OriginCustodyPort.call(OriginCustodyPort.DATA,data,"clear");
        var main=layerFor(original,Map.of());var classes=layerFor(OriginCustodyPort.KEPT_CLASS,Map.of());require(!main.equals(classes),"class layer was flattened");
        Object config=OriginCustodyPort.type("com.cyberday1.neoorigins.config.AdminConfig").getField("UNIQUE_ORIGIN_LAYERS").get(null);
        ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>>)config).set(List.of(main.toString()));
        ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?>)config).save();
        OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setOrigin",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},main,original);
        OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setOrigin",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},classes,OriginCustodyPort.KEPT_CLASS);
        OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setHadAllOrigins",new Class<?>[]{boolean.class},true);
        OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setCustomFloat",new Class<?>[]{String.class,float.class},"kept",7f);
        OriginCustodyPort.call(OriginCustodyPort.DATA,data,"setEssenceKills",new Class<?>[]{int.class},17);
        player.getPersistentData().remove(OriginCustodyPort.RECEIPT);player.setHealth(player.getMaxHealth());
        // This explicit fixture reset is not a transaction/recovery path. The
        // adapter never clears an attachment, rewinds progress or seizes claims.
        OriginCustodyPort.call(OriginCustodyPort.SERVICE,null,"applyOriginPowers",new Class<?>[]{ServerPlayer.class,ResourceLocation.class,ResourceLocation.class,ResourceLocation.class},player,main,original,original);
        Object claims=OriginCustodyPort.call(OriginCustodyPort.CLAIMS,null,"get",new Class<?>[]{net.minecraft.server.MinecraftServer.class},player.server);
        player.server.overworld().getDataStorage().save();
        var p=new OriginCustodyPort(player,new VanillaCustodySmoke.PlayerPort(player),null);
        UUID previous=p.owner(claims,original);require(previous==null||previous.equals(player.getUUID()),"fixture would steal original claim");
        OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},main,original,player.getUUID());
        OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"releaseIfOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},main,p.human,player.getUUID());
        player.server.overworld().getDataStorage().save();VanillaCustodySmoke.seed(player);p.persistAndVerify();callbacks=0;return p;
    }
    static void prepare(Path directory,UUID id,OriginCustodyPort p)throws Exception {
        Files.createDirectory(directory);JailEscrowJournal.provisionNew(directory.resolve("items"));JailEscrowJournal.provisionNew(directory.resolve("origins"));
        try(var items=new JailEscrowJournal(directory.resolve("items"))){JailVanillaCustody.removeForDisposableTest(items,id,p.inventory,at->{});}
    }
    static OriginCustodyPort reopen(Path directory,UUID id,ServerPlayer player)throws Exception {
        try(var origin=new JailEscrowJournal(directory.resolve("origins"))){return new OriginCustodyPort(player,new VanillaCustodySmoke.PlayerPort(player),JailOriginTransaction.intentForReview(origin,id,player.getUUID()));}
    }
    static void temporary(Path directory,UUID id,OriginCustodyPort p,JailOriginTransaction.Checkpoint at)throws Exception {
        try(var items=new JailEscrowJournal(directory.resolve("items"));var origins=new JailEscrowJournal(directory.resolve("origins"))){JailOriginTransaction.temporaryForDisposableTest(origins,items,id,p,at);}
    }
    static void original(Path directory,UUID id,OriginCustodyPort p,JailOriginTransaction.Checkpoint at)throws Exception {
        try(var items=new JailEscrowJournal(directory.resolve("items"));var origins=new JailEscrowJournal(directory.resolve("origins"))){JailOriginTransaction.originalForDisposableTest(origins,items,id,p,at);}
    }
    static void held(Path directory,UUID id,OriginCustodyPort p)throws Exception{
        try(var items=new JailEscrowJournal(directory.resolve("items"));var origins=new JailEscrowJournal(directory.resolve("origins"))){
            refuse(()->JailOriginTransaction.assertInventoryReleaseReady(origins,items,id,p,false));require(items.read(id).state()==JailEscrowJournal.State.HELD,"inventory released prematurely");
        }
    }
    static void faults(Path root,ServerPlayer player)throws Exception {
        for(boolean restore:new boolean[]{false,true})for(var boundary:JailOriginTransaction.Boundary.values()){
            if(restore&&boundary==JailOriginTransaction.Boundary.PREPARED)continue;
            var p=seed(player,OriginCustodyPort.ALPHA);UUID id=UUID.randomUUID();Path dir=root.resolve((restore?"restore-":"switch-")+boundary);prepare(dir,id,p);
            if(restore)temporary(dir,id,p,at->{});
            int before=callbacks;
            refuse(()->{if(restore)original(dir,id,p,at->{if(at==boundary)throw new IOException("injected "+at);});else temporary(dir,id,p,at->{if(at==boundary)throw new IOException("injected "+at);});});
            int after=callbacks;
            try(var items=new JailEscrowJournal(dir.resolve("items"));var origins=new JailEscrowJournal(dir.resolve("origins"))){
                var status=JailOriginTransaction.inspect(origins,items,id,p);require(callbacks==after,"inspection replayed callback");
                if(restore&&boundary==JailOriginTransaction.Boundary.COMPLETE){require(JailOriginTransaction.originalForDisposableTest(origins,items,id,p,at->{})==JailOriginTransaction.Result.ALREADY_COMPLETE,"repeat completion failed");}
                else if(boundary!=JailOriginTransaction.Boundary.BEFORE_PREPARE)refuse(()->{if(restore)JailOriginTransaction.originalForDisposableTest(origins,items,id,p,at->{});else JailOriginTransaction.temporaryForDisposableTest(origins,items,id,p,at->{});});
                require(callbacks==after&&after-before<=1,"interruption replayed native lifecycle");
                if(status==JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW||status==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW){JailOriginTransaction.confirmSavedForDisposableTest(origins,items,id,p);require(callbacks==after,"confirmation invoked callbacks");}
                require(items.read(id).snapshot().length>100&&player.getInventory().isEmpty(),"custody source lost or inserted during fault");
            }
        }
        for(String fault:List.of("throw","item","progress"))for(boolean restore:new boolean[]{false,true}){
            var p=seed(player,OriginCustodyPort.ALPHA);UUID id=UUID.randomUUID();Path dir=root.resolve("callback-"+fault+restore);prepare(dir,id,p);if(restore)temporary(dir,id,p,at->{});
            mode=fault;refuse(()->{if(restore)original(dir,id,p,at->{});else temporary(dir,id,p,at->{});});mode="normal";int count=callbacks;
            try(var items=new JailEscrowJournal(dir.resolve("items"));var origins=new JailEscrowJournal(dir.resolve("origins"))){
                require(origins.read(id).state()==(restore?JailEscrowJournal.State.RESTORE_STARTED:JailEscrowJournal.State.REMOVAL_STARTED),"callback failure promoted phase");
                require(JailOriginTransaction.inspect(origins,items,id,p)==JailOriginTransaction.Inspection.REVIEW_REQUIRED,"unexpected callback side effect accepted");
                refuse(()->JailOriginTransaction.confirmSavedForDisposableTest(origins,items,id,p));require(callbacks==count,"failed callback replayed");
                if(fault.equals("item"))require(player.getInventory().getItem(1).is(Items.DIRT),"callback side effect silently rolled back");
            }
        }
        for(var boundary:OriginCustodyPort.SaveBoundary.values())for(boolean restore:new boolean[]{false,true}){
            var p=seed(player,OriginCustodyPort.ALPHA);UUID id=UUID.randomUUID();Path dir=root.resolve("save-"+boundary+restore);prepare(dir,id,p);if(restore)temporary(dir,id,p,at->{});
            JailOriginTransaction.Checkpoint inject=at->{if(at==JailOriginTransaction.Boundary.BEFORE_SAVE)p.saveFault=b->{if(b==boundary)throw new IOException("injected split save "+b);};};
            refuse(()->{if(restore)original(dir,id,p,inject);else temporary(dir,id,p,inject);});p.saveFault=at->{};
            try(var origins=new JailEscrowJournal(dir.resolve("origins"));var items=new JailEscrowJournal(dir.resolve("items"))){require(origins.read(id).state()==(restore?JailEscrowJournal.State.RESTORE_STARTED:JailEscrowJournal.State.REMOVAL_STARTED),"save failure promoted transaction");
                var expected=boundary==OriginCustodyPort.SaveBoundary.BEFORE_PLAYER||boundary==OriginCustodyPort.SaveBoundary.AFTER_PLAYER?JailOriginTransaction.Inspection.REVIEW_REQUIRED:restore?JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW:JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW;
                require(JailOriginTransaction.inspect(origins,items,id,p)==expected,"split native save misclassified "+boundary+" restore="+restore);
                if(expected==JailOriginTransaction.Inspection.REVIEW_REQUIRED)refuse(()->JailOriginTransaction.confirmSavedForDisposableTest(origins,items,id,p));
            }
            held(dir,id,p);
        }
    }
    static void negatives(Path root,ServerPlayer player,ServerPlayer other)throws Exception {
        for(String fault:List.of("tier","picker","cooldown","class","dynamic","global","humanConflict","originalConflict")){
            var p=seed(player,OriginCustodyPort.ALPHA);byte[] before=p.inventory.current().inventory();Object d=p.data();Object claims=p.claims();
            switch(fault){
                case "tier"->callData(p,"setEvolutionTier",new Class<?>[]{int.class},1);
                case "picker"->callData(p,"setPendingLayerPickerCommit",new Class<?>[]{boolean.class},true);
                case "cooldown"->callData(p,"setCooldown",new Class<?>[]{String.class,int.class,int.class},"probe",player.tickCount,9999);
                case "dynamic"->callData(p,"addDynamicGrant",new Class<?>[]{ResourceLocation.class},OriginCustodyPort.POWER);
                case "global"->callData(p,"addGlobalGrant",new Class<?>[]{ResourceLocation.class},OriginCustodyPort.POWER);
                case "class"->{var classLayer=layerFor(OriginCustodyPort.KEPT_CLASS,p.selections());callData(p,"setOrigin",new Class<?>[]{ResourceLocation.class,ResourceLocation.class},classLayer,OriginCustodyPort.id("origins_classes_ex:crusader"));}
                case "humanConflict","originalConflict"->OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},p.layer,fault.equals("humanConflict")?p.human:p.original,other.getUUID());
            }
            int beforeCallbacks=callbacks;refuse(()->p.prepareIntent(UUID.randomUUID(),new byte[32]));require(callbacks==beforeCallbacks&&Arrays.equals(before,p.inventory.current().inventory()),"preflight changed items/callbacks");
            if(fault.endsWith("Conflict")){
                var origin=fault.equals("humanConflict")?p.human:p.original;require(other.getUUID().equals(p.owner(claims,origin)),"conflicting claim stolen");
                // Explicit synthetic fault teardown, only after owner assertion.
                OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"releaseIfOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},p.layer,origin,other.getUUID());
            }
        }
        for(boolean humanConflict:new boolean[]{false,true}){
            var conflict=seed(player,OriginCustodyPort.ALPHA);UUID tx=UUID.randomUUID();Path test=root.resolve("late-claim-"+humanConflict);prepare(test,tx,conflict);temporary(test,tx,conflict,at->{});
            var contested=humanConflict?conflict.human:conflict.original;Object claims=conflict.claims();
            OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},conflict.layer,contested,other.getUUID());
            int before=callbacks;refuse(()->original(test,tx,conflict,at->{}));held(test,tx,conflict);require(before==callbacks&&other.getUUID().equals(conflict.owner(claims,contested)),"late conflict stole/released a claim");
            OriginCustodyPort.call(OriginCustodyPort.CLAIMS,claims,"releaseIfOwner",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},conflict.layer,contested,other.getUUID());
        }
        var p=seed(player,OriginCustodyPort.ALPHA);UUID id=UUID.randomUUID();Path dir=root.resolve("preowned");
        OriginCustodyPort.call(OriginCustodyPort.CLAIMS,p.claims(),"claim",new Class<?>[]{ResourceLocation.class,ResourceLocation.class,UUID.class},p.layer,p.human,p.owner());p.persistAndVerify();prepare(dir,id,p);temporary(dir,id,p,at->{});original(dir,id,p,at->{});
        require(p.owner().equals(p.owner(p.claims(),p.human)),"preexisting self claim deleted");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.originCustody"))return;
        try{
            if(disconnecting!=null){if(!disconnecting.hasDisconnected()||event.getServer().getPlayerList().getPlayer(disconnecting.getUUID())!=null)return;
                var p=reopen(pendingDirectory,DEV,disconnecting);int count=callbacks;refuse(()->original(pendingDirectory,DEV,p,at->{}));require(callbacks==count,"offline restore invoked native lifecycle");require(p.owner().equals(p.owner(p.claims(),p.original))&&p.owner().equals(p.owner(p.claims(),p.human)),"disconnect released origin reservation/lease");passed(1);disconnecting=null;return;}
            if(finished)return;var server=event.getServer();var dev=server.getPlayerList().getPlayerByName("Dev");var visitor=server.getPlayerList().getPlayerByName("Visitor");if(dev==null||visitor==null||server.getTickCount()<120)return;finished=true;
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")),"hosted fixtures only");require(server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_origin_smoke"),"wrong fixture world");
            require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"not final assembled JAR");listeners();
            Path root=server.getWorldPath(LevelResource.ROOT).resolve("origin-tests");int phase=Integer.getInteger("uq.originPhase",1);
            if(phase==1){Files.createDirectory(root);faults(root,dev);negatives(root,dev,visitor);var v=seed(visitor,OriginCustodyPort.BETA);var p=seed(dev,OriginCustodyPort.ALPHA);
                Path dir=root.resolve("dev");prepare(dir,DEV,p);temporary(dir,DEV,p,at->{});held(dir,DEV,p);
                refuse(()->v.prepareIntent(UUID.randomUUID(),new byte[32]));require(dev.getUUID().equals(p.owner(p.claims(),p.original)),"original reservation lost while Human");
                pendingDirectory=dir;disconnecting=dev;server.saveEverything(true,true,true);dev.connection.disconnect(Component.literal("Disposable origin disconnect probe"));return;
            }else if(phase==2){var p=reopen(root.resolve("dev"),DEV,dev);p.persistAndVerify();
                try(var items=new JailEscrowJournal(root.resolve("dev/items"));var origins=new JailEscrowJournal(root.resolve("dev/origins"))){require(JailOriginTransaction.inspect(origins,items,DEV,p)==JailOriginTransaction.Inspection.TEMPORARY_VERIFIED,"Human state lost across actual restart");}
                callData(p,"setCustomFloat",new Class<?>[]{String.class,float.class},"kept",66f);callData(p,"setEssenceKills",new Class<?>[]{int.class},123);original(root.resolve("dev"),DEV,p,at->{});
                require((int)OriginCustodyPort.call(OriginCustodyPort.DATA,p.data(),"getEssenceKills")==123,"progress rewound");require(p.owner(p.claims(),p.human)==null,"temporary Human lease leaked");
                var v=new OriginCustodyPort(visitor,new VanillaCustodySmoke.PlayerPort(visitor),null);v.persistAndVerify();Path dir=root.resolve("visitor");prepare(dir,VISITOR,v);temporary(dir,VISITOR,v,at->{});
                refuse(()->original(dir,VISITOR,v,at->{if(at==JailOriginTransaction.Boundary.PLAYER_AND_CLAIMS_DURABLE)throw new IOException("saved origin before actual restart");}));server.saveEverything(true,true,true);
            }else if(phase==3){for(ServerPlayer player:List.of(dev,visitor)){boolean first=player==dev;UUID id=first?DEV:VISITOR;Path dir=root.resolve(first?"dev":"visitor");var p=reopen(dir,id,player);p.persistAndVerify();
                    try(var items=new JailEscrowJournal(dir.resolve("items"));var origins=new JailEscrowJournal(dir.resolve("origins"))){int count=callbacks;
                        if(!first){require(JailOriginTransaction.inspect(origins,items,id,p)==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW,"saved origin ambiguity lost on actual restart");refuse(()->JailOriginTransaction.originalForDisposableTest(origins,items,id,p,at->{}));JailOriginTransaction.confirmSavedForDisposableTest(origins,items,id,p);}
                        require(JailOriginTransaction.originalForDisposableTest(origins,items,id,p,at->{})==JailOriginTransaction.Result.ALREADY_COMPLETE,"origin completion forgotten");require(callbacks==count,"restart replayed callbacks");
                        var guarded=new Guarded(p,origins,items,id);require(JailVanillaCustody.restoreForDisposableTest(items,id,guarded,at->{})==JailVanillaCustody.RestoreResult.RESTORED_VERIFIED,"guarded item return failed");
                        require(Arrays.equals(items.read(id).snapshot(),p.inventory.current().inventory()),"original slots/components changed");
                        for(int n=0;n<2;n++)require(JailVanillaCustody.restoreForDisposableTest(items,id,guarded,at->{})==JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,"items duplicated on repeat");
                        require(callbacks==count&&p.owner().equals(p.owner(p.claims(),p.original))&&p.owner(p.claims(),p.human)==null,"claims/callbacks changed on item return");
                    }
                }
            }else throw new IOException("unknown phase");passed(phase);
        }catch(Throwable failure){finished=true;disconnecting=null;UnyieldingQuests.LOGGER.error("Actual origin custody smoke failed",failure);try{Files.writeString(Path.of("/tmp/uq-origin-result"),"failed: "+failure+"\n");}catch(IOException ignored){}}
    }
    record Guarded(OriginCustodyPort origin,JailEscrowJournal origins,JailEscrowJournal items,UUID id)implements JailVanillaCustody.InventoryPort{
        public UUID owner(){return origin.owner();}public boolean online(){return origin.online();}public JailVanillaCustody.View current()throws IOException{return origin.inventory.current();}public JailVanillaCustody.View durable()throws IOException{return origin.inventory.durable();}
        public void clearAndMark(UUID tx)throws IOException{throw new IOException("release only");}
        public JailVanillaCustody.View planRestore(byte[] snapshot,UUID tx)throws IOException{JailOriginTransaction.assertInventoryReleaseReady(origins,items,id,origin,false);return origin.inventory.planRestore(snapshot,tx);}
        public void restoreExact(byte[] snapshot,UUID tx)throws IOException{JailOriginTransaction.assertInventoryReleaseReady(origins,items,id,origin,true);origin.inventory.restoreExact(snapshot,tx);}
        public void persistAndVerify()throws IOException{origin.inventory.persistAndVerify();origin.persistAndVerify();}
    }
    static void passed(int phase)throws IOException{Files.writeString(Path.of("/tmp/uq-origin-result"),"passed: assembled actual NeoOrigins/addons phase "+phase+"; native audited callbacks, durable linked custody, failure refusal, unique leases, current progress, no replay and real restart recovery\n");}
}
