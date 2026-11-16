package dev.unyielding.quests.jail;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/** Exact-slot adapter. Its caller must audit providers and own the quarantine. */
public final class JailVanillaPort implements JailVanillaCustody.InventoryPort {
    public static final String REMOVED="uq_integrated_custody",RESTORED="uq_integrated_returned";
    final ServerPlayer player;private final UUID transaction,retired;
    public JailVanillaPort(ServerPlayer player,UUID transaction,UUID verifiedRetiredReceipt){this.player=player;this.transaction=transaction;this.retired=verifiedRetiredReceipt;}
    private void guard()throws IOException {if(!player.server.isSameThread()||player.containerMenu!=player.inventoryMenu||!player.containerMenu.getCarried().isEmpty())throw new IOException("Transient inventory/menu; custody refused");for(int i=0;i<5;i++)if(player.inventoryMenu.getSlot(i).hasItem())throw new IOException("Crafting/cursor inventory is not empty");}
    public UUID owner(){return player.getUUID();}public boolean online(){return player.server.isSameThread()&&player.server.getPlayerList().getPlayer(owner())==player&&!player.hasDisconnected()&&player.isAlive();}
    private JailVanillaCustody.View view(ListTag items,CompoundTag persistent)throws IOException {
        CompoundTag body=new CompoundTag();body.putInt("version",1);body.putInt("vanillaSlots",player.getInventory().getContainerSize());body.put("items",items);
        UUID removed=persistent.hasUUID(REMOVED)?persistent.getUUID(REMOVED):null,restored=persistent.hasUUID(RESTORED)?persistent.getUUID(RESTORED):null;
        if(restored!=null&&!restored.equals(transaction)){if(!restored.equals(retired))throw new IOException("Foreign/unverified old return receipt");restored=null;}
        if(removed!=null&&!removed.equals(transaction))throw new IOException("Another custody transaction owns this player");
        return new JailVanillaCustody.View(JailNbt.encode(body),items.isEmpty(),removed,restored);
    }
    public JailVanillaCustody.View current()throws IOException {guard();return view(player.getInventory().save(new ListTag()),player.getPersistentData());}
    Path file(){return player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner()+".dat");}
    CompoundTag savedPlayer()throws IOException {try(var input=Files.newInputStream(file())){CompoundTag tag=NbtIo.readCompressed(input,NbtAccounter.create(16L*1024*1024));if(!tag.hasUUID("UUID")||!tag.getUUID("UUID").equals(owner())||!tag.contains("Inventory",9))throw new IOException("Wrong or malformed player save");return tag;}}
    public JailVanillaCustody.View durable()throws IOException {guard();CompoundTag tag=savedPlayer();return view(tag.getList("Inventory",10),tag.getCompound("NeoForgeData"));}
    static void force(Path file)throws IOException {try(var c=FileChannel.open(file,StandardOpenOption.WRITE)){c.force(true);}try(var c=FileChannel.open(file.getParent(),StandardOpenOption.READ)){c.force(true);}}
    public void persistAndVerify()throws IOException {guard();durable();var before=current();player.server.getPlayerList().saveAll();force(file());var saved=durable();if(!same(before,saved)||!same(before,current()))throw new IOException("Player inventory/receipt save did not verify");}
    private static boolean same(JailVanillaCustody.View a,JailVanillaCustody.View b){return Arrays.equals(a.inventory(),b.inventory())&&a.empty()==b.empty()&&Objects.equals(a.removedBy(),b.removedBy())&&Objects.equals(a.restoredBy(),b.restoredBy());}
    public void clearAndMark(UUID id)throws IOException {guard();if(!id.equals(transaction))throw new IOException("Foreign removal ID");player.getInventory().clearContent();player.getPersistentData().remove(RESTORED);player.getPersistentData().putUUID(REMOVED,id);}
    private net.minecraft.world.entity.player.Inventory stage(byte[] snapshot,UUID id)throws IOException {
        guard();if(!id.equals(transaction)||!online()||!player.getInventory().isEmpty()||!id.equals(current().removedBy())||current().restoredBy()!=null)throw new IOException("Return destination occupied, offline or foreign");
        CompoundTag root=JailNbt.decode(snapshot);if(root.getInt("version")!=1||root.getInt("vanillaSlots")!=player.getInventory().getContainerSize()||!root.contains("items",9))throw new IOException("Unsupported exact-slot snapshot");
        var staged=new net.minecraft.world.entity.player.Inventory(player);staged.load(root.getList("items",10));if(staged.isEmpty()||!Arrays.equals(view(staged.save(new ListTag()),new CompoundTag()).inventory(),snapshot))throw new IOException("Snapshot does not round-trip source slots/components");
        for(int i=0;i<staged.getContainerSize();i++){ItemStack stack=staged.getItem(i);if(!stack.isEmpty()&&(stack.getCount()<1||stack.getCount()>stack.getMaxStackSize()))throw new IOException("Return overflow; no drops or merging allowed");}return staged;
    }
    public JailVanillaCustody.View planRestore(byte[] snapshot,UUID id)throws IOException {var staged=stage(snapshot,id);CompoundTag receipt=new CompoundTag();receipt.putUUID(RESTORED,id);return view(staged.save(new ListTag()),receipt);}
    public void restoreExact(byte[] snapshot,UUID id)throws IOException {var staged=stage(snapshot,id);for(int i=0;i<staged.getContainerSize();i++)player.getInventory().setItem(i,staged.getItem(i).copy());player.getPersistentData().remove(REMOVED);player.getPersistentData().putUUID(RESTORED,id);player.getInventory().setChanged();}
}
