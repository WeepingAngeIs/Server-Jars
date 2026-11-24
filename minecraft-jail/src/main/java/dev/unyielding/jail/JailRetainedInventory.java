package dev.unyielding.jail;

import java.io.IOException;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;

/** Read-only inventory port. This contract has no removal, staging or restoration operation. */
final class JailRetainedInventory implements JailVanillaCustody.InventoryPort {
    private static final Set<String> INERT_ITEMS=Set.of("minecraft:diamond","minecraft:amethyst_shard","minecraft:emerald",
            "minecraft:gold_ingot","minecraft:iron_ingot","minecraft:copper_ingot","minecraft:quartz",
            "minecraft:netherite_ingot","minecraft:coal","minecraft:charcoal","minecraft:redstone","minecraft:lapis_lazuli","minecraft:nether_star");
    private static final Set<String> INERT_PATCH=Set.of("minecraft:custom_name","minecraft:lore","minecraft:custom_data");
    final ServerPlayer player;
    final UUID transaction;
    private final JailEquipmentPort observed;

    JailRetainedInventory(ServerPlayer player, UUID transaction, UUID retired, CompoundTag expectedBags) throws IOException {
        this.player=player;this.transaction=transaction;
        observed=new JailEquipmentPort(player,transaction,JailIntegrated.retiredInventory(player,transaction,retired),true,true);
        if(expectedBags!=null&&!expectedBags.isEmpty())throw new IOException("Retained inventory does not support external backpack backing claims");
        observed.bindBackpacks(new CompoundTag());
    }
    CompoundTag bagDigests(){return new CompoundTag();}
    public UUID owner(){return player.getUUID();}
    public boolean online(){return observed.online();}
    public JailVanillaCustody.View current()throws IOException{return observed.current();}
    public JailVanillaCustody.View durable()throws IOException{return observed.durable();}
    public void persistAndVerify()throws IOException{observed.persistAndVerify();verifyBags();}
    public void clearAndMark(UUID id)throws IOException{throw new IOException("Retained inventory cannot be confiscated");}
    public JailVanillaCustody.View planRestore(byte[] data,UUID id)throws IOException{throw new IOException("Retained inventory has no return staging");}
    public void restoreExact(byte[] data,UUID id)throws IOException{throw new IOException("Retained inventory cannot be written by Jail");}

