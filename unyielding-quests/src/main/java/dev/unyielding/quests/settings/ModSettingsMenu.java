package dev.unyielding.quests.settings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The server owns the catalogue, the saved mod pages and every config value. */
final class ModSettingsMenu {
    static final String PAGE = "mod_settings";
    private static final String OPEN = "__mod_settings";
    private static final String ADD = "__mod_settings_add";
    private static final String DETAIL = "__mod_settings_page";
    private static final String WRITE = "__mod_settings_write";
    private static final int PAGE_SIZE = 10;
    private static final Set<String> QUERY_FIELDS = Set.of("p", "q");
    private static final Set<String> EDIT_FIELDS = Set.of("p", "q", "key", "expected", "value");

    private static final Set<String> REVISION_EDIT_FIELDS = Set.of("p", "q", "key", "expected", "value", "revision");

    static boolean handles(String key) {
        return OPEN.equals(key) || ADD.equals(key) || DETAIL.equals(key) || WRITE.equals(key) || key.startsWith("external.");
    }

    static boolean undoableKey(String key) {
        return ModSettings.KEY.equals(key) || !NeoForgeSettings.modId(key).isEmpty();
    }

    record Request(String query, int index, String key, String expected, String desired, long revision, boolean confirmed) {
        static Request parse(String text, boolean edit) {
            if (text.length() > 1600) throw new IllegalArgumentException();
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            String key = edit ? string(json, "key", 150) : "";
            boolean revisioned = edit && StoragePlusSettings.owns(key);
            Set<String> fields = revisioned ? REVISION_EDIT_FIELDS : edit ? EDIT_FIELDS : QUERY_FIELDS;
            boolean confirmed = false;
            if (revisioned && json.has("confirmed")) {
                var flag = json.remove("confirmed").getAsJsonPrimitive();
                if (!flag.isBoolean()) throw new IllegalArgumentException();
                confirmed = flag.getAsBoolean();
            }
            if (!json.keySet().equals(fields)) throw new IllegalArgumentException();
            long revision = -1;
            if (revisioned) {
                var token = json.getAsJsonPrimitive("revision");
                if (!token.isNumber() || !token.getAsString().matches("[0-9]{1,19}")) throw new IllegalArgumentException();
                revision = Long.parseLong(token.getAsString());
            }
            var number = json.getAsJsonPrimitive("p");
            if (!number.isNumber() || !number.getAsString().matches("[0-9]{1,7}")) throw new IllegalArgumentException();
            return new Request(string(json, "q", 96), number.getAsInt(), key,
                    edit ? string(json, "expected", revisioned ? 256 : 128) : "",
                    edit ? string(json, "value", revisioned ? 256 : 128) : "", revision, confirmed);
        }

        private static String string(JsonObject json, String name, int limit) {
            var value = json.getAsJsonPrimitive(name);
            if (!value.isString() || value.getAsString().length() > limit) throw new IllegalArgumentException();
            return value.getAsString();
        }
    }

    static void handle(ServerPlayer player, ConfigEditor.Action action) {
        if (!player.hasPermissions(2)) return;
        var selections = Selections.get(player);
        var selected = selections.mods.getOrDefault(player.getUUID(), Set.of());
        String detail = "", query = "", message = "";
        int index = 0;
        boolean open = false;
        String confirmKey = "";
        long confirmRevision = -1;
        switch (action.key()) {
            case OPEN -> {
                if (!action.expected().isEmpty() || !action.value().isEmpty()) return;
                open = true;
            }
            case ADD -> {
                String id = action.value();
                if (action.expected().isEmpty() && installed(id)) {
                    selected = selections.mods.computeIfAbsent(player.getUUID(), unused -> new HashSet<>());
                    if (selected.add(id)) selections.setDirty();
                    detail = id;
                } else message = "That mod is unavailable.";
            }
            case DETAIL, WRITE -> {
                String id = action.expected();
                if (!selected.contains(id) || !installed(id)) {
                    message = "Add this installed mod to your settings first.";
                    break;
                }
                detail = id;
                boolean edit = action.key().equals(WRITE);
                Request request;
                try { request = Request.parse(action.value(), edit); }
                catch (RuntimeException failure) {
                    message = "Invalid settings request.";
                    break;
                }
                query = request.query;
                index = request.index;
                if (edit) {
                    if (id.equals(ModSettings.UWU) && ModSettings.KEY.equals(request.key)) {
                        message = changeExcavation(player, true, request.expected, request.desired);
                    } else if (id.equals(StoragePlusSettings.ID) && StoragePlusSettings.owns(request.key)) {
                        message = StoragePlusSettings.change(player, request.key, request.expected, request.desired, request.revision, request.confirmed);
                        if (StoragePlusSettings.CONFIRM_REQUIRED.equals(message)) {
                            confirmKey = request.key;
                            confirmRevision = request.revision;
                        }
                    } else if (id.equals(NeoForgeSettings.modId(request.key))) {
                        message = changeRegistered(player, request.key, request.expected, request.desired);
                    } else message = "This setting does not belong to the selected mod.";
                }
            }
            case ModSettings.KEY -> {
                boolean added = selected.contains(ModSettings.UWU);
                message = changeExcavation(player, added, action.expected(), action.value());
                if (added) detail = ModSettings.UWU;
            }
            default -> message = "Unknown mod setting.";
        }
        sync(player, selected, detail, query, index, open, message, confirmKey, confirmRevision);
    }

