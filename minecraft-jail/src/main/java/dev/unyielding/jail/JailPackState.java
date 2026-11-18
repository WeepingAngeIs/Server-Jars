package dev.unyielding.jail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** Read-only checks for neutral auxiliary state in the separately fingerprinted full pack. */
final class JailPackState {
    private static final Set<String> BASE_ATTACHMENTS = Set.of(
            "neoorigins:origin_data", "neoorigins:resource_state", "accessories:inventory_holder",
            "curios:inventory", "lightmanscurrency:wallet", "lightmanscurrency:event_unlocks");

    private static final String DEFAULTS = "/assets/minecraftjail/unyielding-249-neutral.json";
    private static final JsonObject REVIEWED = defaults();

    private JailPackState() {}

    private static JsonObject defaults() {
        try (var stream = JailPackState.class.getResourceAsStream(DEFAULTS)) {
            if (stream != null) return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return new JsonObject();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static void verifyAttachments(ServerPlayer player, CompoundTag attachments) throws IOException {
        verifyWalletAndResources(attachments);
        if (!JailHumanAudit.hasPackStateContract(player)) {
            if (!BASE_ATTACHMENTS.containsAll(attachments.getAllKeys()))
                throw new IOException("An attached death store is outside the certified runtime");
            return;
        }
        verifyDeathProviders(player);
        for (String key : attachments.getAllKeys()) {
            if (!BASE_ATTACHMENTS.contains(key) && !neutral(REVIEWED.getAsJsonObject("attachments"), key, attachments.get(key)))
                throw new IOException("Unreviewed or active attached death state: " + key);
        }
    }

    // SoulTome.onDeath recalls all active summons. Twilight's respawn handler
    // consumes its persisted charm/casket escrow. Neither store belongs to Jail.
    private static void verifyDeathProviders(ServerPlayer player) throws IOException {
        var persisted = player.getPersistentData();
        if (persisted.contains("PlayerPersisted") && !persisted.contains("PlayerPersisted", Tag.TAG_COMPOUND))
            throw new IOException("Malformed persisted player state");
        verifyTwilightState(persisted.getCompound("PlayerPersisted"));
        verifyPersistent(persisted, REVIEWED.getAsJsonObject("persistent"));
        try {
            var type = Class.forName("dev.unyielding.soultome.Allies", false, JailPackState.class.getClassLoader());
            var hasAllies = type.getDeclaredMethod("hasAllies", UUID.class);
            if (!hasAllies.trySetAccessible() || !Boolean.FALSE.equals(hasAllies.invoke(null, player.getUUID())))
                throw new IOException("Soul Tome summons are active or unavailable. Recall them before admission");
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IOException("Unable to verify active Soul Tome summons", e);
        }
    }

    // PlayerPersisted is carried across a native clone by NeoForge. Other saved mod
    // data outside Jail receipts needs a known neutral shape before lethal custody.
    static void verifyPersistent(CompoundTag data, JsonObject approved) throws IOException {
        for (String key : data.getAllKeys()) {
            if (Set.of("PlayerPersisted", JailVanillaPort.REMOVED, JailVanillaPort.RESTORED,
                    JailNativeOriginPort.RECEIPT, JailXp.RECEIPT, "uq_jail_escape_respawn").contains(key)) continue;
            if (!neutral(approved, key, data.get(key)))
                throw new IOException("Unreviewed non-persisted clone state: " + key);
        }
    }

    static void verifyTwilightState(CompoundTag stored) throws IOException {
        if (stored.contains("TFCharmInventory") || stored.contains("CharmStack") || stored.contains("CasketDamage"))
            throw new IOException("A Twilight Forest charm or keepsake casket recovery is pending");
    }

    static void verifyWalletAndResources(CompoundTag attachments) throws IOException {
        if (attachments.contains("neoorigins:resource_state")
                && (!attachments.contains("neoorigins:resource_state", Tag.TAG_COMPOUND)
                || !attachments.getCompound("neoorigins:resource_state").isEmpty()))
            throw new IOException("Origin resource state is active or malformed; its death lifecycle is not certified");
        if (!attachments.contains("lightmanscurrency:wallet")) return;
        CompoundTag wallet = attachments.getCompound("lightmanscurrency:wallet");
        if (!Set.of("Wallet","Visible").containsAll(wallet.getAllKeys()) || !wallet.contains("Wallet", Tag.TAG_COMPOUND)
                || !wallet.getCompound("Wallet").isEmpty() || wallet.contains("Visible") && !wallet.contains("Visible",Tag.TAG_BYTE))
            throw new IOException("Equipped or unknown currency wallet. Move the equipped wallet to safe storage before admission");
    }

    /** Only exact neutral SNBT strings admitted by the audited server fixture can pass.
     * Carry On's tick counter and selected hotbar slot advance independently of its payload. */
    static boolean neutral(JsonObject signatures, String key, Tag value) {
        if (signatures == null || !signatures.has(key) || value == null) return false;
        Tag normalized = value;
        if (key.equals("carryon:carry_on_data")) {
            if (!(value instanceof CompoundTag carry) || !Set.of("tick", "selected", "keyPressed").containsAll(carry.getAllKeys())
                    || carry.contains("tick") && (!carry.contains("tick",Tag.TAG_INT) || carry.getInt("tick") < 0)
                    || carry.contains("selected") && (!carry.contains("selected",Tag.TAG_INT) || carry.getInt("selected") < 0 || carry.getInt("selected") > 8))
                return false;
            CompoundTag copy = carry.copy();
            copy.remove("tick");
            copy.remove("selected");
            normalized = copy;
        }
        String canonical = normalized.toString();
        for (var signature : signatures.getAsJsonArray(key))
            if (signature.getAsString().equals(canonical)) return true;
        return false;
    }

    static boolean intrinsic(ServerPlayer player, String attribute, String modifier, double amount, String operation) throws IOException {
        var list = REVIEWED.getAsJsonArray("modifiers");
        if (list == null || !Double.isFinite(amount)) return false;
        for (var item : list) {
            var spec = item.getAsJsonObject();
            if (spec.get("attribute").getAsString().equals(attribute) && spec.get("id").getAsString().equals(modifier)
                    && spec.get("operation").getAsString().equals(operation) && Double.compare(spec.get("amount").getAsDouble(), amount) == 0)
                return JailHumanAudit.hasPackStateContract(player);
        }
        return false;
    }
}
