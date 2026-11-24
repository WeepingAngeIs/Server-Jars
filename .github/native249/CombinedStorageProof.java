package dev.unyielding.bundlesmoke;

import dev.anya.storage.BagInventory;
import dev.anya.storage.Content;
import dev.anya.storage.Upgrades;
import dev.anya.storage.BackpackHistory;
import dev.anya.storage.BackpackAccess;
import dev.anya.storage.pocket.PocketLeases;
import java.nio.file.Files;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import static dev.unyielding.bundlesmoke.PlayerOnlySmoke.*;

/** Synthetic input and independent exact-stack oracle for a real connected player. */
final class CombinedStorageProof {
    private static final int SLOT = 12;
    private CombinedStorageProof() {}

    static void activeCombatControl(ServerPlayer player) throws Exception {
        require(cmd("jailintegrated check Visitor") == 1, "The neutral player failed preflight before the combat control");
        CompoundTag persisted=player.getPersistentData();
        require(persisted.contains("insanelib",net.minecraft.nbt.Tag.TAG_COMPOUND), "Missing InsaneLib fixture state");
        CompoundTag before=persisted.getCompound("insanelib").copy();
        require(!before.contains("in_combat"), "The fixture player already has combat state");
        CompoundTag combat=new CompoundTag();
        combat.putLong("last_timestamp",player.serverLevel().getGameTime());
        persisted.getCompound("insanelib").put("in_combat",combat.copy());
        try {
            require(cmd("jailintegrated check Visitor") == 0, "Active combat passed admission preflight");
            require(combat.equals(persisted.getCompound("insanelib").getCompound("in_combat")),
                    "Preflight altered the active combat state");
        } finally { persisted.put("insanelib",before); }
        require(cmd("jailintegrated check Visitor") == 1, "Restored neutral state did not pass preflight");
        flag("active_combat_refused_without_change");
    }

    static void seedLightTransition(ServerPlayer player) {
        var jail=dev.unyielding.jail.JailData.get(player.server).assignment(player.getUUID()).jail();
        var position=net.minecraft.core.BlockPos.containing(jail.x(),jail.y(),jail.z());
        int actual=player.serverLevel().getBrightness(net.minecraft.world.level.LightLayer.SKY,position);
        var tags=player.getPersistentData().getCompound("insanelib").getCompound("nbt_tags");
        require(tags.contains("sky_light",net.minecraft.nbt.Tag.TAG_INT)
                && tags.contains("block_light",net.minecraft.nbt.Tag.TAG_INT), "Light cache is unavailable");
        // Test-only setup: simulate the stale but valid light cache caused by moving
        // from a differently lit location. InsaneLib itself refreshes it after death.
        tags.putInt("sky_light",(actual+1)%16);
        flag("stale_light_cache_seeded_for_escape");
    }

    static void activePocketControl(ServerPlayer player) throws Exception {
        require(cmd("jailintegrated check Visitor") == 1, "The neutral player failed preflight before the pocket control");
        var field = Class.forName("dev.anya.storage.pocket.PocketService").getDeclaredField("LEASES");
        field.setAccessible(true);
        var leases = (PocketLeases) field.get(null);
        var playerId = player.getUUID();
        require(leases.get(playerId) == null, "The fixture player already has a pocket lease");
        long now = player.server.overworld().getGameTime();
        var pocketId = java.util.UUID.randomUUID();
        require(leases.acquire(playerId,pocketId,PocketLeases.Kind.REMOTE,1,now,8,64,200,400),
                "Could not create a disposable active pocket lease");
        try {
            require(cmd("jailintegrated check Visitor") == 0, "Active Storage pocket lease passed preflight");
            require(leases.get(playerId) != null && leases.get(playerId).pocket().equals(pocketId),
                    "Preflight altered the active pocket lease");
        } finally {
            leases.release(playerId);
        }
        require(cmd("jailintegrated check Visitor") == 1, "Cleared pocket lease did not restore eligibility");
        flag("active_storage_lease_refused_without_change");
    }

