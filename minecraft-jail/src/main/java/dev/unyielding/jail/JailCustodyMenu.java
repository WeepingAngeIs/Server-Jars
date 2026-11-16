package dev.unyielding.jail;

import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;

/** Disposable preview copies, never a persistent or withdrawable item store. */
public final class JailCustodyMenu extends ChestMenu {
    public static final int PREVIOUS=45,NEXT=46,REVIEW=49,CONFIRM=50,RESTORE=53;
    public record Preview(ItemStack icon,String source,int count){public Preview{icon=icon.copy();}@Override public ItemStack icon(){return icon.copy();}}
    private final JailCustodyLedger.Binding selected;private final List<Preview> rows;private final Display display;private int page;
    /** Read-only even to standard slot/container integrations, not just mouse clicks. */
    private static final class Display implements Container {
        private final net.minecraft.core.NonNullList<ItemStack> icons=net.minecraft.core.NonNullList.withSize(54,ItemStack.EMPTY);
        void draw(int index,ItemStack stack){icons.set(index,stack.copy());}
        public int getContainerSize(){return 54;}public boolean isEmpty(){return icons.stream().allMatch(ItemStack::isEmpty);}
        public ItemStack getItem(int index){return index>=0&&index<54?icons.get(index).copy():ItemStack.EMPTY;}
        public ItemStack removeItem(int index,int count){return ItemStack.EMPTY;}public ItemStack removeItemNoUpdate(int index){return ItemStack.EMPTY;}
        public void setItem(int index,ItemStack stack){}public void setChanged(){}public void clearContent(){}
        public boolean stillValid(Player player){return true;}public boolean canPlaceItem(int index,ItemStack stack){return false;}
        public boolean canTakeItem(Container target,int index,ItemStack stack){return false;}
    }
    public JailCustodyMenu(int id,Inventory inventory,JailCustodyLedger.Binding selected,List<Preview> rows){this(id,inventory,selected,rows,new Display());}
    private JailCustodyMenu(int id,Inventory inventory,JailCustodyLedger.Binding selected,List<Preview> rows,Display display){
        super(MenuType.GENERIC_9x6,id,inventory,display,6);this.selected=selected;this.rows=List.copyOf(rows);this.display=display;
        for(int i=0;i<slots.size();i++){Slot old=slots.get(i);Slot readOnly=new Slot(old.container,old.getSlotIndex(),old.x,old.y){
            @Override public boolean mayPickup(Player player){return false;}@Override public boolean mayPlace(ItemStack stack){return false;}
            @Override public ItemStack getItem(){return super.getItem().copy();}@Override public ItemStack remove(int amount){return ItemStack.EMPTY;}
            @Override public void set(ItemStack stack){}@Override public void setByPlayer(ItemStack stack,ItemStack before){}
        };readOnly.index=i;slots.set(i,readOnly);}
        paint();
    }
    public UUID transaction(){return selected.id();}public UUID owner(){return selected.owner();}public int page(){return page;}public int pages(){return Math.max(1,(rows.size()+44)/45);}
    private void icon(int slot,Item item,String label){ItemStack stack=new ItemStack(item);stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal(label));display.draw(slot,stack);}
    private void paint(){for(int i=0;i<54;i++)display.draw(i,ItemStack.EMPTY);for(int i=0;i<45&&page*45+i<rows.size();i++){Preview p=rows.get(page*45+i);ItemStack stack=p.icon();stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal(stack.getHoverName().getString()+" · "+p.count()+" · "+p.source()+" · preview only"));display.draw(i,stack);}
        icon(PREVIOUS,Items.ARROW,"Previous page");icon(NEXT,Items.ARROW,"Next page");icon(47,Items.PAPER,"Page "+(page+1)+" / "+pages()+" · "+rows.size()+" recorded stacks");icon(48,Items.IRON_BARS,selected.state()+" · "+selected.reason());icon(REVIEW,Items.SPYGLASS,"Verify adapter and review this record");icon(CONFIRM,Items.CLOCK,"Confirm an exact saved phase; never replay");icon(51,Items.NAME_TAG,"Prisoner "+selected.owner());icon(52,Items.WRITABLE_BOOK,"Transaction "+selected.id());icon(RESTORE,Items.CHEST,"Return through verified recovery only");
    }
    @Override public boolean stillValid(Player player){return player instanceof ServerPlayer server&&JailCustodyService.valid(server,selected);}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return false;}
    @Override public boolean clickMenuButton(Player player,int button){return false;}
    @Override public void clicked(int slot,int button,ClickType type,Player player){
        if(!(player instanceof ServerPlayer server))return;
        if(server.containerMenu!=this||!stillValid(player)){server.closeContainer();return;}
        if(type==ClickType.PICKUP&&button==0){
            if(slot==PREVIOUS||slot==NEXT){page=Math.max(0,Math.min(pages()-1,page+(slot==NEXT?1:-1)));paint();}
            else if(slot==REVIEW||slot==CONFIRM||slot==RESTORE){JailCustodyService.menuAction(server,this,selected,slot==REVIEW?"review":slot==CONFIRM?"confirm":"restore");return;}
        }
        // Resynchronize all prediction, including clone/throw/drag/hotbar/shift
        // attempts. No superclass click logic can touch either inventory.
        sendAllDataToRemote();
    }
    public static List<Preview> previews(ServerPlayer viewer,byte[] bytes)throws IOException{
        CompoundTag body=decode(bytes);List<Preview> rows=new ArrayList<>();
        if(body.getInt("version")==1){vanilla(viewer,body,rows);}
        else if(body.getInt("version")==2&&body.contains("vanilla",7)&&body.contains("holder",10)){
            vanilla(viewer,decode(body.getByteArray("vanilla")),rows);CompoundTag stores=body.getCompound("holder").getCompound("accessories_containers");
            for(String name:new TreeSet<>(stores.getAllKeys()))for(String channel:List.of("items","cosmetics"))for(Tag tag:stores.getCompound(name).getList(channel,10)){CompoundTag item=(CompoundTag)tag;add(viewer,item,channel+":"+name+":"+item.getInt("Slot"),rows);}
        }else throw new IOException("Unsupported display snapshot; escrow retained, no adapter assumed");
        return List.copyOf(rows);
    }
    private static void vanilla(ServerPlayer viewer,CompoundTag body,List<Preview> rows)throws IOException{if(body.getInt("version")!=1||!body.contains("items",9))throw new IOException("Unknown vanilla display snapshot");for(Tag tag:body.getList("items",10)){CompoundTag item=(CompoundTag)tag;add(viewer,item,"vanilla:"+Byte.toUnsignedInt(item.getByte("Slot")),rows);}}
    private static void add(ServerPlayer viewer,CompoundTag tag,String source,List<Preview> rows)throws IOException{
        if(rows.size()>=2048)throw new IOException("Display capacity exceeded; records retained");
        ItemStack original=ItemStack.parse(viewer.registryAccess(),tag).orElseThrow(()->new IOException("Unknown recorded item; records retained"));if(original.isEmpty())return;
        // A simple registry icon, not a copy of the authoritative components or
        // nested contents. The original byte snapshot is never rewritten.
        ItemStack icon=new ItemStack(original.getItem());String name=original.getHoverName().getString().replaceAll("[\\p{Cntrl}]"," ");icon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal(name.substring(0,Math.min(120,name.length()))));rows.add(new Preview(icon,source,original.getCount()));
    }
    private static CompoundTag decode(byte[] bytes)throws IOException{return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.create(4L*1024*1024));}
}
