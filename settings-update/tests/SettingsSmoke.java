package dev.unyielding.tests;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.essentials.EssConfig;
import dev.unyielding.quests.essentials.Essentials;
import dev.unyielding.quests.essentials.Loc;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.AdminClaimBypass;
import dev.unyielding.quests.settings.AdminSettings;
import dev.unyielding.quests.settings.AdminWorldControls;
import dev.unyielding.quests.settings.ConfigEditor;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Only loaded on an isolated disposable server; never bundled with the released mod. */
@Mod("settingssmoke")
public final class SettingsSmoke {
    private static final Gson JSON = new Gson();
    private static final UUID TEST_ID = UUID.fromString("c58ed784-4db7-4900-aafe-8cf8dd7c2478");
    private static final String FIXTURE = "settingssmoke_fixture";
    private static final String SCALE_KEY = "quests.custom_pokemon.map_icon_scale";
    private int assertions;
    private final List<String> completed = new ArrayList<>();

    public SettingsSmoke() {
        NeoForge.EVENT_BUS.addListener(this::register);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("settingstest").requires(s -> s.hasPermission(4))
                .executes(context -> run(context.getSource())));
    }

    private int run(CommandSourceStack source) {
        assertions = 0;
        completed.clear();
        Map<String, Object> report = new LinkedHashMap<>();
        TestPlayer player = new TestPlayer(source.getServer().overworld(), TEST_ID, "SettingsSmoke");
        Map<ModConfigSpec.ConfigValue<?>, Object> previous = snapshot();
        try {
            check(Essentials.data() != null, "Essentials initialized on loaded server");
            defaults();
            configAliases(player);
            toggleEdits(player);
            commands(player);
            scale(player);
            worldControls(player);
            claimBypass(player);
            report.put("status", "PASS");
            source.sendSuccess(() -> Component.literal("SETTINGSTEST PASS " + assertions + " assertions; " + String.join(", ", completed)), false);
            return 1;
        } catch (Throwable failure) {
            report.put("status", "FAIL");
            report.put("error", failure.toString());
            failure.printStackTrace();
            source.sendFailure(Component.literal("SETTINGSTEST FAIL " + failure));
            return 0;
        } finally {
            try {
                for (var entry : previous.entrySet()) restore(entry.getKey(), entry.getValue());
                Config.SPEC.save();
                EssConfig.SPEC.save();
                Essentials.data().homes.remove(TEST_ID);
                Essentials.data().nicks.remove(TEST_ID);
                Essentials.data().rec.remove(TEST_ID);
                Essentials.data().back.remove(TEST_ID);
                Essentials.data().warps.remove(FIXTURE);
                Essentials.data().warps.remove(FIXTURE + "_new");
                Essentials.data().setDirty();
                requests().remove(TEST_ID);
                cooldowns().keySet().removeIf(key -> key.toString().endsWith("/" + TEST_ID));
                AdminClaimBypass.reconcile(source.getServer());
                AdminSettings.publishChanges();
            } catch (Throwable cleanup) {
                report.put("status", "FAIL");
                report.put("cleanupError", cleanup.toString());
                cleanup.printStackTrace();
                source.sendFailure(Component.literal("SETTINGSTEST CLEANUP FAIL " + cleanup));
            }
            report.put("assertions", assertions);
            report.put("completed", List.copyOf(completed));
            try { Files.writeString(Path.of("settings-smoke-result.json"), JSON.toJson(report)); }
            catch (Exception failure) { failure.printStackTrace(); }
        }
    }

    private Map<String, ModConfigSpec.BooleanValue> toggles() {
        Map<String, ModConfigSpec.BooleanValue> values = new LinkedHashMap<>();
        values.put("homes", EssConfig.HOME_ENABLED);
        values.put("back", EssConfig.BACK_ENABLED);
        values.put("warps", EssConfig.WARP_ENABLED);
        values.put("random_teleport", EssConfig.RTP_ENABLED);
        values.put("teleport_requests", EssConfig.TPA_ENABLED);
        values.put("nicknames", EssConfig.NICK_ENABLED);
        values.put("recording_tags", EssConfig.RECORDING_ENABLED);
        values.put("nearby_players", EssConfig.NEAR_ENABLED);
        return values;
    }

    private Map<ModConfigSpec.ConfigValue<?>, Object> snapshot() {
        Map<ModConfigSpec.ConfigValue<?>, Object> result = new LinkedHashMap<>();
        toggles().values().forEach(value -> result.put(value, value.get()));
        for (var value : List.of(Config.CUSTOM_MAP_ICON_SCALE, Config.ADMIN_INVENTORY_CONTROLS,
                EssConfig.ADMIN_CLAIM_BYPASS, EssConfig.ADMINS_EXEMPT_COOLDOWNS)) result.put(value, value.get());
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restore(ModConfigSpec.ConfigValue value, Object previous) { value.set(previous); }

    private void defaults() {
        toggles().forEach((name, value) -> {
            check(value.getDefault(), name + " default true");
            check(value.get(), name + " initial true in clean server");
        });
        check(Config.CUSTOM_MAP_ICON_SCALE.getDefault() == 1.0, "map scale default 1");
        check(Config.CUSTOM_MAP_ICON_SCALE.get() == 1.0, "clean server map scale 1");
        check(Config.ADMIN_INVENTORY_CONTROLS.get(), "inventory controls enabled by default");
        check(!EssConfig.ADMIN_CLAIM_BYPASS.get(), "claim bypass safely defaults off");
        completed.add("defaults");
    }

    private void configAliases(TestPlayer player) throws Exception {
        var dispatcher = player.server.getCommands().getDispatcher();
        for (String command : List.of("config", "quests settings")) {
            player.operator = true;
            player.payloads.clear();
            check(dispatcher.execute(command, player.createCommandSourceStack().withPermission(2)) == 1,
                    "operator level 2 can execute /" + command);
            var packet = player.lastAdmin();
            check(packet.open() && packet.tag().getBoolean("allowed") && !packet.tag().getList("settings", 10).isEmpty(),
                    "/" + command + " opens populated admin settings");
            player.operator = false;
            player.payloads.clear();
            var unprivileged = player.createCommandSourceStack().withPermission(0);
            check(!unprivileged.hasPermission(2) && !player.hasPermissions(2), "non-operator fixture has no elevated permissions");
            boolean denied;
            try { denied = dispatcher.execute(command, unprivileged) == 0; }
            catch (CommandSyntaxException expected) { denied = true; }
            check(denied, "non-operator cannot execute /" + command);
            check(player.payloads.stream().noneMatch(payload -> payload instanceof Net.AdminSettingsSync sync
                            && (sync.open() || !sync.tag().getList("settings", 10).isEmpty())),
                    "denied /" + command + " sends no editable settings");
        }
        check(dispatcher.getRoot().getChild("config").getChild("showfile") != null,
                "config alias preserves NeoForge's existing showfile subcommand");
        player.operator = true;
        completed.add("config command aliases and permissions");
    }

    private void toggleEdits(TestPlayer player) throws Exception {
        for (var entry : toggles().entrySet()) {
            String key = entry.getKey();
            var value = entry.getValue();
            player.operator = true;
            AdminSettings.onAction(player, new Net.AdminSettingsAction(key, false));
            check(!value.get(), key + " operator disables live");
            check(Boolean.FALSE.equals(persisted("essentials", value)), key + " persisted false");
            check(!player.lastAdmin().tag().getBoolean("error"), key + " save acknowledged");
            byte[] before = Files.readAllBytes(configFile("essentials"));
            player.operator = false;
            AdminSettings.onAction(player, new Net.AdminSettingsAction(key, true));
            check(!value.get(), key + " non-operator rejected");
            check(Arrays.equals(before, Files.readAllBytes(configFile("essentials"))), key + " denied edit leaves file unchanged");
            player.operator = true;
            AdminSettings.onAction(player, new Net.AdminSettingsAction(key, true));
            check(value.get(), key + " can re-enable without restart");
            check(Boolean.TRUE.equals(persisted("essentials", value)), key + " reset persisted");
        }
        AdminSettings.onAction(player, new Net.AdminSettingsAction("__not_a_setting", false));
        check(player.lastAdmin().tag().getBoolean("error"), "unknown setting rejected");
        AdminSettings.onAction(player, new Net.AdminSettingsAction("admin_inventory_controls", false));
        check(!Config.ADMIN_INVENTORY_CONTROLS.get() && Boolean.FALSE.equals(persisted("common", Config.ADMIN_INVENTORY_CONTROLS)), "inventory controls switch persists");
        AdminSettings.onAction(player, new Net.AdminSettingsAction("admin_inventory_controls", true));
        check(Config.ADMIN_INVENTORY_CONTROLS.get(), "inventory controls switch resets");
        AdminSettings.onAction(player, new Net.AdminSettingsAction("admin_claim_bypass", true));
        check(EssConfig.ADMIN_CLAIM_BYPASS.get() && Boolean.TRUE.equals(persisted("essentials", EssConfig.ADMIN_CLAIM_BYPASS)), "claim bypass switch persists");
        AdminSettings.onAction(player, new Net.AdminSettingsAction("admin_claim_bypass", false));
        check(!EssConfig.ADMIN_CLAIM_BYPASS.get(), "claim bypass switch resets");
        completed.add("toggle permissions and persistence");
    }

    private void commands(TestPlayer player) throws Exception {
        var d = player.server.getCommands().getDispatcher();
        player.operator = true;
        player.setPos(8, 80, 8);
        var s = player.createCommandSourceStack().withPermission(4);
        check(d.execute("sethome " + FIXTURE, s) == 1, "enabled sethome executes");
        check(d.execute("setwarp " + FIXTURE, s) == 1, "enabled setwarp executes");
        check(d.execute("nick FixtureNick", s) == 1, "enabled nickname executes");
        check(d.execute("rec", s) == 1 && Essentials.data().rec.contains(TEST_ID), "enabled recording executes");
        var cached = d.parse("sethome cached_should_not_exist", s);
        Loc home = Essentials.data().homes.get(TEST_ID).get(FIXTURE);
        Loc warp = Essentials.data().warps.get(FIXTURE);
        Loc back = new Loc("minecraft:overworld", 12, 80, 12, 0, 0);
        Essentials.data().back.put(TEST_ID, new ArrayList<>(List.of(back)));
        Object request = requestFixture();
        requests().put(TEST_ID, new ArrayList<>(List.of(request)));
        Map<?, ?> cooldownBefore = new LinkedHashMap<>(cooldowns());
        EssConfig.ADMINS_EXEMPT_COOLDOWNS.set(false);
        toggles().values().forEach(value -> value.set(false));
        for (String cmd : List.of("sethome", "sethome " + FIXTURE + "_new", "delhome " + FIXTURE,
                "home " + FIXTURE, "listhomes", "setwarp " + FIXTURE + "_new", "delwarp " + FIXTURE,
                "warp " + FIXTURE, "listwarps", "back", "rtp", "tpa @s", "tpahere @s", "tpaccept", "tpdeny",
                "nick Changed", "nick", "rec", "near")) {
            player.messages.clear();
            check(d.execute(cmd, s) == 0, "disabled command returns failure: " + cmd);
            check(player.messages.stream().anyMatch(msg -> msg.toLowerCase().contains("disabled")), "disabled feedback: " + cmd);
        }
        check(d.execute(cached) == 0, "already parsed command observes newly disabled setting");
        check(Essentials.data().homes.get(TEST_ID).equals(Map.of(FIXTURE, home)), "disabled homes unchanged");
        check(warp.equals(Essentials.data().warps.get(FIXTURE)) && !Essentials.data().warps.containsKey(FIXTURE + "_new"), "disabled warps unchanged");
        check(Essentials.data().back.get(TEST_ID).equals(List.of(back)), "disabled back preserves destination");
        check(requests().get(TEST_ID).equals(List.of(request)), "disabled teleport requests preserved");
        check("FixtureNick".equals(Essentials.data().nicks.get(TEST_ID)), "disabled nickname retained");
        check(Essentials.data().rec.contains(TEST_ID), "disabled recording flag retained");
        check(cooldownBefore.equals(cooldowns()), "no denied command starts cooldowns");
        check(player.getX() == 8 && player.getY() == 80 && player.getZ() == 8, "denied teleport commands do not move player");
        player.refreshDisplayName();
        check(!player.getDisplayName().getString().contains("FixtureNick") && !player.getDisplayName().getString().contains("[REC]"), "disabled nickname and recording hidden");
        toggles().values().forEach(value -> value.set(true));
        check(d.execute("delhome " + FIXTURE, s) == 1, "home works immediately after re-enable");
        check(d.execute("delwarp " + FIXTURE, s) == 1, "warp works immediately after re-enable");
        check(d.execute("nick", s) == 1 && !Essentials.data().nicks.containsKey(TEST_ID), "nickname reset after re-enable");
        check(d.execute("rec", s) == 1 && !Essentials.data().rec.contains(TEST_ID), "recording reset after re-enable");
        boolean denied = false;
        try { d.execute("setwarp " + FIXTURE, s.withPermission(0)); }
        catch (CommandSyntaxException expected) { denied = true; }
        check(denied && !Essentials.data().warps.containsKey(FIXTURE), "operator command retains permission requirement");
        completed.add("real command gates and preserved data");
    }

    private void scale(TestPlayer player) throws Exception {
        player.operator = true;
        for (double value : new double[]{0.25, 4.0, 1.75, 1.0}) {
            editScale(player, JSON.toJson(Config.CUSTOM_MAP_ICON_SCALE.get()), JSON.toJson(value));
            check(Config.CUSTOM_MAP_ICON_SCALE.get() == value, "valid map scale " + value);
            check(((Number) persisted("common", Config.CUSTOM_MAP_ICON_SCALE)).doubleValue() == value, "map scale persisted " + value);
            AdminSettings.sync(player, false, "test", false);
            assertScalePacket(player.lastAdmin(), value, true, player.server);
        }
        byte[] before = Files.readAllBytes(configFile("common"));
        for (String invalid : List.of("0", "-1", "0.249", "4.001", "9999", "null", "true", "[]", "\"2\"", "\"NaN\"", "1e9999")) {
            editScale(player, "1.0", invalid);
            check(Config.CUSTOM_MAP_ICON_SCALE.get() == 1.0, "reject invalid scale " + invalid);
            check(Arrays.equals(before, Files.readAllBytes(configFile("common"))), "invalid scale leaves file unchanged " + invalid);
        }
        editScale(player, "0.5", "2.0");
        check(Config.CUSTOM_MAP_ICON_SCALE.get() == 1.0, "stale scale edit rejected");
        player.operator = false;
        editScale(player, "1.0", "2.0");
        check(Config.CUSTOM_MAP_ICON_SCALE.get() == 1.0, "non-operator scale edit rejected");
        check(Arrays.equals(before, Files.readAllBytes(configFile("common"))), "non-operator and stale edits leave file unchanged");
        Config.CUSTOM_MAP_ICON_SCALE.set(2.0);
        AdminSettings.sync(player, true, "test", false);
        assertScalePacket(player.lastAdmin(), 2.0, false, player.server);
        check(!player.lastAdmin().open(), "non-operator cannot open editor from sync request");
        check(player.lastAdmin().tag().getList("settings", 10).isEmpty(), "non-operator receives no editable settings");
        Config.CUSTOM_MAP_ICON_SCALE.set(1.0);
        Config.SPEC.save();
        player.operator = true;
        completed.add("map scale boundaries, persistence and wire sync");
    }

    private void editScale(TestPlayer player, String expected, String next) {
        ConfigEditor.handle(player, new ConfigEditor.Action(SCALE_KEY, expected, next));
    }

    private void assertScalePacket(Net.AdminSettingsSync packet, double value, boolean operator, MinecraftServer server) {
        check(packet.tag().getDouble("customMapIconScale") == value, "scale delivered to " + (operator ? "operator" : "non-operator"));
        check(packet.tag().getBoolean("allowed") == operator, "sync permissions correct");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            Net.AdminSettingsSync.CODEC.encode(buffer, packet);
            var decoded = Net.AdminSettingsSync.CODEC.decode(buffer);
            check(decoded.tag().getDouble("customMapIconScale") == value && decoded.open() == packet.open(), "scale survives real packet codec");
        } finally { buffer.release(); }
    }

    private void worldControls(TestPlayer player) throws Exception {
        var server = player.server;
        Map<ServerLevel, Long> times = new LinkedHashMap<>();
        server.getAllLevels().forEach(level -> times.put(level, level.getDayTime()));
        var level = server.overworld();
        boolean rain = level.getLevelData().isRaining(), thunder = level.getLevelData().isThundering();
        try {
            Config.ADMIN_INVENTORY_CONTROLS.set(true);
            player.operator = false;
            long before = level.getDayTime();
            check(!AdminWorldControls.handle(player, "night"), "non-operator night action returns denial");
            check(!AdminWorldControls.handle(player, "weather"), "non-operator weather action returns denial");
            check(level.getDayTime() == before && level.getLevelData().isRaining() == rain && level.getLevelData().isThundering() == thunder, "non-operator world controls denied");
            player.operator = true;
            AdminWorldControls.handle(player, "night");
            check(level.getDayTime() % 24000 == 13000, "night button changes loaded world time");
            AdminWorldControls.handle(player, "day");
            check(level.getDayTime() % 24000 == 1000, "day button changes loaded world time");
            level.setWeatherParameters(6000, 0, false, false);
            AdminWorldControls.handle(player, "weather");
            check(level.getLevelData().isRaining() && !level.getLevelData().isThundering(), "weather cycles clear to rain");
            AdminWorldControls.handle(player, "weather");
            check(level.getLevelData().isRaining() && level.getLevelData().isThundering(), "weather cycles rain to thunder");
            AdminWorldControls.handle(player, "weather");
            check(!level.getLevelData().isRaining() && !level.getLevelData().isThundering(), "weather cycles thunder to clear");
            before = level.getDayTime();
            check(!AdminWorldControls.handle(player, "op SettingsSmoke"), "arbitrary command action rejected");
            check(level.getDayTime() == before && !level.getLevelData().isRaining(), "unknown world action denied");
            Config.ADMIN_INVENTORY_CONTROLS.set(false);
            AdminWorldControls.handle(player, "night");
            AdminWorldControls.handle(player, "weather");
            check(level.getDayTime() == before && !level.getLevelData().isRaining(), "disabled world controls deny operator actions");
            Config.ADMIN_INVENTORY_CONTROLS.set(true);
            var nether = server.getLevel(Level.NETHER);
            check(nether != null && !nether.dimensionType().hasSkyLight(), "no-skylight dimension available");
            var netherPlayer = new TestPlayer(nether, UUID.fromString("259ed496-9dd6-408b-9775-bc6895418957"), "SettingsNether");
            boolean netherRain = nether.getLevelData().isRaining();
            check(!AdminWorldControls.handle(netherPlayer, "weather"), "no-skylight weather returns denial");
            check(!level.getLevelData().isRaining() && nether.getLevelData().isRaining() == netherRain, "no-skylight weather action does not change dimensions");
            check(!netherPlayer.messages.isEmpty(), "no-skylight weather action gives feedback");
        } finally {
            times.forEach(ServerLevel::setDayTime);
            level.setWeatherParameters(6000, 6000, rain, thunder);
        }
        completed.add("admin world controls");
    }

    private void claimBypass(TestPlayer player) throws Exception {
        // Filled out with the optional integration's public API when OPAC is loaded.
        if (!net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) {
            EssConfig.ADMIN_CLAIM_BYPASS.set(true);
            AdminClaimBypass.reconcile(player.server);
            EssConfig.ADMIN_CLAIM_BYPASS.set(false);
            AdminClaimBypass.reconcile(player.server);
            completed.add("optional claim dependency absent safely");
            return;
        }
        claimBypassWithOpac(player);
    }

    @SuppressWarnings("unchecked")
    private void claimBypassWithOpac(TestPlayer player) throws Exception {
        var apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        Object api = apiClass.getMethod("get", MinecraftServer.class).invoke(null, player.server);
        Object claims = apiClass.getMethod("getServerClaimsManager").invoke(api);
        Object protection = apiClass.getMethod("getChunkProtection").invoke(api);
        var claimsApi = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
        var dimension = Level.OVERWORLD.location();
        int x = 321, z = 321;
        Object previous = claimsApi.getMethod("get", net.minecraft.resources.ResourceLocation.class, int.class, int.class)
                .invoke(claims, dimension, x, z);
        check(previous == null, "isolated claim fixture is unclaimed");
        Object claim = claimsApi.getMethod("claim", net.minecraft.resources.ResourceLocation.class, UUID.class,
                int.class, int.class, int.class, boolean.class).invoke(claims, dimension,
                        UUID.fromString("d6bf1f87-3cf6-4fb4-a851-37e6a52bf29e"), 0, x, z, false);
        check(claim != null, "created unrelated owner's protected claim");
        List<ServerPlayer> online = (List<ServerPlayer>) field(net.minecraft.server.players.PlayerList.class, "players").get(player.server.getPlayerList());
        java.util.Set<UUID> passes = (java.util.Set<UUID>) field(protection.getClass(), "fullPasses").get(protection);
        Object serverData = field(protection.getClass(), "serverData").get(protection);
        var interaction = protection.getClass().getMethod("onBlockInteraction",
                Class.forName("xaero.pac.common.server.IServerData"), net.minecraft.world.level.block.state.BlockState.class,
                net.minecraft.world.entity.Entity.class, net.minecraft.world.InteractionHand.class,
                net.minecraft.world.item.ItemStack.class, ServerLevel.class, net.minecraft.core.BlockPos.class,
                net.minecraft.core.Direction.class, boolean.class, boolean.class);
        // Use the same overload as OPAC's actual event handler. Its public simulation API deliberately ignores full passes.
        var pos = new net.minecraft.core.BlockPos(x * 16 + 8, 80, z * 16 + 8);
        Object[] arguments = {serverData, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), player,
                net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY,
                player.server.overworld(), pos, net.minecraft.core.Direction.UP, true, false};
        online.add(player);
        try {
            player.operator = true;
            EssConfig.ADMIN_CLAIM_BYPASS.set(false);
            AdminClaimBypass.reconcile(player.server);
            check(!passes.contains(TEST_ID), "operator has no implicit pass while disabled");
            check((Boolean) interaction.invoke(protection, arguments), "protected block interaction blocked before bypass");
            EssConfig.ADMIN_CLAIM_BYPASS.set(true);
            AdminClaimBypass.reconcile(player.server);
            check(passes.contains(TEST_ID), "enabled bypass grants online operator a real OPAC pass");
            check(!(Boolean) interaction.invoke(protection, arguments), "operator can interact through protected claim when enabled");
            player.operator = false;
            AdminClaimBypass.reconcile(player.server);
            check(!passes.contains(TEST_ID), "de-op revokes OPAC pass immediately");
            check((Boolean) interaction.invoke(protection, arguments), "non-operator still blocked in protected claim");
            player.operator = true;
            AdminClaimBypass.reconcile(player.server);
            check(passes.contains(TEST_ID), "re-op grants OPAC pass again");
            EssConfig.ADMIN_CLAIM_BYPASS.set(false);
            AdminClaimBypass.reconcile(player.server);
            check(!passes.contains(TEST_ID) && (Boolean) interaction.invoke(protection, arguments), "disabling bypass restores protection immediately");
            EssConfig.ADMIN_CLAIM_BYPASS.set(true);
            AdminClaimBypass.reconcile(player.server);
            online.remove(player);
            AdminClaimBypass.reconcile(player.server);
            check(!passes.contains(TEST_ID), "leaving online list revokes pass");
            Object after = claimsApi.getMethod("get", net.minecraft.resources.ResourceLocation.class, int.class, int.class)
                    .invoke(claims, dimension, x, z);
            check(claim.equals(after), "bypass never changes claim ownership");
        } finally {
            online.remove(player);
            EssConfig.ADMIN_CLAIM_BYPASS.set(false);
            AdminClaimBypass.reconcile(player.server);
            claimsApi.getMethod("unclaim", net.minecraft.resources.ResourceLocation.class, int.class, int.class)
                    .invoke(claims, dimension, x, z);
        }
        completed.add("real OPAC protected-claim interaction and revoke");
    }

    private static Path configFile(String suffix) { return FMLPaths.CONFIGDIR.get().resolve("unyieldingquests-" + suffix + ".toml"); }
    private static Object persisted(String suffix, ModConfigSpec.ConfigValue<?> value) {
        try (var config = CommentedFileConfig.of(configFile(suffix))) { config.load(); return config.get(value.getPath()); }
    }
    private void check(boolean passed, String message) {
        assertions++;
        if (!passed) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> requests() throws Exception {
        return (Map<Object, Object>) field(Class.forName("dev.unyielding.quests.essentials.TeleCmds"), "REQUESTS").get(null);
    }
    @SuppressWarnings("unchecked")
    private static Map<Object, Object> cooldowns() throws Exception {
        return (Map<Object, Object>) field(Essentials.class, "COOLDOWNS").get(null);
    }
    private static Object requestFixture() throws Exception {
        var constructor = Class.forName("dev.unyielding.quests.essentials.TeleCmds$Request").getDeclaredConstructor(UUID.class, boolean.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(UUID.randomUUID(), false, System.currentTimeMillis());
    }
    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static final class TestPlayer extends FakePlayer {
        private boolean operator = true;
        private final List<String> messages = new ArrayList<>();
        private final List<CustomPacketPayload> payloads = new ArrayList<>();
        TestPlayer(ServerLevel level, UUID id, String name) {
            super(level, new GameProfile(id, name));
            connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) { record(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { record(packet); }
                private void record(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket custom) payloads.add(custom.payload());
                }
            };
        }
        @Override public boolean hasPermissions(int level) { return level <= (operator ? 4 : 0); }
        @Override public void sendSystemMessage(Component message) { if (messages != null) messages.add(message.getString()); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { sendSystemMessage(message); }
        @Override public void displayClientMessage(Component message, boolean overlay) { sendSystemMessage(message); }
        Net.AdminSettingsSync lastAdmin() {
            for (int i = payloads.size() - 1; i >= 0; i--) if (payloads.get(i) instanceof Net.AdminSettingsSync packet) return packet;
            throw new AssertionError("No AdminSettingsSync packet captured");
        }
    }
}