    static void seed(ServerPlayer player) throws Exception {
        var inventory = player.getInventory();
        require(inventory.getItem(SLOT).isEmpty() && inventory.getItem(9).isEmpty(), "Storage fixture slot is occupied");
        var host = new ItemStack(Content.BACKPACK.get());
        host.set(DataComponents.CUSTOM_NAME, Component.literal("Custody test backpack"));
        inventory.setItem(SLOT, host);
        var bag = new BagInventory(player, SLOT);
        var metadata = new CompoundTag();
        metadata.putString("fixture", "marked item in original bag");
        var item = new ItemStack(Items.AMETHYST_SHARD, 13);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Marked custody shards"));
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(metadata));
        bag.setItem(3, item);
        var upgrade = new ItemStack(Content.UPGRADES.get("feeding").get());
        upgrade.set(DataComponents.CUSTOM_NAME, Component.literal("Marked feeding module"));
        upgrade.set(DataComponents.CUSTOM_DATA, CustomData.of(metadata));
        inventory.setItem(9, upgrade);
        require(Upgrades.change(bag, "feeding", false) && Upgrades.installed(new BagInventory(player,SLOT), "feeding"),
                "Storage upgrade was not installed");
        // Installing an upgrade commits a replacement stack to the inventory.
        // Capture that current stack, including the installed upgrade and contents.
        host = inventory.getItem(SLOT);
        require(ItemStack.matches(item,new BagInventory(player,SLOT).getItem(3)),
                "The upgraded backpack lost its marked contents during setup");
        require(BagInventory.data(host).hasUUID("BagId"), "Storage bag has no native identity");
        Files.write(evidence(player,"storage-bag"), OriginCustodyPort.encode((CompoundTag)host.save(player.registryAccess())));
        // Reproduce a rolled-back physical source with a real recorded loss. This is
        // a disposable fixture, and gives the held bag a valid prior recovery link.
        var id = BagInventory.data(host).getUUID("BagId");
        var copy = host.copy();
        int oldSelected = inventory.selected;
        int hotbar = inventory.getFreeSlot();
        require(hotbar >= 0 && hotbar < 9, "A clear hotbar slot is required for the normal toss");
        inventory.setItem(hotbar, inventory.removeItemNoUpdate(SLOT));
        inventory.selected = hotbar;
        require(player.drop(true), "Native backpack toss was refused");
        var drops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
        for (var entity : player.serverLevel().getAllEntities())
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity drop &&
                    BagInventory.data(drop.getItem()).hasUUID("BagId") &&
                    BagInventory.data(drop.getItem()).getUUID("BagId").equals(id)) drops.add(drop);
        require(drops.size() == 1, "Expected exactly one native backpack drop");
        var drop = drops.getFirst();
        drop.setNeverPickUp();
        require(drop.hurt(drop.damageSources().cactus(),100) && drop.isRemoved(), "Native backpack loss failed");
        player.server.getPlayerList().op(player.getGameProfile());
        try {
            var row = BackpackHistory.page(player,0).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
            require(row.restorable() && row.lossId()!=null, "A valid lost-bag recovery link was not created");
            Files.writeString(evidence(player,"storage-loss"), id + " " + row.lossId());
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        inventory.setItem(SLOT,copy);
        inventory.selected = oldSelected;
    }

    static void attemptRecovery(ServerPlayer player, boolean first) throws Exception {
        var ids = Files.readString(evidence(player,"storage-loss"));
        player.server.getPlayerList().op(player.getGameProfile());
        try {
            var parts = ids.split(" ");
            var bagId = java.util.UUID.fromString(parts[0]);
            if (first) require(BackpackHistory.page(player,0).stream().anyMatch(row -> row.id().equals(bagId)),
                    "Expected the recorded loss before recovery");
            int status = player.server.getCommands().getDispatcher().execute(
                    "anya backpack history restore " + ids, player.createCommandSourceStack());
            require(status == 1, "The real operator recovery command did not execute");
            String state = historyState(bagId,java.util.UUID.fromString(parts[1]));
            require(state.equals("AVAILABLE") || state.equals("LOCATED"),
                    "Recovery advanced to an unsafe state while the original bag is held: " + state);
            proof.put(first ? "held_recovery_state" : "held_recovery_state_after_restart",state);
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        held(player);
        flag(first ? "held_recovery_safely_stopped" : "held_recovery_retry_after_restart_safely_stopped");
    }

    private static String historyState(java.util.UUID bagId, java.util.UUID lossId) throws Exception {
        var active = BackpackHistory.class.getDeclaredField("active");
        active.setAccessible(true);
        Object store = active.get(null);
        require(store != null, "Backpack history is unavailable");
        var get = store.getClass().getDeclaredMethod("get",java.util.UUID.class);
        get.setAccessible(true);
        Object entry = get.invoke(store,bagId);
        require(entry != null, "Backpack loss record disappeared");
        var transaction = entry.getClass().getDeclaredMethod("transaction");
        var state = entry.getClass().getDeclaredMethod("state");
        transaction.setAccessible(true);
        state.setAccessible(true);
        require(lossId.equals(transaction.invoke(entry)), "Backpack loss identity changed");
        return ((Enum<?>)state.invoke(entry)).name();
    }

    static void held(ServerPlayer player) throws Exception {
        require(player.getInventory().getItem(SLOT).isEmpty(), "Held storage bag remained in its original slot");
        var expected = OriginCustodyPort.decode(Files.readAllBytes(evidence(player,"storage-bag")));
        var expectedStack = ItemStack.parse(player.registryAccess(),expected).orElseThrow();
        var bagId = BagInventory.data(expectedStack).getUUID("BagId");
        for (var source : BackpackAccess.all(player)) {
            var data = BagInventory.data(source.stack());
            require(!data.hasUUID("BagId") || !data.getUUID("BagId").equals(bagId),
                    "Held bag is still carried or equipped");
        }
        for (var level : player.server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity dropped)) continue;
            var data = BagInventory.data(dropped.getItem());
            require(!data.hasUUID("BagId") || !data.getUUID("BagId").equals(bagId),
                    "A copy of the held bag is on the ground");
        }
        var index = dev.unyielding.jail.JailIntegrated.activeTransaction(player.server,player.getUUID());
        require(index != null, "No custody transaction for storage bag");
        try (var journal = new dev.unyielding.jail.JailEscrowJournal(CertifiedJailSmoke.world.resolve("integrated-jail-v1").resolve(index.toString()).resolve("items"))) {
            var entry = journal.read(index);
            require(entry != null && entry.state() == dev.unyielding.jail.JailEscrowJournal.State.HELD, "Storage bag journal is not held");
            var root = OriginCustodyPort.decode(entry.snapshot());
            var vanilla = OriginCustodyPort.decode(root.getByteArray("vanilla"));
            int matches = 0;
            for (var row : vanilla.getList("items", 10)) {
                var stack = ((CompoundTag)row).copy();
                stack.remove("Slot");
                if (stack.equals(expected)) matches++;
            }
            require(matches == 1, "Held journal does not contain exactly one unchanged backpack");
        }
        flag("storage_bag_held_once_with_original_contents_and_upgrade");
    }

    static void returned(ServerPlayer player) throws Exception {
        var expected = OriginCustodyPort.decode(Files.readAllBytes(evidence(player,"storage-bag")));
        var expectedStack = ItemStack.parse(player.registryAccess(),expected).orElseThrow();
        var bagId = BagInventory.data(expectedStack).getUUID("BagId");
        player.server.getPlayerList().op(player.getGameProfile());
        try {
            var ids = Files.readString(evidence(player,"storage-loss"));
            int status = player.server.getCommands().getDispatcher().execute(
                    "anya backpack history restore " + ids, player.createCommandSourceStack());
            require(status == 1, "Could not retry the stale recovery link after release");
        } finally {
            player.server.getPlayerList().deop(player.getGameProfile());
        }
        var loss = Files.readString(evidence(player,"storage-loss")).split(" ");
        require(historyState(bagId,java.util.UUID.fromString(loss[1])).equals("LOCATED"),
                "The stale recovery link was not closed after the original bag returned");
        int matches = 0;
        int identities = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.save(player.registryAccess()).equals(expected)) matches++;
        }
        for (var source : BackpackAccess.all(player)) {
            var data = BagInventory.data(source.stack());
            if (data.hasUUID("BagId") && data.getUUID("BagId").equals(bagId)) identities++;
        }
        for (var level : player.server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity dropped)) continue;
            var data = BagInventory.data(dropped.getItem());
            require(!data.hasUUID("BagId") || !data.getUUID("BagId").equals(bagId),
                    "A copy of the returned bag is on the ground");
        }
        require(matches == 1 && player.getInventory().getItem(SLOT).save(player.registryAccess()).equals(expected),
                "Original storage bag was missing, changed, duplicated, or restored to the wrong slot");
        require(identities == 1, "The returned bag identity is missing or duplicated");
        require(Upgrades.installed(new BagInventory(player,SLOT), "feeding"), "Returned storage upgrade missing");
        flag("stale_recovery_after_release_did_not_duplicate_bag");
        flag("storage_bag_returned_once_to_original_slot");
    }
}
