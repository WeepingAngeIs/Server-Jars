package dev.unyielding.quests.client;

import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Browses server-supported adapters and edits only values the server makes available. */
final class ModSettingsScreen extends FormScreen {
    private CompoundTag data;
    private String selectedId = "";
    private String addingId = "";
    private String modQuery = "";
    private String settingQuery = "";
    private String pending = "";

    ModSettingsScreen(ConfigValuesScreen parent, CompoundTag data) {
        super(parent, "Add Mod Settings");
        this.data = data;
    }

    void receive(CompoundTag data) {
        this.data = data;
        String requestedId = addingId;
        addingId = "";
        pending = "";
        CompoundTag added = findMod(requestedId);
        if (added != null && added.getBoolean("added")) {
            select(requestedId);
            return;
        }
        relayout();
    }

    @Override
    protected void build() {
        CompoundTag selected = findMod(selectedId);
        if (!selectedId.isEmpty() && (selected == null || !selected.getBoolean("added"))) selectedId = "";

        int half = (innerWidth() - 4) / 2;
        if (!selectedId.isEmpty()) {
            button(left(), half, "All mods", null, Ui.Style.NORMAL, () -> select("")).active = pending.isEmpty();
        }
        button(right() - half, half, "Refresh", null, Ui.Style.NORMAL,
                () -> request(new ConfigEditor.Action("__mod_settings", "", ""), "Refreshing settings…")).active = canRequest();
        gap(25);
        String message = pending.isEmpty() ? data.getString("message") : pending;
        if (!message.isBlank()) {
            paragraph(message);
            gap(4);
        }
        if (selectedId.isEmpty()) buildCatalog();
        else buildSettings(selected);
    }

    private void buildCatalog() {
        field("modQuery", left(), innerWidth() - 65, modQuery, "Search mods by name or ID", s -> modQuery = s);
        button(right() - 60, 60, "Search", null, Ui.Style.NORMAL, this::resetScroll);
        gap(26);
        boolean found = false;
        for (Tag raw : mods()) {
            CompoundTag mod = (CompoundTag) raw;
            String id = mod.getString("id");
            String name = mod.getString("name");
            if (!matches(modQuery, name + " " + id)) continue;
            found = true;
            paragraph(name);
            paragraph(id);
            String metadata = mod.getString("metadata");
            if (!metadata.isBlank()) paragraph(metadata);
            if (mod.getBoolean("added")) {
                button(left(), innerWidth(), "Open settings", null, Ui.Style.NORMAL,
                        () -> select(id)).active = pending.isEmpty();
            } else if (mod.getBoolean("installed")) {
                button(left(), innerWidth(), "Add settings", null, Ui.Style.ACCENT, () -> {
                    if (!canRequest()) return;
                    addingId = id;
                    request(new ConfigEditor.Action("__mod_settings_add", "", id), "Adding settings…");
                }).active = canRequest();
            } else {
                note("Unavailable · Mod is missing from the server");
                gap(4);
                continue;
            }
            gap(26);
        }
        if (!found) note("No matching mods.");
    }

    private void buildSettings(CompoundTag mod) {
        paragraph(mod.getString("name"));
        gap(4);
        field("settingQuery", left(), innerWidth() - 65, settingQuery, "Search settings or aliases", s -> settingQuery = s);
        button(right() - 60, 60, "Search", null, Ui.Style.NORMAL, this::resetScroll);
        gap(26);
        boolean found = false;
        for (Tag raw : mod.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            String title = row.getString("title");
            if (!matches(settingQuery, title + " " + row.getString("aliases"))) continue;
            found = true;
            paragraph(title);
            if (!row.getString("scope").isBlank()) paragraph("Scope · " + row.getString("scope"));
            if (!row.getString("apply").isBlank()) paragraph(row.getString("apply"));
            if (!row.getString("status").isBlank()) paragraph(row.getString("status"));
            boolean on = row.getBoolean("value");
            if (row.getBoolean("available")) {
                var control = button(left(), innerWidth(), on ? "ON" : "OFF", null,
                        on ? Ui.Style.ACCENT : Ui.Style.NORMAL, () -> request(new ConfigEditor.Action(
                                row.getString("key"), Boolean.toString(on), Boolean.toString(!on)), "Saving on server…"));
                control.active = canRequest();
                tip(control, on ? "Turn off" : "Turn on");
            } else {
                button(left(), innerWidth(), "Unavailable", null, Ui.Style.NORMAL, () -> {}).active = false;
            }
            gap(26);
        }
        if (!found) note("No matching settings.");
    }

    private boolean canRequest() {
        return pending.isEmpty() && AdminSettingsScreen.canManage();
    }

    private void request(ConfigEditor.Action action, String progress) {
        if (!canRequest()) return;
        pending = progress;
        Net.sendToServer(action);
        relayout();
    }

    private ListTag mods() {
        return data.getList("mods", Tag.TAG_COMPOUND);
    }

    private CompoundTag findMod(String id) {
        for (Tag raw : mods()) {
            CompoundTag mod = (CompoundTag) raw;
            if (mod.getString("id").equals(id)) return mod;
        }
        return null;
    }

    private static boolean matches(String query, String text) {
        String haystack = text.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        String needle = query.trim().toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return haystack.contains(needle.replaceAll("\\s+", " "));
    }

    private void select(String id) {
        selectedId = id;
        settingQuery = "";
        resetScroll();
    }

    private void resetScroll() {
        scroll = 0;
        relayout();
    }

    private void paragraph(String text) {
        for (var line : font.split(Component.literal(text), innerWidth())) {
            StringBuilder visible = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                visible.appendCodePoint(codePoint);
                return true;
            });
            note(visible.toString());
        }
    }

    @Override protected String saveLabel() { return "Done"; }
    @Override protected void save() { cancel(); }
}
