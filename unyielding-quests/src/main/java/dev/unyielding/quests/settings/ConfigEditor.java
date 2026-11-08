package dev.unyielding.quests.settings;

import com.google.gson.*;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.essentials.EssConfig;
import dev.unyielding.quests.ultimine.UltimineConfig;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestManager;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;

/** Typed editing of this module's public config values, with server-side validation. */
public final class ConfigEditor {
    private static final Gson JSON = new Gson();
    private static final Map<String, ModConfigSpec.ConfigValue<?>> VALUES = values();
    private static Map<String, ModConfigSpec.ConfigValue<?>> values() {
        Map<String, ModConfigSpec.ConfigValue<?>> result = new TreeMap<>();
        add(result, "quests", Config.class);
        add(result, "essentials", EssConfig.class);
        add(result, "vein_mining", UltimineConfig.class);
        return Collections.unmodifiableMap(result);
    }
    private static void add(Map<String, ModConfigSpec.ConfigValue<?>> result, String group, Class<?> owner) {
        for (var field : owner.getFields()) if (ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
            try {
                var value = (ModConfigSpec.ConfigValue<?>)field.get(null);
                result.put(group + "." + String.join(".", value.getPath()), value);
            } catch (ReflectiveOperationException ex) { throw new ExceptionInInitializerError(ex); }
        }
    }
    public record Action(String key, String expected, String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:config_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(200), Action::key, ByteBufCodecs.stringUtf8(30000), Action::expected,
                ByteBufCodecs.stringUtf8(30000), Action::value, Action::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Sync(CompoundTag data, boolean open) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.parse("unyieldingquests:config_values"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, Sync::data, ByteBufCodecs.BOOL, Sync::open, Sync::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(Action.TYPE, Action.CODEC, (p, c) -> handle((ServerPlayer)c.player(), p));
        r.playToClient(Sync.TYPE, Sync.CODEC, (p, c) -> dev.unyielding.quests.client.ConfigValuesScreen.receive(p));
    }
    public static void handle(ServerPlayer player, Action action) {
        if (!player.hasPermissions(2)) return;
        if (action.key().equals("__open")) { sync(player, true, "Select a setting to edit."); return; }
        if (action.key().equals("__open_odds")) {
            sync(player, true, "Changes apply to future spins and save on the server.", true);
            return;
        }
        if (action.key().startsWith("__odds:")) { saveOdds(player, action); return; }
        var value = VALUES.get(action.key());
        if (value == null) { sync(player, false, "Unknown setting."); return; }
        Object before = value.getRaw();
        boolean saved = false;
        try {
            if (!JSON.toJson(before).equals(action.expected()))
                throw new IllegalArgumentException("This setting changed. Reopen it before saving.");
            Object next = parse(value, action.value());
            if (!value.getSpec().test(next)) throw new IllegalArgumentException("Value is outside the allowed range.");
            validateOddsEdit(value, next);
            if (value == Config.DAILY_TIMEZONE) java.time.ZoneId.of((String)next);
            if (value == Config.SPIN_LEVEL_MIN && (Integer)next > Config.SPIN_LEVEL_MAX.get()
                    || value == Config.SPIN_LEVEL_MAX && (Integer)next < Config.SPIN_LEVEL_MIN.get())
                throw new IllegalArgumentException("Minimum Pokemon level must not exceed maximum level.");
            if (value == EssConfig.RTP_MIN && (Integer)next > EssConfig.RTP_MAX.get()
                    || value == EssConfig.RTP_MAX && (Integer)next < EssConfig.RTP_MIN.get())
                throw new IllegalArgumentException("Minimum teleport radius must not exceed maximum radius.");
            set(value, next);
            try { value.save(); } catch (RuntimeException failure) { set(value, before); throw failure; }
            saved = true;
            if (value == Config.QUEST_BOOK_SOURCE) QuestManager.reload(false);
            AdminSettings.publishChanges();
            sync(player, false, "Saved. " + (value.getSpec().restartType() == ModConfigSpec.RestartType.NONE
                    ? "Changes are active." : "Restart required for this setting."));
        } catch (RuntimeException error) {
            sync(player, false, (saved ? "Saved. Refresh failed; reload quests or restart: " : "Not saved: ")
                    + Objects.toString(error.getMessage(), "Invalid value."));
        }
    }
    private static ModConfigSpec.IntValue[] odds(String group) {
        return switch (group) {
            case "quest" -> new ModConfigSpec.IntValue[]{Config.SPIN_BASIC, Config.SPIN_GOOD,
                    Config.SPIN_PARADOX, Config.SPIN_MYTHICAL, Config.SPIN_LEGENDARY};
            case "daily" -> new ModConfigSpec.IntValue[]{Config.DAILY_BASIC, Config.DAILY_GOOD,
                    Config.DAILY_PARADOX, Config.DAILY_MYTHICAL, Config.DAILY_LEGENDARY};
            default -> throw new IllegalArgumentException("Unknown spin group.");
        };
    }
    private static int[] weights(ModConfigSpec.IntValue[] values) {
        return Arrays.stream(values).mapToInt(ModConfigSpec.IntValue::get).toArray();
    }
    private static void setOdds(ModConfigSpec.IntValue[] values, int[] weights) {
        for (int i = 0; i < values.length; i++) values[i].set(weights[i]);
    }
    private static void validateOddsEdit(ModConfigSpec.ConfigValue<?> edited, Object next) {
        for (String group : List.of("quest", "daily")) {
            var values = odds(group);
            for (int i = 0; i < values.length; i++) if (values[i] == edited) {
                int[] weights = weights(values);
                weights[i] = (Integer)next;
                SpinOdds.validate(weights);
                return;
            }
        }
    }
    private static void saveOdds(ServerPlayer player, Action action) {
        boolean saved = false;
        try {
            String group = action.key().substring("__odds:".length());
            var values = odds(group);
            int[] before = weights(values);
            SpinOdds.requireUnchanged(before, action.expected());
            int[] next = SpinOdds.parse(action.value());
            try {
                setOdds(values, next);
                Config.SPEC.save();
            } catch (RuntimeException failure) {
                setOdds(values, before);
                // Saving can write the file before a reload listener fails. Persist the rollback too.
                try { Config.SPEC.save(); }
                catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    UnyieldingQuests.LOGGER.error("Could not persist restored {} spin chances", group, failure);
                    throw new IllegalStateException("Previous chances restored in memory, but saving failed. Check the server config before restarting.", failure);
                }
                UnyieldingQuests.LOGGER.error("Could not save {} spin chances", group, failure);
                throw new IllegalStateException("Could not save spin chances. Previous chances restored.", failure);
            }
            saved = true;
            UnyieldingQuests.LOGGER.info("{} changed {} spin weights from {} to {}",
                    player.getGameProfile().getName(), group, Arrays.toString(before), Arrays.toString(next));
            AdminSettings.publishChanges();
            sync(player, false, "Saved. " + (group.equals("daily") ? "Daily" : "Quest") + " spin chances are active.");
        } catch (RuntimeException error) {
            sync(player, false, (saved ? "Saved. Refresh failed; reopen settings: " : "Not saved: ")
                    + Objects.toString(error.getMessage(), "Invalid chances."));
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(ModConfigSpec.ConfigValue value, Object next) { value.set(next); }
    static Object parse(ModConfigSpec.ConfigValue<?> value, String text) {
        JsonElement json = JsonParser.parseString(text);
        Object sample = value.getDefault();
        if (sample instanceof List<?>) {
            if (!json.isJsonArray() || json.getAsJsonArray().size() > 512) throw new IllegalArgumentException("Use up to 512 list entries.");
            List<String> list = new ArrayList<>();
            for (JsonElement item : json.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("List entries must be text.");
                if (item.getAsString().length() > 1024) throw new IllegalArgumentException("Entry is too long.");
                list.add(item.getAsString());
            }
            return list;
        }
        if (!json.isJsonPrimitive()) throw new IllegalArgumentException("Enter a single value.");
        JsonPrimitive primitive = json.getAsJsonPrimitive();
        if (sample instanceof Boolean && primitive.isBoolean()) return primitive.getAsBoolean();
        if (sample instanceof Integer && primitive.isNumber()) return primitive.getAsBigDecimal().intValueExact();
        if (sample instanceof Long && primitive.isNumber()) return primitive.getAsBigDecimal().longValueExact();
        if (sample instanceof Double && primitive.isNumber() && Double.isFinite(primitive.getAsDouble())) return primitive.getAsDouble();
        if (sample instanceof String && primitive.isString()) return primitive.getAsString();
        throw new IllegalArgumentException("Incorrect value type.");
    }
    public static void sync(ServerPlayer player, boolean open, String message) {
        sync(player, open, message, false);
    }
    private static void sync(ServerPlayer player, boolean open, String message, boolean oddsOpen) {
        if (!player.hasPermissions(2)) return;
        CompoundTag data = new CompoundTag();
        ListTag rows = new ListTag();
        VALUES.forEach((key, value) -> {
            CompoundTag row = new CompoundTag();
            row.putString("key", key);
            row.putString("value", JSON.toJson(value.getRaw()));
            row.putString("help", Objects.toString(value.getSpec().getComment(), ""));
            rows.add(row);
        });
        data.put("rows", rows);
        data.putString("message", message);
        data.putBoolean("oddsOpen", oddsOpen);
        Net.sendTo(player, new Sync(data, open));
    }
}
