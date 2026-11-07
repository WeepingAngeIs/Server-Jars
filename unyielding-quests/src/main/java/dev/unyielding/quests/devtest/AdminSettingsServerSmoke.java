package dev.unyielding.quests.devtest;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestData;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.settings.AdminSettings;
import dev.unyielding.quests.spin.LuckySpin;
import dev.unyielding.quests.team.Team;
import dev.unyielding.quests.team.TeamManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Cloud-only server-thread assertions. This entire package is excluded from release JARs. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class AdminSettingsServerSmoke {
    private static final Path COMMAND = Path.of("/tmp/uq-admin-server-cmd");
    private static final Path RESULT = Path.of("/tmp/uq-admin-server-result");
    private static final UUID OFFLINE = UUID.fromString("df1668d9-8129-4d4b-a2e0-6b7e73cc6560");
    private static final long INHERITED = 0x6ff1000000000010L;
    private static final long OWN = 0x6ff1000000000020L;
    private static final long OFFLINE_QUEST = 0x6ff1000000000030L;
    private static final long OTHER_PARTY = 0x6ff1000000000040L;

    private AdminSettingsServerSmoke() {}

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("uq.adminSmoke") || event.getServer().getTickCount() % 5 != 0 || !Files.exists(COMMAND)) return;
        String command = "unread";
        try {
            command = Files.readString(COMMAND).trim();
            Files.delete(COMMAND);
            MinecraftServer server = event.getServer();
            ServerPlayer player = server.getPlayerList().getPlayerByName("Dev");
            require(player != null, "Cloud client is not connected");
            switch (command) {
                case "deop", "deop-stale" -> {
                    server.getPlayerList().deop(player.getGameProfile());
                    require(!player.hasPermissions(2), "Deop did not revoke administrator permission");
                    AdminSettings.sync(player, false, "Cloud permission check", false);
                }
                case "op" -> {
                    server.getPlayerList().op(player.getGameProfile());
                    require(player.hasPermissions(2), "Op did not grant administrator permission");
                    AdminSettings.sync(player, false, "Cloud administrator enabled", false);
                }
                case "assert-denied" -> {
                    require(!player.hasPermissions(2), "Permission rejection checked as an operator");
                    require(!Config.SHARE_QUEST_PROGRESS.get(), "Forged settings action changed server config");
                }
                case "assert-on" -> assertSaved(true);
                case "assert-off" -> assertSaved(false);
                case "verify-progress" -> verifyProgress(server, player);
                default -> throw new IllegalStateException("Unknown server smoke command " + command);
            }
            result("passed: " + command);
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Cloud admin smoke failed at {}", command, failure);
            try { result("failed: " + command + ": " + failure); }
            catch (Exception ignored) { }
        }
    }

    private static void assertSaved(boolean enabled) throws Exception {
        require(Config.SHARE_QUEST_PROGRESS.get() == enabled, "Settings action did not change server value");
        Path path = FMLPaths.CONFIGDIR.get().resolve("unyieldingquests-common.toml");
        String config = Files.readString(path);
        require(Pattern.compile("(?m)^\\s*share_quest_progress\\s*=\\s*" + enabled + "\\s*$").matcher(config).find(),
                "Sharing setting was not saved to the server TOML file");
    }

    private static void verifyProgress(MinecraftServer server, ServerPlayer player) throws Exception {
        require(player.hasPermissions(2), "Progress fixtures need the temporary operator");
        require(!Config.SHARE_QUEST_PROGRESS.get(), "Progress isolation must begin with sharing off");
        QuestData data = server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME);
        verifyLegacyMigration(server);
        QuestFile.Quest inherited = fixture(INHERITED, "Cloud party completion", 17);
        QuestFile.Quest own = fixture(OWN, "Cloud personal completion", 13);
        try {
            require(TeamManager.createParty(player, "Cloud progress test") == null, "Could not create test party");
            Team party = TeamManager.teamOf(player.getUUID());
            party.freeToJoin = true;
            Team personal = new Team();
            personal.id = OFFLINE;
            personal.type = Team.Type.PLAYER;
            personal.owner = OFFLINE;
            personal.name = "Offline tester";
            personal.members.put(OFFLINE, Team.Rank.OWNER);
            TeamManager.data().teams.put(OFFLINE, personal);
            TeamManager.data().playerTeam.put(OFFLINE, party.id);
            party.members.put(OFFLINE, Team.Rank.MEMBER);
            party.names.put(OFFLINE, "Offline tester");
            data.get(OFFLINE).completedQuests.add(OFFLINE_QUEST);
            data.get(party.id).completedQuests.add(INHERITED);
            data.get(party.id).completedTasks.add(INHERITED + 1);

            require(TeamManager.leave(player) == null, "Could not leave fixture party");
            require(TeamManager.join(player, party.id.toString()) == null, "Could not join completed party");
            require(!QuestManager.progressOf(player).questDone(INHERITED), "Joining a completed party inherited its quests while sharing was off");
            int before = player.totalExperience;
            QuestManager.onClaim(player, new Net.Claim(INHERITED + 2, 0));
            require(player.totalExperience == before, "Inherited party reward could be claimed while sharing was off");

            QuestManager.onTaskAction(player, new Net.TaskAction(OWN + 1, 0));
            require(QuestManager.progressOf(player).questDone(OWN), "Personal task action did not complete the player's quest");
            require(!data.get(party.id).questDone(OWN) && !data.get(OFFLINE).questDone(OWN), "Solo task completion leaked to party or offline member");
            QuestManager.onClaim(player, new Net.Claim(OWN + 2, 0));
            require(player.totalExperience == before + 13, "Valid personal reward was not granted exactly once");
            require(data.isClaimed(player.getUUID(), OWN + 2), "Claim was not recorded against the player");

            AdminSettings.onAction(player, new Net.AdminSettingsAction("share_quest_progress", true));
            require(QuestManager.progressOf(player).questDone(INHERITED), "Enabling sharing did not expose current party progress");
            require(data.get(party.id).questDone(OWN) && data.get(party.id).questDone(OFFLINE_QUEST), "Enabling sharing lost online or offline personal progress");
            QuestManager.onClaim(player, new Net.Claim(OWN + 2, 0));
            require(player.totalExperience == before + 13, "Switching to shared progress replayed a claimed reward");
            QuestManager.onClaim(player, new Net.Claim(INHERITED + 2, 0));
            require(player.totalExperience == before + 30, "A newly available shared reward was not granted");
            verifySpinReplay(player, data, own);

            AdminSettings.onAction(player, new Net.AdminSettingsAction("share_quest_progress", false));
            require(QuestManager.progressOf(player).questDone(OWN) && QuestManager.progressOf(player).questDone(INHERITED), "Disabling sharing lost current member progress");
            require(data.get(OFFLINE).questDone(OWN) && data.get(OFFLINE).questDone(INHERITED), "Disabling sharing lost offline member progress");
            QuestManager.onClaim(player, new Net.Claim(OWN + 2, 0));
            QuestManager.onClaim(player, new Net.Claim(INHERITED + 2, 0));
            require(player.totalExperience == before + 30, "Switching back to solo progress replayed a reward");

            require(TeamManager.leave(player) == null, "Could not leave shared fixture party");
            require(TeamManager.createParty(player, "Cloud second party") == null, "Could not create another party");
            Team second = TeamManager.teamOf(player.getUUID());
            data.get(second.id).completedQuests.add(OTHER_PARTY);
            require(!QuestManager.progressOf(player).questDone(OTHER_PARTY), "New party shared record bypassed solo mode");
            require(TeamManager.leave(player) == null, "Could not disband second party");
            require(TeamManager.join(player, party.id.toString()) == null, "Could not rejoin original party");
            QuestManager.onClaim(player, new Net.Claim(OWN + 2, 0));
            require(player.totalExperience == before + 30, "Party hopping replayed a reward");

            QuestData restored = QuestData.load(data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
            require(restored.sharingInitialized && !restored.sharingEnabled, "Saved mode did not survive NBT round-trip");
            require(restored.get(player.getUUID()).questDone(OWN) && restored.get(OFFLINE).questDone(INHERITED), "Personal or offline progress was not persisted");
            require(restored.isClaimed(player.getUUID(), OWN + 2)
                    && restored.isClaimed(player.getUUID(), LuckySpin.spinId(OWN)), "Reward or spin ledger was not persisted");
            assertSaved(false);
            QuestManager.sendProgress(player);
            TeamManager.syncAllPlayers();
        } finally {
            removeFixture(inherited);
            removeFixture(own);
        }
    }

    private static void verifyLegacyMigration(MinecraftServer server) {
        UUID player = UUID.fromString("2c292775-19be-49fe-bec8-9609dc327628");
        UUID team = UUID.fromString("ed687a5a-a646-4059-b819-d1648748059f");
        QuestData legacy = new QuestData();
        legacy.get(team).completedQuests.add(INHERITED);
        legacy.get(team).claimed.put(player, Set.of(INHERITED + 2));
        CompoundTag nbt = legacy.save(new CompoundTag(), server.registryAccess());
        nbt.remove("player_claims");
        nbt.remove("sharing_initialized");
        nbt.remove("sharing_enabled");
        QuestData migrated = QuestData.load(nbt, server.registryAccess());
        migrated.prepareSharing(false, Map.of(player, team));
        require(migrated.get(player).questDone(INHERITED), "Upgrade lost existing shared progress for an offline member");
        require(migrated.isClaimed(player, INHERITED + 2), "Legacy claimed reward was not migrated");
    }

    private static void verifySpinReplay(ServerPlayer player, QuestData data, QuestFile.Quest quest) {
        int[] weights = {Config.SPIN_BASIC.get(), Config.SPIN_GOOD.get(), Config.SPIN_PARADOX.get(),
                Config.SPIN_MYTHICAL.get(), Config.SPIN_LEGENDARY.get()};
        try {
            Config.SPIN_BASIC.set(100);
            Config.SPIN_GOOD.set(0);
            Config.SPIN_PARADOX.set(0);
            Config.SPIN_MYTHICAL.set(0);
            Config.SPIN_LEGENDARY.set(0);
            long spin = LuckySpin.spinId(quest.id);
            QuestManager.onClaim(player, new Net.Claim(spin, 0));
            require(data.isClaimed(player.getUUID(), spin), "First quest spin was not recorded");
            int items = itemCount(player);
            QuestManager.onClaim(player, new Net.Claim(spin, 0));
            require(itemCount(player) == items, "Quest spin replayed in the same mode");
            AdminSettings.onAction(player, new Net.AdminSettingsAction("share_quest_progress", false));
            QuestManager.onClaim(player, new Net.Claim(spin, 0));
            require(itemCount(player) == items, "Switching mode replayed a quest spin");
            AdminSettings.onAction(player, new Net.AdminSettingsAction("share_quest_progress", true));
        } finally {
            Config.SPIN_BASIC.set(weights[0]);
            Config.SPIN_GOOD.set(weights[1]);
            Config.SPIN_PARADOX.set(weights[2]);
            Config.SPIN_MYTHICAL.set(weights[3]);
            Config.SPIN_LEGENDARY.set(weights[4]);
        }
    }

    private static int itemCount(ServerPlayer player) {
        int result = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) result += player.getInventory().getItem(i).getCount();
        return result;
    }

    private static QuestFile.Quest fixture(long id, String title, int xp) {
        require(!QuestManager.file.quests.containsKey(id), "Fixture quest ID already exists");
        QuestFile.Quest quest = new QuestFile.Quest();
        quest.id = id;
        quest.title = title;
        QuestFile.Task task = new QuestFile.Task();
        task.id = id + 1;
        task.quest = id;
        task.type = QuestFile.TaskType.CHECKMARK;
        quest.tasks.add(task);
        QuestFile.Reward reward = new QuestFile.Reward();
        reward.id = id + 2;
        reward.quest = id;
        reward.type = QuestFile.RewardType.XP;
        reward.amount = xp;
        quest.rewards.add(reward);
        QuestManager.file.quests.put(id, quest);
        QuestManager.file.tasks.put(task.id, task);
        QuestManager.file.rewards.put(reward.id, reward);
        QuestManager.file.spinQuests.put(LuckySpin.spinId(id), quest);
        return quest;
    }

    private static void removeFixture(QuestFile.Quest quest) {
        QuestManager.file.quests.remove(quest.id);
        QuestManager.file.tasks.remove(quest.id + 1);
        QuestManager.file.rewards.remove(quest.id + 2);
        QuestManager.file.spinQuests.remove(LuckySpin.spinId(quest.id));
    }

    private static void result(String value) throws Exception {
        Path temporary = Path.of(RESULT + ".tmp");
        Files.writeString(temporary, value + "\n");
        Files.move(temporary, RESULT, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
