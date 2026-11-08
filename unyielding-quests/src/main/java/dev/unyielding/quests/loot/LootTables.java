package dev.unyielding.quests.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.unyielding.quests.Registration;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import dev.unyielding.quests.spin.LuckySpin;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Server-owned weighted tables, shared by quest rewards and consumable crate items. */
public final class LootTables {
    public record Entry(String type, String value, int min, int max, int weight, String label) { }
    public record Table(String id, String name, boolean enabled, boolean crateEnabled, int rolls, List<Entry> entries) { }
    private record Store(int schema, int revision, boolean enabled, List<Table> tables) { }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random RANDOM = new Random();
    private static final Set<UUID> PAYING = new HashSet<>();
    private static Store store = new Store(1, 0, true, List.of());
    private static Path path;
    private static boolean writable;
    private static MinecraftServer server;

    private LootTables() { }

    public static void start(MinecraftServer value) {
        server = value;
        PAYING.clear();
        path = FMLPaths.CONFIGDIR.get().resolve("unyieldingquests/loot_tables.json");
        store = new Store(1, 0, true, List.of());
        writable = true;
        if (Files.exists(path)) {
            try {
                if (Files.size(path) > 2_000_000) throw new IllegalArgumentException("Reward table file is too large");
                Store loaded = JSON.fromJson(Files.readString(path), Store.class);
                if (loaded == null || loaded.schema != 1 || loaded.tables == null || loaded.tables.size() > 64)
                    throw new IllegalArgumentException("Unsupported reward table file");
                Set<String> ids = new HashSet<>();
                for (Table table : loaded.tables) {
                    validate(table, false);
                    if (!ids.add(table.id)) throw new IllegalArgumentException("Duplicate table ID");
                }
                store = loaded;
            } catch (Exception error) {
                // Preserve an invalid file; never silently replace it with defaults.
                writable = false;
                store = new Store(1, 0, false, List.of());
                UnyieldingQuests.LOGGER.error("Could not load loot_tables.json; crates disabled until corrected and restarted", error);
            }
        } else {
            try { persist(store); }
            catch (IOException error) {
                writable = false;
                UnyieldingQuests.LOGGER.error("Could not create loot_tables.json", error);
            }
        }
    }

    public static void stop() { server = null; path = null; writable = false; PAYING.clear(); }
    public static String legacyId(long id) { return "legacy_" + QuestFile.idToString(id).toLowerCase(java.util.Locale.ROOT); }

    /** Legacy SNBT tables appear alongside custom tables; JSON overrides preserve old quest IDs. */
    private static Map<String, Table> all() {
        Map<String, Table> tables = new LinkedHashMap<>();
        for (var old : QuestManager.file.lootTables.values()) {
            if (old.lootSize < 1 || old.lootSize > 16 || old.entries.size() > 128) continue;
            List<Entry> entries = new ArrayList<>();
            for (var entry : old.entries) {
                if (entry.item == null || entry.item.isFilter() || entry.item.components != null
                        || entry.count < 1 || entry.count > 64 || !Float.isFinite(entry.weight)
                        || entry.weight <= 0 || entry.weight > 1000
                        || Math.abs(entry.weight * 100 - Math.round(entry.weight * 100)) > 0.001f) continue;
                entries.add(new Entry("item", entry.item.id, entry.count, entry.count,
                        Math.round(entry.weight * 100), ""));
            }
            // Complex legacy tables remain handled by the original engine, without lossy conversion.
            if (entries.size() != old.entries.size() || entries.isEmpty()) continue;
            String id = legacyId(old.id);
            tables.put(id, new Table(id, old.title.isBlank() ? id : old.title, true, true,
                    old.lootSize, entries));
        }
        for (Table table : store.tables) tables.put(table.id, table);
        return tables;
    }

    public static boolean hasOverride(long legacy) {
        return store.tables.stream().anyMatch(table -> table.id.equals(legacyId(legacy)));
    }

