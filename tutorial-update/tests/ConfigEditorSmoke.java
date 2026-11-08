package dev.unyielding.bundlesmoke;

import com.google.gson.Gson;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.settings.AdminSettings;
import dev.unyielding.quests.settings.ConfigEditor;
import dev.unyielding.quests.ultimine.UltimineConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Real connected-player checks of the typed editor; never shipped in the release. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID)
public final class ConfigEditorSmoke {
    private static final Gson JSON = new Gson();
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (checked || !Files.exists(Path.of("/tmp/uq-tutorial-access-passed.json"))) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayerByName("Dev");
        if (player == null) return;
        checked = true;
        try {
            Map<String, Object> report = BundleSmoke.verifyProductionJar();
            exercise(player, report);
            SpinOddsSmoke.exercise(player, report);
            TutorialHistorySmoke.exercise(player, report);
            BundleSmoke.write("/tmp/uq-config-editor-passed.json", report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-config-editor-failed.txt", failure);
        }
    }

    private static void exercise(ServerPlayer player, Map<String, Object> report) throws Exception {
        var players = player.server.getPlayerList();
        var profile = player.getGameProfile();
        boolean wasOperator = players.isOp(profile);
        int originalSize = Config.MAX_PARTY_SIZE.get();
        List<? extends String> originalTags = new ArrayList<>(UltimineConfig.MERGE_TAGS.get());
        String originalSizeJson = json(Config.MAX_PARTY_SIZE);
        try {
            players.deop(profile);
            rejected(player, Config.MAX_PARTY_SIZE, "quests", "common", "7");
            rejected(player, UltimineConfig.MERGE_TAGS, "vein_mining", "ultimine", "[\"minecraft:logs\"]");
            players.op(profile);
            require(player.hasPermissions(2), "Could not grant config-editor fixture operator status");

            for (String invalid : List.of("-1", "1001", "3.5", "\"7\"", "true", "null", "[]", "{}"))
                rejected(player, Config.MAX_PARTY_SIZE, "quests", "common", invalid);
            for (String invalid : List.of("\"minecraft:logs\"", "[3]", "[null]", "{}"))
                rejected(player, UltimineConfig.MERGE_TAGS, "vein_mining", "ultimine", invalid);
            rejected(player, Config.DAILY_TIMEZONE, "quests", "common", "\"Not/A_Timezone\"");
            if (Config.SPIN_LEVEL_MAX.get() < 100)
                rejected(player, Config.SPIN_LEVEL_MIN, "quests", "common", "100");
            if (Config.SPIN_LEVEL_MIN.get() > 1)
                rejected(player, Config.SPIN_LEVEL_MAX, "quests", "common", "1");
            String beforeUnknown = Files.readString(config("common"));
            ConfigEditor.handle(player, new ConfigEditor.Action("quests.no_such_setting", "0", "1"));
            require(Files.readString(config("common")).equals(beforeUnknown), "Unknown setting wrote a config");
            report.put("non_operator_unknown_key_invalid_types_ranges_and_related_values_rejected", "passed");

            int changed = originalSize == 7 ? 8 : 7;
            edit(player, Config.MAX_PARTY_SIZE, "quests", Integer.toString(changed));
            require(Config.MAX_PARTY_SIZE.get() == changed, "Typed numeric edit was not applied");
            require(Pattern.compile("(?m)^\\s*max_party_size\\s*=\\s*" + changed + "\\s*$")
                    .matcher(Files.readString(config("common"))).find(), "Numeric edit not persisted in common TOML");
            List<String> tags = List.of("minecraft:logs", "minecraft:base_stone_overworld");
            edit(player, UltimineConfig.MERGE_TAGS, "vein_mining", JSON.toJson(tags));
            require(UltimineConfig.MERGE_TAGS.get().equals(tags), "Typed list edit was not applied");
            String savedTags = Files.readString(config("ultimine"));
            require(savedTags.contains("\"minecraft:logs\"") && savedTags.contains("\"minecraft:base_stone_overworld\""),
                    "List edit not persisted in vein-mining TOML");
            report.put("numeric_and_list_edits_persist_to_owning_files", "passed");

            String beforeStale = Files.readString(config("common"));
            ConfigEditor.handle(player, new ConfigEditor.Action(key("quests", Config.MAX_PARTY_SIZE), originalSizeJson, "9"));
            require(Config.MAX_PARTY_SIZE.get() == changed && Files.readString(config("common")).equals(beforeStale),
                    "Stale expected value overwrote another edit");
            players.deop(profile);
            rejected(player, Config.MAX_PARTY_SIZE, "quests", "common", "10");
            report.put("stale_editor_and_revoked_operator_cannot_overwrite", "passed");
        } finally {
            // Restore directly even when a test deliberately exposes a broken editor path.
            Config.MAX_PARTY_SIZE.set(originalSize);
            Config.MAX_PARTY_SIZE.save();
            UltimineConfig.MERGE_TAGS.set(originalTags);
            UltimineConfig.MERGE_TAGS.save();
            if (wasOperator) players.op(profile);
            else players.deop(profile);
            AdminSettings.publishChanges();
        }
        require(Config.MAX_PARTY_SIZE.get() == originalSize && UltimineConfig.MERGE_TAGS.get().equals(originalTags)
                        && players.isOp(profile) == wasOperator,
                "Config-editor fixtures did not restore original state");
        require(Pattern.compile("(?m)^\\s*max_party_size\\s*=\\s*" + originalSize + "\\s*$")
                .matcher(Files.readString(config("common"))).find(), "Original numeric value not restored on disk");
        String restoredTags = Files.readString(config("ultimine"));
        for (String tag : originalTags)
            require(restoredTags.contains(JSON.toJson(tag)), "Original list entry not restored on disk: " + tag);
        report.put("original_values_files_and_operator_status_restored", "passed");
    }

    private static void rejected(ServerPlayer player, ModConfigSpec.ConfigValue<?> value,
                                 String group, String file, String next) throws Exception {
        String before = json(value), disk = Files.readString(config(file));
        ConfigEditor.handle(player, new ConfigEditor.Action(key(group, value), before, next));
        require(json(value).equals(before), "Rejected input changed " + key(group, value) + ": " + next);
        require(Files.readString(config(file)).equals(disk), "Rejected input wrote " + key(group, value));
    }

    private static void edit(ServerPlayer player, ModConfigSpec.ConfigValue<?> value, String group, String next) {
        ConfigEditor.handle(player, new ConfigEditor.Action(key(group, value), json(value), next));
    }

    private static String json(ModConfigSpec.ConfigValue<?> value) { return JSON.toJson(value.getRaw()); }
    private static String key(String group, ModConfigSpec.ConfigValue<?> value) { return group + "." + String.join(".", value.getPath()); }
    private static Path config(String file) { return FMLPaths.CONFIGDIR.get().resolve("unyieldingquests-" + file + ".toml"); }
}
