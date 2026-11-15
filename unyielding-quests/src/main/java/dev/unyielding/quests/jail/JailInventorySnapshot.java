package dev.unyielding.quests.jail;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Read-only experimental snapshot, not an admission check or transfer. It never
 * extracts, equips, inserts or removes a stack. A successful snapshot does not
 * establish that unequip callbacks, origin changes, or restoration are safe.
 */
public final class JailInventorySnapshot {
    private static final int MAX_CONTAINERS = 256, MAX_SLOTS = 1024;
    private JailInventorySnapshot() {}

    public enum Status { CAPTURED_ONLY, WRONG_THREAD, TRANSIENT_MENU, UNSUPPORTED_PROVIDER, UNAVAILABLE }
    /** Informational only: none of these values authorizes an inventory transfer. */
    public enum UnequipStatus { NO_KNOWN_EQUIP_RESTRICTION, BOUND_EQUIPMENT, ACCESSORY_CALLBACK_UNVERIFIED, INSPECTION_UNAVAILABLE }
    public record Result(Status status, CompoundTag snapshot) {
        public Result { snapshot = snapshot == null ? null : snapshot.copy(); }
        @Override public CompoundTag snapshot() { return snapshot == null ? null : snapshot.copy(); }
    }

    public static Result capture(ServerPlayer player) {
        if (player == null || player.server == null || !player.server.isSameThread()) return new Result(Status.WRONG_THREAD, null);
        // An open external menu can hold additional items; the carried stack and
        // player crafting grid are not part of Inventory.getContainerSize(). Do not
        // silently omit them or close the menu, which itself can move/drop items.
        if (player.inventoryMenu == null || player.containerMenu != player.inventoryMenu
                || !player.containerMenu.getCarried().isEmpty() || player.inventoryMenu.slots.size() < 5)
            return new Result(Status.TRANSIENT_MENU, null);
        for (int i = 0; i < 5; i++) {
            if (player.inventoryMenu.getSlot(i).hasItem()) return new Result(Status.TRANSIENT_MENU, null);
        }
        if (!version("accessories", "1.1.0-beta.53+1.21.1")
                || !version("accessories_compat_layer", "0.1.12")
                || !version("curios", "9.5.1+1.21.1")) return new Result(Status.UNSUPPORTED_PROVIDER, null);
        try {
            Class<?> accessoriesApi = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            Class<?> containerApi = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> curiosInventoryApi = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> wrapperApi = Class.forName("io.wispforest.accessories_compat.curios.wrapper.AccessoriesBasedStackHandler");
            Object capability = ((Optional<?>) accessoriesApi.getMethod("getOptionally", LivingEntity.class).invoke(null, player)).orElseThrow();
            Object curios = ((Optional<?>) curiosApi.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, player)).orElseThrow();
            Map<?, ?> source = (Map<?, ?>) accessoriesApi.getMethod("getContainers").invoke(capability);
            Map<?, ?> aliases = (Map<?, ?>) curiosInventoryApi.getMethod("getCurios").invoke(curios);
            if (source.size() > MAX_CONTAINERS || aliases.size() > MAX_SLOTS) return new Result(Status.UNSUPPORTED_PROVIDER, null);
            Method unwrap = wrapperApi.getMethod("container");
            // Curios translates names (e.g. hat -> head), so key sets are not
            // identical. Verify every enumerated alias by backing identity instead
            // of counting either the aliases or their stacks as a second inventory.
            Map<String, Object> sourceBacking = new java.util.LinkedHashMap<>();
            for (var entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String name) || entry.getValue() == null)
                    return new Result(Status.UNSUPPORTED_PROVIDER, null);
                sourceBacking.put(name, entry.getValue());
            }
            Map<String, Object> aliasBacking = new java.util.LinkedHashMap<>();
            for (var entry : aliases.entrySet()) {
                if (!(entry.getKey() instanceof String name) || entry.getValue() == null || entry.getValue().getClass() != wrapperApi)
                    return new Result(Status.UNSUPPORTED_PROVIDER, null);
                aliasBacking.put(name, unwrap.invoke(entry.getValue()));
            }
            if (!AccessoryBackingCoverage.covers(sourceBacking, aliasBacking)) return new Result(Status.UNSUPPORTED_PROVIDER, null);
            Method normal = containerApi.getMethod("getAccessories");
            Method cosmetic = containerApi.getMethod("getCosmeticAccessories");
            Method size = containerApi.getMethod("getSize");
            CompoundTag snapshot = new CompoundTag();
            snapshot.putInt("version", 1);
            snapshot.putUUID("player", player.getUUID());
            ListTag items = new ListTag();
            int vanillaSlots = player.getInventory().getContainerSize();
            if (vanillaSlots < 0 || vanillaSlots > MAX_SLOTS) return new Result(Status.UNSUPPORTED_PROVIDER, null);
            snapshot.putInt("vanillaSlots", vanillaSlots);
            int total = vanillaSlots;
            for (int i = 0; i < vanillaSlots; i++) add(items, "vanilla", "", i, player.getInventory().getItem(i), player);
            var sorted = new TreeMap<String, Object>();
            for (var entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String name) || name.isBlank() || name.length() > 128 || entry.getValue() == null)
                    return new Result(Status.UNSUPPORTED_PROVIDER, null);
                sorted.put(name, entry.getValue());
            }
            ListTag slots = new ListTag();
            for (var entry : sorted.entrySet()) {
                String name = entry.getKey(); Object container = entry.getValue(); Object alias = aliases.get(name);
                // The inspected compatibility layer wraps this exact backing container.
                // A different handler, missing slot, or separate store must never be ignored.
                if (alias == null || alias.getClass() != wrapperApi || unwrap.invoke(alias) != container)
                    return new Result(Status.UNSUPPORTED_PROVIDER, null);
                int count = (int) size.invoke(container);
                if (count < 0 || count > MAX_SLOTS || total + count * 2 > MAX_SLOTS)
                    return new Result(Status.UNSUPPORTED_PROVIDER, null);
                Object n = normal.invoke(container), c = cosmetic.invoke(container);
                if (!(n instanceof Container normalSlots) || !(c instanceof Container cosmeticSlots)
                        || normalSlots.getContainerSize() != count || cosmeticSlots.getContainerSize() != count)
                    return new Result(Status.UNSUPPORTED_PROVIDER, null);
                CompoundTag descriptor = new CompoundTag(); descriptor.putString("name", name); descriptor.putInt("size", count); slots.add(descriptor);
                for (int i = 0; i < count; i++) {
                    add(items, "accessory", name, i, normalSlots.getItem(i), player);
                    add(items, "cosmetic", name, i, cosmeticSlots.getItem(i), player);
                }
                total += count * 2;
            }
            snapshot.put("accessorySlots", slots); snapshot.put("items", items);
            // Querying canUnequip, origin callbacks, and crash recovery remain separate
            // prerequisites. This record must not be used to permit a jail admission.
            return new Result(Status.CAPTURED_ONLY, snapshot);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return new Result(Status.UNAVAILABLE, null);
        }
    }
    /**
     * Conservative read-only check. Vanilla binding is data-only, but the
     * Accessories unequip API invokes arbitrary mod callbacks against the live
     * player. Merely inspecting must not call them: an equipped accessory makes
     * this result unknown rather than assuming permission or risking mutation.
     * Even NO_KNOWN_EQUIP_RESTRICTION is not permission to transfer any items.
     */
    public static UnequipStatus inspectUnequip(ServerPlayer player) {
        Result result = capture(player);
        if (result.status() != Status.CAPTURED_ONLY || result.snapshot() == null) return UnequipStatus.INSPECTION_UNAVAILABLE;
        try {
            for (int slot = 36; slot <= 40; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && net.minecraft.world.item.enchantment.EnchantmentHelper.has(
                        stack, net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE))
                    return UnequipStatus.BOUND_EQUIPMENT;
            }
            var entries = result.snapshot().getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (var raw : entries) {
                String kind = ((CompoundTag) raw).getString("kind");
                if (kind.equals("accessory") || kind.equals("cosmetic")) return UnequipStatus.ACCESSORY_CALLBACK_UNVERIFIED;
            }
            return UnequipStatus.NO_KNOWN_EQUIP_RESTRICTION;
        } catch (RuntimeException | LinkageError unavailable) {
            return UnequipStatus.INSPECTION_UNAVAILABLE;
        }
    }
    private static boolean version(String id, String expected) {
        try { return ModList.get().getModContainerById(id).map(c -> expected.equals(c.getModInfo().getVersion().toString())).orElse(false); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    private static void add(ListTag entries, String kind, String name, int index, ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return;
        CompoundTag entry = new CompoundTag(); entry.putString("kind", kind); entry.putString("slot", name); entry.putInt("index", index);
        entry.put("stack", stack.save(player.registryAccess()).copy()); entries.add(entry);
    }
}