    void validateAdmission()throws IOException{
        if(!online()||player.isUsingItem()||player.isPassenger()||player.isVehicle()
                ||!player.getShoulderEntityLeft().isEmpty()||!player.getShoulderEntityRight().isEmpty())
            throw new IOException("Retained inventory requires an idle, online player");
        if(!player.getMainHandItem().isEmpty()||!player.getOffhandItem().isEmpty())
            throw new IOException("Retained inventory currently requires empty hands");
        for(var stack:player.getArmorSlots())if(!stack.isEmpty())
            throw new IOException("Retained equipped armor requires separate death-hook evidence");
        var layout=observed.layout();
        for(var containers:List.of(layout.normal(),layout.cosmetic()))for(var container:containers.values())
            for(int i=0;i<container.getContainerSize();i++)if(!container.getItem(i).isEmpty())
                throw new IOException("Retained accessories require separate native death evidence");
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            ItemStack stack=player.getInventory().getItem(i);if(stack.isEmpty())continue;
            String item=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            boolean accepted=stack.getItem().getClass()==Item.class && INERT_ITEMS.contains(item)&&inertPatch(stack);
            if(item.equals("lightmanscurrency:coin_copper")) {
                accepted=stack.getItem().getClass()==Item.class && i<36 && i!=player.getInventory().selected&&inertPatch(stack);
            }
            if(JailPackedItems.coin(player,i,stack))accepted=inertPatch(stack);
            if(item.equals("anyastorage:backpack"))accepted=storageBag(stack);
            if(JailPackedWallet.accepts(player,i,stack))accepted=true;
            if(!accepted||stack.getCount()<1||stack.getCount()>stack.getMaxStackSize())
                throw new IOException("Unreviewed retained item provider in slot "+i+": "+item);
        }
        JailEquipmentEffects.settled(player);JailEquipmentEffects.neutral(player);
        var snapshot=current();
        if(snapshot.removedBy()!=null||snapshot.restoredBy()!=null)
            throw new IOException("Existing item custody receipt requires review");
    }
    private boolean inertPatch(ItemStack stack){
        if(((CompoundTag)stack.save(player.registryAccess())).contains("neoforge:attachments"))return false;
        for(var component:stack.getComponentsPatch().entrySet()){
            var key=BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.getKey());
            if(key==null||!INERT_PATCH.contains(key.toString()))return false;
        }
        return true;
    }
    private boolean storageBag(ItemStack stack)throws IOException{
        if(stack.getCount()!=1||!stack.getItem().getClass().getName().equals("dev.anya.storage.StorageItem")
                ||!JailOrdinaryItem.accepts(player,stack))return false;
        for(var patch:stack.getComponentsPatch().entrySet()){
            var key=BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(patch.getKey());
            if(key==null||!Set.of("minecraft:custom_name","minecraft:lore","minecraft:custom_data","minecraft:container").contains(key.toString()))return false;
        }
        CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!Set.of("BagId","Tier","TierName","Capacity","UpgradeSlots","InstalledUpgrades").containsAll(data.getAllKeys())
                ||!data.hasUUID("BagId")||!data.contains("Tier",Tag.TAG_STRING)||!data.contains("Capacity",Tag.TAG_INT)
                ||!data.getString("Tier").matches("[a-z][a-z0-9_-]{0,63}")||!data.contains("TierName",Tag.TAG_STRING)
                ||!data.contains("UpgradeSlots",Tag.TAG_INT)||data.getInt("UpgradeSlots")<0||data.getInt("UpgradeSlots")>32
                ||data.getInt("Capacity")<9||data.getInt("Capacity")>216)return false;
        if(data.contains("InstalledUpgrades")&&(!(data.get("InstalledUpgrades") instanceof net.minecraft.nbt.ListTag list)
                ||!list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND))return false;
        var installed=data.getList("InstalledUpgrades",Tag.TAG_COMPOUND);if(installed.size()>1||installed.size()>data.getInt("UpgradeSlots"))return false;
        for(Tag raw:installed){
            CompoundTag upgrade=(CompoundTag)raw;
            if(!upgrade.getAllKeys().equals(Set.of("Id","Stack"))||!upgrade.getString("Id").equals("feeding"))return false;
            ItemStack module=ItemStack.parseOptional(player.registryAccess(),upgrade.getCompound("Stack"));
            if(module.getCount()!=1||module.getItem().getClass()!=Item.class
                    ||!BuiltInRegistries.ITEM.getKey(module.getItem()).toString().equals("anyastorage:feeding_upgrade")||!inertPatch(module)
                    ||!upgrade.getCompound("Stack").equals(module.save(player.registryAccess())))return false;
        }
        var contents=stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY);
        if(contents.getSlots()>data.getInt("Capacity"))return false;
        for(ItemStack item:contents.stream().toList())if(!item.isEmpty()&&(item.getItem().getClass()!=Item.class
                ||item.getCount()<1||item.getCount()>item.getMaxStackSize()
                ||!INERT_ITEMS.contains(BuiltInRegistries.ITEM.getKey(item.getItem()).toString())||!inertPatch(item)))return false;
        UUID id=data.getUUID("BagId");int copies=0;
        for(ServerPlayer owner:player.server.getPlayerList().getPlayers())for(int slot=0;slot<owner.getInventory().getContainerSize();slot++){
            ItemStack candidate=owner.getInventory().getItem(slot);
            if(!BuiltInRegistries.ITEM.getKey(candidate.getItem()).toString().equals("anyastorage:backpack"))continue;
            CompoundTag identity=candidate.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
            if(identity.hasUUID("BagId")&&id.equals(identity.getUUID("BagId"))){if(owner!=player||++copies!=1)return false;}
        }
        return copies==1;
    }
    void verifyBags()throws IOException{
        for(int i=0;i<player.getInventory().getContainerSize();i++){
            var stack=player.getInventory().getItem(i);
            if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("anyastorage:backpack")&&!storageBag(stack))
                throw new IOException("Retained backpack shape or identity changed; no repair or copying is authorized");
        }
    }
    byte[] digest()throws IOException{return JailOriginTransaction.hash(current().inventory());}
    void verify(byte[] expected,boolean saved)throws IOException{
        var live=current();
        if(live.removedBy()!=null||live.restoredBy()!=null||expected.length!=32
                ||!Arrays.equals(expected,JailOriginTransaction.hash(live.inventory())))
            throw new IOException("Retained inventory or custody receipt changed; no restoration is authorized");
        if(saved){var disk=durable();if(disk.removedBy()!=null||disk.restoredBy()!=null
                ||!Arrays.equals(live.inventory(),disk.inventory()))throw new IOException("Retained inventory differs from its native save");}
        verifyBags();
    }
}
