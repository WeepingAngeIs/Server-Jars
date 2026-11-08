package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.loot.LootTables;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Focused actual-final-JAR checks. No fixture code is packaged into the release. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID)
public final class LootTableServerSmoke {
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (checked || !Files.exists(Path.of("/tmp/uq-config-editor-passed.json"))
                || !Files.exists(Path.of("/tmp/uq-admin-features-passed.json"))) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayerByName("Dev");
        if (player == null) return;
        checked = true;
        Path config = FMLPaths.CONFIGDIR.get().resolve("unyieldingquests/loot_tables.json");
        byte[] before = null;
        boolean op = player.hasPermissions(2);
        try {
            before = Files.readAllBytes(config);
            var report = BundleSmoke.verifyProductionJar();
            var table = new LootTables.Table("cloud_test", "Cloud Test", true, true, 1,
                    List.of(new LootTables.Entry("item", "minecraft:diamond", 3, 3, 10, "Diamonds")));
            player.server.getPlayerList().deop(player.getGameProfile());
            int revision = revision();
            save(player, table);
            require(revision() == revision && java.util.Arrays.equals(before, Files.readAllBytes(config)), "Non-admin edited reward tables");
            require(LootTables.snapshot(false, "", false).getList("tables", 10).isEmpty(), "Non-admin received editable tables");
            player.server.getPlayerList().op(player.getGameProfile());
            save(player, table);
            require(revision() == revision + 1, "Admin table save failed");
            require(Files.readString(config).contains("cloud_test"), "Table was not saved to JSON");
            LootTables.stop();
            LootTables.start(event.getServer());
            require(revision() == revision + 1, "Table revision did not survive reload");
            ItemStack crate = LootTables.crate("cloud_test", 1);
            require(!crate.isEmpty(), "Saved table could not produce a crate");
            int diamonds = player.getInventory().countItem(Items.DIAMOND);
            require(LootTables.openCrate(player, crate), "Valid crate did not open");
            require(crate.isEmpty(), "Crate was not consumed exactly once");
            require(player.getInventory().countItem(Items.DIAMOND) == diamonds + 3, "Crate did not award the configured item count");
            require(!LootTables.openCrate(player, crate), "Consumed crate could open again");
            require(player.getInventory().countItem(Items.DIAMOND) == diamonds + 3, "Replayed empty crate awarded another prize");
            report.put("server_save_reload_and_consume_once", "passed");

            CompoundTag stale = action("toggle");
            stale.putInt("revision", revision);
            stale.putBoolean("enabled", false);
            LootTables.onAction(player, stale);
            require(LootTables.snapshot(true, "", false).getBoolean("enabled"), "Stale editor overwrote newer table state");
            var invalid = new LootTables.Table("cloud_test", "Invalid", true, true, 1,
                    List.of(new LootTables.Entry("item", "minecraft:not_an_item", 1, 1, 1, "")));
            int saved = revision();
            save(player, invalid);
            require(revision() == saved, "Invalid item table was accepted");
            CompoundTag toggle = action("toggle"); toggle.putBoolean("enabled", false);
            LootTables.onAction(player, toggle);
            ItemStack paused = LootTables.crate("cloud_test", 1);
            require(!LootTables.openCrate(player, paused) && paused.getCount() == 1, "Disabled table consumed a crate");
            toggle = action("toggle"); toggle.putBoolean("enabled", true); LootTables.onAction(player, toggle);
            report.put("stale_edits_bad_items_and_disabled_crates_rejected", "passed");
            checkQuestRewardSafety(player);
            report.put("disabled_and_missing_table_rewards_remain_unclaimed", "passed");
            report.put("partial_payout_is_committed_and_cannot_be_claimed_twice", "passed");

            long legacyId = 0x1122334455667788L;
            var old = new QuestFile.LootTable(); old.id = legacyId; old.title = "Legacy test";
            var entry = new QuestFile.LootEntry(); entry.item = QuestFile.ItemRef.of("minecraft:bread"); old.entries.add(entry);
            QuestManager.file.lootTables.put(legacyId, old);
            try {
                save(player, new LootTables.Table(LootTables.legacyId(legacyId), "Edited legacy", true, true, 1, table.entries()));
                require(LootTables.hasOverride(legacyId), "Legacy ID override was not registered");
                diamonds = player.getInventory().countItem(Items.DIAMOND);
                require(LootTables.roll(player, LootTables.legacyId(legacyId)), "Legacy edited table did not roll");
                require(player.getInventory().countItem(Items.DIAMOND) == diamonds + 3, "Legacy table still used the old reward");
            } finally { QuestManager.file.lootTables.remove(legacyId); }
            report.put("legacy_reward_ids_preserved_with_editable_override", "passed");
            report.put("non_admin_edit_and_private_table_disclosure_rejected", "passed");
            BundleSmoke.write("/tmp/uq-loot-tables-passed.json", report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-loot-tables-failed.txt", failure);
        } finally {
            try {
                if (before != null) Files.write(config, before);
                LootTables.stop(); LootTables.start(event.getServer());
                if (op) player.server.getPlayerList().op(player.getGameProfile());
                else player.server.getPlayerList().deop(player.getGameProfile());
            } catch (Throwable failure) { BundleSmoke.fail("/tmp/uq-loot-tables-failed.txt", failure); }
        }
    }

