package dev.unyielding.quests.settings;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Server-thread access to registered settings. Only immutable schema data is cached. */
final class NeoForgeSettings {
    private static final Set<String> MANAGED = Set.of("unyieldingquests", "cubalance", "cusoultome", "curanks",
            "cugiant", "culeaderboard", "cuportal", "mawonna", "uwucobblecurrency", "cusubstitute", "uwuivrebalance");
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Pattern KEY = Pattern.compile("registered\\.([a-z][a-z0-9_]{1,63})\\.[0-9a-f]{64}");
    private static final Pattern PRIVATE_KEY = Pattern.compile("pass(word|phrase|wd)|secret|token|credential|"
            + "api[ _.-]?key|private[ _.-]?key|webhook|authenticat|authorization|access[ _.-]?key|pin[ _.-]?code",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Pattern ENUM_NAME = Pattern.compile("[A-Za-z_][A-Za-z_0-9]{0,63}");
    private static final Pattern CAMEL_CASE = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Map<String, Catalog> CATALOGS = new ConcurrentHashMap<>();

    record Page(ListTag rows, int total, int pageIndex) {}
    record Edit(String key, Object before, Object after) {}
    record Registration(String modId, ModConfig.Type type, String filename, ModConfigSpec spec) {}

    private static final class InvalidSetting extends IllegalArgumentException {
        InvalidSetting(String message) { super(message); }
    }

    private static final class SettingUnavailable extends IllegalStateException {
        SettingUnavailable(String message) { super(message); }
    }

    static String feedback(RuntimeException failure) {
        return failure instanceof InvalidSetting || failure instanceof SettingUnavailable
                ? failure.getMessage() : "Could not access this mod’s setting. Refresh and try again.";
    }

    static boolean supported(String modId) {
        if (!eligibleMod(modId)) return false;
        return ModConfigs.getModConfigs(modId).stream().anyMatch(config -> eligibleType(config.getType())
                && config.getSpec() instanceof ModConfigSpec spec && spec.isLoaded());
    }

    static String modId(String key) {
        if (key == null || key.length() > 150) return "";
        var matcher = KEY.matcher(key);
        return matcher.matches() ? matcher.group(1) : "";
    }

    static Page rows(String modId, String query, int pageIndex, int pageSize) {
        return catalog(modId).rows(query, pageIndex, pageSize);
    }

    static List<String> groups(String modId) {
        return catalog(modId).groups;
    }

    static Edit change(String key, String expected, String desired) {
        return catalog(modId(key)).change(key, expected, desired);
    }

    private static boolean eligibleMod(String modId) {
        return modId != null && MOD_ID.matcher(modId).matches() && !MANAGED.contains(modId);
    }

    private static boolean eligibleType(ModConfig.Type type) {
        return type == ModConfig.Type.COMMON || type == ModConfig.Type.SERVER;
    }

    private static Catalog catalog(String modId) {
        if (!eligibleMod(modId)) return Catalog.EMPTY;
        List<Registration> registrations = new ArrayList<>();
        for (var config : ModConfigs.getModConfigs(modId)) {
            if (eligibleType(config.getType()) && config.getSpec() instanceof ModConfigSpec spec && spec.isLoaded()) {
                registrations.add(new Registration(modId, config.getType(), config.getFileName(), spec));
            }
        }
        if (registrations.isEmpty()) return Catalog.EMPTY;
        return CATALOGS.compute(modId, (id, cached) -> cached != null && cached.registrations.equals(registrations)
                ? cached : new Catalog(registrations));
    }

    static final class Catalog {
        private static final Catalog EMPTY = new Catalog(List.of());
        private final List<Registration> registrations;
        private final Map<String, Entry> byKey = new HashMap<>();
        private final List<Entry> ordered;
        private final List<String> groups;

        Catalog(List<Registration> registrations) {
            this.registrations = List.copyOf(registrations);
            Set<String> duplicates = new HashSet<>();
            for (var reg : registrations) {
                if (eligibleMod(reg.modId) && eligibleType(reg.type)) collect(reg, reg.spec.getValues(), duplicates);
            }
            duplicates.forEach(byKey::remove);
            ordered = byKey.values().stream().sorted(Comparator.comparing(Entry::group, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::aliases).thenComparing(Entry::key)).toList();
            groups = ordered.stream().map(Entry::group).filter(group -> !group.isEmpty()).distinct().toList();
        }

        private void collect(Registration reg, UnmodifiableConfig section, Set<String> duplicates) {
            for (var member : section.entrySet()) {
                Object item = member.getValue();
                if (item instanceof UnmodifiableConfig child) {
                    collect(reg, child, duplicates);
                } else if (item instanceof ModConfigSpec.ConfigValue<?> value) {
                    // A third-party default or validator may be unavailable until that mod finishes loading.
                    try {
                        var entry = Entry.describe(reg, value);
                        if (entry != null && byKey.putIfAbsent(entry.key, entry) != null) duplicates.add(entry.key);
                    } catch (RuntimeException ignored) {
                        // Omit this leaf. Other registered controls remain available.
                    }
                }
            }
        }

        Page rows(String query, int pageIndex, int pageSize) {
            int size = Math.clamp(pageSize, 1, 25);
            String filter = query == null ? "" : query.substring(0, Math.min(query.length(), 128)).strip().toLowerCase(Locale.ROOT);
            List<Entry> matches = filter.isEmpty() ? ordered : ordered.stream()
                    .filter(entry -> entry.search.contains(filter)).toList();
            int page = Math.clamp(pageIndex, 0, Math.max(0, matches.size() - 1) / size);
            int offset = page * size;
            ListTag rows = new ListTag();
            for (int i = offset; i < (long) offset + size && i < matches.size(); i++) rows.add(matches.get(i).row());
            return new Page(rows, matches.size(), page);
        }

        Edit change(String key, String expected, String desired) {
            Entry entry = byKey.get(key);
            if (entry == null) throw new InvalidSetting("Unknown or unsupported setting.");
            synchronized (entry.reg.spec) {
                if (!entry.reg.spec.isLoaded()) throw new SettingUnavailable("This config is no longer loaded.");
                Object before = entry.read();
                if (!printed(before).equals(expected)) throw new SettingUnavailable("The value changed. Refresh and try again.");
                Object next = entry.parse(desired);
                if (Objects.equals(next, before)) throw new InvalidSetting("This value is already saved.");
                if (!entry.valid(next)) throw new InvalidSetting("This value is outside the mod’s allowed settings.");
                try {
                    set(entry.value, next);
                    entry.value.save();
                    if (!Objects.equals(entry.read(), next)) throw new SettingUnavailable("Readback failed.");
                } catch (RuntimeException failure) {
                    // Restore only our own write; a reload listener may have changed this value independently.
                    try {
                        if (Objects.equals(entry.value.getRaw(), next)) {
                            set(entry.value, before);
                            entry.value.save();
                        }
                    } catch (RuntimeException rollbackFailed) {
                        throw new SettingUnavailable("The mod could not confirm a save or restore. Refresh before retrying.");
                    }
                    throw new SettingUnavailable("The mod could not confirm the new value. Refresh before retrying.");
                }
                return new Edit(key, before, next);
            }
        }
    }

    private record Entry(Registration reg, ModConfigSpec.ConfigValue<?> value, Class<?> type, String key,
                         String title, String aliases, String search, CompoundTag presentation) {
        String group() { return presentation.getString("group"); }
        static Entry describe(Registration reg, ModConfigSpec.ConfigValue<?> value) {
            List<String> path = value.getPath();
            if (path.isEmpty() || path.size() > 32) return null;
            String aliases = String.join(".", path);
            if (aliases.length() > 256 || aliases.indexOf('/') >= 0 || aliases.indexOf('\\') >= 0
                    || aliases.indexOf('§') >= 0 || aliases.chars().anyMatch(Character::isISOControl)
                    || PRIVATE_KEY.matcher(aliases).find()) return null;
            if (reg.modId.equals("uwudimension") && path.getLast().equalsIgnoreCase("terrainExcavation")) return null;
            Class<?> type = value.getSpec().getClazz();
            if (type == null || type == Object.class) {
                Object defaultValue = value.getDefault();
                if (defaultValue == null) return null;
                type = defaultValue.getClass();
            }
            if (type != Boolean.class && type != Integer.class && type != Long.class && type != Double.class
                    && !(type.isEnum() && value instanceof ModConfigSpec.EnumValue<?>)) return null;
            if (type.isEnum() && type.getEnumConstants().length > 64) return null;
            String title = CAMEL_CASE.matcher(path.getLast().replace('_', ' ').replace('-', ' ')).replaceAll(" ").strip();
            if (title.isEmpty()) return null;
            title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
            var presentation = new CompoundTag();
            presentation.putString("title", title);
            if (ModSettings.UWU.equals(reg.modId) && path.getFirst().equals("weapons")) {
                String comment = value.getSpec().getComment();
                if (comment != null && !comment.isBlank()) presentation.putString("note", comment.substring(0, Math.min(400, comment.length())).strip());
            }
            WeaponSettingsPresentation.decorate(reg.modId, path, presentation);
            title = presentation.getString("title");
            var range = value.getSpec().getRange();
            if (range != null && range.getMin() instanceof Number low && range.getMax() instanceof Number high
                    && Double.isFinite(low.doubleValue()) && Double.isFinite(high.doubleValue()) && low.doubleValue() < high.doubleValue()) {
                presentation.putDouble("min", low.doubleValue());
                presentation.putDouble("max", high.doubleValue());
                presentation.putBoolean("wholeNumber", type == Integer.class || type == Long.class);
            }
            String search = (presentation.getString("group") + " " + title + " " + aliases + " " + presentation.getString("note") + " " + reg.type.name()).toLowerCase(Locale.ROOT);
            return new Entry(reg, value, type, stableKey(reg, path), title, aliases, search, presentation);
        }

        private boolean valid(Object candidate) {
            try {
                return value.getSpec().test(candidate);
            } catch (RuntimeException failure) {
                return false;
            }
        }

        private Object read() {
            try {
                Object current = value.getRaw();
                if (type.isInstance(current) && (!(current instanceof Double d) || Double.isFinite(d))
                        && (!(current instanceof Enum<?> e) || ENUM_NAME.matcher(e.name()).matches())) return current;
            } catch (RuntimeException ignored) {
                // Some custom accessors fail during reload. Never expose arbitrary exception content to clients.
            }
            throw new SettingUnavailable("Unable to read this registered value.");
        }

        private Object parse(String input) {
            if (input == null || input.isEmpty() || input.length() > 64) throw invalid();
            if (type == Boolean.class) {
                if (input.equals("true")) return true;
                if (input.equals("false")) return false;
                throw invalid();
            }
            if (type.isEnum()) {
                for (Object item : type.getEnumConstants()) {
                    String name = ((Enum<?>) item).name();
                    if (ENUM_NAME.matcher(name).matches() && name.equals(input)) return item;
                }
                throw invalid();
            }
            try {
                if (type == Integer.class) return Integer.valueOf(input);
                if (type == Long.class) return Long.valueOf(input);
                if (DECIMAL.matcher(input).matches()) {
                    double number = Double.parseDouble(input);
                    if (Double.isFinite(number)) return number;
                }
            } catch (NumberFormatException ignored) {
                // Use the same safe message for every bad scalar.
            }
            throw invalid();
        }

        private IllegalArgumentException invalid() {
            return new InvalidSetting("Enter a valid " + (type.isEnum() ? "listed choice" :
                    type == Boolean.class ? "on/off value" : type == Double.class ? "finite number" : "whole number") + ".");
        }

        private CompoundTag row() {
            var row = presentation.copy();
            row.putString("key", key);
            row.putString("aliases", aliases);
            row.putString("kind", type.isEnum() ? "enum" : type == Boolean.class ? "boolean" : "number");
            row.putString("scope", reg.type == ModConfig.Type.SERVER ? "Server config (world override if present)" : "Server installation (common config)");
            var timing = switch (value.getSpec().restartType()) {
                case NONE -> ModSettings.Applies.LIVE;
                case WORLD -> ModSettings.Applies.WORLD_RESTART;
                case GAME -> ModSettings.Applies.GAME_RESTART;
            };
            row.putString("activation", timing.name());
            row.putString("apply", timing == ModSettings.Applies.LIVE ? "No restart declared by the mod" : timing.label);
            try {
                Object current = read();
                if (type.isEnum()) {
                    var choices = new ListTag();
                    for (Object e : type.getEnumConstants()) {
                        String name = ((Enum<?>) e).name();
                        if (ENUM_NAME.matcher(name).matches() && valid(e)) choices.add(StringTag.valueOf(name));
                    }
                    row.put("opt", choices);
                    if (choices.isEmpty()) throw new SettingUnavailable("No available choices.");
                }
                row.putString("current", printed(current));
                if (current instanceof Boolean enabled) row.putBoolean("value", enabled);
                row.putBoolean("available", true);
                row.putString("status", "Available");
            } catch (IllegalStateException failure) {
                row.putBoolean("available", false);
                row.putString("status", "Unable to read this setting right now.");
            }
            return row;
        }
    }

    private static String printed(Object value) {
        return value instanceof Enum<?> choice ? choice.name() : value.toString();
    }

    // All callers check the declared type and validator before writing. Files and paths never come from a client.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void set(ModConfigSpec.ConfigValue value, Object next) {
        value.set(next);
    }

    private static String stableKey(Registration reg, List<String> path) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            hashPart(digest, reg.type.name());
            hashPart(digest, reg.filename);
            path.forEach(part -> hashPart(digest, part));
            return "registered." + reg.modId + "." + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static void hashPart(MessageDigest digest, String part) {
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        for (int shift = 24; shift >= 0; shift -= 8) digest.update((byte) (bytes.length >>> shift));
        digest.update(bytes);
    }
}