    private static boolean installed(String id) {
        return id.length() <= 64 && ModList.get().isLoaded(id);
    }

    private static Optional<ModSettings.ExcavationEndpoint> endpoint(ServerPlayer player) {
        return installed(ModSettings.UWU) ? UwUExcavationEndpoint.bind(player) : Optional.empty();
    }

    private static String changeExcavation(ServerPlayer player, boolean added, String expected, String desired) {
        try {
            return ModSettings.change(player.hasPermissions(2), added, ModSettings.KEY, expected, desired,
                    endpoint(player), (before, after) -> ConfigAudit.record(player, ModSettings.KEY, before, after)).message;
        } catch (RuntimeException failure) {
            UnyieldingQuests.LOGGER.error("Could not confirm the excavation change", failure);
            return "Could not confirm the excavation setting. Refresh to read its current value.";
        }
    }

    private static String changeRegistered(ServerPlayer player, String key, String expected, String value) {
        try {
            var edit = NeoForgeSettings.change(key, expected, value);
            ConfigAudit.record(player, edit.key(), edit.before(), edit.after());
            return ModSettings.Result.SAVED.message;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return NeoForgeSettings.feedback(failure);
        } catch (RuntimeException failure) {
            UnyieldingQuests.LOGGER.error("Could not confirm registered setting {}", key, failure);
            return "Could not confirm this setting. Refresh to read its current value.";
        }
    }

    static int undo(ServerPlayer player, String key, String before, String after) {
        if (!player.hasPermissions(2)) return 0;
        String result = ModSettings.KEY.equals(key) ? changeExcavation(player, true, after, before)
                : changeRegistered(player, key, after, before);
        player.sendSystemMessage(Component.literal(ModSettings.Result.SAVED.message.equals(result)
                ? "Restored the previous setting value." : result));
        return ModSettings.Result.SAVED.message.equals(result) ? 1 : 0;
    }

    private static void sync(ServerPlayer player, Set<String> selected, String detail, String query, int index,
                             boolean open, String message, String confirmKey, long confirmRevision) {
        var data = new CompoundTag();
        if (!confirmKey.isEmpty()) {
            data.putString("confirmKey", confirmKey);
            data.putLong("confirmRevision", confirmRevision);
        }
        data.putString("page", PAGE);
        data.putInt("catalogVersion", 2);
        data.putString("detailId", detail);
        data.putString("query", query);
        data.putString("message", message);
        var mods = new ListTag();
        var others = new ListTag();
        var found = new HashSet<String>();
        var sorted = ModList.get().getMods().stream().sorted(Comparator
                .comparing((net.neoforged.neoforgespi.language.IModInfo info) -> !ModSettings.UWU.equals(info.getModId()))
                .thenComparing(info -> info.getDisplayName(), String.CASE_INSENSITIVE_ORDER)).toList();
        for (var info : sorted) {
            String id = info.getModId();
            found.add(id);
            boolean added = selected.contains(id);
            boolean primary = added || ModSettings.UWU.equals(id) || StoragePlusSettings.ID.equals(id) || NeoForgeSettings.supported(id);
            if (!primary && !open) continue;
            var mod = identity(id, info.getDisplayName(), true, added);
            if (added && id.equals(detail)) {
                var page = NeoForgeSettings.rows(id, query, index, PAGE_SIZE);
                var rows = page.rows();
                int current = page.pageIndex();
                int count = pages(page.total());
                if (StoragePlusSettings.ID.equals(id)) {
                    var storage = StoragePlusSettings.rows(player, query, Math.max(0, index - count), PAGE_SIZE);
                    if (index >= count && storage.total() > 0) {
                        rows = storage.rows();
                        current = count + storage.pageIndex();
                    }
                    count += pages(storage.total());
                }
                if (ModSettings.UWU.equals(id)) {
                    var groups = new ListTag();
                    for (String group : NeoForgeSettings.groups(id)) groups.add(StringTag.valueOf(group));
                    mod.put("groups", groups);
                }
                if (ModSettings.UWU.equals(id) && page.pageIndex() == 0 && matchesExcavation(query)) {
                    rows.add(0, excavationRow(player));
                }
                mod.put("rows", rows);
                data.putInt("index", current);
                data.putInt("pages", Math.max(1, count));
            } else if (open && added && ModSettings.UWU.equals(id)) {
                // Keep the first-generation client able to open its previously added UwU page.
                var rows = new ListTag();
                rows.add(excavationRow(player));
                mod.put("rows", rows);
            }
            (primary ? mods : others).add(mod);
        }
        selected.stream().filter(id -> !found.contains(id)).sorted().forEach(id -> mods.add(identity(id, id, false, true)));
        data.put("mods", mods);
        if (open) data.put("installedMods", others);
        Net.sendTo(player, new ConfigEditor.Sync(data, open));
    }

