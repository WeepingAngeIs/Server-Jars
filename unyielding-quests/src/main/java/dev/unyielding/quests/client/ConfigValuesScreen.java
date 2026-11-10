package dev.unyielding.quests.client;

import com.google.gson.*;
import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import java.util.*;

/** In-game values and list editing; the server is the authority on validation and saving. */
public final class ConfigValuesScreen extends FormScreen {
    private static final Gson JSON = new Gson();
    private CompoundTag data;
    private String filter = "";
    private ConfigValuesScreen(Screen parent, CompoundTag data) {
        super(parent, "Config values");
        this.data = data;
    }
    public static void requestOpen() {
        if (AdminSettingsScreen.canManage()) Net.sendToServer(new ConfigEditor.Action("__open", "", ""));
    }
    static void openFiltered(Screen parent, CompoundTag data, String filter) {
        ConfigValuesScreen screen = new ConfigValuesScreen(parent, data);
        screen.filter = filter;
        Minecraft.getInstance().setScreen(screen);
    }
    public static void receive(ConfigEditor.Sync sync) {
        Minecraft mc = Minecraft.getInstance();
        String page = sync.data().getString("page");
        if (sync.open() && (page.equals("armor") || page.equals("raid"))) {
            if (mc.screen instanceof BalanceSettingsScreen balance) balance.receive(sync.data(), true);
            else mc.setScreen(new BalanceSettingsScreen(mc.screen, sync.data(), page.equals("raid")));
        } else if (mc.screen instanceof BalanceSettingsScreen balance && !sync.open()) balance.receive(sync.data(), false);
        else if (sync.open() && sync.data().getBoolean("oddsOpen")) {
            if (mc.screen instanceof SpinOddsScreen odds) odds.receive(sync.data(), true);
            else mc.setScreen(new SpinOddsScreen(mc.screen, sync.data()));
        } else if (mc.screen instanceof SpinOddsScreen odds && !sync.open()) odds.receive(sync.data(), false);
        else if (sync.open()) mc.setScreen(new ConfigValuesScreen(mc.screen, sync.data()));
        else if (mc.screen instanceof ValueScreen value) {
            value.list.data = sync.data();
            value.list.updateOddsParent();
            String reply = sync.data().getString("message");
            if (reply.startsWith("Saved.")) mc.setScreen(value.list);
            else value.failed(reply);
        } else if (mc.screen instanceof ConfigValuesScreen list) {
            list.data = sync.data();
            list.updateOddsParent();
            list.relayout();
        }
    }
    private void updateOddsParent() {
        if (parent instanceof SpinOddsScreen odds) odds.updateRelatedSettings(data);
        if (parent instanceof BalanceSettingsScreen balance) balance.updateRelatedSettings(data);
    }
    @Override protected void build() {
        note(font.plainSubstrByWidth(data.getString("message"), innerWidth()));
        gap(5);
        field("filter", left(), innerWidth() - 65, filter, "Search settings", s -> filter = s);
        button(right() - 60, 60, "Search", null, Ui.Style.NORMAL, () -> { scroll = 0; relayout(); });
        gap(27);
        int categoryWidth = (innerWidth() - 8) / 3;
        String[] categories = {"portal", "soul_tome", "leaderboard"};
        String[] labels = {"Portals", "Soul Tome", "Leaderboard"};
        for (int i = 0; i < categories.length; i++) {
            final String category = categories[i];
            button(left() + i * (categoryWidth + 4), categoryWidth, labels[i], null, Ui.Style.NORMAL,
                    () -> { filter = category + "."; scroll = 0; relayout(); });
        }
        gap(26);
        button(left(), (innerWidth() - 4) / 2, "Player commands", null, Ui.Style.NORMAL,
                () -> { filter = "essentials."; scroll = 0; relayout(); });
        button(left() + (innerWidth() + 4) / 2, (innerWidth() - 4) / 2, "Map icon size", null, Ui.Style.NORMAL,
                () -> { filter = "quests.custom_pokemon.map_icon_scale"; scroll = 0; relayout(); });
        gap(26);
        for (Tag raw : data.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)raw;
            String key = row.getString("key");
            if (!key.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) continue;
            note(font.plainSubstrByWidth(row.contains("title") ? row.getString("title") : key, innerWidth()));
            String preview = row.getString("value");
            button(left(), innerWidth(), font.plainSubstrByWidth(preview, innerWidth() - 18),
                    Ui.ICON_GEAR, Ui.Style.NORMAL, () -> minecraft.setScreen(new ValueScreen(this, row)));
            gap(28);
        }
    }
    @Override protected String saveLabel() { return "Done"; }
    @Override protected void save() { cancel(); }

    private static final class ValueScreen extends FormScreen {
        private final ConfigValuesScreen list;
        private final CompoundTag row;
        private final JsonElement original;
        private final List<String> entries = new ArrayList<>();
        private String text, message = "";
        private boolean flag, pending;
        private void failed(String reply) {
            message = reply;
            pending = false;
            relayout();
        }
        ValueScreen(ConfigValuesScreen parent, CompoundTag row) {
            super(parent, "Edit setting");
            this.list = parent;
            this.row = row;
            original = JsonParser.parseString(row.getString("value"));
            if (original.isJsonArray()) for (JsonElement item : original.getAsJsonArray()) entries.add(item.getAsString());
            else if (original.getAsJsonPrimitive().isBoolean()) flag = original.getAsBoolean();
            else text = original.getAsString();
        }
        private void paragraph(String text) {
            for (String line : text.split("\\n")) {
                while (!line.isEmpty()) {
                    String part = font.plainSubstrByWidth(line, innerWidth());
                    if (part.isEmpty()) break;
                    note(part);
                    line = line.substring(part.length());
                }
            }
        }
        @Override protected void build() {
            paragraph(row.contains("title") ? row.getString("title") : row.getString("key")); gap(6);
            paragraph(row.getString("help")); gap(10);
            if (!message.isEmpty()) { paragraph(message); gap(8); }
            if (original.isJsonArray()) {
                for (int i = 0; i < entries.size(); i++) {
                    final int index = i;
                    field("entry" + i, left(), cy, innerWidth() - 32, entries.get(i), "Entry", 1024,
                            s -> entries.set(index, s));
                    button(right() - 28, 28, "X", null, Ui.Style.NORMAL, () -> { entries.remove(index); relayout(); });
                    gap(25);
                }
                button(left(), 90, "Add entry", null, Ui.Style.NORMAL, () -> { entries.add(""); relayout(); }); gap(25);
            } else if (original.getAsJsonPrimitive().isBoolean()) {
                toggle(left(), innerWidth(), flag ? "ON" : "OFF", flag, v -> flag = v); gap(25);
            } else {
                field("value", left(), cy, innerWidth(), text, "Value", 4096, s -> text = s); gap(25);
            }
        }
        @Override protected void save() {
            if (pending || !AdminSettingsScreen.canManage()) return;
            try {
                String value;
                if (original.isJsonArray()) value = JSON.toJson(entries);
                else if (original.getAsJsonPrimitive().isBoolean()) value = JSON.toJson(flag);
                else if (original.getAsJsonPrimitive().isString()) value = JSON.toJson(text);
                else { new java.math.BigDecimal(text); value = text; }
                if (value.length() > 30000) {
                    message = "This setting is too large. Use fewer or shorter entries.";
                    relayout();
                    return;
                }
                pending = true;
                message = "Saving on server...";
                Net.sendToServer(new ConfigEditor.Action(row.getString("key"), row.getString("value"), value));
            } catch (RuntimeException ex) { message = "Enter a valid number."; }
            relayout();
        }
    }
}
