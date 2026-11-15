package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailEscrowJournal;
import dev.unyielding.quests.jail.JailVanillaCustody;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Controlled real-mod fixtures only; never installed or shipped in production. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class AccessoryCustodySmoke {
    static final UUID HELD=UUID.fromString("2275e85d-262f-4fc6-adbe-2b13e7375101"), SAVED=UUID.fromString("2275e85d-262f-4fc6-adbe-2b13e7375102");
    static final ResourceLocation RESIZE=ResourceLocation.fromNamespaceAndPath("uq_bundle_smoke","accessory_resize");
    static Map<Item,Object> fixtures;
    static String mode="normal";
    static int permissions,equips,unequips;
    static boolean done;
    static AccessoryCustodyPort pendingDisconnect;
    static Path pendingPath;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("uq.accessoryCustody")) return;
        try {
            if (pendingDisconnect!=null) {
                if (pendingDisconnect.online()) return;
                try(var journal=new JailEscrowJournal(pendingPath)) {
                    expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,HELD,pendingDisconnect,at -> {}),"offline");
                    require(journal.read(HELD).state()==JailEscrowJournal.State.HELD,"disconnect discarded custody");
                }
                pendingDisconnect=null; passed(1); return;
            }
            if(done) return;
            var server=event.getServer(); var player=server.getPlayerList().getPlayerByName("Dev"); var visitor=server.getPlayerList().getPlayerByName("Visitor");
            if(player==null||visitor==null||server.getTickCount()<100) return;
            done=true;
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")),"not hosted CI");
            require(server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_accessory_smoke"),"not disposable accessory world");
            require(Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath().equals(net.neoforged.fml.ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()),"not assembled JAR");
            require(!System.getProperty("java.class.path","").contains("unyielding-quests/build/classes"),"production development classes leaked");
            register(); Path dir=server.getWorldPath(LevelResource.ROOT).resolve("accessory-tests"); int phase=Integer.getInteger("uq.accessoryPhase",1);
            if(phase==1) {
                Files.createDirectory(dir); faults(dir,player); negatives(dir,player);
                var p=seed(player); var v=seed(visitor); prepare(dir.resolve("held"),HELD,p); prepare(dir.resolve("saved"),SAVED,v);
                try(var journal=new JailEscrowJournal(dir.resolve("saved"))) {
                    expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,SAVED,v,at -> {
                        if(at==JailVanillaCustody.RestoreBoundary.PLAYER_DURABLE) throw new IOException("saved interruption");
                    }),"saved interruption");
                    require(Arrays.equals(journal.read(SAVED).snapshot(),v.current().inventory()),"saved full component snapshot changed");
                }
                pendingDisconnect=p; pendingPath=dir.resolve("held");
                player.connection.disconnect(Component.literal("Disposable accessory disconnect/reconnect test"));
                return;
            } else if(phase==2) {
                var p=new AccessoryCustodyPort(player,fixtures); var v=new AccessoryCustodyPort(visitor,fixtures);
                try(var journal=new JailEscrowJournal(dir.resolve("held"))) {
                    require(JailVanillaCustody.inspect(journal,HELD,p)==JailVanillaCustody.Inspection.HELD_VERIFIED,"accessory custody missing after disconnect/restart");
                    resetCounts();
                    require(JailVanillaCustody.restoreForDisposableTest(journal,HELD,p,at -> {})==JailVanillaCustody.RestoreResult.RESTORED_VERIFIED,"restore failed");
                    assertItems(p,journal.read(HELD).snapshot()); require(equips==1,"normal lifecycle equip not exactly once: "+equips);
                }
                try(var journal=new JailEscrowJournal(dir.resolve("saved"))) {
                    require(JailVanillaCustody.inspect(journal,SAVED,v)==JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW,"accessory restore receipt lost after restart");
                    resetCounts(); expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,SAVED,v,at -> {}),"review");
                    JailVanillaCustody.confirmRestoredForDisposableTest(journal,SAVED,v);
                    require(equips==0&&unequips==0&&permissions==0,"confirmation ran callbacks or transferred items"); assertItems(v,journal.read(SAVED).snapshot());
                }
                server.saveEverything(true,true,true);
            } else if(phase==3) {
                for(var person:new ServerPlayer[]{player,visitor}) {
                    var p=new AccessoryCustodyPort(person,fixtures); UUID id=person==player?HELD:SAVED;
                    try(var journal=new JailEscrowJournal(dir.resolve(person==player?"held":"saved"))) {
                        assertItems(p,journal.read(id).snapshot()); var layout=p.layout(); layout.normal().get("ring").setItem(0,ItemStack.EMPTY); p.flush(); p.persistAndVerify();
                        byte[] changed=p.current().inventory(); resetCounts();
                        for(int i=0;i<3;i++) require(JailVanillaCustody.restoreForDisposableTest(journal,id,p,at -> {})==JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,"completion did not survive restart");
                        require(Arrays.equals(changed,p.current().inventory())&&equips==0&&permissions==0,"consumed accessory recreated");
                        require(journal.read(id).snapshot().length>100,"recovery history discarded"); noDrops(person);
                    }
                }
            } else throw new IOException("bad phase");
            passed(phase);
        } catch(Throwable failure) {
            done=true; pendingDisconnect=null; UnyieldingQuests.LOGGER.error("Accessory custody smoke failed",failure);
            try { Files.writeString(Path.of("/tmp/uq-accessory-result"),"failed: "+failure+"\n"); } catch(Exception ignored) {}
        }
    }
    static void register() throws Exception {
        Map<Item,Object> map=new IdentityHashMap<>(); Class<?> api=AccessoryCustodyPort.type("io.wispforest.accessories.api.Accessory");
        for(Item item:new Item[]{Items.GOLD_INGOT,Items.AMETHYST_SHARD}) {
            Object provider=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(proxy,method,args) -> {
                if(method.getName().equals("equals")) return proxy==args[0];
                if(method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if(method.getName().equals("toString")) return "audited synchronous accessory fixture";
                if(method.getName().equals("canUnequip")) {
                    permissions++; var entity=referencePlayer(args[1]);
                    if(mode.equals("deny")) return false;
                    if(mode.equals("mutate")) entity.getInventory().setItem(1,new ItemStack(Items.DIRT));
                    if(mode.equals("resize")) resize(entity,1);
                }
                if(method.getName().equals("canEquip")) {
                    permissions++; if(mode.equals("equip_deny")) return false;
                }
                if(method.getName().equals("onUnequip")&&!((ItemStack)args[0]).isEmpty()) {
                    unequips++; if(mode.equals("unequip_mutate")) referencePlayer(args[1]).getInventory().setItem(1,new ItemStack(Items.DIRT));
                }
                if(method.getName().equals("onEquip")&&!((ItemStack)args[0]).isEmpty()) {
                    equips++; if(mode.equals("equip_mutate")) referencePlayer(args[1]).getInventory().setItem(1,new ItemStack(Items.DIRT));
                }
                return InvocationHandler.invokeDefault(proxy,method,args==null?new Object[0]:args);
            });
            AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesAPI",null,"registerAccessory",new Class<?>[]{Item.class,api},item,provider); map.put(item,provider);
        }
        fixtures=Map.copyOf(map); mode="normal"; resetCounts();
    }
    static ServerPlayer referencePlayer(Object reference) throws IOException {
        return (ServerPlayer)AccessoryCustodyPort.call("io.wispforest.accessories.api.slot.SlotReference",reference,"entity",new Class<?>[0]);
    }
    static void resize(ServerPlayer player,double amount) throws IOException {
        var p=new AccessoryCustodyPort(player,fixtures); Object ring=p.layout().canonical().get("ring");
        var modifier=new AttributeModifier(RESIZE,amount,AttributeModifier.Operation.ADD_VALUE);
        AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",ring,"addTransientModifier",new Class<?>[]{AttributeModifier.class},modifier);
        AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",ring,"update",new Class<?>[0]);
    }
    static void resetCounts() { permissions=equips=unequips=0; }
    static AccessoryCustodyPort seed(ServerPlayer player) throws Exception {
        mode="normal"; var p=new AccessoryCustodyPort(player,fixtures); var layout=p.layout();
        for(var id:layout.slots()) p.container(layout,id).setItem(id.index(),ItemStack.EMPTY);
        p.flush();
        for(Object c:layout.canonical().values()) {
            AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",c,"removeModifier",new Class<?>[]{ResourceLocation.class},RESIZE);
            AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",c,"update",new Class<?>[0]);
        }
        p.flush(); VanillaCustodySmoke.seed(player); layout=p.layout();
        require(layout.normal().get("ring")!=null&&layout.normal().get("ring").getContainerSize()>0,"missing ring slot");
        ItemStack normal=new ItemStack(Items.GOLD_INGOT), cosmetic=new ItemStack(Items.AMETHYST_SHARD);
        normal.set(DataComponents.CUSTOM_NAME,Component.literal("Accessory custody normal")); cosmetic.set(DataComponents.CUSTOM_NAME,Component.literal("Accessory custody cosmetic"));
        CompoundTag custom=new CompoundTag(); custom.putString("fixture",UUID.randomUUID().toString()); normal.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
        cosmetic.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.LAPIS_LAZULI,7))));
        layout.normal().get("ring").setItem(0,normal); layout.cosmetic().get("ring").setItem(0,cosmetic); p.flush(); p.persistAndVerify(); resetCounts();
        require(accessoryCount(p.current().inventory())==2,"compatibility aliases duplicated physical stacks");
        return p;
    }
    static int accessoryCount(byte[] snapshot) throws IOException {
        CompoundTag stores=AccessoryCustodyPort.decode(snapshot).getCompound("holder").getCompound(AccessoryCustodyPort.CONTAINERS); int count=0;
        for(String key:stores.getAllKeys()) count+=stores.getCompound(key).getList("items",10).size()+stores.getCompound(key).getList("cosmetics",10).size(); return count;
    }
    static void prepare(Path path,UUID id,AccessoryCustodyPort p) throws Exception {
        JailEscrowJournal.provisionNew(path); try(var journal=new JailEscrowJournal(path)) { JailVanillaCustody.removeForDisposableTest(journal,id,p,at -> {}); }
    }
    static void assertItems(AccessoryCustodyPort p,byte[] snapshot) throws IOException {
        require(Arrays.equals(snapshot,p.current().inventory()),"full accessory/vanilla NBT or layout changed"); require(accessoryCount(snapshot)==2,"alias double capture");
        var layout=p.layout(); require(layout.normal().get("ring").getItem(0).get(DataComponents.CUSTOM_DATA)!=null,"custom accessory data lost");
        require(layout.cosmetic().get("ring").getItem(0).get(DataComponents.CONTAINER)!=null,"cosmetic nested component lost");
        require(p.player.getInventory().getItem(36).is(Items.DIAMOND_BOOTS)&&p.player.getInventory().getItem(40).is(Items.SHIELD),"vanilla armor/offhand lost");
    }
    static void faults(Path dir,ServerPlayer player) throws Exception {
        for(var boundary:JailVanillaCustody.Boundary.values()) {
            var p=seed(player); UUID id=UUID.randomUUID(); Path path=dir.resolve("remove-"+boundary); JailEscrowJournal.provisionNew(path);
            try(var journal=new JailEscrowJournal(path)) {
                expect(() -> JailVanillaCustody.removeForDisposableTest(journal,id,p,at -> { if(at==boundary) throw new IOException("injected "+at); }),"injected "+boundary);
            }
            try(var journal=new JailEscrowJournal(path)) {
                var expected=switch(boundary) {
                    case BEFORE_PREPARE -> JailVanillaCustody.Inspection.NO_RECORD;
                    case PREPARED -> JailVanillaCustody.Inspection.PREPARED_UNCHANGED;
                    case REMOVAL_STARTED -> JailVanillaCustody.Inspection.REMOVAL_NOT_PERSISTED_REVIEW;
                    case MUTATED -> JailVanillaCustody.Inspection.REVIEW_REQUIRED;
                    case PLAYER_DURABLE -> JailVanillaCustody.Inspection.REMOVAL_PERSISTED_REVIEW;
                    case HELD -> JailVanillaCustody.Inspection.HELD_VERIFIED;
                };
                require(JailVanillaCustody.inspect(journal,id,p)==expected,"wrong removal fault "+boundary);
                if(boundary!=JailVanillaCustody.Boundary.BEFORE_PREPARE) {
                    byte[] unchanged=p.current().inventory(); int callbacks=permissions;
                    expect(() -> JailVanillaCustody.removeForDisposableTest(journal,id,p,at -> {}),"Existing custody");
                    require(Arrays.equals(unchanged,p.current().inventory())&&callbacks==permissions,"replayed accessory removal");
                }
            }
            noDrops(player);
        }
        for(var boundary:JailVanillaCustody.RestoreBoundary.values()) {
            var p=seed(player); UUID id=UUID.randomUUID(); Path path=dir.resolve("restore-"+boundary); prepare(path,id,p);
            try(var journal=new JailEscrowJournal(path)) { expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,id,p,at -> { if(at==boundary) throw new IOException("injected "+at); }),"injected "+boundary); }
            try(var journal=new JailEscrowJournal(path)) {
                if(boundary!=JailVanillaCustody.RestoreBoundary.BEFORE_PHASE) {
                    byte[] unchanged=p.current().inventory(); resetCounts();
                    if(boundary==JailVanillaCustody.RestoreBoundary.COMPLETE) require(JailVanillaCustody.restoreForDisposableTest(journal,id,p,at -> {})==JailVanillaCustody.RestoreResult.ALREADY_COMPLETE,"complete replay");
                    else expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,id,p,at -> {}),"review");
                    require(Arrays.equals(unchanged,p.current().inventory())&&permissions==0&&equips==0,"replayed restoration callbacks");
                }
                require(journal.read(id).snapshot().length>100,"restoration discarded source");
            }
            noDrops(player);
        }
    }
    static void negatives(Path dir,ServerPlayer player) throws Exception {
        for(String problem:new String[]{"unsupported","binding","deny","mutate","resize","unequip_mutate","partial_remove"}) {
            var p=seed(player); var layout=p.layout(); ItemStack source=layout.normal().get("ring").getItem(0).copy();
            if(problem.equals("unsupported")) layout.normal().get("ring").setItem(0,new ItemStack(Items.DIAMOND));
            if(problem.equals("binding")) layout.normal().get("ring").getItem(0).enchant(player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.BINDING_CURSE),1);
            if(problem.equals("unsupported")||problem.equals("binding")) { p.flush(); p.persistAndVerify(); resetCounts(); }
            mode=problem; if(problem.equals("partial_remove")) p.failAfterRemoval=1;
            Path path=dir.resolve(problem); UUID id=UUID.randomUUID(); JailEscrowJournal.provisionNew(path);
            try(var journal=new JailEscrowJournal(path)) {
                byte[] before=p.current().inventory(); expect(() -> JailVanillaCustody.removeForDisposableTest(journal,id,p,at -> {}),"");
                if(problem.equals("unsupported")||problem.equals("binding")) {
                    require(journal.list().isEmpty()&&permissions==0&&Arrays.equals(before,p.current().inventory()),"unsafe preflight mutated or called permission hooks");
                } else {
                    require(journal.read(id).state()==JailEscrowJournal.State.REMOVAL_STARTED&&Arrays.equals(before,journal.read(id).snapshot()),"callback failure lost source");
                    if(problem.equals("deny")||problem.equals("mutate")||problem.equals("resize")) require(ItemStack.matches(source,p.layout().normal().get("ring").getItem(0)),"permission side effect caused extraction");
                    if(problem.equals("mutate")||problem.equals("unequip_mutate")) require(player.getInventory().getItem(1).is(Items.DIRT),"callback side effect was overwritten");
                    mode="normal"; p.flush(); p.persistAndVerify(); byte[] changed=p.current().inventory(); resetCounts();
                    expect(() -> JailVanillaCustody.removeForDisposableTest(journal,UUID.randomUUID(),p,at -> {}),"Existing custody");
                    require(Arrays.equals(changed,p.current().inventory())&&permissions==0,"callback outcome replayed");
                }
            }
            mode="normal"; noDrops(player);
        }
        for(String problem:new String[]{"occupied","missing_slot","dependencies","equip_deny","equip_mutate","partial_insert","save_before","save_after"}) {
            var p=seed(player); UUID id=UUID.randomUUID(); Path path=dir.resolve(problem); prepare(path,id,p);
            if(problem.equals("occupied")) { p.layout().cosmetic().get("ring").setItem(0,new ItemStack(Items.AMETHYST_SHARD)); p.flush(); p.persistAndVerify(); }
            if(problem.equals("missing_slot")) resize(player,-1);
            if(problem.equals("partial_insert")) p.failAfterInsertion=1;
            mode=problem;
            try(var journal=new JailEscrowJournal(path)) {
                byte[] source=journal.read(id).snapshot();
                if(problem.equals("dependencies")) {
                    CompoundTag stale=AccessoryCustodyPort.decode(source); stale.putString("dependencies","changed");
                    expect(() -> p.planRestore(AccessoryCustodyPort.encode(stale),id),"dependencies");
                } else {
                    JailVanillaCustody.InventoryPort port=problem.startsWith("save_")?new SaveFault(p,problem.equals("save_after")):p;
                    expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,id,port,at -> {}),"");
                }
                require(Arrays.equals(source,journal.read(id).snapshot()),"restore failure discarded escrow");
                if(problem.equals("occupied")||problem.equals("missing_slot")||problem.equals("dependencies")) require(journal.read(id).state()==JailEscrowJournal.State.HELD,"unsafe restoration changed phase");
                else {
                    require(journal.read(id).state()==JailEscrowJournal.State.RESTORE_STARTED,"uncertain restoration completed");
                    if(problem.equals("equip_mutate")) require(player.getInventory().getItem(1).is(Items.DIRT),"equip callback side effect overwritten");
                    mode="normal"; p.flush(); p.persistAndVerify(); byte[] changed=p.current().inventory(); resetCounts();
                    expect(() -> JailVanillaCustody.restoreForDisposableTest(journal,id,p,at -> {}),"review");
                    require(Arrays.equals(changed,p.current().inventory())&&permissions==0&&equips==0,"uncertain accessory restore replayed");
                }
            }
            mode="normal"; noDrops(player);
        }
    }
    record SaveFault(AccessoryCustodyPort base,boolean after) implements JailVanillaCustody.InventoryPort {
        public UUID owner(){return base.owner();} public boolean online(){return base.online();}
        public JailVanillaCustody.View current()throws IOException{return base.current();} public JailVanillaCustody.View durable()throws IOException{return base.durable();}
        public void clearAndMark(UUID id)throws IOException{base.clearAndMark(id);} public JailVanillaCustody.View planRestore(byte[] b,UUID id)throws IOException{return base.planRestore(b,id);}
        public void restoreExact(byte[] b,UUID id)throws IOException{base.restoreExact(b,id);}
        public void persistAndVerify()throws IOException{if(after)base.persistAndVerify();throw new IOException("Injected accessory save failure");}
    }
    static void noDrops(ServerPlayer player) {
        for(ItemEntity entity:player.serverLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(128))) {
            var name=entity.getItem().get(DataComponents.CUSTOM_NAME);
            require(name==null||(!name.getString().startsWith("Accessory custody")&&!name.getString().startsWith("Escrow component")),"custody item dropped into world");
        }
    }
    @FunctionalInterface interface Action{void run()throws Exception;}
    static void expect(Action action,String contains)throws Exception {
        try{action.run();}catch(IOException expected){require(expected.getMessage().contains(contains),"Unexpected failure: "+expected);return;} throw new AssertionError("Expected failure: "+contains);
    }
    static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
    static void passed(int phase)throws IOException{Files.writeString(Path.of("/tmp/uq-accessory-result"),"passed: assembled accessory phase "+phase+"; actual normal/cosmetic and alias APIs, callback restrictions and side effects, controlled transfers, durable phases and no replay\n");}
}