    private static void validate(Table table, boolean resolve) {
        if (table == null || table.id == null || !table.id.matches("[a-z0-9_]{1,48}"))
            throw new IllegalArgumentException("Use a table ID with 1-48 lowercase letters, digits or underscores.");
        if (table.name == null || table.name.isBlank() || table.name.length() > 80)
            throw new IllegalArgumentException("Table name must be 1-80 characters.");
        if (table.rolls < 1 || table.rolls > 16) throw new IllegalArgumentException("Rolls must be 1-16.");
        if (table.entries == null || table.entries.isEmpty() || table.entries.size() > 128)
            throw new IllegalArgumentException("Each table needs 1-128 reward entries.");
        for (Entry entry : table.entries) {
            if (entry == null || entry.type == null || entry.value == null || entry.label == null
                    || entry.value.length() > 512 || entry.label.length() > 80)
                throw new IllegalArgumentException("Reward value or label is invalid.");
            int limit = entry.type.equals("pokemon") ? 100 : 64;
            if (entry.min < 1 || entry.max < entry.min || entry.max > limit || entry.weight < 1 || entry.weight > 100000)
                throw new IllegalArgumentException("Check reward amount/level range and weight (1-100000).");
            switch (entry.type) {
                case "item" -> {
                    ResourceLocation id = ResourceLocation.tryParse(entry.value);
                    if (id == null || (resolve && (!BuiltInRegistries.ITEM.containsKey(id)
                            || BuiltInRegistries.ITEM.get(id) == Items.AIR)))
                        throw new IllegalArgumentException("Unknown item: " + entry.value);
                }
                case "pokemon" -> {
                    if (ResourceLocation.tryParse(entry.value.contains(":") ? entry.value : "cobblemon:" + entry.value) == null
                            || (resolve && (!ModList.get().isLoaded("cobblemon") || !LootPokemon.exists(entry.value))))
                        throw new IllegalArgumentException("Unknown Pokemon species: " + entry.value);
                }
                case "command" -> {
                    if (entry.value.isBlank() || entry.value.contains("\n") || entry.value.contains("\r"))
                        throw new IllegalArgumentException("Enter one reward command; use {p} for the player.");
                }
                case "lucky_spin" -> { }
                default -> throw new IllegalArgumentException("Unknown reward type: " + entry.type);
            }
        }
    }

