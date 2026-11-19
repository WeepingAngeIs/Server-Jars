package dev.unyielding.quests.client;

import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Searchable admin pages. Every edit waits for a fresh server snapshot. */
final class ModSettingsScreen extends FormScreen {
    private static final int PAGE_SIZE = 5;
    private CompoundTag data;
    private ListTag installedMods = new ListTag();
    private String selectedId = "";
    private String addingId = "";
    private String modQuery = "";
    private String settingQuery = "";
    private String pending = "";
    private boolean searchChanged;
    private boolean wasAdmin = true;
    private int page;

    ModSettingsScreen(ConfigValuesScreen parent, CompoundTag data) {
        super(parent, "Add Mod Settings");
        updateSnapshot(data);
    }

    private void updateSnapshot(CompoundTag snapshot) {
        data = snapshot;
        if (snapshot.contains("installedMods", Tag.TAG_LIST)) {
            installedMods = snapshot.getList("installedMods", Tag.TAG_COMPOUND);
        }
    }

    void receive(CompoundTag snapshot) {
        updateSnapshot(snapshot);
        CompoundTag added = findMod(addingId);
        addingId = "";
        pending = "";
        if (added != null && added.getBoolean("added") && added.getBoolean("installed")) {
            select(added.getString("id"));
            return;
        }
        relayout();
    }

    @Override public void tick() {
        super.tick();
        boolean admin = AdminSettingsScreen.canManage();
        if (wasAdmin != admin) {
            wasAdmin = admin;
            addingId = "";
            pending = "";
            relayout();
        }
        // Rebuild after the EditBox finishes moving its cursor for this keystroke.
        if (searchChanged) {
            searchChanged = false;
            page = 0;
            scroll = 0;
            relayout();
        }
    }

    @Override protected void build() {
        if (!AdminSettingsScreen.canManage()) {
            paragraph("Server administrator permission is required to view these settings.", Theme.text, false);
            return;
        }
        CompoundTag selected = findMod(selectedId);
        if (selected == null || !selected.getBoolean("added")) selectedId = "";
        if (selectedId.isEmpty()) buildCatalog();
        else buildSettings(selected);
    }

    private void buildCatalog() {
        field("modQuery", left(), innerWidth() - 70, modQuery, "Search mods by name or ID", s -> {
            modQuery = s;
            searchChanged = true;
        });
        refresh(right() - 65, 65);
        gap(27);
        feedback();
        paragraph("Pages are saved for your admin account in this world.", Theme.textDim, false);
        gap(8);
        String query = ModSettingsView.normalized(modQuery);
        List<CompoundTag> results = new ArrayList<>();
        collectMatches(mods(), query, results);
        if (!query.isEmpty()) collectMatches(installedMods, query, results);
        if (results.isEmpty()) {
            paragraph("No matching mod. Try a different name or mod ID.", Theme.text, false);
            return;
        }
        int lastPage = (results.size() - 1) / PAGE_SIZE;
        page = Math.min(page, lastPage);
        if (lastPage > 0) {
            int half = (innerWidth() - 4) / 2;
            button(left(), half, "Previous", null, Ui.Style.NORMAL, () -> changePage(-1)).active = page > 0;
            button(left() + half + 4, half, "Next", null, Ui.Style.NORMAL, () -> changePage(1)).active = page < lastPage;
            gap(24);
            note("Page " + (page + 1) + " of " + (lastPage + 1));
            gap(4);
        }
        for (int i = page * PAGE_SIZE; i < Math.min(results.size(), (page + 1) * PAGE_SIZE); i++) {
            modRow(results.get(i));
        }
    }

    private static void collectMatches(ListTag mods, String query, List<CompoundTag> results) {
        for (Tag raw : mods) {
            CompoundTag mod = (CompoundTag) raw;
            if (ModSettingsView.matches(query, mod.getString("name") + " " + mod.getString("id"))) results.add(mod);
        }
    }

    private void modRow(CompoundTag mod) {
        String id = mod.getString("id");
        paragraph(mod.getString("name"), Theme.text, true);
        paragraph(id, Theme.textDim, false);
        gap(4);
        switch (ModSettingsView.action(mod.getBoolean("installed"), mod.getBoolean("added"), mod.getBoolean("unsupported"))) {
            case ADD -> {
                var add = button(left(), innerWidth(), "Add to my settings", null, Ui.Style.ACCENT, () -> {
                    if (!canRequest()) return;
                    addingId = id;
                    request(new ConfigEditor.Action("__mod_settings_add", "", id), "Adding page…");
                });
                add.active = canRequest();
                if (!mod.getString("metadata").isBlank()) tip(add, mod.getString("metadata"));
                gap(26);
            }
            case OPEN -> {
                button(left(), innerWidth(), "Open settings", null, Ui.Style.NORMAL,
                        () -> select(id)).active = pending.isEmpty();
                gap(26);
            }
            case MISSING -> {
                paragraph("Mod missing from this server.", Theme.heading, false);
                gap(8);
            }
            case UNSUPPORTED -> {
                paragraph("Installed · No supported settings in Configtopia.", Theme.textDim, false);
                gap(8);
            }
        }
    }

