package dev.unyielding.quests.settings;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.team.TeamManager;
import dev.unyielding.quests.tutorial.TutorialManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** An allowlisted, operator-only editor for server-owned settings. */
public final class AdminSettings {
    private record Setting(String key, String title, String description, ModConfigSpec.BooleanValue value) { }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("share_quest_progress", "Share quest progress",
                    "OFF: everyone earns their own quest completion, even in a party. ON: team members share progress. Existing progress and claimed rewards are preserved.",
                    Config.SHARE_QUEST_PROGRESS),
            new Setting("allow_parties", "Allow new parties",
                    "Let players create parties for team chat and invitations. Existing parties remain available. Progress sharing has its own switch above.",
                    Config.ALLOW_PARTIES),
            new Setting("tutorial_on_join", "Tutorial on join",
                    "Show the player guide after origin selection. Players can always use /tutorial to open it themselves.",
                    Config.TUTORIAL_ON_JOIN),
            new Setting("announce_completion", "Quest completion messages",
                    "Show completion messages to the players whose quest progress was completed.",
                    Config.ANNOUNCE_COMPLETION),
            new Setting("lucky_spin", "Quest Lucky Spins",
                    "Give each player one bonus prize roll per completed quest. Turning this off does not remove normal quest rewards.",
                    Config.SPIN_ENABLED),
            new Setting("daily_rewards", "Daily login rewards",
                    "Give the daily login prize and streak bonuses. Turning this off preserves existing daily claim history.",
                    Config.DAILY_ENABLED),
            new Setting("announce_spin_wins", "Announce Pokemon prizes",
                    "Tell the server when somebody wins a Pokemon from a Lucky Spin.",
                    Config.SPIN_ANNOUNCE));

    private static MinecraftServer server;
    private static List<Boolean> observed = List.of();

    private AdminSettings() { }

    public static void onServerStart(MinecraftServer value) {
        server = value;
        observed = values();
    }

    public static void onServerStop() {
        server = null;
        observed = List.of();
    }

    private static List<Boolean> values() {
        return SETTINGS.stream().map(setting -> setting.value().get()).toList();
    }

    /** Also reconcile normal config-file reloads on the server thread. */
    public static void onTick(MinecraftServer value) {
        if (value.getTickCount() % 20 == 0 && !observed.equals(values())) publishChanges();
    }

    public static void open(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            sync(player, false, "Only server admins can change these settings.", true);
            return;
        }
        sync(player, true, "Changes apply to the server immediately and are saved.", false);
    }

    public static void onAction(ServerPlayer player, Net.AdminSettingsAction action) {
        // A client-supplied packet is never proof of operator status.
        if (!player.hasPermissions(2)) {
            sync(player, false, "Only server admins can change these settings.", true);
            return;
        }
        if ("__open".equals(action.key())) {
            open(player);
            return;
        }
        Setting setting = SETTINGS.stream().filter(value -> value.key().equals(action.key())).findFirst().orElse(null);
        if (setting == null) {
            sync(player, false, "That setting is not available in this menu.", true);
            return;
        }
        boolean before = setting.value().get();
        if (before == action.value()) {
            sync(player, false, setting.title() + " is already " + (before ? "ON." : "OFF."), false);
            return;
        }
        try {
            setting.value().set(action.value());
            Config.SPEC.save();
        } catch (RuntimeException error) {
            setting.value().set(before);
            UnyieldingQuests.LOGGER.error("Could not save admin setting {}", action.key(), error);
            sync(player, false, "Could not save this setting. The previous value is still active.", true);
            return;
        }
        publishChanges();
        UnyieldingQuests.LOGGER.info("{} changed {} to {}", player.getGameProfile().getName(), action.key(), action.value());
        sync(player, false, "Saved: " + setting.title() + " " + (action.value() ? "ON" : "OFF") + ".", false);
    }

    private static void publishChanges() {
        if (server == null) return;
        observed = values();
        QuestManager.onSharingChanged();
        TutorialManager.onAutomaticChanged();
        TeamManager.syncAllPlayers();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            QuestManager.sendSpinInfo(player);
            sync(player, false, "Server settings updated.", false);
        }
    }

    /** Non-operators receive public behavior flags only, never editable settings. */
    public static void sync(ServerPlayer player, boolean open, String message, boolean error) {
        boolean allowed = player.hasPermissions(2);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("allowed", allowed);
        tag.putBoolean("shareProgress", Config.SHARE_QUEST_PROGRESS.get());
        tag.putBoolean("tutorialOnJoin", Config.TUTORIAL_ON_JOIN.get());
        tag.putString("message", message);
        tag.putBoolean("error", error);
        ListTag settings = new ListTag();
        if (allowed) {
            for (Setting setting : SETTINGS) {
                CompoundTag item = new CompoundTag();
                item.putString("key", setting.key());
                item.putString("title", setting.title());
                item.putString("description", setting.description());
                item.putBoolean("value", setting.value().get());
                settings.add(item);
            }
        }
        tag.put("settings", settings);
        Net.sendTo(player, new Net.AdminSettingsSync(tag, open && allowed));
    }
}
