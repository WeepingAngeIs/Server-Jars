package dev.unyielding.quests.settings;

import com.google.gson.*;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Explicit, optional adapters for the existing combined mod's server configuration. */
public final class BalanceSettings {
    private static final Gson JSON = new Gson();
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = new TreeMap<>();
    private static final Map<ModConfigSpec.ConfigValue<?>, String> KEYS = new IdentityHashMap<>();
    private static ModConfigSpec balanceSpec;
    private static ModConfigSpec.DoubleValue[] armor;
    private static ModConfigSpec.DoubleValue[][] tiers;
    private static Method balanceSend, soulReset;
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY);

    static {
        optional("cubalance", () -> {
            Class<?> config = Class.forName("dev.unyielding.balance.BalanceConfig");
            balanceSpec = (ModConfigSpec)field(config, "SPEC");
            armor = new ModConfigSpec.DoubleValue[]{(ModConfigSpec.DoubleValue)field(config, "ARMOR_PERCENT"),
                    (ModConfigSpec.DoubleValue)field(config, "TOUGHNESS_PERCENT"),
                    (ModConfigSpec.DoubleValue)field(config, "KNOCKBACK_PERCENT")};
            tiers = (ModConfigSpec.DoubleValue[][])field(config, "TIER_CHANCES");
            balanceSend = Class.forName("dev.unyielding.balance.BalanceSync").getDeclaredMethod("send", ServerPlayer.class);
            balanceSend.setAccessible(true);
            for (String name : List.of("ARMOR_PERCENT", "TOUGHNESS_PERCENT", "KNOCKBACK_PERCENT",
                    "MOD_OVERRIDES", "ITEM_OVERRIDES", "RAID_ENABLED", "NORMAL_IV_MAX")) add("", config, name);
            for (int i = 0; i < 6; i++) for (var value : tiers[i]) add("", value);
        });
        optional("uwuivrebalance", () -> {
            Class<?> config = Class.forName("com.uwustudio.ivrebalance.RebalanceConfig");
            add("egg_ivs", config, "RANDOMIZE_EGG_IVS");
            add("tier_seven", config, "TIER_SEVEN_BONUS_CHANCE");
            add("tier_seven", config, "TIER_SEVEN_BONUS_MAX_IVS");
        });
        optional("cuportal", () -> {
            Class<?> config = Class.forName("dev.unyielding.portal.PortalConfig");
            add("portal", config, "TARGET");
            add("portal", config, "SEARCH_RADIUS");
        });
        optional("cusoultome", () -> {
            Class<?> config = Class.forName("dev.unyielding.soultome.SoulConfig");
            soulReset = config.getDeclaredMethod("reset");
            soulReset.setAccessible(true);
            for (String name : List.of("CAPACITY", "PLAYER_SOULS", "BIND_ALIVE_PERCENT", "NEVER_CAPTURE",
                    "MAX_ACTIVE", "RESPAWN_SECONDS", "CHASE_RANGE", "HUNT_RANGE", "TELEPORT_DISTANCE",
                    "OWNER_CREDIT", "PARTICLES")) add("soul_tome", config, name);
        });
        optional("culeaderboard", () -> {
            Class<?> config = Class.forName("dev.unyielding.leaderboard.LbConfig");
            for (String name : List.of("ROTATE_SECONDS", "REFRESH_SECONDS", "TOP_COUNT", "PAGES", "HIDDEN_PLAYERS",
                    "PODIUM_FOLLOWS_PAGE", "PODIUM_CATEGORY", "PODIUM_VIEW_DISTANCE")) add("leaderboard", config, name);
        });
    }

    private interface Loader { void load() throws ReflectiveOperationException; }
    private static void optional(String mod, Loader loader) {
        if (!ModList.get().isLoaded(mod)) return;
        try { loader.load(); }
        catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Cannot safely expose the installed " + mod + " settings", error);
        }
    }
    private static Object field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }
    private static void add(String prefix, Class<?> config, String name) throws ReflectiveOperationException {
        add(prefix, (ModConfigSpec.ConfigValue<?>)field(config, name));
    }
    private static void add(String prefix, ModConfigSpec.ConfigValue<?> value) {
        String key = (prefix.isEmpty() ? "" : prefix + ".") + String.join(".", value.getPath());
        VALUES.put(key, value);
        KEYS.put(value, key);
    }
    public static void addValues(Map<String, ModConfigSpec.ConfigValue<?>> target) { target.putAll(VALUES); }
    public static boolean available() { return balanceSpec != null && balanceSpec.isLoaded(); }
    public static double[] armorValues() {
        requireBalance();
        return numbers(armor);
    }
    public static double[] raidValues(int tier) {
        return numbers(tier(tier));
    }
    private static double[] numbers(ModConfigSpec.DoubleValue[] values) {
        return Arrays.stream(values).mapToDouble(ModConfigSpec.DoubleValue::get).toArray();
    }
    private static void requireBalance() {
        if (!available()) throw new IllegalArgumentException("Armor and raid balance settings are unavailable on this server.");
    }
    private static ModConfigSpec.DoubleValue[] tier(int tier) {
        requireBalance();
        if (tier < 1 || tier > 7) throw new IllegalArgumentException("Choose a raid tier from 1 to 7.");
        return tiers[tier - 1];
    }
    private static boolean armorSetting(ModConfigSpec.ConfigValue<?> value) {
        return KEYS.getOrDefault(value, "").startsWith("armor.");
    }

    public static void validate(ModConfigSpec.ConfigValue<?> edited, Object next, MinecraftServer server) {
        String key = KEYS.get(edited);
        if (key == null) return;
        if (key.equals("armor.mod_overrides") || key.equals("armor.item_overrides")) {
            Set<String> identifiers = new HashSet<>();
            for (Object entry : (List<?>)next) {
                String[] parts = ((String)entry).split("=", -1);
                if (parts.length != 2) throw new IllegalArgumentException("Use one identifier=percent per override.");
                String id = parts[0].trim();
                boolean item = key.equals("armor.item_overrides");
                if (item ? !id.contains(":") || ResourceLocation.tryParse(id) == null : !id.matches("[a-z0-9_.-]+"))
                    throw new IllegalArgumentException("Use a lowercase " + (item ? "modid:item" : "mod ID") + " in each override.");
                if (item && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)))
                    throw new IllegalArgumentException("Unknown item: " + id);
                double percent;
                try { percent = Double.parseDouble(parts[1].trim()); }
                catch (NumberFormatException error) { throw new IllegalArgumentException("Override percentages must be numbers."); }
                if (!Double.isFinite(percent) || percent < 0 || percent > 300)
                    throw new IllegalArgumentException("Override percentages must be from 0 to 300.");
                if (!identifiers.add(id)) throw new IllegalArgumentException("Duplicate override: " + id);
            }
        }
        if (tiers != null) for (var values : tiers) for (int i = 0; i < values.length; i++) if (values[i] == edited) {
            double[] prospective = numbers(values);
            prospective[i] = ((Number)next).doubleValue();
            validateNumbers(prospective, true);
        }
        if (key.equals("portal.targetDimension")) {
            ResourceLocation id = ResourceLocation.tryParse((String)next);
            if (id == null || server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) == null)
                throw new IllegalArgumentException("Choose an existing loaded dimension, such as minecraft:overworld.");
        }
        if (key.equals("soul_tome.tome.never_capture")) {
            for (Object entry : (List<?>)next) {
                String id = ((String)entry).trim();
                if (!id.matches("[a-z0-9_.-]+:\\*") && (!id.contains(":") || ResourceLocation.tryParse(id) == null))
                    throw new IllegalArgumentException("Use modid:entity or modid:* for blocked captures.");
            }
        }
    }

    private static void validateNumbers(double[] values, boolean raid) {
        if (values.length != 3) throw new IllegalArgumentException("Exactly three percentages are required.");
        for (double value : values) if (!Double.isFinite(value) || value < 0 || value > (raid ? 100 : 300))
            throw new IllegalArgumentException("Percentages must be finite numbers from 0 to " + (raid ? "100." : "300."));
        if (raid && java.math.BigDecimal.valueOf(values[0]).add(java.math.BigDecimal.valueOf(values[1]))
                .add(java.math.BigDecimal.valueOf(values[2])).compareTo(java.math.BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("The three perfect-IV chances cannot total more than 100%.");
    }
    private static double[] parseNumbers(String json, boolean raid) {
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonArray() || parsed.getAsJsonArray().size() != 3)
            throw new IllegalArgumentException("Exactly three percentages are required.");
        double[] values = new double[3];
        for (int i = 0; i < values.length; i++) {
            JsonElement item = parsed.getAsJsonArray().get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Percentages must be JSON numbers.");
            values[i] = item.getAsDouble();
        }
        validateNumbers(values, raid);
        return values;
    }
    public static void saveGroup(ServerPlayer player, ConfigEditor.Action action, boolean raid) {
        requireBalance();
        int tier = 0;
        if (raid) {
            try { tier = Integer.parseInt(action.key().substring("__raid_odds:".length())); }
            catch (NumberFormatException error) { throw new IllegalArgumentException("Choose a raid tier from 1 to 7."); }
        }
        ModConfigSpec.DoubleValue[] values = raid ? tier(tier) : armor;
        double[] before = numbers(values);
        // Validate the expected array's shape/numbers without requiring legacy raid totals to be valid.
        double[] expected = parseNumbers(action.expected(), false);
        if (!Arrays.equals(before, expected)) throw new IllegalArgumentException("These settings changed. Reopen them before saving.");
        double[] next = parseNumbers(action.value(), raid);
        List<EquippedModifiers> equipment = captureEquipment(player.getServer(), Arrays.asList(values));
        try {
            for (int i = 0; i < values.length; i++) values[i].set(next[i]);
            balanceSpec.save();
        } catch (RuntimeException error) {
            for (int i = 0; i < values.length; i++) values[i].set(before[i]);
            try { balanceSpec.save(); }
            catch (RuntimeException rollback) {
                error.addSuppressed(rollback);
                UnyieldingQuests.LOGGER.error("Could not persist restored balance settings", error);
                throw new IllegalStateException("Previous values restored in memory; verify the server config before restarting.", error);
            }
            throw new IllegalStateException("Saving failed. Previous values restored.", error);
        }
        try { changed(player.getServer(), Arrays.asList(values), equipment); }
        catch (RuntimeException error) { throw new IllegalStateException("Saved, but live refresh failed. Reopen settings and check the server log.", error); }
        UnyieldingQuests.LOGGER.info("{} changed {} from {} to {}", player.getGameProfile().getName(),
                raid ? "raid tier " + tier : "armor balance", Arrays.toString(before), Arrays.toString(next));
    }

    public record EquippedModifiers(LivingEntity entity, Map<Holder<Attribute>, Set<ResourceLocation>> ids) {}
    public static List<EquippedModifiers> captureEquipment(MinecraftServer server, Collection<? extends ModConfigSpec.ConfigValue<?>> edited) {
        if (edited.stream().noneMatch(BalanceSettings::armorSetting)) return List.of();
        List<EquippedModifiers> captured = new ArrayList<>();
        for (var level : server.getAllLevels()) for (var entity : level.getAllEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            Map<Holder<Attribute>, Set<ResourceLocation>> ids = new HashMap<>();
            for (EquipmentSlot slot : ARMOR_SLOTS) living.getItemBySlot(slot).forEachModifier(slot, (attribute, modifier) -> {
                if (isArmorAttribute(attribute)) ids.computeIfAbsent(attribute, ignored -> new HashSet<>()).add(modifier.id());
            });
            if (!ids.isEmpty()) captured.add(new EquippedModifiers(living, ids));
        }
        return captured;
    }
    private static boolean isArmorAttribute(Holder<Attribute> attribute) {
        return attribute.equals(Attributes.ARMOR) || attribute.equals(Attributes.ARMOR_TOUGHNESS)
                || attribute.equals(Attributes.KNOCKBACK_RESISTANCE);
    }
    public static void changed(MinecraftServer server, Collection<? extends ModConfigSpec.ConfigValue<?>> edited,
                               List<EquippedModifiers> equipment) {
        if (edited.stream().anyMatch(BalanceSettings::armorSetting)) {
            for (EquippedModifiers previous : equipment) {
                LivingEntity living = previous.entity();
                if (living.isRemoved()) continue;
                previous.ids().forEach((attribute, ids) -> {
                    var instance = living.getAttribute(attribute);
                    if (instance != null) ids.forEach(instance::removeModifier);
                });
                for (EquipmentSlot slot : ARMOR_SLOTS) living.getItemBySlot(slot).forEachModifier(slot, (attribute, modifier) -> {
                    if (!isArmorAttribute(attribute)) return;
                    var instance = living.getAttribute(attribute);
                    if (instance != null) {
                        instance.removeModifier(modifier.id());
                        instance.addTransientModifier(modifier);
                    }
                });
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) invoke(balanceSend, player);
        }
        if (edited.stream().anyMatch(value -> KEYS.getOrDefault(value, "").startsWith("soul_tome."))) invoke(soulReset);
    }
    private static void invoke(Method method, Object... arguments) {
        try { method.invoke(null, arguments); }
        catch (ReflectiveOperationException error) {
            UnyieldingQuests.LOGGER.error("Could not refresh live module settings", error);
            throw new IllegalStateException("Live module refresh failed.", error);
        }
    }
    public static String help(ModConfigSpec.ConfigValue<?> value, String original) {
        String key = KEYS.getOrDefault(value, "");
        if (key.startsWith("armor.")) return original + "\nApplied immediately to equipped armor on loaded entities and synchronized to connected clients. Item overrides take priority over mod overrides.";
        if (key.startsWith("raid_ivs.")) return original + "\nApplies when future raid reward Pokemon are generated. Existing Pokemon are unchanged.";
        if (key.startsWith("leaderboard.")) return original + "\nBoards update on their next refresh; existing podium viewers may need to leave and return.";
        if (key.equals("soul_tome.tome.capacity")) return original + "\nLowering capacity does not delete stored souls. Reopen an existing tome menu to refresh its layout.";
        if (key.equals("soul_tome.allies.max_out_at_once")) return original + "\nApplies to future summons; existing allies are not removed.";
        if (key.startsWith("portal.")) return original + "\nApplies to subsequent portal travel and linking.";
        return original;
    }
    public static void appendSnapshot(CompoundTag data) {
        data.putBoolean("balance_available", available());
        if (!available()) return;
        data.putString("armor_values", JSON.toJson(armorValues()));
        ListTag rows = new ListTag();
        for (int t = 1; t <= 7; t++) {
            double[] values = raidValues(t);
            CompoundTag row = new CompoundTag();
            row.putInt("tier", t);
            row.putString("value", JSON.toJson(values));
            row.putDouble("none", Math.max(0, 100 - Arrays.stream(values).sum()));
            rows.add(row);
        }
        data.put("raid_tiers", rows);
    }
}
