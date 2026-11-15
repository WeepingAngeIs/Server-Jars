package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailInventorySnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only snapshot test using disposable player fixtures and installed APIs. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class JailInventorySnapshotSmoke {
    private static boolean done;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (done || !Boolean.getBoolean("uq.jailInventorySnapshot")) return;
        var server = event.getServer();
        var player = server.getPlayerList().getPlayerByName("Dev");
        if (player == null || server.getTickCount() < 100) return;
        done = true;
        try {
            var item = new ItemStack(Items.DIAMOND, 3);
            item.set(DataComponents.CUSTOM_NAME, Component.literal("Snapshot custom name"));
            var armor = new ItemStack(Items.DIAMOND_HELMET);
            var offhand = new ItemStack(Items.SHIELD);
            // Synthetic player fixtures only; never used on the user's world/profile.
            player.getInventory().setItem(0, item.copy());
            player.getInventory().setItem(39, armor.copy());
            player.getInventory().setItem(40, offhand.copy());
            var capabilityClass = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
            var containerClass = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            Object capability = ((Optional<?>) capabilityClass.getMethod("getOptionally", LivingEntity.class).invoke(null, player)).orElseThrow();
            Map<?, ?> containers = (Map<?, ?>) capabilityClass.getMethod("getContainers").invoke(capability);
            Object ring = containers.get("ring");
            require(ring != null, "ring slot is unavailable");
            var regular = (Container) containerClass.getMethod("getAccessories").invoke(ring);
            var cosmetic = (Container) containerClass.getMethod("getCosmeticAccessories").invoke(ring);
            require(regular.getContainerSize() > 0 && cosmetic.getContainerSize() > 0, "ring slots unavailable");
            var accessory = new ItemStack(Items.GOLD_INGOT);
            accessory.set(DataComponents.CUSTOM_NAME, Component.literal("Normal accessory fixture"));
            var vanity = new ItemStack(Items.AMETHYST_SHARD);
            vanity.set(DataComponents.CUSTOM_NAME, Component.literal("Cosmetic fixture"));
            regular.setItem(0, accessory.copy());
            cosmetic.setItem(0, vanity.copy());
            ItemStack cursor = new ItemStack(Items.EMERALD);
            player.containerMenu.setCarried(cursor.copy());
            require(JailInventorySnapshot.capture(player).status() == JailInventorySnapshot.Status.TRANSIENT_MENU
                    && ItemStack.matches(cursor, player.containerMenu.getCarried()), "cursor was ignored or changed");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            ItemStack crafting = new ItemStack(Items.OAK_PLANKS);
            player.inventoryMenu.getSlot(1).set(crafting.copy());
            require(JailInventorySnapshot.capture(player).status() == JailInventorySnapshot.Status.TRANSIENT_MENU
                    && ItemStack.matches(crafting, player.inventoryMenu.getSlot(1).getItem()), "crafting item was ignored or changed");
            player.inventoryMenu.getSlot(1).set(ItemStack.EMPTY);
            var first = JailInventorySnapshot.capture(player);
            require(first.status() == JailInventorySnapshot.Status.CAPTURED_ONLY && first.snapshot() != null,
                    "snapshot preflight declined compatible APIs: " + first.status() + " " + diagnostics(player, containers));
            var snapshot = first.snapshot();
            require(snapshot.getUUID("player").equals(player.getUUID()), "incorrect owner");
            require(snapshot.getInt("vanillaSlots") == player.getInventory().getContainerSize(), "slot count changed");
            check(snapshot, "vanilla", "", 0, item, player.registryAccess());
            check(snapshot, "vanilla", "", 39, armor, player.registryAccess());
            check(snapshot, "vanilla", "", 40, offhand, player.registryAccess());
            check(snapshot, "accessory", "ring", 0, accessory, player.registryAccess());
            check(snapshot, "cosmetic", "ring", 0, vanity, player.registryAccess());
            var second = JailInventorySnapshot.capture(player);
            require(second.status() == JailInventorySnapshot.Status.CAPTURED_ONLY && snapshot.equals(second.snapshot()), "repeat capture changed snapshot");
            require(item.save(player.registryAccess()).equals(player.getInventory().getItem(0).save(player.registryAccess()))
                    && armor.save(player.registryAccess()).equals(player.getInventory().getItem(39).save(player.registryAccess()))
                    && offhand.save(player.registryAccess()).equals(player.getInventory().getItem(40).save(player.registryAccess()))
                    && accessory.save(player.registryAccess()).equals(regular.getItem(0).save(player.registryAccess()))
                    && vanity.save(player.registryAccess()).equals(cosmetic.getItem(0).save(player.registryAccess())), "capture modified fixture items");
            snapshot.putString("tamper", "test");
            require(!first.snapshot().contains("tamper"), "snapshot record was not defensively copied");
            Files.writeString(Path.of("/tmp/uq-jail-snapshot-result"), "passed: exact accessory APIs, vanilla armor/offhand/custom components, normal/cosmetic slots and non-mutation\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Jail inventory snapshot smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-jail-snapshot-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static String diagnostics(net.minecraft.server.level.ServerPlayer player, Map<?, ?> containers) {
        try {
            var curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var handlerApi = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            var contApi = Class.forName("io.wispforest.accessories.api.AccessoriesContainer");
            var wrapperApi = Class.forName("io.wispforest.accessories_compat.curios.wrapper.AccessoriesBasedStackHandler");
            Object handler = ((Optional<?>) curiosApi.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, player)).orElseThrow();
            Map<?, ?> aliases = (Map<?, ?>) handlerApi.getMethod("getCurios").invoke(handler);
            StringBuilder details = new StringBuilder("accessories=").append(containers.keySet()).append(" curios=").append(aliases.keySet());
            for (var e : containers.entrySet()) {
                Object alias = aliases.get(e.getKey());
                int expected = (int) contApi.getMethod("getSize").invoke(e.getValue());
                var normal = (Container) contApi.getMethod("getAccessories").invoke(e.getValue());
                var cosmetic = (Container) contApi.getMethod("getCosmeticAccessories").invoke(e.getValue());
                boolean match = alias != null && alias.getClass() == wrapperApi && wrapperApi.getMethod("container").invoke(alias) == e.getValue();
                details.append(" [").append(e.getKey()).append(" class=").append(alias == null ? "null" : alias.getClass().getName()).append(" same=").append(match)
                        .append(" size=").append(expected).append('/').append(normal.getContainerSize()).append('/').append(cosmetic.getContainerSize()).append(']');
            }
            for (var e : aliases.entrySet()) {
                Object alias = e.getValue(); Object backing = alias != null && alias.getClass() == wrapperApi ? wrapperApi.getMethod("container").invoke(alias) : null;
                String matched = "none";
                for (var src : containers.entrySet()) if (src.getValue() == backing) { matched = src.getKey().toString(); break; }
                details.append(" {alias=").append(e.getKey()).append(" class=").append(alias == null ? "null" : alias.getClass().getName()).append(" backing=").append(matched).append('}');
            }
            return details.toString();
        } catch (Exception failure) { return "diagnostic error " + failure; }
    }
    private static void check(CompoundTag snapshot, String kind, String slot, int index, ItemStack expected, net.minecraft.core.HolderLookup.Provider registry) {
        var rows = snapshot.getList("items", Tag.TAG_COMPOUND);
        int matches = 0;
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            if (kind.equals(row.getString("kind")) && slot.equals(row.getString("slot")) && index == row.getInt("index")) {
                matches++;
                require(expected.save(registry).equals(row.get("stack")), "item components or count changed for " + kind);
            }
        }
        require(matches == 1, "missing or duplicated item slot " + kind + "/" + slot + "/" + index);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
