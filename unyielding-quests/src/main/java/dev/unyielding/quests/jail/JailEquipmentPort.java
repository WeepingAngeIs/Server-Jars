package dev.unyielding.quests.jail;

import dev.unyielding.quests.jail.AccessoryCustodyPolicy;
import dev.unyielding.quests.jail.JailVanillaCustody;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

/** Native default-provider custody. Unknown providers and effects are refused before mutation. */
public final class JailEquipmentPort implements JailVanillaCustody.InventoryPort {
    static final String STORE = "accessories:inventory_holder", CONTAINERS = "accessories_containers";
    private static final String VERSIONS = "accessories=1.1.0-beta.53+1.21.1;accessories_compat_layer=0.1.12;curios=9.5.1+1.21.1";
    final ServerPlayer player;
    final JailVanillaPort vanilla;
    final UUID transaction;
    final boolean certified;
    record Layout(Map<String,Object> canonical, Map<String,Container> normal, Map<String,Container> cosmetic,
                  List<AccessoryCustodyPolicy.Slot> slots) {}
    record Row(AccessoryCustodyPolicy.Slot id, ItemStack stack) {}
    public JailEquipmentPort(ServerPlayer player, UUID transaction, UUID retired) throws IOException {
        this(player,transaction,retired,false);
    }
    public JailEquipmentPort(ServerPlayer player, UUID transaction, UUID retired,boolean certified) throws IOException {
        this.player=player;this.transaction=transaction;this.vanilla=new JailVanillaPort(player,transaction,retired);
        this.certified=certified;
        JailHumanAudit.runtime(player);
    }
    static Class<?> type(String name) throws IOException {
        try { return Class.forName(name); } catch (ReflectiveOperationException|LinkageError e) { throw new IOException("Missing API " + name,e); }
    }
    static Object call(String api, Object target, String method, Class<?>[] types, Object... args) throws IOException {
        try { return type(api).getMethod(method, types).invoke(target,args); }
        catch (ReflectiveOperationException|RuntimeException e) { throw new IOException("Accessory API failed: " + method,e); }
    }
    private static Object call(String api,Object target,String method) throws IOException { return call(api,target,method,new Class<?>[0]); }
    @Override public UUID owner() { return player.getUUID(); }
    private String policy(){return certified?"native-default-equipment-packed-coins-v2":"native-default-equipment-v1";}
    @Override public boolean online() { return vanilla.online(); }
    private void versions() throws IOException {
        for (String row : VERSIONS.split(";")) {
            String[] part = row.split("=",2);
            if (!ModList.get().getModContainerById(part[0]).map(c -> part[1].equals(c.getModInfo().getVersion().toString())).orElse(false))
                throw new IOException("Changed accessory dependency " + part[0]);
        }
    }
    @SuppressWarnings("unchecked") Layout layout() throws IOException {
        versions(); JailHumanAudit.runtime(player); vanilla.current();
        Object capability = ((Optional<?>)call("io.wispforest.accessories.api.AccessoriesCapability",null,"getOptionally",new Class<?>[]{LivingEntity.class},player)).orElseThrow(() -> new IOException("Missing capability"));
        Map<String,Object> canonical = new TreeMap<>((Map<String,Object>)call("io.wispforest.accessories.api.AccessoriesCapability",capability,"getContainers"));
        Object holder = call("io.wispforest.accessories.api.AccessoriesCapability",capability,"getHolder");
        try {
            var pending=(List<?>)type("io.wispforest.accessories.impl.AccessoriesHolderImpl").getField("invalidStacks").get(holder);
            // Resizing may queue EMPTY entries. They carry no items; nonempty
            // entries remain an unconditional refusal and are never discarded.
            for(Object value:pending) if(!(value instanceof ItemStack stack)||!stack.isEmpty()) throw new IOException("Pending invalid accessory stacks require review: "+pending);
        } catch (ReflectiveOperationException e) { throw new IOException("Cannot inspect invalid stack queue",e); }
        Object curios = ((Optional<?>)call("top.theillusivec4.curios.api.CuriosApi",null,"getCuriosInventory",new Class<?>[]{LivingEntity.class},player)).orElseThrow(() -> new IOException("Missing Curios view"));
        Map<String,Object> aliases = new TreeMap<>();
        Map<?,?> handlers = (Map<?,?>)call("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler",curios,"getCurios");
        Class<?> wrapper = type("io.wispforest.accessories_compat.curios.wrapper.AccessoriesBasedStackHandler");
        for (var entry : handlers.entrySet()) {
            if (!(entry.getKey() instanceof String key) || entry.getValue() == null || entry.getValue().getClass() != wrapper) throw new IOException("Unsupported compatibility handler");
            aliases.put(key,call(wrapper.getName(),entry.getValue(),"container"));
        }
        Map<String,Container> normal = new TreeMap<>(), cosmetic = new TreeMap<>();
        Set<Container> physical=Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String,AccessoryCustodyPolicy.Size> sizes = new TreeMap<>();
        for (var entry : canonical.entrySet()) {
            Container n = (Container)call("io.wispforest.accessories.api.AccessoriesContainer",entry.getValue(),"getAccessories");
            Container c = (Container)call("io.wispforest.accessories.api.AccessoriesContainer",entry.getValue(),"getCosmeticAccessories");
            if(!physical.add(n)||!physical.add(c)) throw new IOException("Overlapping accessory channel storage");
            int size = (int)call("io.wispforest.accessories.api.AccessoriesContainer",entry.getValue(),"getSize");
            if (size != n.getContainerSize() || size != c.getContainerSize()) throw new IOException("Accessory size mismatch");
            normal.put(entry.getKey(),n); cosmetic.put(entry.getKey(),c); sizes.put(entry.getKey(),new AccessoryCustodyPolicy.Size(n.getContainerSize(),c.getContainerSize()));
        }
        return new Layout(canonical,normal,cosmetic,AccessoryCustodyPolicy.slots(canonical,aliases,sizes));
    }
    Container container(Layout layout,AccessoryCustodyPolicy.Slot id) { return (id.cosmetic()?layout.cosmetic():layout.normal()).get(id.name()); }
    Slot menuSlot(Layout layout,AccessoryCustodyPolicy.Slot id) throws IOException {
        try { return (Slot)type("io.wispforest.accessories.menu.AccessoriesInternalSlot").getConstructor(type("io.wispforest.accessories.api.AccessoriesContainer"),boolean.class,int.class,int.class,int.class)
                .newInstance(layout.canonical().get(id.name()),id.cosmetic(),id.index(),0,0); }
        catch (ReflectiveOperationException e) { throw new IOException("Cannot create actual accessory slot",e); }
    }
    void settled(Layout layout) throws IOException {
        for (var id : layout.slots()) {
            Container store = container(layout,id);
            ItemStack previous = (ItemStack)call("io.wispforest.accessories.impl.ExpandedSimpleContainer",store,"getPreviousItem",new Class<?>[]{int.class},id.index());
            if (!ItemStack.matches(previous,store.getItem(id.index()))) throw new IOException("Pending accessory lifecycle callback");
        }
    }
    void flush() throws IOException { JailEquipmentEffects.reconcile(player); call("io.wispforest.accessories.impl.AccessoriesEventHandler",null,"onLivingEntityTick",new Class<?>[]{LivingEntity.class},player); }
    private void supported(ItemStack stack) throws IOException {
        if (stack.isEmpty()) return;
        Object accessory = call("io.wispforest.accessories.api.AccessoriesAPI",null,"getOrDefaultAccessory",new Class<?>[]{ItemStack.class},stack);
        Object expected;try{expected=type("io.wispforest.accessories.api.AccessoriesAPI").getField("DEFAULT").get(null);}catch(ReflectiveOperationException bad){throw new IOException("Default provider unavailable",bad);}
        if(accessory!=expected||!accessory.getClass().getName().equals("io.wispforest.accessories.api.AccessoriesAPI$1")||stack.getCount()!=1)throw new IOException("Unaudited native accessory provider/count");
        if(!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft"))throw new IOException("Accessory item provider not audited");
        for(var component:stack.getComponents())if(net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).getNamespace().equals("accessories"))throw new IOException("Accessory component effects need a separate audit");
        if (EnchantmentHelper.has(stack,EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) throw new IOException("Bound accessory");
    }
    @Override public void validateRemoval() throws IOException {
        if (!online()) throw new IOException("Player offline");
        JailEquipmentEffects.supported(player,certified);
        Layout layout=layout();
        for (int slot=36;slot<=40;slot++) if (EnchantmentHelper.has(player.getInventory().getItem(slot),EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) throw new IOException("Bound vanilla equipment");
        for (Object c : layout.canonical().values()) if (!((Map<?,?>)call("io.wispforest.accessories.api.AccessoriesContainer",c,"getModifiers")).isEmpty()) throw new IOException("Unaudited slot modifiers");
        for (var id : layout.slots()) supported(container(layout,id).getItem(id.index()));
        settled(layout);
    }
    static byte[] encode(CompoundTag tag) throws IOException {
        var output = new ByteArrayOutputStream(); var data = new DataOutputStream(output);
        data.writeByte(10); data.writeUTF(""); JailNbt.payload(tag,data);
        byte[] result=output.toByteArray();
        if (!tag.equals(decode(result))) throw new IOException("Accessory snapshot round-trip failed");
        return result;
    }
    static CompoundTag decode(byte[] bytes) throws IOException { return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)),NbtAccounter.create(16L*1024*1024)); }
    private CompoundTag normalize(CompoundTag holder) throws IOException {
        if (!holder.contains(CONTAINERS,10)) throw new IOException("Unknown holder serialization: " + holder.getAllKeys());
        CompoundTag copy=holder.copy(), stores=copy.getCompound(CONTAINERS);
        for (String key : stores.getAllKeys()) {
            if (!stores.contains(key,10)) throw new IOException("Unknown serialized container");
            CompoundTag store=stores.getCompound(key);
            for (String channel : new String[]{"items","cosmetics"}) {
                if (!store.contains(channel)) store.put(channel,new ListTag());
                if (!store.contains(channel,9)) throw new IOException("Unknown serialized channel");
                ListTag list=(ListTag)store.get(channel);
                if(!list.isEmpty()&&list.getElementType()!=10) throw new IOException("Unknown serialized stack list");
            }
        }
        // Holder-level fields are UI/equip-from-use preferences. Custody does
        // not own or restore them. Keep every serialized container field, slot
        // identity and stack component; native player saves retain current UI.
        CompoundTag equipment=new CompoundTag(); equipment.put(CONTAINERS,stores); return equipment;
    }
    private JailVanillaCustody.View view(JailVanillaCustody.View vanillaView,CompoundTag playerTag) throws IOException {
        CompoundTag attachments=playerTag.getCompound("neoforge:attachments");
        if (!attachments.contains(STORE,10)) throw new IOException("Missing serialized accessory holder: " + attachments.getAllKeys());
        CompoundTag holder=normalize(attachments.getCompound(STORE));
        CompoundTag body=new CompoundTag(); body.putInt("version",2); body.putString("dependencies",VERSIONS);
        body.putString("policy",policy()); body.putString("runtime",JailHumanAudit.runtime(player)); body.putInt("selected",playerTag.getInt("SelectedItemSlot")); body.putByteArray("vanilla",vanillaView.inventory()); body.put("holder",holder);
        boolean empty=vanillaView.empty();
        for (String name : holder.getCompound(CONTAINERS).getAllKeys()) {
            CompoundTag store=holder.getCompound(CONTAINERS).getCompound(name);
            empty &= store.getList("items",10).isEmpty() && store.getList("cosmetics",10).isEmpty();
        }
        return new JailVanillaCustody.View(encode(body),empty,vanillaView.removedBy(),vanillaView.restoredBy());
    }
    @Override public JailVanillaCustody.View current() throws IOException {
        layout(); var v=vanilla.current(); var serialized=player.saveWithoutId(new CompoundTag()); layout(); return view(v,serialized);
    }
    @Override public JailVanillaCustody.View durable() throws IOException {
        try (var input=Files.newInputStream(player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(owner()+".dat"))) {
            return view(vanilla.durable(),NbtIo.readCompressed(input,NbtAccounter.create(16L*1024*1024)));
        }
    }
    @Override public void persistAndVerify() throws IOException {
        settled(layout()); JailEquipmentEffects.settled(player); byte[] neutral=JailEquipmentEffects.neutral(player); var before=current(); vanilla.persistAndVerify(); var saved=durable();
        if(!Arrays.equals(neutral,JailEquipmentEffects.neutral(player)))throw new IOException("Player save changed undeclared equipment effects");
        if (!Arrays.equals(before.inventory(),saved.inventory()) || !Arrays.equals(before.inventory(),current().inventory())
                || !Objects.equals(before.removedBy(),saved.removedBy()) || !Objects.equals(before.restoredBy(),saved.restoredBy())) throw new IOException("Accessory player save mismatch");
        if(transaction.equals(saved.removedBy()))verifyEffects(false,true);else if(transaction.equals(saved.restoredBy()))verifyEffects(true,true);
    }
    private JailEscrowJournal effectJournal(boolean restore)throws IOException {
        return new JailEscrowJournal(JailCustodyService.journalPath(player.server,"integrated-jail-v1/"+transaction+"/"+(restore?"return-effects":"remove-effects")));
    }
    private void beforeCallbacks(boolean restore,byte[] neutral)throws IOException {
        try(var journal=effectJournal(restore)){
            if(journal.read(transaction)!=null||!journal.list().isEmpty())throw new IOException("Existing equipment callback intent; never replay an uncertain mutation");
            journal.begin(transaction,owner(),neutral);journal.transition(transaction,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
        }
    }
    private void verifyEffects(boolean restore,boolean promote)throws IOException {
        if(!transaction.equals(JailIntegrated.activeTransaction(player.server,owner())))return;
        try(var journal=effectJournal(restore)){
            var e=journal.read(transaction);if(e==null)throw new IOException("Equipment callback intent missing; saved item markers alone cannot authorize confirmation");
            if(!e.owner().equals(owner()))throw new IOException("Foreign equipment effect intent");
            if(e.state()==JailEscrowJournal.State.HELD)return;
            if(e.state()!=JailEscrowJournal.State.REMOVAL_STARTED)throw new IOException("Uncertain equipment effect phase");
            settled(layout());JailEquipmentEffects.settled(player);
            if(!Arrays.equals(e.snapshot(),JailEquipmentEffects.neutral(player)))throw new IOException("Equipment callback changed undeclared persisted state; no replay/confirmation");
            if(promote)journal.transition(transaction,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
        }
    }
    @Override public void validateSavedOutcome(UUID id,boolean restored)throws IOException {
        if(!transaction.equals(id))throw new IOException("Foreign callback outcome");
        if(!Arrays.equals(current().inventory(),durable().inventory()))throw new IOException("Equipment outcome is not saved");verifyEffects(restored,true);
    }
    private byte[] emptyBody(byte[] bytes) throws IOException {
        CompoundTag root=decode(bytes), v=decode(root.getByteArray("vanilla")); v.put("items",new ListTag()); root.putByteArray("vanilla",encode(v));
        CompoundTag stores=root.getCompound("holder").getCompound(CONTAINERS);
        for (String name:stores.getAllKeys()) for (String channel:new String[]{"items","cosmetics"}) stores.getCompound(name).put(channel,new ListTag());
        return encode(root);
    }
    private void unchanged(Layout original,byte[] expected) throws IOException {
        Layout observed=layout();
        if (!original.slots().equals(observed.slots()) || !Arrays.equals(expected,current().inventory())) throw new IOException("Accessory callback changed inventory or layout");
        for (String name:original.canonical().keySet()) if (original.canonical().get(name)!=observed.canonical().get(name)
                || original.normal().get(name)!=observed.normal().get(name) || original.cosmetic().get(name)!=observed.cosmetic().get(name)) throw new IOException("Accessory backing replaced");
    }
    private List<Row> rows(byte[] snapshot,Layout layout) throws IOException {
        CompoundTag root=decode(snapshot);
        if (root.getInt("version")!=2 || !VERSIONS.equals(root.getString("dependencies")) || !policy().equals(root.getString("policy"))) throw new IOException("Changed accessory snapshot dependencies");
        if(!root.getCompound("holder").equals(normalize(root.getCompound("holder")))) throw new IOException("Unknown accessory holder format");
        byte[] sourceEmpty=emptyBody(snapshot), currentEmpty=emptyBody(current().inventory());
        if (!Arrays.equals(sourceEmpty,currentEmpty)) {
            var differences=new ArrayList<String>(); differences(decode(sourceEmpty),decode(currentEmpty),"",differences);
            throw new IOException("Accessory slots or holder state changed: "+differences.stream().limit(12).toList());
        }
        List<Row> rows=new ArrayList<>(); Set<AccessoryCustodyPolicy.Slot> used=new HashSet<>();
        CompoundTag stores=root.getCompound("holder").getCompound(CONTAINERS);
        for (String name:stores.getAllKeys()) for (boolean cosmetic:new boolean[]{false,true}) {
            for (Tag raw:stores.getCompound(name).getList(cosmetic?"cosmetics":"items",10)) {
                CompoundTag row=(CompoundTag)raw; int index=row.getInt("Slot");
                var id=new AccessoryCustodyPolicy.Slot(name,cosmetic,index);
                if (!layout.slots().contains(id) || !used.add(id)) throw new IOException("Missing or duplicate accessory destination");
                ItemStack stack=ItemStack.parseOptional(player.registryAccess(),row); supported(stack);
                if (stack.isEmpty()) throw new IOException("Unreadable accessory stack");
                CompoundTag encoded=new CompoundTag(); encoded.putInt("Slot",index);
                if (!row.equals(stack.save(player.registryAccess(),encoded))) throw new IOException("Accessory component loss on decode");
                rows.add(new Row(id,stack));
            }
        }
        return rows;
    }
    private byte[] changedSlot(byte[] body,Row row,boolean remove) throws IOException {
        CompoundTag root=decode(body), store=root.getCompound("holder").getCompound(CONTAINERS).getCompound(row.id().name());
        String channel=row.id().cosmetic()?"cosmetics":"items";
        List<CompoundTag> entries=new ArrayList<>();
        for (Tag tag:store.getList(channel,10)) if (((CompoundTag)tag).getInt("Slot")!=row.id().index()) entries.add(((CompoundTag)tag).copy());
        if (!remove) { CompoundTag tag=new CompoundTag(); tag.putInt("Slot",row.id().index()); entries.add((CompoundTag)row.stack().save(player.registryAccess(),tag)); }
        entries.sort(Comparator.comparingInt(tag -> tag.getInt("Slot"))); ListTag list=new ListTag(); list.addAll(entries); store.put(channel,list);
        return encode(root);
    }
    private static void differences(Tag source,Tag observed,String path,List<String> result) {
        if(Objects.equals(source,observed)) return;
        if(source instanceof CompoundTag a && observed instanceof CompoundTag b) {
            Set<String> keys=new TreeSet<>(a.getAllKeys()); keys.addAll(b.getAllKeys());
            for(String key:keys) differences(a.get(key),b.get(key),path+"/"+key,result);
        } else result.add(path+": "+source+" -> "+observed);
    }
    @Override public void clearAndMark(UUID id) throws IOException {
        validateRemoval(); byte[] neutral=JailEquipmentEffects.neutral(player); Layout layout=layout(); byte[] before=current().inventory(); List<Row> planned=rows(before,layout);beforeCallbacks(false,neutral);
        // Actual UI permission semantics: cosmetics do not invoke canUnequip;
        // normal slots do. No binding bypass is permitted by this fixture.
        for (Row row:planned) {
            boolean allowed=menuSlot(layout,row.id()).mayPickup(player); unchanged(layout,before);
            if (!allowed) throw new IOException("Accessory unequip denied");
        }
        unchanged(layout,before);
        byte[] expected=before;
        for (Row row:planned) {
            unchanged(layout,expected);
            if (!ItemStack.matches(row.stack(),container(layout,row.id()).getItem(row.id().index()))) throw new IOException("Accessory source changed");
            ItemStack removed=menuSlot(layout,row.id()).remove(row.stack().getCount());
            if (!ItemStack.matches(row.stack(),removed)) throw new IOException("Partial accessory extraction");
            expected=changedSlot(expected,row,true); unchanged(layout,expected);
        }
        vanilla.clearAndMark(id); flush(); settled(layout());
        if(!Arrays.equals(neutral,JailEquipmentEffects.neutral(player)))throw new IOException("Unequip callback changed undeclared native state");
        if (!Arrays.equals(emptyBody(before),current().inventory())) throw new IOException("Unequip lifecycle changed custody state");
    }
    @Override public JailVanillaCustody.View planRestore(byte[] snapshot,UUID id) throws IOException {
        Layout layout=layout(); settled(layout);
        if (!current().empty() || !id.equals(current().removedBy())) throw new IOException("Accessory destination occupied or changed");
        rows(snapshot,layout);
        vanilla.planRestore(decode(snapshot).getByteArray("vanilla"),id);
        return new JailVanillaCustody.View(snapshot,false,null,id);
    }
    @Override public void restoreExact(byte[] snapshot,UUID id) throws IOException {
        planRestore(snapshot,id); byte[] neutral=JailEquipmentEffects.neutral(player); Layout layout=layout(); byte[] before=current().inventory(); List<Row> rows=rows(snapshot,layout);beforeCallbacks(true,neutral);
        byte[] expected=before;
        for (Row row:rows) {
            unchanged(layout,expected);
            boolean allowed=menuSlot(layout,row.id()).mayPlace(row.stack().copy()); unchanged(layout,before);
            if (!allowed) throw new IOException("Accessory equip denied");
        }
        unchanged(layout,before);
        for (Row row:rows) {
            unchanged(layout,expected);
            Layout now=layout();
            if (!layout.slots().equals(now.slots()) || layout.canonical().get(row.id().name())!=now.canonical().get(row.id().name())
                    || !container(now,row.id()).getItem(row.id().index()).isEmpty()) throw new IOException("Accessory destination changed during insertion");
            menuSlot(now,row.id()).set(row.stack().copy());
            expected=changedSlot(expected,row,false); unchanged(layout,expected);
        }
        vanilla.restoreExact(decode(snapshot).getByteArray("vanilla"),id); flush(); settled(layout());
        if(!Arrays.equals(neutral,JailEquipmentEffects.neutral(player)))throw new IOException("Equip callback changed undeclared native state");
        if (!Arrays.equals(snapshot,current().inventory())) throw new IOException("Equip lifecycle changed restored state");
    }
}