    private static void persist(Store next) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(next) + "\n", StandardCharsets.UTF_8);
        if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void commit(boolean enabled, List<Table> tables) throws IOException {
        if (!writable) throw new IOException("Fix loot_tables.json and restart before editing.");
        Store next = new Store(1, store.revision + 1, enabled, List.copyOf(tables));
        if (JSON.toJson(next).getBytes(StandardCharsets.UTF_8).length > 400_000)
            throw new IllegalArgumentException("Reward tables are too large. Shorten entries or remove unused tables.");
        persist(next);
        store = next;
    }

    public static void onAction(ServerPlayer player, CompoundTag action) {
        if (!player.hasPermissions(2)) { sync(player, false, "Only server admins can edit reward tables.", true); return; }
        String operation = action.getString("action");
        if (operation.equals("open")) { sync(player, true, writable ? "Changes are saved on the server." : "Fix loot_tables.json and restart before editing.", !writable); return; }
        if (action.getInt("revision") != store.revision) { sync(player, false, "Another admin changed these tables. Review the latest version and try again.", true); return; }
        try {
            List<Table> tables = new ArrayList<>(store.tables);
            switch (operation) {
                case "save" -> {
                    Table table = fromTag(action.getCompound("table"));
                    validate(table, true);
                    tables.removeIf(old -> old.id.equals(table.id));
                    if (tables.size() >= 64) throw new IllegalArgumentException("At most 64 custom tables or legacy overrides are supported.");
                    tables.add(table);
                    commit(store.enabled, tables);
                }
                case "delete" -> {
                    String id = action.getString("id");
                    if (id.startsWith("legacy_")) throw new IllegalArgumentException("Disable legacy tables instead of deleting them; existing quests still reference them.");
                    if (!tables.removeIf(table -> table.id.equals(id))) throw new IllegalArgumentException("Table not found.");
                    commit(store.enabled, tables);
                }
                case "toggle" -> commit(action.getBoolean("enabled"), tables);
                case "give" -> {
                    int count = action.getInt("count");
                    if (!giveCrate(player, action.getString("id"), count)) throw new IllegalArgumentException("Cannot give this crate. Check that the table and crates are enabled.");
                }
                default -> throw new IllegalArgumentException("Unknown table action.");
            }
            sync(player, false, operation.equals("give") ? "Crates added to your inventory." : "Saved reward tables.", false);
        } catch (Exception error) {
            sync(player, false, error.getMessage() == null ? "Could not save reward tables." : error.getMessage(), true);
        }
    }

    public static CompoundTag snapshot(boolean allowed, String message, boolean error) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("allowed", allowed);
        tag.putBoolean("enabled", store.enabled);
        tag.putInt("revision", store.revision);
        tag.putString("message", message);
        tag.putBoolean("error", error);
        ListTag tables = new ListTag();
        if (allowed) {
            int size = 0;
            // Config tables take priority when an unusually large legacy book exceeds one editor page.
            Map<String, Table> available = new LinkedHashMap<>();
            for (Table table : store.tables) available.put(table.id, table);
            all().forEach(available::putIfAbsent);
            for (Table table : available.values()) {
                CompoundTag row = toTag(table);
                size += row.toString().getBytes(StandardCharsets.UTF_8).length;
                if (size > 500_000) {
                    tag.putString("message", message + " Some legacy tables exceed the editor size limit and remain in their original files.");
                    break;
                }
                tables.add(row);
            }
        }
        tag.put("tables", tables);
        return tag;
    }

    private static void sync(ServerPlayer player, boolean open, String message, boolean error) {
        Net.sendTo(player, new Net.LootTablesSync(snapshot(player.hasPermissions(2), message, error), open));
    }

    public static CompoundTag toTag(Table table) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", table.id); tag.putString("name", table.name);
        tag.putBoolean("enabled", table.enabled); tag.putBoolean("crateEnabled", table.crateEnabled);
        tag.putInt("rolls", table.rolls);
        ListTag entries = new ListTag();
        for (Entry entry : table.entries) {
            CompoundTag row = new CompoundTag();
            row.putString("type", entry.type); row.putString("value", entry.value); row.putString("label", entry.label);
            row.putInt("min", entry.min); row.putInt("max", entry.max); row.putInt("weight", entry.weight);
            entries.add(row);
        }
        tag.put("entries", entries);
        return tag;
    }

    private static Table fromTag(CompoundTag tag) {
        ListTag rows = tag.getList("entries", Tag.TAG_COMPOUND);
        if (rows.size() > 128) throw new IllegalArgumentException("At most 128 entries per table.");
        List<Entry> entries = new ArrayList<>();
        for (Tag raw : rows) {
            CompoundTag entry = (CompoundTag)raw;
            entries.add(new Entry(entry.getString("type"), entry.getString("value"), entry.getInt("min"),
                    entry.getInt("max"), entry.getInt("weight"), entry.getString("label")));
        }
        return new Table(tag.getString("id"), tag.getString("name"), tag.getBoolean("enabled"),
                tag.getBoolean("crateEnabled"), tag.getInt("rolls"), entries);
    }

    public static ItemStack crate(String id, int count) {
        Table table = all().get(id);
        if (table == null || count < 1 || count > 64) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(Registration.LOOT_CRATE.get(), count);
        CompoundTag data = new CompoundTag(); data.putString("unyielding_table", id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(table.name + " Crate"));
        return stack;
    }

    public static boolean giveCrate(ServerPlayer player, String id, int count) {
        Table table = all().get(id);
        if (!store.enabled || table == null || !table.enabled || !table.crateEnabled || count < 1 || count > 64) return false;
        validate(table, true);
        ItemHandlerHelper.giveItemToPlayer(player, crate(id, count));
        return true;
    }

    public static boolean openCrate(ServerPlayer player, ItemStack held) {
        if (!held.is(Registration.LOOT_CRATE.get()) || held.isEmpty()) return false;
        String id = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("unyielding_table");
        return roll(player, id, held);
    }

    public static boolean roll(ServerPlayer player, String id) { return roll(player, id, null); }

    private static boolean roll(ServerPlayer player, String id, ItemStack consume) {
        Table table = all().get(id);
        if (!store.enabled || table == null || !table.enabled || (consume != null && !table.crateEnabled)) {
            player.sendSystemMessage(Component.literal("This reward table is unavailable. Your crate was not consumed."));
            return false;
        }
        if (!PAYING.add(player.getUUID())) return false;
        try {
            // Validate the whole table before consuming, so unavailable entries cannot silently waste a crate.
            validate(table, true);
            List<Entry> picks = new ArrayList<>();
            int total = table.entries.stream().mapToInt(Entry::weight).sum();
            for (int i = 0; i < table.rolls; i++) {
                int roll = RANDOM.nextInt(total);
                for (Entry entry : table.entries) { roll -= entry.weight; if (roll < 0) { picks.add(entry); break; } }
            }
            // Consume once before any grant. A failing command must never allow already-paid prizes to reroll.
            if (consume != null) consume.shrink(1);
            List<LuckySpin.Spin> spins = new ArrayList<>();
            for (Entry entry : picks) {
                int amount = entry.min + RANDOM.nextInt(entry.max - entry.min + 1);
                switch (entry.type) {
                    case "item" -> {
                        ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.value)), amount);
                        ItemHandlerHelper.giveItemToPlayer(player, item);
                        player.sendSystemMessage(Component.literal(table.name + ": " + amount + " × " + item.getHoverName().getString()));
                    }
                    case "pokemon" -> LootPokemon.give(player, entry.value, amount);
                    case "command" -> {
                        String command = entry.value.replace("{p}", player.getGameProfile().getName());
                        if (command.startsWith("/")) command = command.substring(1);
                        player.getServer().getCommands().getDispatcher().execute(command,
                                player.createCommandSourceStack().withPermission(2).withSuppressedOutput());
                    }
                    case "lucky_spin" -> spins.add(LuckySpin.roll(player, table.name));
                }
            }
            if (!spins.isEmpty()) Net.sendTo(player, new Net.SpinResult(spins));
            return true;
        } catch (Exception failure) {
            UnyieldingQuests.LOGGER.error("Reward table {} failed for {}", id, player.getGameProfile().getName(), failure);
            player.sendSystemMessage(Component.literal("Reward table error: " + failure.getMessage() + ". Ask an admin to check the table."));
            return false;
        } finally { PAYING.remove(player.getUUID()); }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("questsloot").requires(source -> source.hasPermission(2))
                .executes(context -> { sync(context.getSource().getPlayerOrException(), true, "Edit reward tables and crates.", false); return 1; })
                .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> { all().keySet().forEach(builder::suggest); return builder.buildFuture(); })
                                .executes(context -> giveCrate(EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "id"), 1) ? 1 : 0)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> giveCrate(EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "id"), IntegerArgumentType.getInteger(context, "count")) ? 1 : 0)))))
                .then(Commands.literal("roll").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((context, builder) -> { all().keySet().forEach(builder::suggest); return builder.buildFuture(); })
                                .executes(context -> roll(EntityArgument.getPlayer(context, "player"), StringArgumentType.getString(context, "id")) ? 1 : 0)))));
    }
}
