package dev.unyielding.quests.devtest;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestData;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.team.TeamManager;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Two real players, disposable server only. Never included in the release overlay. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class QuestSharingPlayerSmoke {
    private static boolean done;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (done || !Boolean.getBoolean("uq.questSharingPlayers")) return;
        var server = event.getServer();
        var owner = server.getPlayerList().getPlayerByName("Dev");
        var visitor = server.getPlayerList().getPlayerByName("Visitor");
        if (owner == null || visitor == null || server.getTickCount() < 100) return;
        done = true;
        try {
            Config.SHARE_QUEST_PROGRESS.set(false);
            QuestManager.onSharingChanged(false);
            var data = server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME);
            var book = new QuestFile();
            var quest = new QuestFile.Quest(); quest.id = 9876001L;
            var task = new QuestFile.Task(); task.id = 9876002L; task.quest = quest.id; task.type = QuestFile.TaskType.CHECKMARK;
            var reward = new QuestFile.Reward(); reward.id = 9876003L; reward.quest = quest.id; reward.type = QuestFile.RewardType.XP_LEVELS; reward.amount = 3;
            var automatic = new QuestFile.Reward(); automatic.id = 9876004L; automatic.quest = quest.id; automatic.type = QuestFile.RewardType.XP_LEVELS; automatic.amount = 2; automatic.auto = true;
            quest.tasks.add(task); quest.rewards.add(reward); quest.rewards.add(automatic);
            book.quests.put(quest.id, quest); book.tasks.put(task.id, task); book.rewards.put(reward.id, reward); book.rewards.put(automatic.id, automatic);
            QuestManager.file = book;
            int ownerStart = owner.experienceLevel, visitorStart = visitor.experienceLevel;
            if (Integer.getInteger("uq.questSharingPhase", 1) == 2) {
                require(ownerStart == 5 && visitorStart == 0, "player balances did not persist across restart");
                require(QuestManager.progressOf(owner).questDone(quest.id), "owner completion missing after restart");
                require(!QuestManager.progressOf(visitor).questDone(quest.id), "visitor inherited completion after restart");
                require(data.isClaimed(owner.getUUID(), reward.id) && !data.isClaimed(visitor.getUUID(), reward.id), "claim ledger incorrect after restart");
                QuestManager.onClaim(owner, new Net.Claim(reward.id, 0));
                QuestManager.onClaim(visitor, new Net.Claim(reward.id, 0));
                QuestManager.autoClaim(owner); QuestManager.autoClaim(visitor);
                require(owner.experienceLevel == 5 && visitor.experienceLevel == 0, "repeat claim paid after restart");
                Files.writeString(Path.of("/tmp/uq-quest-sharing-result"), "passed: restarted server retained independent progress, balances, and claims\n");
                return;
            }
            require(ownerStart == 0 && visitorStart == 0, "fresh players expected");
            require(TeamManager.createParty(owner, "Quest Test") == null, "create party");
            QuestManager.onTaskAction(owner, new Net.TaskAction(task.id, 0));
            require(QuestManager.progressOf(owner).questDone(quest.id), "owner quest completion failed");
            require(owner.experienceLevel == ownerStart + 2 && data.isClaimed(owner.getUUID(), automatic.id), "automatic payout missing");
            QuestManager.onClaim(owner, new Net.Claim(reward.id, 0));
            QuestManager.onClaim(owner, new Net.Claim(reward.id, 0));
            require(owner.experienceLevel == ownerStart + 5, "owner payout duplicated or missing");
            require(data.isClaimed(owner.getUUID(), reward.id), "owner claim missing");
            require(TeamManager.invite(owner, "Visitor") == null, "invite visitor");
            require(TeamManager.join(visitor, TeamManager.teamOf(owner.getUUID()).id.toString()) == null, "join visitor");
            require(!QuestManager.progressOf(visitor).questDone(quest.id), "visitor inherited quest while sharing off");
            QuestManager.autoClaim(visitor);
            QuestManager.onClaim(visitor, new Net.Claim(reward.id, 0));
            require(visitor.experienceLevel == visitorStart && !data.isClaimed(visitor.getUUID(), reward.id) && !data.isClaimed(visitor.getUUID(), automatic.id), "visitor received unearned reward");
            require(TeamManager.leave(visitor) == null, "visitor leave");
            require(TeamManager.invite(owner, "Visitor") == null, "reinvite");
            require(TeamManager.join(visitor, TeamManager.teamOf(owner.getUUID()).id.toString()) == null, "rejoin");
            require(!QuestManager.progressOf(visitor).questDone(quest.id), "rejoin inherited quest");
            require(QuestManager.progressOf(owner).questDone(quest.id), "owner lost quest");
            QuestManager.onClaim(owner, new Net.Claim(reward.id, 0));
            require(owner.experienceLevel == ownerStart + 5, "rejoin duplicated owner payout");
            require(TeamManager.leave(visitor) == null, "visitor leave before toggle");
            Config.SHARE_QUEST_PROGRESS.set(true); QuestManager.onSharingChanged(true);
            Config.SHARE_QUEST_PROGRESS.set(false); QuestManager.onSharingChanged(false);
            require(TeamManager.invite(owner, "Visitor") == null, "invite after toggle");
            require(TeamManager.join(visitor, TeamManager.teamOf(owner.getUUID()).id.toString()) == null, "join after toggle");
            require(!QuestManager.progressOf(visitor).questDone(quest.id), "join after sharing off inherited completion");
            QuestManager.onClaim(owner, new Net.Claim(reward.id, 0));
            require(owner.experienceLevel == ownerStart + 5, "toggle duplicated owner payout");
            var saved = data.save(new CompoundTag(), server.registryAccess());
            var restored = QuestData.load(saved, server.registryAccess());
            require(restored.isClaimed(owner.getUUID(), reward.id), "saved owner claim lost");
            require(restored.get(owner.getUUID()).questDone(quest.id), "saved owner progress lost");
            server.getPlayerList().saveAll();
            server.saveEverything(true, true, true);
            Files.writeString(Path.of("/tmp/uq-quest-sharing-result"), "passed: real players, completion, payout, off-mode join/rejoin, toggles, save/load and repeat claims\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Quest sharing player smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-quest-sharing-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
