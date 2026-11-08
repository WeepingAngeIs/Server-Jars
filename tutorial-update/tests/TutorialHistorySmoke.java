package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.tutorial.TutorialHistory;
import dev.unyielding.quests.tutorial.TutorialManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import static dev.unyielding.bundlesmoke.BundleSmoke.require;

final class TutorialHistorySmoke {
    @SuppressWarnings("unchecked")
    static void exercise(ServerPlayer player, Map<String,Object> report) throws Exception {
        var field = TutorialManager.class.getDeclaredField("PENDING"); field.setAccessible(true);
        Map<UUID,Boolean> pending = (Map<UUID,Boolean>)field.get(null);
        boolean enabled = Config.TUTORIAL_ON_JOIN.get();
        try {
            Config.TUTORIAL_ON_JOIN.set(true);
            TutorialManager.onLogout(player);
            // Earlier UI fixtures may have dismissed their guide. Use an isolated UUID for serialization coverage.
            UUID fresh = UUID.randomUUID();
            var history = new TutorialHistory();
            require(!history.contains(fresh), "New player already marked seen");
            history.mark(fresh);
            var loaded = TutorialHistory.load(history.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
            require(loaded.contains(fresh) && !loaded.contains(UUID.randomUUID()), "Per-player history did not survive reload");
            var send = TutorialManager.class.getDeclaredMethod("send", ServerPlayer.class, boolean.class);
            send.setAccessible(true);
            send.invoke(null, player, false);
            TutorialManager.dismissed(player);
            require(TutorialHistory.get(player).contains(player.getUUID()), "Dismissal did not persist server history");
            TutorialManager.onLogout(player); TutorialManager.onLogin(player);
            require(!pending.containsKey(player.getUUID()), "Seen tutorial queued after reconnect");
            TutorialManager.onServerStop(); TutorialManager.onServerStart(); TutorialManager.onLogin(player);
            require(!pending.containsKey(player.getUUID()), "Seen tutorial queued after manager restart");
            Config.TUTORIAL_ON_JOIN.set(false); TutorialManager.onAutomaticChanged();
            send.invoke(null, player, false);
            var delivered = TutorialManager.class.getDeclaredField("DELIVERED"); delivered.setAccessible(true);
            require(((java.util.Set<?>)delivered.get(null)).contains(player.getUUID()), "Manual tutorial delivery blocked for returning player");
            player.server.overworld().getDataStorage().save();
            report.put("tutorial_dismissal_reconnect_restart_serialization_and_manual_replay", "passed");
        } finally {
            Config.TUTORIAL_ON_JOIN.set(enabled);
            TutorialManager.onLogout(player);
        }
    }
}
