package dev.unyielding.quests.client.edit;

import dev.unyielding.quests.client.AdminSettingsScreen;
import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.List;
import java.util.Locale;

/** Draft editor. The server validates IDs, rewards, permissions and revision before saving. */
public final class EditLootTableScreen extends FormScreen {
    private static final String[] KINDS = {"Item", "Command", "Pokémon", "Lucky Spin"};
    private static final List<String> TYPES = List.of("item", "command", "pokemon", "lucky_spin");
    private final CompoundTag draft;
    private final boolean newTable;
    private final int revision;
    private int seenVersion;
    private boolean pending;
    private String error = "";

    public EditLootTableScreen(Screen parent, CompoundTag table) {
        super(parent, table == null || table.getString("id").isBlank() ? "Create loot table" : "Edit loot table");
        draft = table == null ? new CompoundTag() : table.copy();
        newTable = draft.getString("id").isBlank();
        revision = ClientData.lootTables.getInt("revision");
        seenVersion = ClientData.lootTablesVersion;
        if (table == null) {
            draft.putString("name", "New loot table");
            draft.putBoolean("enabled", true);
            draft.putBoolean("crateEnabled", true);
            draft.putInt("rolls", 1);
            ListTag entries = new ListTag();
            entries.add(newEntry());
            draft.put("entries", entries);
        }
    }

    @Override protected int panelWidth() { return Math.min(440, width - 16); }
    @Override protected String saveLabel() { return pending ? "Saving..." : "Save"; }

    private static CompoundTag newEntry() {
        CompoundTag entry = new CompoundTag();
        entry.putString("type", "item");
        entry.putString("value", "minecraft:diamond");
        entry.putInt("min", 1);
        entry.putInt("max", 1);
        entry.putInt("weight", 10);
        entry.putString("label", "");
        return entry;
    }

    @Override public void tick() {
        if (seenVersion == ClientData.lootTablesVersion) return;
        seenVersion = ClientData.lootTablesVersion;
        if (!pending) return;
        pending = false;
        if (ClientData.lootTables.getBoolean("error")) {
            error = ClientData.lootTables.getString("message");
            relayout();
        } else minecraft.setScreen(parent);
    }

    @Override protected void build() {
        if (pending) { note("Saving on server..."); return; }
        int x = labelled("Table ID");
        if (newTable) field("id", x, right() - x, draft.getString("id"), "example: daily_rewards",
                text -> draft.putString("id", text.trim()));
        else label(draft.getString("id"), x, cy + 6);
        gap(24);
        x = labelled("Name");
        field("name", x, right() - x, draft.getString("name"), "Shown on the crate",
                text -> draft.putString("name", text));
        gap(24);
        toggle(left(), (innerWidth() - 6) / 2, "Table enabled", draft.getBoolean("enabled"),
                value -> draft.putBoolean("enabled", value));
        toggle(left() + (innerWidth() + 6) / 2, (innerWidth() - 6) / 2,
                "Crates allowed", draft.getBoolean("crateEnabled"), value -> draft.putBoolean("crateEnabled", value));
        gap(24);
        x = labelled("Rolls");
        field("rolls", x, 65, String.valueOf(draft.getInt("rolls")), "1–16",
                text -> draft.putInt("rolls", parseInt(text, 0)));
        gap(24);
        note("One weighted reward per roll; the same reward can repeat.");
        note("More weight means a higher chance. Changes apply to old crates.");
        gap(8);
        if (!error.isEmpty()) {
            for (var line : font.split(net.minecraft.network.chat.Component.literal(error), innerWidth())) {
                StringBuilder text = new StringBuilder();
                line.accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
                note(text.toString());
            }
            if (revision != ClientData.lootTables.getInt("revision")) {
                button(left(), 160, "Reload saved version", null, Ui.Style.NORMAL, () -> {
                    CompoundTag saved = null;
                    for (Tag value : ClientData.lootTables.getList("tables", Tag.TAG_COMPOUND)) {
                        CompoundTag table = (CompoundTag)value;
                        if (table.getString("id").equals(draft.getString("id"))) saved = table;
                    }
                    minecraft.setScreen(new EditLootTableScreen(parent, saved));
                });
                gap(25);
            }
            gap(6);
        }
        heading("Rewards");
        ListTag entries = draft.getList("entries", Tag.TAG_COMPOUND);
        long totalWeight = 0;
        for (Tag value : entries) totalWeight += Math.max(0, ((CompoundTag)value).getInt("weight"));
        for (int i = 0; i < entries.size(); i++) entryRow(entries, i, totalWeight);
        button(left(), 110, "Add reward", null, Ui.Style.ACCENT, () -> {
            entries.add(newEntry());
            draft.put("entries", entries);
            relayout();
        }).active = entries.size() < 128 && !pending;
        gap(27);
        note("Item IDs can come from any installed mod.");
        note("Commands run as the server. Use {p} for the player name.");
        note("Lucky Spin uses the existing spin pools and chances.");
    }