    private static int pages(int total) {
        return (total + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private static CompoundTag identity(String id, String name, boolean installed, boolean added) {
        var mod = new CompoundTag();
        mod.putString("id", id);
        mod.putString("name", name);
        if (StoragePlusSettings.ID.equals(id)) {
            mod.putString("name", "Anya Storage Plus");
            mod.putString("aliases", name);
        }
        mod.putBoolean("installed", installed);
        mod.putBoolean("added", added);
        return mod;
    }

    private static boolean matchesExcavation(String query) {
        String search = ModSettings.TITLE + " " + ModSettings.ALIASES + " " + ModSettings.KEY;
        return search.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));
    }

    private static CompoundTag excavationRow(ServerPlayer player) {
        var row = new CompoundTag();
        row.putString("key", ModSettings.KEY);
        row.putString("title", ModSettings.TITLE);
        row.putString("aliases", ModSettings.ALIASES);
        row.putString("kind", "boolean");
        row.putString("status", "This UwU Dimension build does not provide the world excavation control.");
        try {
            endpoint(player).map(ModSettings.ExcavationEndpoint::read).ifPresent(state -> {
                row.putBoolean("available", true);
                row.putBoolean("value", state.enabled());
                row.putString("current", Boolean.toString(state.enabled()));
                row.putString("scope", "Current world, all dimensions");
                row.putString("apply", state.applies().label);
                row.putString("activation", state.applies().name());
                row.putString("status", "Available");
                row.putString("note", "Changing this saves a world override. Undo restores the previous on/off value.");
            });
        } catch (RuntimeException failure) {
            row.putString("status", "UwU Dimension could not read this world's excavation setting.");
        }
        return row;
    }

    static final class Selections extends SavedData {
        private final Map<UUID, Set<String>> mods = new HashMap<>();

        private static Selections get(ServerPlayer player) {
            return player.server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(Selections::new, Selections::load, null), "unyieldingquests_mod_settings");
        }

        static Selections load(CompoundTag tag, HolderLookup.Provider provider) {
            var selections = new Selections();
            for (Tag raw : tag.getList("admins", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) raw;
                if (!entry.hasUUID("id")) continue;
                var ids = selections.mods.computeIfAbsent(entry.getUUID("id"), unused -> new HashSet<>());
                if (entry.contains("mods", Tag.TAG_LIST)) {
                    for (var id : entry.getList("mods", Tag.TAG_STRING)) ids.add(id.getAsString());
                } else ids.add(ModSettings.UWU);
            }
            return selections;
        }

        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            var list = new ListTag();
            mods.forEach((admin, ids) -> {
                var entry = new CompoundTag();
                entry.putUUID("id", admin);
                var names = new ListTag();
                ids.stream().sorted().forEach(id -> names.add(StringTag.valueOf(id)));
                entry.put("mods", names);
                list.add(entry);
            });
            tag.put("admins", list);
            return tag;
        }
    }

    private ModSettingsMenu() {}
}
