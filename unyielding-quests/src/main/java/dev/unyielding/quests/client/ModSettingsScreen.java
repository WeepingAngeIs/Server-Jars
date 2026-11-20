package dev.unyielding.quests.client;

import com.google.gson.JsonObject;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Searchable admin pages. Every server edit waits for an authoritative snapshot. */
final class ModSettingsScreen extends FormScreen {
    private static final int PAGE_SIZE = 5;
    private static final String EXCAVATION = "external.uwudimension.heart_launcher_excavation";
    private CompoundTag data;
    private ListTag installedMods = new ListTag();
    private final Map<String, Optional<IConfigScreenFactory>> factories = new HashMap<>();
    private final ModCommandControls commandControls = new ModCommandControls();
    private String selectedId = "";
    private String addingId = "";
    private String modQuery = "";
    private String settingQuery = "";
    private String pending = "";
    private String clientMessage = "";
    private EditBox searchBox;
    private boolean searchChanged;
    private boolean wasAdmin = true;
    private int page;
    private CompoundTag editing;
    private String draft = "";

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
        clientMessage = "";
        if (editing != null) {
            // Keep a rejected draft if the server still reports the value the user started from.
            // A concurrent change ends the edit so a subsequent save always starts with a fresh value.
            var page = findMod(selectedId);
            String key = editing.getString("key"), original = current(editing);
            editing = null;
            if (page != null) for (Tag raw : page.getList("rows", Tag.TAG_COMPOUND)) {
                var row = (CompoundTag) raw;
                if (key.equals(row.getString("key")) && row.getBoolean("available") && original.equals(current(row))) {
                    editing = row;
                    break;
                }
            }
        }
        if (added != null && added.getBoolean("added") && added.getBoolean("installed")) {
            selectedId = added.getString("id");
            settingQuery = "";
            scroll = 0;
        }
        if (selectedId.equals(data.getString("detailId"))) settingQuery = data.getString("query");
        relayout();
    }

    private boolean paged() {
        return data.getInt("catalogVersion") >= 2;
    }

    @Override public void tick() {
        super.tick();
        boolean admin = AdminSettingsScreen.canManage();
        if (wasAdmin != admin) {
            wasAdmin = admin;
            addingId = "";
            pending = "";
            editing = null;
            relayout();
        }
        if (searchChanged) {
            searchChanged = false;
            page = 0;
            scroll = 0;
            relayout();
        }
    }

    @Override protected void build() {
        searchBox = null;
        if (!AdminSettingsScreen.canManage()) {
            paragraph("Server administrator permission is required to view these settings.", Theme.text, false);
            return;
        }
        CompoundTag selected = findMod(selectedId);
        if (selected == null || !selected.getBoolean("added")) {
            selectedId = "";
            editing = null;
        }
        if (selectedId.isEmpty()) buildCatalog();
        else if (editing != null) buildEditor();
        else buildSettings(selected);
    }

    private void buildCatalog() {
        field("modQuery", left(), innerWidth() - 70, modQuery, "Search mods by name or ID", s -> {
            modQuery = s;
            searchChanged = true;
        });
        refresh(right() - 65, 65);
        gap(25);
        commandsButton(left(), innerWidth(), "", "Server commands");
        gap(25);
        feedback();
        String query = ModSettingsView.normalized(modQuery);
        List<CompoundTag> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectMatches(mods(), query, results, seen);
        collectMatches(installedMods, query, results, seen);
        if (!query.isEmpty()) {
            collectNativeMatches(query, results, seen);
        }
        if (results.isEmpty()) {
            paragraph("No matching mod. Try a different name or mod ID.", Theme.text, false);
            return;
        }
        int lastPage = (results.size() - 1) / PAGE_SIZE;
        page = Math.min(page, lastPage);
        if (lastPage > 0) {
            pagination(page, lastPage + 1, i -> {
                page = i;
                scroll = 0;
                relayout();
            });
        }
        for (int i = page * PAGE_SIZE; i < Math.min(results.size(), (page + 1) * PAGE_SIZE); i++) {
            modRow(results.get(i));
        }
    }

    private static void collectMatches(Iterable<? extends Tag> mods, String query, List<CompoundTag> results,
                                       Set<String> seen) {
        for (Tag raw : mods) {
            CompoundTag mod = (CompoundTag) raw;
            String id = mod.getString("id");
            if (ModSettingsView.matches(query, mod.getString("name") + " " + id) && seen.add(id)) results.add(mod);
        }
    }

    private void collectNativeMatches(String query, List<CompoundTag> results, Set<String> seen) {
        ModList.get().getMods().stream()
                .filter(info -> !seen.contains(info.getModId())
                        && ModSettingsView.matches(query, info.getDisplayName() + " " + info.getModId()))
                .sorted(java.util.Comparator.comparing(info -> info.getDisplayName(), String.CASE_INSENSITIVE_ORDER))
                .forEach(info -> {
                    if (nativeFactory(info.getModId()).isEmpty()) return;
                    CompoundTag entry = new CompoundTag();
                    entry.putString("id", info.getModId());
                    entry.putString("name", info.getDisplayName());
                    entry.putBoolean("clientOnlyEntry", true);
                    results.add(entry);
                });
    }

    private Optional<IConfigScreenFactory> nativeFactory(String id) {
        return factories.computeIfAbsent(id, key -> {
            try {
                return ModList.get().getModContainerById(key)
                        .flatMap(container -> IConfigScreenFactory.getForMod(container.getModInfo()));
            } catch (RuntimeException | LinkageError e) {
                UnyieldingQuests.LOGGER.warn("Could not check config screen for {}", key, e);
                return Optional.empty();
            }
        });
    }

    private void nativeButton(String id) {
        var open = button(left(), innerWidth(), "Open mod’s own settings", null, Ui.Style.NORMAL, () -> {
            if (!canRequest()) return;
            try {
                var container = ModList.get().getModContainerById(id);
                if (container.isPresent()) {
                    var factory = nativeFactory(id);
                    if (factory.isPresent()) {
                        var screen = factory.get().createScreen(container.get(), this);
                        if (screen != null) {
                            minecraft.setScreen(screen);
                            return;
                        }
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                UnyieldingQuests.LOGGER.warn("Could not open config screen for {}", id, e);
            }
            clientMessage = "This mod could not open its settings screen.";
            relayout();
        });
        tip(open, "Opens the configuration screen provided by the copy of this mod on your computer.");
        open.active = canRequest();
        gap(25);
    }

    private void commandsButton(int x, int w, String mod, String label) {
        button(x, w, label, null, Ui.Style.NORMAL, () -> {
            if (canRequest()) minecraft.setScreen(new ModCommandsBrowser(this, ModCommandControls.modFilter(mod)));
        }).active = canRequest();
    }

    private void modRow(CompoundTag mod) {
        String id = mod.getString("id");
        paragraph(mod.getString("name"), Theme.text, true);
        paragraph(id, Theme.textDim, false);
        gap(4);
        if (mod.getBoolean("clientOnlyEntry")) {
            nativeButton(id);
            return;
        }
        switch (ModSettingsView.action(mod.getBoolean("installed"), mod.getBoolean("added"), mod.getBoolean("unsupported"))) {
            case ADD -> {
                var add = button(left(), innerWidth(), "Add to my settings", null, Ui.Style.ACCENT, () -> {
                    if (!canRequest()) return;
                    addingId = id;
                    request(new ConfigEditor.Action("__mod_settings_add", "", id), "Opening settings…");
                });
                add.active = canRequest();
                if (!mod.getString("metadata").isBlank()) tip(add, mod.getString("metadata"));
                gap(26);
                if (findMod(id) == null && nativeFactory(id).isPresent()) nativeButton(id);
            }
            case OPEN -> {
                button(left(), innerWidth(), "Open settings", null, Ui.Style.NORMAL,
                        () -> select(id)).active = canRequest();
                gap(26);
            }
            case MISSING -> {
                paragraph("Mod missing from this server.", Theme.textDim, false);
                if (!modQuery.isBlank() && nativeFactory(id).isPresent()) nativeButton(id);
                gap(8);
            }
            case UNSUPPORTED -> {
                if (nativeFactory(id).isPresent()) nativeButton(id);
                else {
                    paragraph("No registered server settings.", Theme.textDim, false);
                }
                commandsButton(left(), innerWidth(), id, "Find server commands");
                gap(26);
            }
        }
    }

    private void buildSettings(CompoundTag mod) {
        int third = (innerWidth() - 8) / 3;
        button(left(), third, "All mods", null, Ui.Style.NORMAL, () -> select("")).active = canRequest();
        commandsButton(left() + third + 4, third, selectedId, "Commands");
        refresh(left() + 2 * third + 8, innerWidth() - 2 * third - 8);
        gap(26);
        paragraph(mod.getString("name"), Theme.text, true);
        gap(4);
        boolean remote = paged();
        int searchWidth = remote ? innerWidth() - 63 : innerWidth();
        searchBox = field("settingQuery", left(), cy, searchWidth, settingQuery, "Search settings", 96, s -> {
            settingQuery = s;
            if (!remote) searchChanged = true;
        });
        searchBox.setEditable(canRequest());
        if (remote) button(right() - 59, 59, "Search", null, Ui.Style.NORMAL,
                () -> loadPage(settingQuery, 0)).active = canRequest();
        gap(27);
        feedback();
        boolean hasDetail = !remote || selectedId.equals(data.getString("detailId"));
        if (hasDetail) {
            int pages = data.getInt("pages");
            if (remote && pages > 1) pagination(data.getInt("index"), pages, i -> loadPage(data.getString("query"), i));
            String query = ModSettingsView.normalized(settingQuery);
            boolean found = false;
            boolean excavationVisible = false;
            for (Tag raw : mod.getList("rows", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag) raw;
                if (!remote && !ModSettingsView.matches(query, row.getString("title") + " " + row.getString("key")
                        + " " + row.getString("aliases"))) continue;
                found = true;
                if (EXCAVATION.equals(row.getString("key")) && row.getBoolean("available")) excavationVisible = true;
                settingRow(row, mod.getBoolean("installed"));
            }
            String appliedQuery = remote ? data.getString("query") : settingQuery;
            var connection = minecraft.getConnection();
            var controls = commandControls.find(connection == null ? null : connection.getCommands().getRoot(),
                    selectedId, appliedQuery, excavationVisible);
            if (!controls.isEmpty()) {
                gap(2);
                paragraph("Matching server commands", Theme.heading, true);
                for (var control : controls) {
                    paragraph("/" + control.entry().usage(), Theme.text, false);
                    button(left(), innerWidth(), control.label(), null, Ui.Style.NORMAL, () -> {
                        if (!canRequest()) return;
                        String status = ModCommandsBrowser.activate(this, ModCommandControls.modFilter(selectedId), control.entry());
                        if (!status.isEmpty()) {
                            clientMessage = status;
                            relayout();
                        }
                    }).active = canRequest();
                    gap(25);
                }
            } else if (!found) {
                paragraph(appliedQuery.isBlank()
                        ? "No standard settings were found. Open Commands to see what the server provides."
                        : "No matching setting. Try another search.", Theme.text, false);
            }
        }
        if (canRequest() && nativeFactory(selectedId).isPresent()) nativeButton(selectedId);
    }

    private void pagination(int index, int pages, java.util.function.IntConsumer go) {
        int half = (innerWidth() - 4) / 2;
        button(left(), half, "Previous", null, Ui.Style.NORMAL, () -> go.accept(index - 1))
                .active = canRequest() && index > 0;
        button(left() + half + 4, half, "Next", null, Ui.Style.NORMAL, () -> go.accept(index + 1))
                .active = canRequest() && index + 1 < pages;
        gap(24);
        note("Page " + (index + 1) + " of " + pages);
        gap(4);
    }

    private static String kind(CompoundTag row) {
        if (!row.getString("kind").isEmpty()) return row.getString("kind");
        return row.contains("value", Tag.TAG_BYTE) ? "boolean" : "";
    }

    private static String current(CompoundTag row) {
        if (row.contains("current", Tag.TAG_STRING)) return row.getString("current");
        return row.contains("value", Tag.TAG_BYTE) ? Boolean.toString(row.getBoolean("value")) : "";
    }

    private void showPath(CompoundTag row) {
        String path = row.getString("path");
        if (path.isBlank() && !EXCAVATION.equals(row.getString("key"))) path = row.getString("aliases");
        if (!path.isBlank()) paragraph(path, Theme.textDim, false);
    }

    private void settingRow(CompoundTag row, boolean installed) {
        paragraph(row.getString("title"), Theme.heading, true);
        showPath(row);
        String value = current(row);
        String type = kind(row);
        if (!installed || !row.getBoolean("available") || value.isEmpty()) {
            String reason = row.getString("status");
            if (!installed) reason = "Mod missing from this server.";
            paragraph(reason.isBlank() ? "The server could not read this setting." : reason, Theme.textDim, false);
            gap(7);
            return;
        }
        boolean bool = type.equals("boolean");
        paragraph("Saved value · " + (bool ? (Boolean.parseBoolean(value) ? "ON" : "OFF") : value), Theme.text, false);
        String scope = row.getString("scope");
        var timing = ModSettingsView.timing(row.getString("activation"), row.getString("apply"));
        paragraph(scope.isBlank() ? timing.label() : scope + " · " + timing.label(),
                timing.restart() ? Theme.heading : Theme.textDim, false);
        if (!row.getString("note").isBlank()) paragraph(row.getString("note"), Theme.textDim, false);
        gap(4);
        if (bool) {
            boolean on = Boolean.parseBoolean(value);
            button(left(), innerWidth(), on ? "Turn OFF" : "Turn ON", null, Ui.Style.NORMAL,
                    () -> write(row, value, Boolean.toString(!on))).active = canRequest();
        } else if (type.equals("number") || (type.equals("enum") && !row.getList("opt", Tag.TAG_STRING).isEmpty())) {
            button(left(), innerWidth(), "Edit value", null, Ui.Style.NORMAL, () -> {
                if (!canRequest()) return;
                editing = row;
                draft = value;
                clientMessage = "";
                scroll = 0;
                relayout();
            }).active = canRequest();
        } else {
            paragraph("This value type has no editor.", Theme.textDim, false);
            gap(6);
            return;
        }
        gap(28);
    }

    private void buildEditor() {
        paragraph(editing.getString("title"), Theme.heading, true);
        showPath(editing);
        paragraph("Saved value · " + current(editing), Theme.text, false);
        gap(6);
        if (kind(editing).equals("enum")) {
            ListTag items = editing.getList("opt", Tag.TAG_STRING);
            String[] options = new String[items.size()];
            for (int i = 0; i < options.length; i++) options[i] = items.getString(i);
            if (options.length > 0) cycle(left(), innerWidth(), "", options, draft, s -> draft = s).active = canRequest();
        } else {
            field("number", left(), cy, innerWidth(), draft, "Number", 64, s -> draft = s).setEditable(canRequest());
        }
        gap(25);
        int half = (innerWidth() - 4) / 2;
        button(left(), half, "Back", null, Ui.Style.NORMAL, this::closeEditor).active = canRequest();
        button(left() + half + 4, half, "Save", null, Ui.Style.ACCENT, () -> {
            String next = draft.trim();
            if (kind(editing).equals("number") && !ModSettingsView.isNumber(next)) {
                clientMessage = "Enter a number.";
                relayout();
                return;
            }
            write(editing, current(editing), next);
        }).active = canRequest();
        gap(27);
        feedback();
    }

    private void closeEditor() {
        editing = null;
        clientMessage = "";
        scroll = 0;
        relayout();
    }

    private void write(CompoundTag row, String expected, String value) {
        if (!canRequest()) return;
        String key = row.getString("key");
        if (EXCAVATION.equals(key)) {
            request(new ConfigEditor.Action(key, expected, value), "Saving on server…");
        } else {
            JsonObject payload = pageArgs(data.getString("query"), data.getInt("index"));
            payload.addProperty("key", key);
            payload.addProperty("expected", expected);
            payload.addProperty("value", value);
            request(new ConfigEditor.Action("__mod_settings_write", selectedId, payload.toString()), "Saving on server…");
        }
    }

    private void feedback() {
        String message = pending.isEmpty() ? (clientMessage.isEmpty() ? data.getString("message") : clientMessage) : pending;
        if (!message.isBlank()) {
            paragraph(message, Theme.text, false);
            gap(5);
        }
    }

    private void refresh(int x, int width) {
        button(x, width, "Refresh", null, Ui.Style.NORMAL, () -> {
            if (paged() && !selectedId.isEmpty()) loadPage(data.getString("query"), data.getInt("index"));
            else request(new ConfigEditor.Action("__mod_settings", "", ""), "Refreshing…");
        }).active = canRequest();
    }

    private static JsonObject pageArgs(String query, int index) {
        JsonObject request = new JsonObject();
        request.addProperty("q", query);
        request.addProperty("p", index);
        return request;
    }

    private void loadPage(String query, int index) {
        if (!canRequest()) return;
        scroll = 0;
        request(new ConfigEditor.Action("__mod_settings_page", selectedId, pageArgs(query, index).toString()), "Loading settings…");
    }

    private boolean canRequest() {
        return pending.isEmpty() && AdminSettingsScreen.canManage();
    }

    private void request(ConfigEditor.Action action, String progress) {
        if (!canRequest()) return;
        clientMessage = "";
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
        clientMessage = "";
        editing = null;
        scroll = 0;
        if (!id.isEmpty() && paged()) loadPage("", 0);
        else relayout();
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
        if (key == GLFW.GLFW_KEY_ESCAPE && !selectedId.isEmpty() && canRequest()) {
            if (editing != null) closeEditor();
            else select("");
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && paged()
                && searchBox != null && searchBox.isFocused() && canRequest()) {
            loadPage(settingQuery, 0);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override protected String saveLabel() { return "Done"; }
    @Override protected boolean showCancelButton() { return false; }
    @Override protected void save() { cancel(); }
}
