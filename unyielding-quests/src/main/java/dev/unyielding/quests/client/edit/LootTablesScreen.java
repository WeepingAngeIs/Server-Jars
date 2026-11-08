package dev.unyielding.quests.client.edit;

import dev.unyielding.quests.client.AdminSettingsScreen;
import dev.unyielding.quests.client.ClientData;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Server-owned reward tables. Every write waits for an authoritative response. */
public final class LootTablesScreen extends FormScreen {
    private int seenVersion = -1;
    private boolean pending;

    public LootTablesScreen(Screen parent) { super(parent, "Loot tables & crates"); }

    public static void requestOpen() {
        if (AdminSettingsScreen.canManage()) send("open", null);
    }

    public static CompoundTag action(String action) {
        CompoundTag tag = new CompoundTag();
        tag.putString("action", action);
        tag.putInt("revision", ClientData.lootTables.getInt("revision"));
        return tag;
    }

    public static void send(String action, String id) {
        CompoundTag tag = action(action);
        if (id != null) tag.putString("id", id);
        Net.sendToServer(new Net.LootTablesAction(tag));
    }

    @Override protected int panelWidth() { return Math.min(440, width - 16); }
    @Override protected String saveLabel() { return "Done"; }
    @Override protected void save() { cancel(); }

    @Override public void tick() {
        if (seenVersion != ClientData.lootTablesVersion) {
            seenVersion = ClientData.lootTablesVersion;
            pending = false;
            relayout();
        }
    }

    @Override protected void build() {
        boolean allowed = AdminSettingsScreen.canManage() && ClientData.lootTables.getBoolean("allowed");
        String message = ClientData.lootTables.getString("message");
        if (!allowed) { note("Administrator permission is required."); return; }
        if (pending) note("Waiting for server...");
        else if (!message.isBlank()) {
            for (String line : wrap(message, innerWidth())) note(line);
        }
        toggle(left(), innerWidth(), "Loot tables and crates", ClientData.lootTables.getBoolean("enabled"), value -> {
            if (pending) return;
            pending = true;
            CompoundTag tag = action("toggle");
            tag.putBoolean("enabled", value);
            Net.sendToServer(new Net.LootTablesAction(tag));
        }).active = !pending;
        gap(25);
        note("OFF pauses table rewards and crate opening.");
        gap(4);
        button(left(), 112, "New loot table", null, Ui.Style.ACCENT,
                () -> minecraft.setScreen(new EditLootTableScreen(this, null))).active = !pending;
        button(left() + 118, 74, "Refresh", null, Ui.Style.NORMAL, () -> send("open", null));
        gap(28);
        note("Each crate is used once. Rewards are rolled on the server.");
        note("Add crates or table rolls in a quest's reward editor.");
        gap(8);
        var tables = ClientData.lootTables.getList("tables", Tag.TAG_COMPOUND);
        if (tables.isEmpty()) note("No tables yet. Create your first reward pool above.");
        for (Tag value : tables) {
            CompoundTag table = (CompoundTag)value;
            String id = table.getString("id");
            card(75);
            cy += 5;
            note(font.plainSubstrByWidth(table.getString("name"), innerWidth() - 12));
            note(font.plainSubstrByWidth(id + " · " + table.getList("entries", Tag.TAG_COMPOUND).size()
                    + " rewards · " + table.getInt("rolls") + " roll(s)", innerWidth() - 12));
            int bw = (innerWidth() - 18) / 4;
            button(left() + 4, bw, "Edit", Ui.ICON_GEAR, Ui.Style.NORMAL,
                    () -> minecraft.setScreen(new EditLootTableScreen(this, table)));
            button(left() + 8 + bw, bw, "Copy", null, Ui.Style.NORMAL, () -> {
                CompoundTag copy = table.copy();
                copy.putString("id", "");
                copy.putString("name", table.getString("name") + " copy");
                minecraft.setScreen(new EditLootTableScreen(this, copy));
            });
            button(left() + 12 + bw * 2, bw, "Get crate", Ui.ICON_GIFT, Ui.Style.GOLD, () -> {
                CompoundTag tag = action("give");
                tag.putString("id", id);
                tag.putInt("count", 1);
                pending = true;
                Net.sendToServer(new Net.LootTablesAction(tag));
                relayout();
            }).active = !pending && table.getBoolean("enabled") && table.getBoolean("crateEnabled");
            button(left() + 16 + bw * 3, bw, "Delete", null, Ui.Style.NORMAL,
                    () -> minecraft.setScreen(new ConfirmDialog(this, "Delete loot table",
                            "Delete " + table.getString("name") + "? Existing crates for it will stop opening.",
                            "Delete", () -> { pending = true; send("delete", id); relayout(); })));
            gap(25);
            note((table.getBoolean("enabled") ? "Table ON" : "Table OFF") + " · "
                    + (table.getBoolean("crateEnabled") ? "Crates ON" : "Crates OFF"));
            gap(21);
        }
    }

    private java.util.List<String> wrap(String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        while (!text.isEmpty()) {
            String line = font.plainSubstrByWidth(text, width);
            if (line.isEmpty()) break;
            lines.add(line);
            text = text.substring(line.length()).stripLeading();
        }
        return lines;
    }

    @Override protected void note(String text) {
        for (String line : wrap(text, innerWidth())) super.note(line);
    }
}