    private void buildSettings(CompoundTag mod) {
        int half = (innerWidth() - 4) / 2;
        button(left(), half, "All mods", null, Ui.Style.NORMAL, () -> select("")).active = pending.isEmpty();
        refresh(left() + half + 4, half);
        gap(26);
        paragraph(mod.getString("name"), Theme.text, true);
        gap(4);
        field("settingQuery", left(), innerWidth(), settingQuery, "Search settings or try explosions", s -> {
            settingQuery = s;
            searchChanged = true;
        });
        gap(27);
        feedback();
        String query = ModSettingsView.normalized(settingQuery);
        boolean found = false;
        for (Tag raw : mod.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            if (!ModSettingsView.matches(query, row.getString("title") + " " + row.getString("aliases"))) continue;
            found = true;
            settingRow(row, mod.getBoolean("installed"));
        }
        if (!found) paragraph("No matching setting. Try its name or an alias.", Theme.text, false);
    }

    private void settingRow(CompoundTag row, boolean installed) {
        paragraph(row.getString("title"), Theme.heading, true);
        gap(4);
        if (!installed || !row.getBoolean("available") || !row.contains("value", Tag.TAG_BYTE)) {
            button(left(), innerWidth(), "Editing unavailable", Ui.ICON_LOCK, Ui.Style.NORMAL, () -> {}).active = false;
            gap(24);
            String reason = row.getString("status");
            if (!installed) reason = "UwU Dimension is missing from this server.";
            else if (row.getBoolean("available")) reason = "The server did not report a saved on/off value.";
            if (reason.isBlank()) reason = "The server could not confirm this setting.";
            paragraph(reason, Theme.text, false);
            gap(3);
            paragraph("Scope and restart timing unknown.", Theme.textDim, false);
        } else {
            boolean on = row.getBoolean("value");
            paragraph("Saved value · " + (on ? "ON" : "OFF"), Theme.text, false);
            paragraph("Scope · " + row.getString("scope"), Theme.textDim, false);
            var timing = ModSettingsView.timing(row.getString("activation"), row.getString("apply"));
            paragraph(timing.label(), timing.restart() ? Theme.heading : Theme.textDim, false);
            gap(4);
            button(left(), innerWidth(), on ? "Turn OFF" : "Turn ON", null, Ui.Style.NORMAL,
                    () -> request(new ConfigEditor.Action(row.getString("key"), Boolean.toString(on),
                            Boolean.toString(!on)), "Saving on server…")).active = canRequest();
            gap(24);
        }
        gap(6);
    }

    private void feedback() {
        String message = pending.isEmpty() ? data.getString("message") : pending;
        if (!message.isBlank()) {
            paragraph(message, Theme.text, false);
            gap(5);
        }
    }

    private void refresh(int x, int width) {
        button(x, width, "Refresh", null, Ui.Style.NORMAL,
                () -> request(new ConfigEditor.Action("__mod_settings", "", ""), "Refreshing…")).active = canRequest();
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
        if (id.isEmpty()) return null;
        for (Tag raw : mods()) {
            CompoundTag mod = (CompoundTag) raw;
            if (mod.getString("id").equals(id)) return mod;
        }
        return null;
    }

    private void select(String id) {
        selectedId = id;
        settingQuery = "";
        scroll = 0;
        relayout();
    }

    private void changePage(int direction) {
        page += direction;
        scroll = 0;
        relayout();
    }

    private void paragraph(String text, int color, boolean bold) {
        var component = Component.literal(text);
        if (bold) component.withStyle(ChatFormatting.BOLD);
        for (var line : font.split(component, innerWidth())) {
            StringBuilder visible = new StringBuilder();
            line.accept((index, style, codePoint) -> {
                visible.appendCodePoint(codePoint);
                return true;
            });
            bodyLine(visible.toString(), color, bold);
        }
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !selectedId.isEmpty() && pending.isEmpty()) {
            select("");
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override protected String saveLabel() { return "Done"; }
    @Override protected boolean showCancelButton() { return false; }
    @Override protected void save() { cancel(); }
}
