package dev.unyielding.quests.settings;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Server-side discovery, page selection and validation for reviewed mod settings. */
final class ModSettingsMenu {
    static final String PAGE = "mod_settings";
    private static final String OPEN = "__mod_settings";
    private static final String ADD = "__mod_settings_add";

    static boolean handles(String key) {
        return OPEN.equals(key) || ADD.equals(key) || key.startsWith("external.");
    }

    static void handle(ServerPlayer player, ConfigEditor.Action action) {
        if (!player.hasPermissions(2)) return;
        ModSettings.Installed mod = installed();
        Selections selections = Selections.get(player);
        boolean added = selections.admins.contains(player.getUUID());
        switch (action.key()) {
            case OPEN -> {
                if (action.expected().isEmpty() && action.value().isEmpty()) sync(player, mod, added, true, "");
            }
            case ADD -> {
                if (mod.present() && action.expected().isEmpty() && ModSettings.UWU.equals(action.value())) {
                    if (selections.admins.add(player.getUUID())) selections.setDirty();
                    sync(player, mod, true, false, "UwU Dimension added.");
                } else sync(player, mod, added, false, "That mod is unavailable.");
            }
            default -> {
                String reply;
                try {
                    reply = ModSettings.change(player.hasPermissions(2), added, action.key(), action.expected(),
                            action.value(), endpoint(player, mod), (before, after) ->
                                    ConfigAudit.record(player, ModSettings.KEY, before, after)).message;
                } catch (RuntimeException failure) {
                    UnyieldingQuests.LOGGER.error("Could not confirm or audit the UwU excavation setting change", failure);
                    reply = "Could not confirm the change. Check the current value and server log.";
                }
                sync(player, mod, added, false, reply);
            }
        }
    }

    static int undo(ServerPlayer player, String key, String before, String after) {
        if (!player.hasPermissions(2)) return 0;
        ModSettings.Installed mod = installed();
        try {
            ModSettings.Result result = ModSettings.undo(true, key, before, after, endpoint(player, mod),
                    (oldValue, newValue) -> ConfigAudit.record(player, key, oldValue, newValue));
            player.sendSystemMessage(Component.literal(result == ModSettings.Result.SAVED
                    ? "Restored Heart Launcher excavation to " + (Boolean.parseBoolean(before) ? "ON." : "OFF.")
                    : result.message));
            sync(player, mod, Selections.get(player).admins.contains(player.getUUID()), false, result.message);
            return result == ModSettings.Result.SAVED ? 1 : 0;
        } catch (RuntimeException failure) {
            UnyieldingQuests.LOGGER.error("Could not confirm or audit undo for UwU excavation", failure);
            player.sendSystemMessage(Component.literal("Could not confirm undo. Check the current value and server log."));
            return 0;
        }
    }

    private static ModSettings.Installed installed() {
        var mod = ModList.get().getModContainerById(ModSettings.UWU);
        return ModSettings.discover(mod.map(container -> Map.of(ModSettings.UWU, container.getModInfo().getDisplayName()))
                .orElseGet(Map::of));
    }

    private static Optional<ModSettings.ExcavationEndpoint> endpoint(ServerPlayer player, ModSettings.Installed mod) {
        if (!mod.present()) return Optional.empty();
        // Supply an actor-bound UwU endpoint here once UwU publishes and agrees its server API.
        return Optional.empty();
    }

    private static void sync(ServerPlayer player, ModSettings.Installed mod, boolean added, boolean open, String message) {
        CompoundTag data = new CompoundTag();
        data.putString("page", PAGE);
        data.putString("message", message);
        CompoundTag page = new CompoundTag();
        page.putString("id", ModSettings.UWU);
        page.putString("name", mod.name());
        page.putBoolean("installed", mod.present());
        page.putBoolean("added", added);
        if (mod.present()) page.putString("metadata", registeredTypes(ModSettings.UWU));
        ListTag rows = new ListTag();
        if (added) rows.add(row(player, mod));
        page.put("rows", rows);
        ListTag mods = new ListTag();
        mods.add(page);
        data.put("mods", mods);
        Net.sendTo(player, new ConfigEditor.Sync(data, open));
    }

    private static String registeredTypes(String modId) {
        String types = ModConfigs.getModConfigs(modId).stream()
                .filter(config -> config.getSpec() instanceof ModConfigSpec)
                .map(config -> config.getType().name().toLowerCase(Locale.ROOT))
                .distinct().sorted().collect(Collectors.joining(", "));
        return types.isEmpty() ? "No NeoForge config metadata available." : "Registered config types (" + types + ")";
    }

    private static CompoundTag row(ServerPlayer player, ModSettings.Installed mod) {
        CompoundTag row = new CompoundTag();
        row.putString("key", ModSettings.KEY);
        row.putString("title", ModSettings.TITLE);
        row.putString("aliases", ModSettings.ALIASES);
        row.putString("scope", "Unverified");
        row.putString("apply", "Restart requirements unverified");
        row.putString("status", mod.present() ? "Requires a supported UwU server integration."
                : "UwU Dimension is missing from this server.");
        try {
            endpoint(player, mod).map(ModSettings.ExcavationEndpoint::read).ifPresent(state -> {
                row.putBoolean("available", true);
                row.putBoolean("value", state.enabled());
                row.putString("scope", state.scope().label);
                row.putString("apply", state.applies().label);
                row.putString("status", "Available");
            });
        } catch (RuntimeException failure) {
            UnyieldingQuests.LOGGER.error("Could not read the UwU excavation setting", failure);
            row.putString("status", "Unable to read the UwU setting. Check the server log.");
        }
        return row;
    }

    private static final class Selections extends SavedData {
        private final Set<UUID> admins = new HashSet<>();

        private static Selections get(ServerPlayer player) {
            return player.server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(Selections::new, Selections::load, null), "unyieldingquests_mod_settings");
        }

        private static Selections load(CompoundTag tag, HolderLookup.Provider provider) {
            Selections selections = new Selections();
            for (Tag raw : tag.getList("admins", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                if (entry.hasUUID("id")) selections.admins.add(entry.getUUID("id"));
            }
            return selections;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            ListTag list = new ListTag();
            for (UUID admin : admins) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("id", admin);
                list.add(entry);
            }
            tag.put("admins", list);
            return tag;
        }
    }

    private ModSettingsMenu() {}
}