    private static int revision() { return LootTables.snapshot(true, "", false).getInt("revision"); }

    private static void checkQuestRewardSafety(ServerPlayer player) throws Exception {
        long questId = 0x4211223344556677L;
        var quest = new QuestFile.Quest(); quest.id = questId;
        var progress = new dev.unyielding.quests.quest.TeamProgress();
        progress.completedQuests.add(questId);
        var claim = QuestManager.class.getDeclaredMethod("claimReward", ServerPlayer.class,
                dev.unyielding.quests.quest.TeamProgress.class, java.util.UUID.class, QuestFile.Reward.class, boolean.class);
        claim.setAccessible(true);
        QuestManager.file.quests.put(questId, quest);
        try {
            CompoundTag off = action("toggle"); off.putBoolean("enabled", false); LootTables.onAction(player, off);
            for (String mode : List.of("give", "roll")) {
                var reward = new QuestFile.Reward(); reward.id = questId + (mode.equals("give") ? 1 : 2);
                reward.quest = questId; reward.type = QuestFile.RewardType.COMMAND;
                reward.command = "questsloot " + mode + " {p} cloud_test";
                require(!(Boolean)claim.invoke(null, player, progress, player.getUUID(), reward, true),
                        "Disabled table quest reward was consumed: " + mode);
                require(!progress.isClaimed(player.getUUID(), reward.id), "Disabled reward was marked claimed");
            }
            CompoundTag on = action("toggle"); on.putBoolean("enabled", true); LootTables.onAction(player, on);
            var missing = new QuestFile.Reward(); missing.id = questId + 3; missing.quest = questId;
            missing.type = QuestFile.RewardType.COMMAND; missing.command = "questsloot give {p} cloud_missing";
            require(!(Boolean)claim.invoke(null, player, progress, player.getUUID(), missing, true), "Missing table quest reward was consumed");

            // Deterministic partial failure: the command grants one item and then fails.
            player.server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("uq_cloud_partial")
                    .requires(source -> source.hasPermission(2)).executes(context -> {
                        net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.DIAMOND));
                        throw new IllegalStateException("Intentional cloud partial-payout fixture");
                    }));
            save(player, new LootTables.Table("cloud_partial", "Partial test", true, true, 1,
                    List.of(new LootTables.Entry("command", "uq_cloud_partial", 1, 1, 1, "Partial fixture"))));
            var partial = new QuestFile.Reward(); partial.id = questId + 4; partial.quest = questId;
            partial.type = QuestFile.RewardType.COMMAND; partial.command = "questsloot roll {p} cloud_partial";
            int before = player.getInventory().countItem(Items.DIAMOND);
            require((Boolean)claim.invoke(null, player, progress, player.getUUID(), partial, true), "Started payout was left retryable");
            require(progress.isClaimed(player.getUUID(), partial.id), "Started payout was not marked claimed");
            require(player.getInventory().countItem(Items.DIAMOND) == before + 1, "Partial fixture did not grant exactly one item");
            require(!(Boolean)claim.invoke(null, player, progress, player.getUUID(), partial, true), "Partial payout could be claimed twice");
            require(player.getInventory().countItem(Items.DIAMOND) == before + 1, "Partial payout duplicated an item");
        } finally {
            QuestManager.file.quests.remove(questId);
            CompoundTag on = action("toggle"); on.putBoolean("enabled", true); LootTables.onAction(player, on);
        }
    }
    private static CompoundTag action(String operation) {
        CompoundTag tag = new CompoundTag(); tag.putString("action", operation); tag.putInt("revision", revision()); return tag;
    }
    private static void save(ServerPlayer player, LootTables.Table table) {
        CompoundTag tag = action("save"); tag.put("table", LootTables.toTag(table)); LootTables.onAction(player, tag);
    }
}
