package dev.unyielding.bundlesmoke;

import dev.unyielding.jail.JailCustodyMenu;
import dev.unyielding.jail.JailCustodyService;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

final class Items249Proof {
    private static final int CARRIED=12,ORDINARY=13;
    private Items249Proof() {}
    private static Container back(ServerPlayer player) throws Exception {
        Object cap=((java.util.Optional<?>)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesCapability",null,
                "getOptionally",new Class<?>[]{LivingEntity.class},player)).orElseThrow();
        var containers=(Map<?,?>)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesCapability",cap,"getContainers",new Class<?>[0]);
        var slot=containers.get("back");
        require(slot!=null,"The provider's actual equipped back slot was not available: "+containers.keySet());
        return (Container)AccessoryCustodyPort.call("io.wispforest.accessories.api.AccessoriesContainer",slot,"getAccessories",new Class<?>[0]);
    }
    private static ItemStack createBag(ServerPlayer p,String type,String source,Item item,int count) throws Exception {
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:"+type)));
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Marked "+source+" backpack"));
        var wrapper=BackpackWrapper.fromStack(stack);
        var contents=new ItemStack(item,count);
        var mark=new CompoundTag();
        mark.putUUID("fixture",UUID.randomUUID());
        contents.set(DataComponents.CUSTOM_DATA,CustomData.of(mark));
        contents.set(DataComponents.CUSTOM_NAME,Component.literal("Inside "+source));
        wrapper.getInventoryHandler().setStackInSlot(4,contents);
        var id=wrapper.getContentsUuid().orElseThrow();
        var nativeData=BackpackStorage.get(p.server.overworld()).getBackpackContents(id).orElseThrow().copy();
        require(!nativeData.isEmpty() && ItemStack.matches(contents,wrapper.getInventoryHandler().getStackInSlot(4)),"Native item failed to fill");
        var golden=new CompoundTag();
        golden.putUUID("id",id);
        golden.put("stack",stack.save(p.registryAccess()));
        golden.put("native_store",nativeData);
        golden.put("inside",contents.save(p.registryAccess()));
        Files.write(evidence(p,"bag-"+source),OriginCustodyPort.encode(golden));
        return stack;
    }
    private static CompoundTag golden(ServerPlayer p,String source) throws Exception {
        return OriginCustodyPort.decode(Files.readAllBytes(evidence(p,"bag-"+source)));
    }
    static void seed(ServerPlayer visitor) throws Exception {
        var carried=createBag(visitor,"backpack","carried",Items.DIAMOND,13);
        var worn=createBag(visitor,"iron_backpack","worn",Items.EMERALD,17);
        require(!golden(visitor,"carried").getUUID("id").equals(golden(visitor,"worn").getUUID("id")),"Native bag identities collide");
        require(visitor.getInventory().getItem(CARRIED).isEmpty() && back(visitor).getItem(0).isEmpty(),"Bag destinations already occupied");
        visitor.getInventory().setItem(CARRIED,carried);
        back(visitor).setItem(0,worn);
        var plain=BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("neoorigins:gold_orb")).orElseThrow();
        require(plain.getClass()==Item.class,"NeoOrigins gold orb is not its native plain item");
        var ordinary=new ItemStack(plain,1);
        ordinary.set(DataComponents.CUSTOM_NAME,Component.literal("Ordinary sealed modded item"));
        visitor.getInventory().setItem(ORDINARY,ordinary);
        Files.write(evidence(visitor,"ordinary"),OriginCustodyPort.encode((CompoundTag)ordinary.save(visitor.registryAccess())));
        proof.put("ordinary_modded_item",BuiltInRegistries.ITEM.getKey(plain).toString());
        Items249Food.seed(visitor);
    }
    static void duplicateRefused(ServerPlayer visitor) throws Exception {
        int spare=34;
        require(visitor.getInventory().getItem(spare).isEmpty(),"Duplicate fixture collision");
        visitor.getInventory().setItem(spare,visitor.getInventory().getItem(CARRIED).copy());
        try { Custody249Proof.refused(visitor,"duplicate_backpack_handle"); }
        finally { visitor.getInventory().setItem(spare,ItemStack.EMPTY); }
        checkNativeStores(visitor);
    }
    private static void checkNativeStores(ServerPlayer p) throws Exception {
        var storage=BackpackStorage.get(p.server.overworld());
        for (String name:List.of("carried","worn")) {
            var expected=golden(p,name);
            require(storage.getBackpackContents(expected.getUUID("id")).orElseThrow().equals(expected.getCompound("native_store")),
                    "Native storage record for "+name+" backpack changed");
        }
    }
    static void inspectLocker(ServerPlayer staff,ServerPlayer prisoner) throws Exception {
        var entry=JailCustodyService.ledger(staff.server).list().stream().filter(row->row.id().equals(CertifiedJailSmoke.last)).findFirst().orElseThrow();
        var anchor=entry.anchor();
        var pos=new BlockPos(anchor.x(),anchor.y(),anchor.z());
        var previous=staff.position();
        float yaw=staff.getYRot(),pitch=staff.getXRot();
        staff.server.getPlayerList().op(staff.getGameProfile());
        try {
            staff.teleportTo(staff.serverLevel(),pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5,yaw,pitch);
            JailCustodyService.open(staff,pos);
            require(staff.containerMenu instanceof JailCustodyMenu,"Staff did not open real cell custody chest");
            var menu=(JailCustodyMenu)staff.containerMenu;
            require(menu.owner().equals(prisoner.getUUID()) && menu.transaction().equals(entry.id()) && menu.stillValid(staff),"Wrong/invalid detention locker");
            int carried=0,worn=0,inside=0;
            for (int index=0;index<45;index++) {
                var s=menu.getSlot(index);
                var preview=s.getItem();
                String label=preview.getHoverName().getString();
                if (label.contains("Inside carried") || label.contains("Inside worn")) inside++;
                if (!BuiltInRegistries.ITEM.getKey(preview.getItem()).getNamespace().equals("sophisticatedbackpacks")) continue;
                if (!label.contains("Marked carried backpack") && !label.contains("Marked worn backpack")) continue;
                require(!s.mayPickup(staff) && s.remove(1).isEmpty() && menu.quickMoveStack(staff,index).isEmpty(),"Backpack preview can be taken");
                require(BackpackWrapper.fromStack(preview).getContentsUuid().isEmpty(),"Backpack preview carries a usable storage UUID");
                if (label.contains("Marked carried backpack") && label.contains("vanilla:"+CARRIED)) carried++;
                if (label.contains("Marked worn backpack") && label.contains("items:back:0")) worn++;
            }
            require(carried==1 && worn==1 && inside==0,"Custody chest did not show two whole bags in their correct sources");
            flag("actual_locker_correct_two_sources_and_no_live_handles");
        } finally {
            staff.closeContainer();
            staff.teleportTo(staff.serverLevel(),previous.x,previous.y,previous.z,yaw,pitch);
            staff.server.getPlayerList().deop(staff.getGameProfile());
        }
        held(prisoner,"admission");
    }
    static void held(ServerPlayer p,String phase) throws Exception {
        require(p.getInventory().getItem(CARRIED).isEmpty() && back(p).getItem(0).isEmpty(),"Bag remains accessible during custody");
        Object unavailable=Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper$Noop").getField("INSTANCE").get(null);
        var storage=BackpackStorage.get(p.server.overworld());
        for (String name:List.of("carried","worn")) {
            var gold=golden(p,name);
            var stale=ItemStack.parse(p.registryAccess(),gold.getCompound("stack")).orElseThrow();
            require(BackpackWrapper.fromStack(stale)==unavailable,"Provider still opens a held backpack's UUID");
            UUID id=gold.getUUID("id");
            var writable=storage.getOrCreateBackpackContents(id);
            require(writable.isEmpty(),"Provider exposed the held writable inventory");
            writable.putString("fixture","detached");
            var inspected=storage.getBackpackContents(id).orElseThrow();
            inspected.putString("fixture","defensive copy");
            storage.setBackpackContents(id,new CompoundTag());
            storage.removeBackpackContents(id);
        }
        var unrelated=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:gold_backpack")));
        require(BackpackWrapper.fromStack(unrelated)!=unavailable,"Unrelated backpacks were globally disabled");
        checkNativeStores(p);
        Items249Food.verify(p,!phase.equals("admission"));
        flag("intact_native_bags_and_food_"+phase);
    }
    static void returned(ServerPlayer p) throws Exception {
        checkNativeStores(p);
        for (String name:List.of("carried","worn")) {
            var expected=golden(p,name);
            var restored=name.equals("carried")?p.getInventory().getItem(CARRIED):back(p).getItem(0);
            require(expected.getCompound("stack").equals(restored.save(p.registryAccess())),"Backpack stack/UUID did not return exactly to "+name);
            var handle=BackpackWrapper.fromStack(restored);
            require(handle.getContentsUuid().orElseThrow().equals(expected.getUUID("id")) &&
                    handle.getInventoryHandler().getStackInSlot(4).save(p.registryAccess()).equals(expected.getCompound("inside")),
                    "Restored handle cannot open its exact marked contents");
            int copies=0;
            for (int i=0;i<p.getInventory().getContainerSize();i++)
                if (ItemStack.matches(restored,p.getInventory().getItem(i))) copies++;
            for (int i=0;i<back(p).getContainerSize();i++) if (ItemStack.matches(restored,back(p).getItem(i))) copies++;
            require(copies==1,"Backpack handle duplicated on release");
        }
        require(OriginCustodyPort.decode(Files.readAllBytes(evidence(p,"ordinary"))).equals(p.getInventory().getItem(ORDINARY).save(p.registryAccess())),
                "Ordinary modded item or its components changed");
        Items249Food.verify(p,true);
        flag("restored_filled_bags_exact_slots_uuid_storage_and_food");
    }
}
