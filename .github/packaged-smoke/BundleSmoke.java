package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.essentials.Loc;
import dev.unyielding.quests.jail.JailData;
import dev.unyielding.quests.jail.JailPlacement;
import dev.unyielding.quests.quest.QuestData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.HolderLookup;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Separate test mod: production classes must come exclusively from the assembled JAR. */
@Mod("uq_bundle_smoke")
public final class BundleSmoke {
    private static final UUID PLAYER = UUID.fromString("f9cc33f0-445b-47ad-b707-b7e542ed57e1");
    private static final long REWARD = 98763123L;
    public BundleSmoke() { NeoForge.EVENT_BUS.addListener(this::started); }
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
    private void started(ServerStartedEvent event) {
        String phase = System.getenv("UQ_PACKAGED_PHASE");
        // The independent connected-player test uses this same harness in a later run.
        if (phase == null) return;
        Path result = Path.of("/tmp/uq-packaged-" + phase + ".txt");
        try {
            Path jar = Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
            require(hash.equals(System.getenv("UQ_BUNDLE_SHA256")), "assembled jar hash mismatch");
            for (String id : new String[]{"unyieldingquests", "cusoultome", "curanks", "cugiant", "culeaderboard", "cuportal", "cubalance", "mawonna"}) {
                require(ModList.get().isLoaded(id), "missing mod " + id);
                Path loaded = ModList.get().getModFileById(id).getFile().getFilePath().toRealPath();
                require(loaded.equals(jar), id + " loaded from " + loaded);
            }
            require(!System.getProperty("java.class.path", "").contains("unyielding-quests/build/classes"), "development production classes leaked into runtime");
            Class.forName("dev.unyielding.quests.quest.QuestRewardEpochs");
            Class.forName("dev.unyielding.quests.spin.RewardCredit");
            var server = event.getServer();
            var root = server.getCommands().getDispatcher().getRoot();
            require(root.getChild("quests") != null && root.getChild("jail") != null
                    && root.getChild("unjail") != null && root.getChild("jailbuild") != null, "quest or jail commands missing");
            QuestData quest = server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME);
            require(quest.validRewardEpochs(), "reward epochs invalid");
            JailData jail = JailData.get(server);
            if ("1".equals(phase)) {
                // Verify actual legacy records loaded by the assembled JAR, not copied source.
                var pos = new Loc("minecraft:overworld", 0.5, 80, 0.5, 0, 0);
                var row = new CompoundTag(); row.putUUID("player", PLAYER);
                row.put("jail", pos.toTag()); row.put("prior", pos.toTag());
                var rows = new ListTag(); rows.add(row);
                var old = new CompoundTag(); old.put("location", pos.toTag()); old.put("prisoners", rows);
                var load = JailData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
                load.setAccessible(true);
                var legacy = (JailData) load.invoke(null, old, null);
                require(legacy.geometry() == JailData.LEGACY_GEOMETRY && legacy.assignment(PLAYER).geometry() == JailData.LEGACY_GEOMETRY,
                        "legacy assignment was reinterpreted");
                row.putString("geometry", "corrupt"); old.putString("geometry", "corrupt");
                var corrupt = (JailData) load.invoke(null, old, null);
                require(corrupt.geometry() == JailData.INVALID_GEOMETRY && corrupt.assignment(PLAYER) != null
                        && corrupt.assignment(PLAYER).geometry() == JailData.INVALID_GEOMETRY
                        && !JailPlacement.intact(server, pos, corrupt.assignment(PLAYER).geometry()),
                        "malformed geometry did not remain recoverable and fail closed");
                require(!quest.isClaimed(PLAYER, REWARD) && jail.assignment(PLAYER) == null, "world was not fresh");
                quest.markClaimed(PLAYER, REWARD);
                jail.assign(PLAYER, new JailData.Assignment(pos, pos));
                server.saveEverything(true, true, true);
            } else if ("2".equals(phase)) {
                require(quest.isClaimed(PLAYER, REWARD), "quest reward claim missing after world restart");
                require(jail.assignment(PLAYER) != null, "jail assignment missing after world restart");
                require(jail.assignment(PLAYER).geometry() == JailData.LEGACY_GEOMETRY, "legacy geometry changed after restart");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "unjail pardonuuid " + PLAYER);
                require(jail.assignment(PLAYER) == null, "packaged jail console recovery did not clear assignment");
            } else throw new IllegalArgumentException("invalid test phase");
            Files.writeString(result, "passed: packaged " + hash + " phase " + phase + " startup, quest ledger, jail data and commands\n");
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(result, "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
}
