package dev.unyielding.jail;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/** A chest stores the physical handle. Sophisticated owns the same, unmodified SavedData record
 * before, during and after custody. Its complete bytes are bound to the exact-slot journal. */
public final class JailBackpacks {
    static final String RECORDS = "backpack_records";
    static final String STORAGE = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage";
    static final String COMPONENT = "sophisticatedcore:storage_uuid";
    private static final String ITEM = "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem";
    private static final Set<String> TIERS = Set.of("backpack", "copper_backpack", "iron_backpack",
            "gold_backpack", "diamond_backpack", "netherite_backpack");

    private JailBackpacks() {}

    static boolean isBag(ItemStack stack) {
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals("sophisticatedbackpacks") && TIERS.contains(key.getPath());
    }

    public static UUID identity(ItemStack stack) {
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse(COMPONENT));
        return type != null && stack.get(type) instanceof UUID id ? id : null;
    }

    static boolean accepts(ServerPlayer player, ItemStack stack) throws IOException {
        if (!isBag(stack)) return false;
        if (!JailHumanAudit.hasItems249Contract(player) || !stack.getItem().getClass().getName().equals(ITEM)
                || stack.getCount() != 1) throw new IOException("Unreviewed backpack provider, runtime or count");
        var encoded = (CompoundTag) stack.save(player.registryAccess());
        if (!Set.of("id","count","components").containsAll(encoded.getAllKeys()))
            throw new IOException("Backpack has unknown attached state");
        for (var component : stack.getComponents()) {
            String key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).toString();
            if (linked(key)) throw new IOException("Linked backpacks share an external inventory; custody refused");
        }
        for (String key : encoded.getCompound("components").getAllKeys()) {
            if (!Set.of(COMPONENT, "sophisticatedcore:main_color", "sophisticatedcore:accent_color",
                    "sophisticatedcore:number_of_inventory_slots", "sophisticatedcore:number_of_upgrade_slots",
                    "sophisticatedcore:render_info_tag", "sophisticatedcore:open_tab_id", "sophisticatedcore:sort_by",
                    "minecraft:custom_name", "minecraft:lore", "minecraft:enchantments",
                    "minecraft:repair_cost").contains(key)) throw new IOException("Unreviewed backpack component: "+key);
        }
        ItemStack parsed = ItemStack.parseOptional(player.registryAccess(),encoded.copy());
        if (!ItemStack.matches(parsed,stack) || !encoded.equals(parsed.save(player.registryAccess())))
            throw new IOException("Complete backpack stack does not round-trip");
        return true;
    }

    private static boolean linked(String key) {
        return key.startsWith("sophisticatedcore:linked_storage_") || key.startsWith("sophisticatedcore:ender_link");
    }

    static void selfContained(Tag value) throws IOException {
        if (value instanceof CompoundTag n) {
            if (n.getString("id").startsWith("sophisticatedbackpacks:") &&
                    TIERS.contains(n.getString("id").substring("sophisticatedbackpacks:".length())))
                throw new IOException("Nested backpack needs a separately bound backing record");
            for (String key : n.getAllKeys()) {
                if (key.equals(COMPONENT) || linked(key)) throw new IOException("Nested or linked storage needs separate custody");
                selfContained(n.get(key));
            }
        } else if (value instanceof ListTag list) {
            for (Tag child : list) selfContained(child);
        }
    }

    static CompoundTag live(ServerPlayer player, Set<UUID> ids) throws IOException {
        CompoundTag result = new CompoundTag();
        if (ids.isEmpty()) return result;
        Object storage = JailHumanAudit.call(STORAGE,null,"get",new Class<?>[]{ServerLevel.class},player.server.overworld());
        for (UUID id : ids) {
            Object row = JailHumanAudit.call(STORAGE,storage,"getBackpackContents",new Class<?>[]{UUID.class},id);
            if (!(row instanceof Optional<?> option) || !(option.orElse(null) instanceof CompoundTag record))
                throw new IOException("Missing native backpack backing record for "+id);
            result.put(id.toString(),record.copy());
        }
        return result;
    }

    static CompoundTag fromDisk(CompoundTag data, Set<UUID> ids) throws IOException {
        if (!data.contains("backpackContents",Tag.TAG_LIST)) throw new IOException("Backpack save has no contents table");
        CompoundTag result = new CompoundTag();
        for (Tag raw : data.getList("backpackContents",Tag.TAG_COMPOUND)) {
            var row = (CompoundTag)raw;
            if (!row.hasUUID("uuid") || !row.contains("contents",Tag.TAG_COMPOUND)) throw new IOException("Malformed native backpack save");
            UUID id = row.getUUID("uuid");
            if (ids.contains(id)) {
                if (result.contains(id.toString())) throw new IOException("Duplicate saved backpack identity");
                result.put(id.toString(),row.getCompound("contents"));
            }
        }
        if (result.size()!=ids.size()) throw new IOException("Backpack backing is absent from the saved provider data");
        return result;
    }

    private static Path file(ServerPlayer p) {
        return p.server.getWorldPath(LevelResource.ROOT).resolve("data/sophisticatedbackpacks.dat");
    }

    static void save(ServerPlayer p) throws IOException {
        p.server.overworld().getDataStorage().save();
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        JailVanillaPort.force(file(p));
    }

    static CompoundTag disk(ServerPlayer p,Set<UUID> ids) throws IOException {
        if (ids.isEmpty()) return new CompoundTag();
        try (var in = Files.newInputStream(file(p))) {
            return fromDisk(NbtIo.readCompressed(in,NbtAccounter.create(256L*1024*1024)).getCompound("data"),ids);
        }
    }

    static CompoundTag digests(CompoundTag records) throws IOException {
        CompoundTag result = new CompoundTag();
        for (String id : records.getAllKeys()) {
            if (!records.contains(id,Tag.TAG_COMPOUND)) throw new IOException("Invalid backpack evidence");
            result.putByteArray(id,JailOriginTransaction.hash(JailNbt.encode(records.getCompound(id))));
        }
        return result;
    }

    static Set<UUID> ids(CompoundTag hashes) throws IOException {
        Set<UUID> ids = new HashSet<>();
        try {
            for (String key : hashes.getAllKeys()) {
                UUID id=UUID.fromString(key);
                if (!id.toString().equals(key) || hashes.getByteArray(key).length!=32 || !ids.add(id))
                    throw new IOException("Invalid backpack identity in custody record");
            }
        } catch (IllegalArgumentException bad) {throw new IOException("Invalid backpack identity",bad);}
        return ids;
    }

    static void verifyCopies(ServerPlayer subject, Set<UUID> ids, boolean carried) throws IOException {
        if (ids.isEmpty()) return;
        Map<UUID,Integer> owned = new HashMap<>();
        for (ServerPlayer p : subject.server.getPlayerList().getPlayers()) {
            List<ItemStack> stacks = new ArrayList<>();
            var inv=p.getInventory();
            for (int i=0;i<inv.getContainerSize();i++) stacks.add(inv.getItem(i));
            Object cap=((Optional<?>)JailHumanAudit.call("io.wispforest.accessories.api.AccessoriesCapability",null,
                    "getOptionally",new Class<?>[]{LivingEntity.class},p)).orElseThrow(()->new IOException("Missing Accessories capability"));
            var stores=(Map<?,?>)JailHumanAudit.call("io.wispforest.accessories.api.AccessoriesCapability",cap,"getContainers");
            for (Object store : stores.values()) for (String method : List.of("getAccessories","getCosmeticAccessories")) {
                Container items=(Container)JailHumanAudit.call("io.wispforest.accessories.api.AccessoriesContainer",store,method);
                for(int i=0;i<items.getContainerSize();i++) stacks.add(items.getItem(i));
            }
            if (p.containerMenu!=p.inventoryMenu) {
                String type=p.containerMenu.getClass().getName();
                if (type.startsWith("net.p3pp3rf1y.sophisticatedbackpacks.common.gui.")) {
                    Object wrapper=JailHumanAudit.call(type,p.containerMenu,"getStorageWrapper");
                    var uuid=(Optional<?>)JailHumanAudit.call("net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper",wrapper,"getContentsUuid");
                    if (uuid.isPresent() && ids.contains(uuid.get())) throw new IOException("Someone still has this backpack open");
                }
                for (var slot : p.containerMenu.slots) if (slot.container!=inv && ids.contains(identity(slot.getItem())))
                    throw new IOException("A second backpack handle is visible in an open menu");
            }
            stacks.add(p.containerMenu.getCarried());
            for (ItemStack stack : stacks) {
                UUID id=identity(stack);
                if(id==null || !ids.contains(id))continue;
                if(p!=subject || !carried || owned.merge(id,1,Integer::sum)!=1)
                    throw new IOException("A second or foreign backpack handle is present");
            }
        }
        for (var level : subject.server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity item && ids.contains(identity(item.getItem())))
                throw new IOException("A duplicate backpack is on the ground");
        }
        if (carried && !owned.keySet().equals(ids)) throw new IOException("Backpack source handle changed");
    }

    /** Guard the provider's normal wrapper and UUID recovery commands while the handle is in custody. */
    public static boolean locked(UUID id) {
        return BackpackClaims.get(id)!=null;
    }

    public static Object unavailableWrapper() {
        try {return Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper$Noop")
                .getField("INSTANCE").get(null);}
        catch(ReflectiveOperationException bad) {throw new IllegalStateException("Missing pinned empty backpack wrapper",bad);}
    }
}
