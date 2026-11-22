package dev.unyielding.tests;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.unyielding.quests.settings.ConfigEditor;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Dedicated-server operator and guest use the live menu payload and native provider commands. */
@Mod("configtopia_optional_probe")
public final class OptionalControlsProbe {
    private static final String STORAGE = "anyastorage";
    private static final String UWU = "uwudimension";
    private static final String TABLET = "expansion.tabletOpeningChancePercent";
    private static final String CMD = "anya storage settings ";
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public OptionalControlsProbe() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (done || server.getTickCount() < 10) return;
        done = true;
        String mode = System.getenv("UQ_OPTIONAL_MODE"), phase = System.getenv("UQ_OPTIONAL_PHASE");
        String failure = "";
        try {
            check(server.isSameThread(), "Interactions run on the authoritative server thread");
            Actor admin = new Actor(server, "ConfigtopiaOp", true);
            Actor guest = new Actor(server, "ConfigtopiaGuest", false);
            boolean withStorage = mode.startsWith("storage") || mode.equals("both");
            boolean withUwu = mode.equals("uwu") || mode.equals("both");
            check(ModList.get().isLoaded(STORAGE) == withStorage && ModList.get().isLoaded(UWU) == withUwu,
                    "Real optional provider set matches " + mode);
            CompoundTag open = admin.action("__mod_settings", "", "");
            check(open != null && open.getString("page").equals("mod_settings"), "Operator opened Add Mod Settings");
            check(guest.action("__mod_settings", "", "") == null, "Guest could not open server admin settings");
            if (!withStorage) absent(admin, open, STORAGE);
            if (!withUwu) absent(admin, open, UWU);
            if (withStorage) storage(admin, guest, phase);
            if (withUwu) uwu(admin, guest, phase);
        } catch (Throwable error) {
            failure = error.toString();
            error.printStackTrace();
        } finally {
            try {
                var report = Map.of("status", failure.isEmpty() ? "PASS" : "FAIL", "error", failure,
                        "phase", phase, "mode", mode, "checks", checks);
                Files.writeString(Path.of(System.getenv("UQ_OPTIONAL_RESULT")), new Gson().toJson(report));
            } catch (Exception writeError) { writeError.printStackTrace(); }
            server.halt(false);
        }
    }

    private void check(boolean condition, String evidence) {
        if (!condition) throw new AssertionError(evidence);
        checks.add(evidence);
    }

    private static CompoundTag mod(CompoundTag page, String id) {
        if (page == null) return null;
        for (String list : List.of("mods", "installedMods")) {
            for (Tag raw : page.getList(list, Tag.TAG_COMPOUND)) {
                CompoundTag item = (CompoundTag) raw;
                if (item.getString("id").equals(id)) return item;
            }
        }
        return null;
    }

    private void absent(Actor admin, CompoundTag open, String id) {
        check(mod(open, id) == null, "Absent " + id + " has no phantom catalog entry");
        var response = admin.action("__mod_settings_add", "", id);
        check(response != null && response.getString("detailId").isEmpty() && mod(response, id) == null,
                "Absent " + id + " cannot acquire a settings page");
    }

    private static JsonObject query(String text) {
        var json = new JsonObject();
        json.addProperty("q", text);
        json.addProperty("p", 0);
        return json;
    }

    private CompoundTag row(Actor admin, String id, String term, String publicPath) {
        var request = query(term);
        List<CompoundTag> matches = new ArrayList<>();
        int pages = 1;
        for (int index = 0; index < pages; index++) {
            request.addProperty("p", index);
            var result = admin.action("__mod_settings_page", id, request.toString());
            var owner = mod(result, id);
            if (owner == null) throw new AssertionError("No settings response for " + id);
            pages = Math.max(1, result.getInt("pages"));
            if (pages > 100) throw new AssertionError("Unexpected page count: " + pages);
            for (Tag raw : owner.getList("rows", Tag.TAG_COMPOUND)) {
                var item = (CompoundTag) raw;
                if (item.getString("aliases").equals(publicPath)
                        && (!id.equals(STORAGE) || item.getString("key").equals("storageplus." + publicPath)))
                    matches.add(item);
            }
        }
        if (matches.size() == 1 && matches.getFirst().getBoolean("available")) return matches.getFirst();
        throw new AssertionError("Expected one editable " + id + "/" + publicPath + "; got " + matches);
    }

    private CompoundTag write(Actor actor, String id, String term, CompoundTag row, String proposed) {
        return write(actor, id, term, row, proposed, false);
    }

    private CompoundTag write(Actor actor, String id, String term, CompoundTag row, String proposed, boolean confirmed) {
        var json = query(term);
        if (confirmed) json.addProperty("confirmed", true);
        json.addProperty("key", row.getString("key"));
        json.addProperty("expected", row.getString("current"));
        json.addProperty("value", proposed);
        if (row.contains("revision")) json.addProperty("revision", row.getLong("revision"));
        return actor.action("__mod_settings_write", id, json.toString());
    }

    private static Object storageSnapshot(Actor actor) throws Exception {
        var config = Class.forName("dev.anya.storage.Config");
        return config.getMethod("serverSettings", ServerPlayer.class).invoke(null, actor);
    }

    private static String tablet(Actor actor) throws Exception {
        Object snapshot = storageSnapshot(actor);
        var values = (Map<?, ?>) snapshot.getClass().getMethod("values").invoke(snapshot);
        return values.get(TABLET).toString();
    }

    private void storage(Actor admin, Actor guest, String phase) throws Exception {
        var add = admin.action("__mod_settings_add", "", STORAGE);
        check(add != null && STORAGE.equals(add.getString("detailId")), "Storage Plus page selected");
        String term = "tabletOpeningChancePercent";
        CompoundTag original = row(admin, STORAGE, term, TABLET);
        check(original.getString("key").equals("storageplus." + TABLET)
                && original.getString("current").equals(tablet(admin)) && original.contains("revision", Tag.TAG_LONG),
                "Unique menu row reads authoritative revisioned storage value");
        check(original.getDouble("min") == 0 && original.getDouble("max") == 100, "Storage range 0 to 100");
        check(storageSnapshot(guest) == null, "Provider denies guest access");
        if (phase.equals("reopen")) {
            check(tablet(admin).equals("81"), "Provider and menu reopened at 81 after dedicated server restart");
            return;
        }
        var definitions = (List<?>) Class.forName("dev.anya.storage.ServerSettings").getMethod("keys").invoke(null);
        for (Object id : definitions) {
            String key = (String) id;
            check(row(admin, STORAGE, key, key).getString("key").equals("storageplus." + key), "Owner key exposed once: " + key);
        }
        check(admin.command(CMD + "list") == 1 && admin.heard(TABLET), "Native Storage list includes the control");
        check(guest.action("__mod_settings_add", "", STORAGE) == null &&
                write(guest, STORAGE, term, original, "99") == null && tablet(admin).equals(original.getString("current")),
                "Guest menu writes are denied");
        check(guest.command(CMD + "set " + TABLET + " 99") != 1, "Native Storage set denied to guests");
        write(admin, STORAGE, term, original, "83");
        check(tablet(admin).equals("83") && admin.command(CMD + "get " + TABLET) == 1 && admin.heard(TABLET + " = 83"),
                "Menu write visible immediately in Storage API and native get");
        var old = row(admin, STORAGE, term, TABLET);
        check(admin.command(CMD + "set " + TABLET + " 67") == 1
                && row(admin, STORAGE, term, TABLET).getString("current").equals("67"), "Native Storage set visible in menu");
        check(admin.command(CMD + "set " + TABLET + " 83") == 1, "Native Storage change advanced provider revision");
        write(admin, STORAGE, term, old, "84");
        check(tablet(admin).equals("83"), "Stale menu revision rejected even after value restored");
        var fresh = row(admin, STORAGE, term, TABLET);
        write(admin, STORAGE, term, fresh, "101");
        check(tablet(admin).equals("83"), "Out-of-range storage value rejected");
        write(admin, STORAGE, term, fresh, "81");
        check(tablet(admin).equals("81"), "Fresh storage write accepted");
        var config = FMLPaths.CONFIGDIR.get().resolve("anya-storage-plus.json");
        var json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(json.getAsJsonObject("expansion").get("tabletOpeningChancePercent").getAsInt() == 81,
                "Storage Plus saved its own JSON");
        String sensitive = "expansion.pocketStorage";
        check(admin.command(CMD + "set " + sensitive + " true") == 1, "Native pocket enable accepted");
        var enabled = row(admin, STORAGE, sensitive, sensitive);
        check(enabled.getBoolean("value") && enabled.contains("confirmValue"), "Sensitive switch carries provider confirmation metadata");
        var pending = write(admin, STORAGE, sensitive, enabled, "false");
        check(pending != null && pending.getString("confirmKey").equals(enabled.getString("key")) &&
                row(admin, STORAGE, sensitive, sensitive).getBoolean("value"), "First disable prompts and preserves pocket access");
        write(admin, STORAGE, sensitive, enabled, "false", true);
        check(!row(admin, STORAGE, sensitive, sensitive).getBoolean("value"), "Explicit second request confirms pocket disable");
    }

    private void uwu(Actor admin, Actor guest, String phase) throws Exception {
        var add = admin.action("__mod_settings_add", "", UWU);
        check(add != null && UWU.equals(add.getString("detailId")), "UwU Dimension page selected");
        String key = "cupidBowCharmSeconds";
        String command = "uwu cupid charmseconds";
        var charm = row(admin, UWU, key, key);
        var policy = Class.forName("dev.uwu.dimension.AcquisitionPolicy");
        var getCharm = policy.getMethod("cupidBowCharmSeconds");
        var getDusk = policy.getMethod("duskVillageChancePercent");
        check(charm.getString("key").startsWith("registered.uwudimension.")
                && charm.getString("current").equals(getCharm.invoke(null).toString()),
                "Cupid row shares UwU's registered server value");
        check(charm.getInt("min") == 1 && charm.getInt("max") == Integer.MAX_VALUE / 20
                && charm.getString("activation").equals("LIVE"), "Cupid uses provider range, units and live scope");
        String dirkKey = "weapons.returnbloom_dirk.turnBackCooldownSeconds";
        String dirkCommand = "uwu weapon returnbloom_dirk turnBackCooldownSeconds";
        var dirk = row(admin, UWU, dirkKey, dirkKey);
        for (String other : List.of("baseHitDamage", "throwCooldownSeconds", "throwDamage", "backstabMultiplier")) {
            String path = "weapons.returnbloom_dirk." + other;
            check(row(admin, UWU, path, path).getString("kind").equals("number"), "Dirk exposes " + other);
        }
        String shared = "weapons.blossom_revival.rechargeSeconds";
        check(row(admin, UWU, shared, shared).getString("kind").equals("number"), "Shared fatal-hit recharge is discoverable");
        String chance = "duskVillageChancePercent";
        var dusk = row(admin, UWU, chance, chance);
        if (phase.equals("reopen")) {
            check(getCharm.invoke(null).equals(93) && charm.getString("current").equals("93"),
                    "Cupid charm saved across dedicated restart");
            check(Double.parseDouble(dirk.getString("current")) == 7.35, "Fractional Dirk seconds persisted across restart");
            check(getDusk.invoke(null).equals(100) && dusk.getString("current").equals("100"), "Dusk 100% persisted across restart");
            return;
        }
        check(charm.getString("current").equals("40"), "Real UwU default is 40 seconds");
        check(write(guest, UWU, key, charm, "11") == null && guest.command(command + " 11") != 1
                && getCharm.invoke(null).equals(40), "Guest cannot change Cupid via menu or command");
        write(admin, UWU, key, charm, "638");
        check(getCharm.invoke(null).equals(638) && admin.command(command) == 1 && admin.heard("638"),
                "Typed 638 seconds reaches the live getter and native Cupid command immediately");
        write(admin, UWU, key, charm, "90");
        check(getCharm.invoke(null).equals(638), "Stale Cupid edit is rejected");
        var current = row(admin, UWU, key, key);
        write(admin, UWU, key, current, "0");
        write(admin, UWU, key, current, "0.5");
        check(getCharm.invoke(null).equals(638), "Invalid fractional and zero Cupid durations are rejected");
        check(admin.command(command + " 93") == 1 && row(admin, UWU, key, key).getString("current").equals("93"),
                "Native Cupid command updates the same menu setting");
        write(admin, UWU, dirkKey, dirk, "8.35");
        check(admin.command(dirkCommand) == 1 && admin.heard("8.35"), "Menu accepts fractional Dirk cooldown seconds");
        check(admin.command(dirkCommand + " 7.35") == 1
                && Double.parseDouble(row(admin, UWU, dirkKey, dirkKey).getString("current")) == 7.35,
                "Native Dirk command writes back to the same menu");
        check(dusk.getString("current").equals("75") && dusk.getInt("min") == 0 && dusk.getInt("max") == 100,
                "Dusk default 75, both endpoints editable");
        var dimension = admin.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse("uwudimension:sakura_reach")));
        var level = net.minecraft.server.level.ServerLevel.class;
        var groves = Class.forName("dev.uwu.dimension.GroveSites");
        Object site = groves.getMethod("site", level, int.class, int.class).invoke(null, dimension, 87, 93);
        var layout = Class.forName("dev.uwu.dimension.SettlementLayout");
        var futureChance = layout.getMethod("duskChanceForSite", level, site.getClass());
        check(Boolean.FALSE.equals(layout.getMethod("duskChanceFrozen", level, site.getClass()).invoke(null, dimension, site)),
                "Verified untouched Dusk site for live policy check");
        for (int percent : List.of(0, 100)) {
            write(admin, UWU, chance, row(admin, UWU, chance, chance), Integer.toString(percent));
            check(getDusk.invoke(null).equals(percent) && futureChance.invoke(null, dimension, site).equals(percent),
                    "Menu reached the native untouched Dusk site policy at " + percent + "%");
        }
        var configs = net.neoforged.fml.config.ModConfigs.getModConfigs(UWU).stream()
                .filter(c -> c.getType() == net.neoforged.fml.config.ModConfig.Type.SERVER
                        && c.getFileName().equals("uwudimension-server.toml")).toList();
        check(configs.size() == 1, "Single authoritative UwU TOML");
        String toml = Files.readString(configs.getFirst().getFullPath());
        check(toml.matches("(?s).*cupidBowCharmSeconds\\s*=\\s*93\\b.*")
                && toml.matches("(?s).*duskVillageChancePercent\\s*=\\s*100\\b.*"),
                "UwU's own loaded TOML saved Cupid and Dusk");
    }

    private static final class Actor extends ServerPlayer {
        private final boolean operator;
        private final List<String> messages = new ArrayList<>();
        private ConfigEditor.Sync reply;

        Actor(MinecraftServer server, String name, boolean operator) {
            super(server, server.overworld(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name),
                    ClientInformation.createDefault());
            this.operator = operator;
            var transport = new Connection(PacketFlow.SERVERBOUND) {
                @Override public boolean isMemoryConnection() { return true; }
                @Override public void send(Packet<?> packet, PacketSendListener listener, boolean flush) {
                    if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof ConfigEditor.Sync sync)
                        reply = sync;
                }
            };
            new EmbeddedChannel(transport);
            NetworkRegistry.configureMockConnection(transport);
            server.getPlayerList().placeNewPlayer(transport, this, CommonListenerCookie.createInitial(getGameProfile(), false));
        }

        @Override public boolean hasPermissions(int level) { return level <= (operator ? 2 : 0); }
        @Override public void sendSystemMessage(Component message) { if (messages != null) messages.add(message.getString()); }
        @Override public void sendSystemMessage(Component message, boolean overlay) { sendSystemMessage(message); }
        boolean heard(String text) { return messages.stream().anyMatch(s -> s.contains(text)); }
        int command(String text) throws CommandSyntaxException {
            messages.clear();
            var src = createCommandSourceStack().withPermission(operator ? 2 : 0);
            try { return getServer().getCommands().getDispatcher().execute(text, src); }
            catch (CommandSyntaxException failure) { if (operator) throw failure; return 0; }
        }
        CompoundTag action(String key, String expected, String value) {
            reply = null;
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), getServer().registryAccess());
            try {
                ConfigEditor.Action.CODEC.encode(buf, new ConfigEditor.Action(key, expected, value));
                ConfigEditor.handle(this, ConfigEditor.Action.CODEC.decode(buf));
                if (reply == null) return null;
                buf.clear();
                ConfigEditor.Sync.CODEC.encode(buf, reply);
                return ConfigEditor.Sync.CODEC.decode(buf).data();
            } finally { buf.release(); }
        }
    }
}
