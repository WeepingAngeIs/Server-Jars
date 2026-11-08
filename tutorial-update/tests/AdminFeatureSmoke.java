package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.essentials.EssConfig;
import dev.unyielding.quests.essentials.Essentials;
import dev.unyielding.quests.essentials.Loc;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.AdminSettings;
import dev.unyielding.quests.ultimine.Ultimine;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Tests the final combined JAR; never included in production. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID)
public final class AdminFeatureSmoke {
    private record Fixture(String key, ModConfigSpec.BooleanValue value, ModConfigSpec spec,
                           String file, String tomlKey) { }
    private static final List<Fixture> SETTINGS = List.of(
            new Fixture("quest_popups", Config.QUEST_TOASTS, Config.SPEC, "common", "completion_popups"),
            new Fixture("inventory_buttons", Config.INVENTORY_BUTTONS, Config.SPEC, "common", "inventory_buttons"),
            new Fixture("vein_mining", UltimineConfig.ENABLED, UltimineConfig.SPEC, "ultimine", "enabled"),
            new Fixture("vein_mining_require_item", UltimineConfig.REQUIRE_TOOL, UltimineConfig.SPEC, "ultimine", "require_tool"),
            new Fixture("mass_log_stripping", UltimineConfig.RIGHT_CLICK_AXE, UltimineConfig.SPEC, "ultimine", "axe"),
            new Fixture("mass_tilling", UltimineConfig.RIGHT_CLICK_HOE, UltimineConfig.SPEC, "ultimine", "hoe"),
            new Fixture("mass_path_making", UltimineConfig.RIGHT_CLICK_SHOVEL, UltimineConfig.SPEC, "ultimine", "shovel"),
            new Fixture("admin_teleport_cooldown_bypass", EssConfig.ADMINS_EXEMPT_COOLDOWNS, EssConfig.SPEC,
                    "essentials", "admins_exempt_cooldowns"),
            new Fixture("back_record_deaths_only", EssConfig.BACK_ONLY_ON_DEATH, EssConfig.SPEC,
                    "essentials", "only_on_death"));
    private static final Path READY = Path.of("/tmp/uq-admin-features-client-ready.json");
    private static final Path CLIENT = Path.of("/tmp/uq-admin-features-client-passed.json");
    private static final Map<String, Boolean> ORIGINAL = new LinkedHashMap<>();
    private static Map<String, Object> report;
    private static boolean complete;
    private static boolean wasOp;
    private static int waitTicks;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (complete || !Files.exists(Path.of("/tmp/uq-currency-server-passed.json"))
                || !Files.exists(Path.of("/tmp/uq-compact-tasks-passed.json"))) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayerByName("Dev");
        if (player == null) return;
        try {
            if (report == null) {
                report = new LinkedHashMap<>(BundleSmoke.verifyProductionJar());
                wasOp = player.hasPermissions(2);
                for (Fixture setting : SETTINGS) ORIGINAL.put(setting.key(), setting.value().get());
                verifySecurityAndPersistence(player);
                verifyLiveBehavior(player);
                change(player, "inventory_buttons", false);
                change(player, "quest_popups", false);
                change(player, "vein_mining", false);
                change(player, "vein_mining_require_item", true);
                player.server.getPlayerList().deop(player.getGameProfile());
                AdminSettings.sync(player, false, "Cloud feature visibility check", false);
                Files.writeString(READY, "public flags changed; editable settings withheld from non-operator\n");
            } else if (Files.exists(CLIENT)) {
                restore(player);
                report.put("new_setting_count", SETTINGS.size());
                report.put("non_operator_and_revoked_operator_changes_rejected", "passed");
                report.put("all_new_settings_saved_in_their_own_config_files", "passed");
                report.put("vein_mining_master_and_teleport_policies_apply_live", "passed");
                report.put("public_flags_synced_without_editable_settings", "passed");
                report.put("original_settings_and_player_history_restored", "passed");
                BundleSmoke.write("/tmp/uq-admin-features-passed.json", report);
                complete = true;
            } else if (++waitTicks > 600) {
                throw new IllegalStateException("Client did not acknowledge public feature flags");
            }
        } catch (Throwable failure) {
            complete = true;
            try { restore(player); } catch (Throwable restoreFailure) { failure.addSuppressed(restoreFailure); }
            BundleSmoke.fail("/tmp/uq-admin-features-failed.txt", failure);
        }
    }

    private static void verifySecurityAndPersistence(ServerPlayer player) throws Exception {
        player.server.getPlayerList().deop(player.getGameProfile());
        for (Fixture setting : SETTINGS) {
            boolean before = setting.value().get();
            String configBefore = Files.readString(configFile(setting));
            AdminSettings.onAction(player, new Net.AdminSettingsAction(setting.key(), !before));
            require(setting.value().get() == before, "Non-operator changed " + setting.key());
            require(Files.readString(configFile(setting)).equals(configBefore), "Denied packet wrote config " + setting.key());
        }
        player.server.getPlayerList().op(player.getGameProfile());
        require(player.hasPermissions(2), "Could not grant fixture operator status");
        for (Fixture setting : SETTINGS) {
            boolean before = setting.value().get();
            change(player, setting.key(), !before);
            assertSaved(setting, !before);
            change(player, setting.key(), before);
            assertSaved(setting, before);
        }
        boolean before = Config.INVENTORY_BUTTONS.get();
        player.server.getPlayerList().deop(player.getGameProfile());
        AdminSettings.onAction(player, new Net.AdminSettingsAction("inventory_buttons", !before));
        require(Config.INVENTORY_BUTTONS.get() == before, "Revoked operator changed inventory visibility");
        player.server.getPlayerList().op(player.getGameProfile());
    }

    private static void verifyLiveBehavior(ServerPlayer player) throws Exception {
        var active = Ultimine.class.getDeclaredMethod("active", ServerPlayer.class);
        active.setAccessible(true);
        try {
            Ultimine.onState(player, new Net.UltimineState(true, 0));
            change(player, "vein_mining", false);
            require(active.invoke(null, player) == null, "Vein mining still active with master switch off");
            change(player, "vein_mining", true);
            require(active.invoke(null, player) != null, "Vein mining did not resume with master switch on");
        } finally { Ultimine.onState(player, new Net.UltimineState(false, 0)); }

        change(player, "admin_teleport_cooldown_bypass", true);
        require(Essentials.cooldown(player, "admin-feature-check-bypass", 30) == null
                        && Essentials.cooldown(player, "admin-feature-check-bypass", 30) == null,
                "Operator bypass did not skip cooldowns");
        change(player, "admin_teleport_cooldown_bypass", false);
        require(Essentials.cooldown(player, "admin-feature-check-enforced", 30) == null,
                "Fixture cooldown unexpectedly already active");
        require(Essentials.cooldown(player, "admin-feature-check-enforced", 30) != null,
                "Operator cooldown was not enforced after disabling bypass");

        var history = Essentials.data().back;
        List<Loc> before = history.get(player.getUUID());
        Loc location = Loc.of(player);
        try {
            history.put(player.getUUID(), new ArrayList<>(List.of(location)));
            change(player, "back_record_deaths_only", true);
            require(Essentials.teleport(player, location), "Same-location fixture teleport failed");
            require(history.get(player.getUUID()).size() == 1, "Death-only mode recorded a teleport");
            change(player, "back_record_deaths_only", false);
            require(Essentials.teleport(player, location), "Same-location fixture teleport failed");
            require(history.get(player.getUUID()).size() == 2, "Normal /back mode did not record teleport origin");
        } finally {
            if (before == null) history.remove(player.getUUID());
            else history.put(player.getUUID(), before);
            Essentials.data().setDirty();
        }
    }

    private static void change(ServerPlayer player, String key, boolean value) {
        AdminSettings.onAction(player, new Net.AdminSettingsAction(key, value));
        Fixture setting = SETTINGS.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
        require(setting.value().get() == value, "Server setting did not change: " + key);
    }

    private static Path configFile(Fixture setting) {
        return FMLPaths.CONFIGDIR.get().resolve("unyieldingquests-" + setting.file() + ".toml");
    }

    private static void assertSaved(Fixture setting, boolean value) throws Exception {
        String text = Files.readString(configFile(setting));
        require(Pattern.compile("(?m)^\\s*" + Pattern.quote(setting.tomlKey()) + "\\s*=\\s*" + value + "\\s*$")
                .matcher(text).find(), "Setting was not persisted in its owning config: " + setting.key());
    }

    private static void restore(ServerPlayer player) {
        if (ORIGINAL.isEmpty()) return;
        player.server.getPlayerList().op(player.getGameProfile());
        for (Fixture setting : SETTINGS) change(player, setting.key(), ORIGINAL.get(setting.key()));
        if (!wasOp) player.server.getPlayerList().deop(player.getGameProfile());
        AdminSettings.sync(player, false, "Cloud feature checks restored settings", false);
    }
}
