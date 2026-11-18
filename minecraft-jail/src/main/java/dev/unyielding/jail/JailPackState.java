package dev.unyielding.jail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** Read-only checks for neutral auxiliary state in the separately fingerprinted full pack. */
final class JailPackState {
    private static final Set<String> BASE_ATTACHMENTS = Set.of(
            "neoorigins:origin_data", "neoorigins:resource_state", "accessories:inventory_holder",
            "curios:inventory", "lightmanscurrency:wallet", "lightmanscurrency:event_unlocks");

    private static final String REFORGE_SEED = "apoth_reforge_seed";
    private static final String MANA = "ars_nouveau:mana_cap";
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
        verifyAttachments(player,attachments,true);
    }

    private static void verifyAttachments(ServerPlayer player, CompoundTag attachments, boolean beforeDeath) throws IOException {
        verifyWalletAndResources(attachments);
        boolean fullPack = JailHumanAudit.hasPackStateContract(player);
        boolean food = fullPack || JailHumanAudit.hasItems249Contract(player);
        if (food) JailFoodProgress.validate(player,beforeDeath);
        if (!fullPack) {
            if (attachments.getAllKeys().stream().anyMatch(key -> !BASE_ATTACHMENTS.contains(key) && !(food && JailFoodProgress.attachment(key))))
                throw new IOException("An attached death store is outside the certified runtime");
            return;
        }
        currencyEventUnlocks(attachments);
        verifyDeathProviders(player);
        for (String key : attachments.getAllKeys()) {
            if (!BASE_ATTACHMENTS.contains(key) && !(food && JailFoodProgress.attachment(key)) && !neutral(REVIEWED.getAsJsonObject("attachments"), key, attachments.get(key)))
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
            if (key.equals(REFORGE_SEED)) {
                if (!data.contains(key,Tag.TAG_INT)) throw new IOException("Malformed native reforging seed");
                continue;
            }
            if (Set.of("PlayerPersisted", JailVanillaPort.REMOVED, JailVanillaPort.RESTORED,
                    JailNativeOriginPort.RECEIPT, JailXp.RECEIPT, "uq_jail_escape_respawn").contains(key)) continue;
            if (!neutral(approved, key, data.get(key)))
                throw new IOException("Unreviewed non-persisted clone state: " + key);
        }
    }

    // Compare isolated journal projections. InsaneLib recalculates both fields from the
    // corpse's current block every other tick. Keep the entire saved byte representation,
    // including native inventory, claims and receipts, as the final equality criterion.
    static boolean onlyCorpseLightingDiffers(byte[] observed, byte[] saved) throws IOException {
        CompoundTag liveCopy=JailNativeOriginPort.decode(observed);
        CompoundTag diskCopy=JailNativeOriginPort.decode(saved);
        CompoundTag live=completeLightCache(liveCopy),disk=completeLightCache(diskCopy);
        if(live==null || disk==null || live.equals(disk)) return false;
        live.putInt("sky_light",disk.getInt("sky_light"));
        live.putInt("block_light",disk.getInt("block_light"));
        return java.util.Arrays.equals(JailNativeOriginPort.encode(liveCopy),saved);
    }

    private static CompoundTag completeLightCache(CompoundTag snapshot) throws IOException {
        if (!(snapshot.get("other_persistent") instanceof CompoundTag data)
                || !(data.get("insanelib") instanceof CompoundTag owner)
                || !owner.getAllKeys().equals(Set.of("nbt_tags"))
                || !(owner.get("nbt_tags") instanceof CompoundTag light)
                || !validLightPair(light)) return null;
        if(data.contains("PlayerPersisted") && !data.contains("PlayerPersisted",Tag.TAG_COMPOUND)) return null;
        verifyTwilightState(data.getCompound("PlayerPersisted"));
        verifyPersistent(data,REVIEWED.getAsJsonObject("persistent"));
        return light;
    }

    private static boolean validLightPair(CompoundTag light) {
        if (!light.getAllKeys().equals(Set.of("sky_light","block_light"))) return false;
        for (String name : light.getAllKeys())
            if (!light.contains(name,Tag.TAG_INT) || light.getInt(name)<0 || light.getInt(name)>15) return false;
        return true;
    }

    static void verifyTwilightState(CompoundTag stored) throws IOException {
        if (stored.contains("TFCharmInventory") || stored.contains("CharmStack") || stored.contains("CasketDamage"))
            throw new IOException("A Twilight Forest charm or keepsake casket recovery is pending");
    }

    // Lightman's copies both attachments on native death. Keep only an equality digest in the
    // escape record. A missing fresh attachment has its provider's normal initial value.
    static byte[] currencyCloneDigest(CompoundTag attachments) throws IOException {
        verifyWalletAndResources(attachments);
        CompoundTag state = new CompoundTag();
        CompoundTag wallet = attachments.getCompound("lightmanscurrency:wallet");
        state.putString("unlocked", currencyEventUnlocks(attachments));
        state.putBoolean("visible", !wallet.contains("Visible") || wallet.getBoolean("Visible"));
        return JailOriginTransaction.hash(JailNbt.encode(state));
    }

    private static String currencyEventUnlocks(CompoundTag attachments) throws IOException {
        String key = "lightmanscurrency:event_unlocks";
        if (!attachments.contains(key)) return "";
        CompoundTag value = attachments.getCompound(key);
        if (!value.getAllKeys().equals(Set.of("Unlocked")) || !value.contains("Unlocked", Tag.TAG_STRING))
            throw new IOException("Malformed currency event progress");
        return value.getString("Unlocked");
    }

    // Apotheosis AdventureEvents.clone reads the old getInt and always writes putInt on
    // the new player (absent -> 0). Jail neither creates nor updates the provider's seed.
    static byte[] reforgeDigest(CompoundTag data, boolean replacement) throws IOException {
        if ((replacement || data.contains(REFORGE_SEED)) && !data.contains(REFORGE_SEED,Tag.TAG_INT))
            throw new IOException("Missing or malformed native reforging seed");
        var digestInput=new CompoundTag();
        digestInput.putInt("seed",data.getInt(REFORGE_SEED));
        return JailOriginTransaction.hash(JailNbt.encode(digestInput));
    }

    static void recordClone(ServerPlayer player, CompoundTag attempt) throws IOException {
        JailFoodProgress.record(player,attempt);
        if (!JailHumanAudit.hasPackStateContract(player)) return;
        CompoundTag attachments = player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments");
        attempt.putByteArray("pack_currency_clone", currencyCloneDigest(attachments));
        attempt.putByteArray("pack_reforge_clone",reforgeDigest(player.getPersistentData(),false));
        if (attachments.contains(MANA)) {
            if (!neutral(REVIEWED.getAsJsonObject("attachments"), MANA, attachments.get(MANA)))
                throw new IOException("Mana progression changed before escape");
            attempt.putDouble("pack_mana_charge", attachments.getCompound(MANA).getDouble("current"));
        }
    }

    static void verifyClone(ServerPlayer player, CompoundTag attempt, CompoundTag disk) throws IOException {
        JailFoodProgress.verifyClone(player,attempt,disk);
        if (!JailHumanAudit.hasPackStateContract(player)) return;
        CompoundTag attachments = disk.getCompound("neoforge:attachments");
        verifyAttachments(player, attachments, false);
        byte[] reforge=attempt.getByteArray("pack_reforge_clone");
        if (!java.util.Arrays.equals(reforge,reforgeDigest(player.getPersistentData(),true))
                || !java.util.Arrays.equals(reforge,reforgeDigest(disk.getCompound("NeoForgeData"),true)))
            throw new IOException("Native respawn did not preserve the original reforging seed");
        if (!java.util.Arrays.equals(attempt.getByteArray("pack_currency_clone"), currencyCloneDigest(attachments)))
            throw new IOException("Currency unlock progress or wallet visibility changed during native respawn");
        if (attempt.contains("pack_mana_charge") != attachments.contains(MANA) ||
                attempt.contains("pack_mana_charge") && Double.compare(attempt.getDouble("pack_mana_charge"),
                        attachments.getCompound(MANA).getDouble("current")) != 0)
            throw new IOException("Native respawn did not preserve the original mana charge");
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
        if (key.equals(MANA)) {
            if (!(value instanceof CompoundTag mana) || !mana.contains("current",Tag.TAG_DOUBLE)) return false;
            double current = mana.getDouble("current");
            if (!Double.isFinite(current) || current < 0 || current > 100) return false;
            // Mana ticks regenerate charge. The provider uses NeoForge copyOnDeath. The full
            // charge is separately bound to this escape and compared with the saved replacement.
            CompoundTag copy = mana.copy();
            copy.remove("current");
            normalized = copy;
        }
        if (key.equals("insanelib") && value instanceof CompoundTag owner && owner.contains("nbt_tags")) {
            if (!owner.getAllKeys().equals(Set.of("nbt_tags")) || !owner.contains("nbt_tags", Tag.TAG_COMPOUND))
                return false;
            CompoundTag light = owner.getCompound("nbt_tags");
            if (!light.isEmpty() && !validLightPair(light)) return false;
            // The provider recomputes these two brightness readings every other entity tick.
            // Combat clocks, XP multipliers, ammo modifiers, or any other state remain ineligible.
            normalized = new CompoundTag();
        }
        String canonical = normalized.toString();
        for (var signature : signatures.getAsJsonArray(key))
            if (signature.getAsString().equals(canonical)) return true;
        return false;
    }

    static boolean intrinsic(ServerPlayer player, JailFoodProgress.ModifierAudit food, String attribute, String modifier, double amount, String operation) throws IOException {
        if (JailFoodProgress.candidate(modifier)) return food.owns(attribute,modifier,amount,operation);
        if (!reviewedIntrinsic(attribute,modifier,amount,operation) || !JailHumanAudit.hasPackStateContract(player))
            return false;
        boolean shard=modifier.equals("aether:life_shard_max_health");
        if (!shard && !modifier.equals("ars_nouveau:max_mana_mod") && !modifier.equals("ars_nouveau:max_regen_mod")) return true;
        // Aether and Ars apply these modifiers transiently. A persistent replacement
        // with the same identifier and amount must not inherit their starter-state approval.
        CompoundTag serialized=player.saveWithoutId(new CompoundTag());
        String attachment=shard ? "aether:aether_player" : MANA;
        if (!neutral(REVIEWED.getAsJsonObject("attachments"),attachment,
                serialized.getCompound("neoforge:attachments").get(attachment))) return false;
        for (Tag tag:serialized.getList("attributes",Tag.TAG_COMPOUND)) {
            CompoundTag row=(CompoundTag)tag;
            if (!attribute.equals(row.getString("id"))) continue;
            for (Tag savedModifier:row.getList("modifiers",Tag.TAG_COMPOUND))
                if (modifier.equals(((CompoundTag)savedModifier).getString("id"))) return false;
        }
        if (shard) return true; // The exact accepted Aether projection has integer life_shard_count=0.
        try {
            var config=Class.forName("com.hollingsworth.arsnouveau.setup.config.ServerConfig",false,JailPackState.class.getClassLoader());
            return config.getField("INIT_MANA_REGEN").get(null) instanceof net.neoforged.neoforge.common.ModConfigSpec.IntValue regen && regen.get()==5
                    && config.getField("INIT_MAX_MANA").get(null) instanceof net.neoforged.neoforge.common.ModConfigSpec.IntValue max && max.get()==100;
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IOException("Cannot verify Ars Nouveau starter mana settings",failure);
        }
    }

    static boolean reviewedIntrinsic(String attribute, String modifier, double amount, String operation) {
        var list = REVIEWED.getAsJsonArray("modifiers");
        if (list == null || !Double.isFinite(amount)) return false;
        for (var item : list) {
            var spec = item.getAsJsonObject();
            if (spec.get("attribute").getAsString().equals(attribute) && spec.get("id").getAsString().equals(modifier)
                    && spec.get("operation").getAsString().equals(operation) && Double.compare(spec.get("amount").getAsDouble(), amount) == 0)
                return true;
        }
        return false;
    }
}
