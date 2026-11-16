package dev.unyielding.bundlesmoke;

import com.mojang.authlib.GameProfile;
import dev.unyielding.quests.quest.*;
import dev.unyielding.quests.spin.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Destructive, disposable, hosted-only fixture. Never included in a release JAR. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    static final UUID P = UUID.fromString("f9cc33f0-445b-47ad-b707-b7e542ed57e1");
    static final UUID OTHER = UUID.fromString("f9cc33f0-445b-47ad-b707-b7e542ed57e2");
    static final long OLD = 101, FIRST = 102, SECOND = 103, LATER = 104;
    static long sid(long quest) { return LuckySpin.spinId(quest); }
    public BundleSmoke() { NeoForge.EVENT_BUS.addListener(this::started); }
    static void require(boolean ok, String text) { if (!ok) throw new IllegalStateException(text); }
    static DailyLogin.Data daily(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(DailyLogin.Data.factory(), DailyLogin.Data.NAME);
    }
    static QuestData quests(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME);
    }
    static void balances(MinecraftServer s, long earned) {
        var d = daily(s);
        require(Arrays.equals(d.players.get(P), new long[]{100,9,7,earned,123456}), "primary balance/streak changed");
        require(Arrays.equals(d.players.get(OTHER), new long[]{99,3,3,4,123000}), "other player's balance changed");
    }
    static void intent(MinecraftServer s, long q) throws Exception {
        var d = quests(s);
        DailyLogin.journal().creditQuest(P, sid(q), d.rewardEpochs.generation(P, sid(q)));
    }
    static void claim(MinecraftServer s, long id) throws Exception {
        var q = new QuestFile.Quest(); q.id = id;
        QuestManager.file.quests.put(id, q);
        var d = quests(s); var progress = d.get(P);
        progress.completedQuests.add(id); d.setDirty();
        var method = QuestManager.class.getDeclaredMethod("spin", net.minecraft.server.level.ServerPlayer.class,
                TeamProgress.class, UUID.class, QuestFile.Quest.class, List.class);
        method.setAccessible(true);
        method.invoke(null, FakePlayerFactory.get(s.overworld(), new GameProfile(P, "RewardFixture")), progress, P, q, new ArrayList<>());
        require(d.isClaimed(P, sid(id)), "quest claim did not complete");
    }
    static void reset(MinecraftServer s, long id) {
        var q = new QuestFile.Quest(); q.id = id; QuestManager.file.quests.put(id, q);
        String error = QuestManager.adminReset(FakePlayerFactory.get(s.overworld(), new GameProfile(P, "RewardFixture")), id);
        require(error == null, "admin reset failed: " + error);
    }
    static void legacy(Path file, String... fields) throws Exception {
        var nbt = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        for (String name : fields) nbt.getCompound("data").remove(name);
        NbtIo.writeCompressed(nbt, file);
    }
    void started(ServerStartedEvent event) {
        if (!"true".equals(System.getenv("GITHUB_ACTIONS")) || !"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))) return;
        int phase = Integer.parseInt(System.getenv("UQ_REWARD_PHASE"));
        Path evidence = Path.of(System.getenv("UQ_REWARD_EVIDENCE"));
        try {
            var s = event.getServer();
            var jar = Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
            require(hash.equals(System.getenv("UQ_BUNDLE_SHA256")), "wrong candidate hash");
            require(jar.equals(ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath()), "production source is not assembled jar");
            require(!ModList.get().isLoaded("minecraft_jail"), "standalone Jail must not be loaded");
            require(s.getCommands().getDispatcher().getRoot().getChild("jail") == null, "integrated Jail returned");
            var d = daily(s); var q = quests(s);
            Path root = s.getWorldPath(LevelResource.ROOT);
            Path dataFile = root.resolve("data/" + DailyLogin.Data.NAME + ".dat");
            if (phase == 1) {
                require(d.players.isEmpty() && q.claimedRewards.isEmpty(), "world not fresh");
                d.players.put(P, new long[]{100,9,7,11,123456});
                d.players.put(OTHER, new long[]{99,3,3,4,123000}); d.setDirty();
                q.get(P).claimed.computeIfAbsent(P, ignored -> new HashSet<>()).add(sid(OLD));
                q.get(P).taskProgress.put(777L, 42L); q.setDirty();
                DailyLogin.provisionNewJournal(s);
                // Forced journal intent survives without its balance/marker ever being applied.
                intent(s, FIRST);
                s.saveEverything(true, true, true);
                // Genuine old-format files: next JVM must migrate the legacy copies on disk.
                legacy(dataFile, "appliedDebits", "appliedQuestCredits", "journalProvisioned");
                legacy(root.resolve("data/" + QuestData.NAME + ".dat"), "player_claims", "reward_epochs");
            } else if (phase == 2) {
                balances(s, 12);
                require(q.isClaimed(P, sid(OLD)), "legacy claimed reward was lost");
                require(q.get(P).taskProgress.get(777L) == 42L, "legacy progress lost");
                require(q.rewardEpochs.generation(P, sid(FIRST)).equals(QuestRewardEpochs.LEGACY), "legacy generation changed");
                claim(s, FIRST); balances(s, 12); // Retries recorded intent, no duplicate credit.
                s.saveEverything(true, true, true);
                Files.copy(dataFile, evidence.resolve("balance-before-failed-save.dat"));
                claim(s, SECOND); balances(s, 13);
                s.saveEverything(true, true, true);
            } else if (phase == 3) {
                balances(s, 13); // Previous phase's balance save was deliberately discarded outside the JVM.
                require(q.isClaimed(P, sid(FIRST)) && q.isClaimed(P, sid(SECOND)), "saved claims lost during balance recovery");
                DailyLogin.retryRecovery(); DailyLogin.retryRecovery(); balances(s, 13);
                reset(s, FIRST);
                require(!q.isClaimed(P, sid(FIRST)) && q.isClaimed(P, sid(SECOND)) && q.isClaimed(P, sid(OLD)), "single reset cleared unrelated claims");
                require(q.get(P).taskProgress.get(777L) == 42L, "single reset removed unrelated progress");
                require(!q.rewardEpochs.generation(P, sid(FIRST)).equals(QuestRewardEpochs.LEGACY), "single reset reused generation");
                claim(s, FIRST); claim(s, FIRST); balances(s, 14);
                s.saveEverything(true, true, true);
            } else if (phase == 4) {
                balances(s, 14); require(q.isClaimed(P, sid(FIRST)), "reset generation claim lost after restart");
                // Simulate unavailable journal while an administrator intentionally resets quests.
                DailyLogin.onServerStop(); reset(s, 0);
                q.get(P).completedQuests.add(LATER); q.get(P).taskProgress.put(888L, 73L); q.setDirty();
                require(!q.isClaimed(P, sid(FIRST)) && !q.isClaimed(P, sid(SECOND)), "all reset kept old claims");
                s.saveEverything(true, true, true);
            } else if (phase == 5) {
                balances(s, 14);
                require(!q.isClaimed(P, sid(FIRST)) && !q.isClaimed(P, sid(OLD)), "recovery replayed obsolete claim/reset state");
                require(q.get(P).completedQuests.contains(LATER) && q.get(P).taskProgress.get(888L) == 73L, "recovery erased post-reset progress");
                claim(s, FIRST); balances(s, 15);
                claim(s, FIRST); DailyLogin.retryRecovery(); balances(s, 15);
                s.saveEverything(true, true, true);
            } else if (phase == 6) {
                balances(s, 15); claim(s, FIRST); DailyLogin.retryRecovery(); balances(s, 15);
                require(q.isClaimed(P, sid(FIRST)) && !q.isClaimed(P, sid(SECOND)), "final claim history changed");
                require(q.get(P).taskProgress.get(888L) == 73L, "later progress lost after second restart");
                require(d.appliedQuestCredits.size() == 4, "credit marker history lost/duplicated");
                require(DailyLogin.journal().questCredits().size() == 4, "journal identity history lost/duplicated");
                require(DailyLogin.journal().list().isEmpty(), "credit-only checks unexpectedly created a prize payout");
                s.saveEverything(true, true, true);
            } else throw new IllegalArgumentException("unknown phase");
            Files.writeString(evidence.resolve("phase-" + phase + ".txt"), "passed: assembled " + hash + " phase " + phase + "\n");
            // Abrupt process exit prevents shutdown/autosave from rewriting our deliberate legacy fixtures.
            Runtime.getRuntime().halt(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(evidence.resolve("phase-" + phase + ".txt"), "failed: " + failure + "\n"); } catch (Exception ignored) {}
            Runtime.getRuntime().halt(1);
        }
    }
}
