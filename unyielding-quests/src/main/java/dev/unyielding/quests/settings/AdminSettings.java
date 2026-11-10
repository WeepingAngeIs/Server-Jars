package dev.unyielding.quests.settings;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.essentials.EssConfig;
import dev.unyielding.quests.essentials.Essentials;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.team.TeamManager;
import dev.unyielding.quests.tutorial.TutorialManager;
import dev.unyielding.quests.ultimine.UltimineConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** An allowlisted, operator-only editor for server-owned settings. */
public final class AdminSettings {
    private record Setting(String key, String title, String description, ModConfigSpec.BooleanValue value,
                           ModConfigSpec spec) {
        private Setting(String key, String title, String description, ModConfigSpec.BooleanValue value) {
            this(key, title, description, value, Config.SPEC);
        }
    }

    private static final List<Setting> SETTINGS = List.of(
            new Setting("admin_inventory_controls", "Admin inventory controls",
                    "Show Day, Night and Weather buttons in operator inventories. Time changes all dimensions; weather cycles the current skylight dimension for ten minutes.", Config.ADMIN_INVENTORY_CONTROLS),
            new Setting("admin_claim_bypass", "Admin claim bypass",
                    "Automatically bypass claim interaction protection for online operators. Turning OFF removes this automatic bypass; existing OPAC manual modes remain separate. Requires Open Parties and Claims.", EssConfig.ADMIN_CLAIM_BYPASS, EssConfig.SPEC),
            new Setting("homes", "Home commands",
                    "Allow /home, /sethome, /delhome and /listhomes. Saved homes are kept while OFF.", EssConfig.HOME_ENABLED, EssConfig.SPEC),
            new Setting("back", "Return with /back",
                    "Allow /back. Destination history is kept and continues recording while OFF.", EssConfig.BACK_ENABLED, EssConfig.SPEC),
            new Setting("warps", "Warp commands",
                    "Allow /warp, /setwarp, /delwarp and /listwarps. Saved warps are kept while OFF.", EssConfig.WARP_ENABLED, EssConfig.SPEC),
            new Setting("random_teleport", "Random teleport",
                    "Allow /rtp. OFF prevents new random-location searches.", EssConfig.RTP_ENABLED, EssConfig.SPEC),
            new Setting("teleport_requests", "Teleport requests",
                    "Allow /tpa, /tpahere, /tpaccept and /tpdeny. Turning OFF cancels pending requests.", EssConfig.TPA_ENABLED, EssConfig.SPEC),
            new Setting("nicknames", "Player nicknames",
                    "Allow /nick and display saved nicknames. OFF shows real names without deleting saved nicknames.", EssConfig.NICK_ENABLED, EssConfig.SPEC),
            new Setting("recording_tags", "Recording tags",
                    "Allow /rec and display recording tags. OFF hides tags without deleting saved recording states.", EssConfig.RECORDING_ENABLED, EssConfig.SPEC),
            new Setting("nearby_players", "Nearby-player lookup",
                    "Allow /near to list nearby players.", EssConfig.NEAR_ENABLED, EssConfig.SPEC),
            new Setting("share_quest_progress", "Share quest progress",
                    "OFF: everyone earns their own quest completion, even in a party. ON: team members share progress. Existing progress and claimed rewards are preserved.",
                    Config.SHARE_QUEST_PROGRESS),
            new Setting("allow_parties", "Allow new parties",
                    "Let players create parties for team chat and invitations. Existing parties remain available. Progress sharing has its own switch above.",
                    Config.ALLOW_PARTIES),
            new Setting("tutorial_on_join", "Tutorial on join",
                    "Show the player guide after origin selection until the player finishes or dismisses it. Players can always use /tutorial to open it themselves.",
                    Config.TUTORIAL_ON_JOIN),
            new Setting("announce_completion", "Quest completion messages",
                    "Show completion messages to the players whose quest progress was completed.",
                    Config.ANNOUNCE_COMPLETION),
            new Setting("quest_popups", "Quest completion popups",
                    "Show the small completion popup. Chat messages have their own switch; progress and rewards are unchanged.",
                    Config.QUEST_TOASTS),
            new Setting("inventory_buttons", "Inventory quest and team buttons",
                    "Show the quest book and team shortcuts in player inventories. Players can still open both menus with their commands.",
                    Config.INVENTORY_BUTTONS),
            new Setting("lucky_spin", "Quest Lucky Spins",
                    "Give each player one bonus prize roll per completed quest. Turning this off does not remove normal quest rewards.",
                    Config.SPIN_ENABLED),
            new Setting("daily_rewards", "Daily login rewards",
                    "Give the daily login prize and streak bonuses. Turning this off preserves existing daily claim history.",
                    Config.DAILY_ENABLED),
            new Setting("announce_spin_wins", "Announce Pokemon prizes",
                    "Tell the server when somebody wins a Pokemon from a Lucky Spin.",
                    Config.SPIN_ANNOUNCE),
            new Setting("vein_mining", "Vein mining",
                    "Allow players to mine connected blocks with the vein-mining key. Turning it off also disables mass tool actions.",
                    UltimineConfig.ENABLED, UltimineConfig.SPEC),
            new Setting("vein_mining_require_item", "Require an item for vein mining",
                    "Require something in the player's main hand before mining connected blocks. OFF also allows an empty hand.",
                    UltimineConfig.REQUIRE_TOOL, UltimineConfig.SPEC),
            new Setting("mass_log_stripping", "Mass log stripping",
                    "Allow an axe to strip connected logs while the vein-mining key is held. Normal single-block use is unchanged.",
                    UltimineConfig.RIGHT_CLICK_AXE, UltimineConfig.SPEC),
            new Setting("mass_tilling", "Mass tilling",
                    "Allow a hoe to till a connected patch while the vein-mining key is held. Normal single-block use is unchanged.",
                    UltimineConfig.RIGHT_CLICK_HOE, UltimineConfig.SPEC),
            new Setting("mass_path_making", "Mass path making",
                    "Allow a shovel to make connected paths while the vein-mining key is held. Normal single-block use is unchanged.",
                    UltimineConfig.RIGHT_CLICK_SHOVEL, UltimineConfig.SPEC),
            new Setting("admin_teleport_cooldown_bypass", "Admins bypass teleport cooldowns",
                    "Let operators skip Unyielding teleport cooldowns. OFF makes operators wait like other players.",
                    EssConfig.ADMINS_EXEMPT_COOLDOWNS, EssConfig.SPEC),
            new Setting("back_record_deaths_only", "/back: record deaths only",
                    "Record only deaths as new /back destinations. OFF also records teleport departure points. Existing history is kept.",
                    EssConfig.BACK_ONLY_ON_DEATH, EssConfig.SPEC));