    private void entryRow(ListTag entries, int index, long totalWeight) {
        CompoundTag entry = entries.getCompound(index);
        String key = "entry" + index;
        String type = entry.getString("type");
        int typeIndex = TYPES.indexOf(type);
        String kind = typeIndex < 0 ? KINDS[0] : KINDS[typeIndex];
        boolean quantity = type.equals("item") || type.equals("pokemon");
        int rowBottom = cy + (quantity ? 136 : 112);
        card(quantity ? 136 : 112);
        cy += 5;
        int x = left() + 5;
        cycle(x, 96, "", KINDS, kind, selected -> {
            int next = java.util.Arrays.asList(KINDS).indexOf(selected);
            String nextType = TYPES.get(next);
            entry.putString("type", nextType);
            entry.putString("value", switch (nextType) {
                case "command" -> "/give {p} minecraft:apple 1";
                case "pokemon" -> "pikachu";
                case "lucky_spin" -> "";
                default -> "minecraft:diamond";
            });
            entry.putInt("min", nextType.equals("pokemon") ? 5 : 1);
            entry.putInt("max", nextType.equals("pokemon") ? 5 : 1);
        });
        label("Weight", x + 101, cy + 6);
        field(key + "weight", x + 140, 62, String.valueOf(entry.getInt("weight")), "1–100000",
                text -> entry.putInt("weight", parseInt(text, 0)));
        double chance = totalWeight <= 0 ? 0 : 100.0 * Math.max(0, entry.getInt("weight")) / totalWeight;
        label(String.format(Locale.ROOT, "%.1f%%", chance), x + 207, cy + 6);
        button(right() - 27, 22, "×", null, Ui.Style.NORMAL, () -> { entries.remove(index); relayout(); });
        gap(24);
        if (type.equals("lucky_spin")) note("Rolls the server's configured Lucky Spin rewards.");
        else {
            String hint = switch (type) { case "command" -> "/command {p}"; case "pokemon" -> "Pokémon species, e.g. pikachu"; default -> "Item id, e.g. minecraft:diamond"; };
            field(key + "value", x, right() - x - 5, entry.getString("value"), hint,
                    text -> entry.putString("value", text.trim()));
        }
        gap(24);
        if (quantity) {
            label(type.equals("pokemon") ? "Level" : "Count", x, cy + 6);
            field(key + "min", x + 38, 54, String.valueOf(entry.getInt("min")), "min",
                    text -> entry.putInt("min", parseInt(text, 0)));
            label("to", x + 97, cy + 6);
            field(key + "max", x + 113, 54, String.valueOf(entry.getInt("max")), "max",
                    text -> entry.putInt("max", parseInt(text, 0)));
            if (type.equals("item")) button(x + 173, 80, "Held item", null, Ui.Style.NORMAL, () -> {
                String held = heldItemId();
                if (!held.isBlank()) entry.putString("value", held);
                relayout();
            });
            gap(24);
        }
        label("Prize label", x, cy + 6);
        field(key + "label", x + 62, right() - x - 67, entry.getString("label"), "Optional display name",
                text -> entry.putString("label", text));
        gap(27);
        note(type.equals("item") ? "Count range: 1–64" : type.equals("pokemon") ? "Level range: 1–100" : "One action per selected reward.");
        cy = Math.max(cy + 6, rowBottom + 6);
    }

    @Override protected void save() {
        if (pending) return;
        if (!AdminSettingsScreen.canManage()) { error = "Administrator permission is required."; relayout(); return; }
        if (!draft.getString("id").matches("[a-z0-9_]{1,48}")) {
            error = "Use 1–48 lowercase letters, numbers or underscores for the table ID.";
            relayout(); return;
        }
        if (draft.getString("name").isBlank()) { error = "Enter a table name."; relayout(); return; }
        CompoundTag action = LootTablesScreen.action("save");
        action.putInt("revision", revision);
        action.put("table", draft.copy());
        pending = true;
        error = "";
        Net.sendToServer(new Net.LootTablesAction(action));
        relayout();
    }

    @Override protected void note(String text) {
        while (!text.isEmpty()) {
            String line = font.plainSubstrByWidth(text, innerWidth());
            if (line.isEmpty()) break;
            super.note(line);
            text = text.substring(line.length()).stripLeading();
        }
    }
}

