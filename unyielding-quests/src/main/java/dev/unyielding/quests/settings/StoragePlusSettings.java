package dev.unyielding.quests.settings;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Optional, server-side access to Anya Storage Plus's own operator settings transaction. */
final class StoragePlusSettings {
    static final String ID = "anyastorage";
    static final String CONFIRM_REQUIRED = "Confirm this Storage Plus change to continue.";
    private static final String PREFIX = "storageplus.";
    private static final String UNAVAILABLE = "Anya Storage Plus could not confirm this setting. Refresh to read its current value.";
    private static final Set<String> IMMEDIATE = Set.of("expansion.pocketStorage", "expansion.pocketEditing",
            "expansion.tabletOpeningChancePercent", "expansion.chestCameoEnabled",
            "expansion.chestCameoChancePercent", "expansion.backpackCameoEnabled");

    record Page(ListTag rows, int total, int pageIndex) {}
    private record Snapshot(long revision, Map<?, ?> values) {}
    private record Confirmation(String key, String target, long revision, long expires) {}
    private static final Map<ServerPlayer, Confirmation> LEGACY_CONFIRMATIONS = new WeakHashMap<>();

    /** Metadata is supplied by the installed Storage Plus build. */
    record Setting(String key, String type, String title, String description, Number minimum, Number maximum,
                   String unit, String applyMode, Boolean confirmValue) {
        private String kind() {
            return switch (type) {
                case "BOOLEAN" -> "boolean";
                case "INTEGER", "DECIMAL" -> "number";
                case "REGISTRY_ID" -> "registry_id";
                default -> "";
            };
        }

        private boolean readable(Object value) {
            return switch (type) {
                case "BOOLEAN" -> value instanceof Boolean;
                case "INTEGER" -> value instanceof Integer;
                case "DECIMAL" -> value instanceof Double n && Double.isFinite(n);
                case "REGISTRY_ID" -> value instanceof String id && registryId(id);
                default -> false;
            };
        }

        private static boolean registryId(String id) {
            return id.length() <= 256 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
        }

        Object parse(String input) {
            if (input == null || input.isEmpty() || input.length() > 256) return null;
            Object parsed;
            try {
                parsed = switch (type) {
                    case "BOOLEAN" -> "true".equals(input) ? Boolean.TRUE : "false".equals(input) ? Boolean.FALSE : null;
                    case "INTEGER" -> input.matches("-?[0-9]+") ? Integer.valueOf(input) : null;
                    case "DECIMAL" -> input.matches("-?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
                            ? Double.valueOf(input) : null;
                    case "REGISTRY_ID" -> registryId(input) ? input : null;
                    default -> null;
                };
            } catch (NumberFormatException invalid) {
                return null;
            }
            if (!readable(parsed)) return null;
            if (parsed instanceof Number n && ((minimum != null && n.doubleValue() < minimum.doubleValue())
                    || (maximum != null && n.doubleValue() > maximum.doubleValue()))) return null;
            return parsed;
        }

        CompoundTag row(Object current, long revision) {
            var tag = new CompoundTag();
            tag.putString("key", PREFIX + key);
            tag.putString("aliases", key);
            tag.putString("title", title);
            tag.putString("kind", kind());
            tag.putString("note", description);
            if (!unit.isEmpty()) tag.putString("unit", unit);
            tag.putString("scope", "Server installation (Anya Storage Plus)");
            tag.putString("apply", switch (applyMode) {
                case "live" -> "Applies live";
                case "resource_reload" -> "Storage Plus reloads server recipes after saving";
                default -> "Timing depends on Anya Storage Plus";
            });
            if ("live".equals(applyMode)) tag.putString("activation", "LIVE");
            if (confirmValue != null) tag.putBoolean("confirmValue", confirmValue);
            if (minimum != null && Double.isFinite(minimum.doubleValue())) tag.putDouble("min", minimum.doubleValue());
            if (maximum != null && Double.isFinite(maximum.doubleValue())) tag.putDouble("max", maximum.doubleValue());
            tag.putLong("revision", revision);
            tag.putBoolean("available", readable(current));
            if (readable(current)) {
                tag.putString("current", current.toString());
                if (current instanceof Boolean value) tag.putBoolean("value", value);
            } else tag.putString("status", "Anya Storage Plus could not read this setting.");
            return tag;
        }

        private boolean matches(String query) {
            return (title + " " + key + " " + description).toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private record PackApi(Method apply, Method status, Method resultStatus, Method resultMessage, Method resultRevision,
                           Method unit, Method applyMode, Method confirmValue) {
        static PackApi discover(Class<?> config, Class<?> definition) throws ReflectiveOperationException {
            int version;
            try {
                version = config.getField("PACK_SETTINGS_API").getInt(null);
            } catch (NoSuchFieldException olderBuild) {
                return null;
            }
            if (version != 1) throw new NoSuchMethodException("Unsupported Storage Plus settings API " + version);
            var apply = config.getMethod("applyServerSetting", ServerPlayer.class, long.class, String.class, String.class, boolean.class);
            var status = config.getMethod("serverSettingsStatus", ServerPlayer.class);
            var result = status.getReturnType();
            if (result != apply.getReturnType()) throw new NoSuchMethodException("Incompatible settings result");
            return new PackApi(apply, status, result.getMethod("status"), result.getMethod("message"), result.getMethod("revision"),
                    definition.getMethod("unit"), definition.getMethod("applyMode"), definition.getMethod("confirmValue"));
        }

        private String state(ServerPlayer actor) throws ReflectiveOperationException {
            return (String)resultStatus.invoke(status.invoke(null, actor));
        }

        private String feedback(Object result, String state) throws ReflectiveOperationException {
            if (!"rejected".equals(state) && !"recovery_required".equals(state)) return stateMessage(state);
            String text = (String)resultMessage.invoke(result);
            if (text == null || text.isBlank()) return stateMessage(state);
            text = text.replaceAll("\\p{Cntrl}", " ").strip();
            return "Storage Plus: " + text.substring(0, Math.min(text.length(), 200));
        }
    }

    private record Api(Method read, Method revision, Method values, Method set, PackApi pack,
                       Field legacyStatus, Map<String, Setting> settings) {
        private static Optional<Api> discover() {
            try {
                if (!ModList.get().isLoaded(ID)) return Optional.empty();
                ClassLoader loader = StoragePlusSettings.class.getClassLoader();
                Class<?> config = Class.forName("dev.anya.storage.Config", false, loader);
                Class<?> catalog = Class.forName("dev.anya.storage.ServerSettings", false, loader);
                Method read = config.getMethod("serverSettings", ServerPlayer.class);
                Method set = config.getMethod("setServerSetting", ServerPlayer.class, long.class, String.class, String.class);
                Method definition = catalog.getMethod("definition", String.class);
                Class<?> schema = definition.getReturnType();
                var pack = PackApi.discover(config, schema);
                Method getKey = schema.getMethod("key"), getKind = schema.getMethod("kind"),
                        getLabel = schema.getMethod("label"), getHelp = schema.getMethod("description"),
                        getMin = schema.getMethod("minimum"), getMax = schema.getMethod("maximum");
                Map<String, Setting> settings = new LinkedHashMap<>();
                for (Object raw : (List<?>)catalog.getMethod("keys").invoke(null)) {
                    if (!(raw instanceof String key) || !validKey(key)) continue;
                    Object def = definition.invoke(null, key);
                    if (def == null || !key.equals(getKey.invoke(def))) continue;
                    var setting = new Setting(key, ((Enum<?>)getKind.invoke(def)).name(),
                            (String)getLabel.invoke(def), (String)getHelp.invoke(def),
                            (Number)getMin.invoke(def), (Number)getMax.invoke(def),
                            pack == null ? "" : (String)pack.unit.invoke(def),
                            pack == null ? legacyApplyMode(key) : (String)pack.applyMode.invoke(def),
                            pack == null ? legacyConfirmation(key) : (Boolean)pack.confirmValue.invoke(def));
                    if (!setting.kind().isEmpty()) settings.put(key, setting);
                }
                Class<?> snapshotType = read.getReturnType();
                return Optional.of(new Api(read, snapshotType.getMethod("revision"), snapshotType.getMethod("values"),
                        set, pack, pack == null ? config.getField("lastStatus") : null, Collections.unmodifiableMap(settings)));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                return Optional.empty();
            }
        }

        private Snapshot snapshot(ServerPlayer actor) throws ReflectiveOperationException {
            Object result = read.invoke(null, actor);
            if (result == null) return null;
            return new Snapshot((long)revision.invoke(result), (Map<?, ?>)values.invoke(result));
        }
    }

    private static final class Holder {
        private static final Optional<Api> API = Api.discover();
    }

    static String legacyApplyMode(String key) {
        return IMMEDIATE.contains(key) ? "live" : "resource_reload";
    }

    private static Boolean legacyConfirmation(String key) {
        return switch (key) {
            case "expansion.pocketStorage", "expansion.imageModeration" -> Boolean.FALSE;
            case "expansion.imageSharing" -> Boolean.TRUE;
            default -> null;
        };
    }

    private static boolean validKey(String key) {
        return key.length() <= 135 && key.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");
    }

    static boolean owns(String key) {
        return key != null && key.startsWith(PREFIX) && validKey(key.substring(PREFIX.length()));
    }

    private static boolean operator(ServerPlayer actor) {
        return actor != null && actor.server.isSameThread() && actor.hasPermissions(2)
                && actor.server.getPlayerList().getPlayer(actor.getUUID()) == actor;
    }

    static Page rows(ServerPlayer actor, String query, int index, int pageSize) {
        var empty = new Page(new ListTag(), 0, 0);
        if (!operator(actor) || Holder.API.isEmpty()) return empty;
        var api = Holder.API.get();
        try {
            Snapshot snapshot = api.snapshot(actor);
            if (snapshot == null) return empty;
            String filter = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
            var matches = new ArrayList<Setting>();
            for (var setting : api.settings.values()) {
                if (snapshot.values.containsKey(setting.key) && setting.matches(filter)) matches.add(setting);
            }
            int size = Math.clamp(pageSize, 1, 25);
            int page = Math.clamp(index, 0, Math.max(0, matches.size() - 1) / size);
            int start = page * size;
            var entries = new ListTag();
            String state = api.pack == null ? "ready" : api.pack.state(actor);
            for (int i = start; i < Math.min(start + size, matches.size()); i++) {
                Setting setting = matches.get(i);
                CompoundTag row = setting.row(snapshot.values.get(setting.key), snapshot.revision);
                if (!"ready".equals(state)) {
                    row.putBoolean("available", false);
                    row.putString("status", stateMessage(state));
                }
                entries.add(row);
            }
            return new Page(entries, matches.size(), page);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return empty;
        }
    }

    static String change(ServerPlayer actor, String key, String expected, String value, long revision) {
        return change(actor, key, expected, value, revision, false);
    }

    static String change(ServerPlayer actor, String key, String expected, String value, long revision, boolean confirmed) {
        if (!operator(actor)) return ModSettings.Result.DENIED.message;
        if (!owns(key) || revision < 0) return "Invalid Storage Plus settings request.";
        if (Holder.API.isEmpty()) return UNAVAILABLE;
        Api api = Holder.API.get();
        Setting setting = api.settings.get(key.substring(PREFIX.length()));
        if (setting == null) return "Unknown Storage Plus setting.";
        try {
            Snapshot before = api.snapshot(actor);
            if (before == null) return UNAVAILABLE;
            Object previous = before.values.get(setting.key);
            if (!setting.readable(previous)) return UNAVAILABLE;
            if (before.revision != revision || !previous.toString().equals(expected)) return ModSettings.Result.CONFLICT.message;
            Object next = setting.parse(value);
            if (next == null) return "Enter a value within the limits shown for this Storage Plus setting.";
            if (next.equals(previous)) return ModSettings.Result.UNCHANGED.message;
            boolean pending = false;
            if (api.pack != null) {
                Object response = api.pack.apply.invoke(null, actor, revision, setting.key, value, confirmed);
                String state = (String)api.pack.resultStatus.invoke(response);
                if (!"applied".equals(state) && !"pending_reload".equals(state)) return api.pack.feedback(response, state);
                if ((long)api.pack.resultRevision.invoke(response) <= revision) return UNAVAILABLE;
                pending = "pending_reload".equals(state);
            } else {
                Confirmation previousRequest = LEGACY_CONFIRMATIONS.remove(actor);
                if (setting.confirmValue != null && setting.confirmValue.equals(next)) {
                    long now = actor.server.overworld().getGameTime();
                    if (!confirmed || previousRequest == null || previousRequest.expires < now ||
                            previousRequest.revision != revision || !previousRequest.key.equals(key) || !previousRequest.target.equals(value)) {
                        LEGACY_CONFIRMATIONS.put(actor, new Confirmation(key, value, revision, now + 600));
                        return stateMessage("confirmation_required");
                    }
                }
                if (!Boolean.TRUE.equals(api.set.invoke(null, actor, revision, setting.key, value)))
                    return "Storage Plus declined this change. Check its message and refresh.";
                pending = !"live".equals(setting.applyMode);
            }
            Snapshot after = api.snapshot(actor);
            if (after == null || after.revision <= revision || !next.equals(after.values.get(setting.key))) return UNAVAILABLE;
            if (pending) {
                ReloadReceipt.watch(actor, setting.key, previous, next, after.revision);
                return stateMessage("pending_reload");
            }
            ConfigAudit.record(actor, key, previous, after.values.get(setting.key));
            return ModSettings.Result.SAVED.message;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return UNAVAILABLE;
        }
    }

    /** A resource reload may roll the stored value back. Record and announce only its verified outcome. */
    @EventBusSubscriber(modid = UnyieldingQuests.MODID)
    public static final class ReloadReceipt {
        private record Edit(UUID player, String key, Object previous, Object next, long revision, long deadline) {}
        private static final Map<MinecraftServer, List<Edit>> outstanding = new WeakHashMap<>();

        private static void watch(ServerPlayer actor, String key, Object previous, Object next, long revision) {
            outstanding.computeIfAbsent(actor.server, ignored -> new ArrayList<>())
                    .add(new Edit(actor.getUUID(), key, previous, next, revision, actor.server.overworld().getGameTime() + 12000));
        }

        @SubscribeEvent
        public static void afterTick(ServerTickEvent.Post event) {
            var server = event.getServer();
            var edits = outstanding.get(server);
            if (edits == null || server.getTickCount() % 10 != 0) return;
            var api = Holder.API.orElseThrow();
            edits.removeIf(edit -> {
                var actor = server.getPlayerList().getPlayer(edit.player);
                if (!operator(actor)) return true;
                try {
                    // The pre-v1 build exposes the successful commit's exact revision only in its public status line.
                    String state = api.pack == null ? "pending_reload" : api.pack.state(actor);
                    if (api.pack != null && "pending_reload".equals(state) && server.overworld().getGameTime() < edit.deadline)
                        return false;
                    Snapshot current = api.snapshot(actor);
                    if (api.pack == null && ("Applied revision " + edit.revision).equals(api.legacyStatus.get(null)))
                        state = "ready";
                    if ("pending_reload".equals(state) && current != null && current.revision == edit.revision
                            && server.overworld().getGameTime() < edit.deadline) return false;
                    if ("ready".equals(state) && current != null && current.revision == edit.revision &&
                            edit.next.equals(current.values.get(edit.key))) {
                        ConfigAudit.record(actor, PREFIX + edit.key, edit.previous, edit.next);
                        // Older Storage Plus already sends the final transaction message to this player.
                        if (api.pack != null) actor.sendSystemMessage(Component.literal("Storage Plus applied " + api.settings.get(edit.key).title + "."));
                    } else {
                        actor.sendSystemMessage(Component.literal("Storage Plus could not confirm this edit. Refresh to review its current value."));
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    actor.sendSystemMessage(Component.literal(UNAVAILABLE));
                }
                return true;
            });
            if (edits.isEmpty()) outstanding.remove(server);
        }
    }

    private static String stateMessage(String state) {
        return switch (state) {
            case "confirmation_required" -> CONFIRM_REQUIRED;
            case "pending_reload" -> "Storage Plus is applying the change. Refresh after its recipe reload.";
            case "recovery_required" -> "Storage Plus could not recover its config. Check its server log before editing.";
            case "unchanged" -> ModSettings.Result.UNCHANGED.message;
            case "rejected" -> "Storage Plus declined this change. Refresh and review its current settings.";
            default -> UNAVAILABLE;
        };
    }

    private StoragePlusSettings() {}
}