    private static MinecraftServer server;
    private static List<Boolean> observed = List.of();
    private static double observedMapIconScale = 1.0;

    private AdminSettings() { }

    public static void onServerStart(MinecraftServer value) {
        server = value;
        observed = values();
        observedMapIconScale = Config.CUSTOM_MAP_ICON_SCALE.get();
    }

    public static void onServerStop() {
        server = null;
        observed = List.of();
        observedMapIconScale = 1.0;
    }

    private static List<Boolean> values() {
        return SETTINGS.stream().map(setting -> setting.value().get()).toList();
    }

    /** Also reconcile normal config-file reloads on the server thread. */
    public static void onTick(MinecraftServer value) {
        if (value.getTickCount() % 20 == 0 && (!observed.equals(values())
                || Double.compare(observedMapIconScale, Config.CUSTOM_MAP_ICON_SCALE.get()) != 0)) publishChanges();
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
            setting.spec().save();
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

    public static void publishChanges() {
        if (server == null) return;
        observed = values();
        observedMapIconScale = Config.CUSTOM_MAP_ICON_SCALE.get();
        Essentials.settingsChanged();
        AdminClaimBypass.reconcile(server);
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
        tag.putBoolean("inventoryButtons", Config.INVENTORY_BUTTONS.get());
        tag.putBoolean("adminInventoryControls", Config.ADMIN_INVENTORY_CONTROLS.get());
        tag.putDouble("customMapIconScale", Config.CUSTOM_MAP_ICON_SCALE.get());
        tag.putBoolean("questPopups", Config.QUEST_TOASTS.get());
        tag.putBoolean("veinMining", UltimineConfig.ENABLED.get());
        tag.putBoolean("veinMiningRequiresItem", UltimineConfig.REQUIRE_TOOL.get());
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
