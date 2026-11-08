package dev.unyielding.quests.client;

import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.MusicSettings;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.util.Locale;

/** Server mixer for the bundled song catalog; never rewrites a player's sound options. */
public final class MusicAdminScreen extends FormScreen {
    private CompoundTag data;
    private String search = "", message;

    public MusicAdminScreen(Screen parent, CompoundTag data, String message) {
        super(parent, "Server music mixer");
        this.data = data.copy();
        this.message = message;
    }
    public static void requestOpen() {
        if (AdminSettingsScreen.canManage()) Net.sendToServer(new MusicSettings.Action("__open", 0, 0));
    }
    public static void receive(MusicSettings.Sync sync) {
        Minecraft mc = Minecraft.getInstance();
        if (sync.open() && AdminSettingsScreen.canManage()) {
            mc.setScreen(new MusicAdminScreen(mc.screen, sync.state(), sync.message()));
        } else if (mc.screen instanceof MusicAdminScreen screen) {
            screen.data = sync.state().copy();
            if (!sync.message().isEmpty()) screen.message = sync.message();
            screen.relayout();
        } else if (mc.screen instanceof TrackScreen track) track.receive(sync);
    }
    static String name(String track) {
        if (track.equals(MusicSettings.MASTER)) return "Overall music volume";
        if (track.equals("lavender")) return "Lavender Town";
        String[] words = track.replace('_', ' ').replace('/', ' ').split(" ");
        for (int i = 0; i < words.length; i++) if (!words[i].isEmpty())
            words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
        return String.join(" ", words);
    }
    static double value(CompoundTag data, String track) {
        if (track.equals(MusicSettings.MASTER)) return data.getDouble("master");
        CompoundTag entries = data.getCompound("volumes");
        return entries.contains(track) ? entries.getDouble(track) : 100;
    }
    static String number(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
    private void paragraph(String text) {
        while (!text.isEmpty()) {
            String line = font.plainSubstrByWidth(text, innerWidth());
            if (line.isEmpty()) break;
            note(line); text = text.substring(line.length());
        }
    }
    @Override protected int panelHeight() { return 420; }
    @Override protected void build() {
        paragraph("Live volume for everyone using the Cobblemon Unyielding songpack and this mod update.");
        paragraph("Personal sound sliders still apply. 100% keeps the existing audio level; 0% mutes.");
        gap(5);
        row(MusicSettings.MASTER);
        field("music-search", left(), innerWidth(), search, "Search tracks", text -> {
            search = text; scroll = 0; relayout();
        });
        gap(28);
        int matches = 0;
        for (String track : MusicSettings.TRACKS) {
            if (!(track + " " + name(track)).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
            row(track); matches++;
        }
        if (matches == 0) note("No matching tracks.");
        gap(5);
        paragraph(message);
        paragraph("New audio or different music triggers require a songpack update.");
    }
    private void row(String track) {
        String percent = number(value(data, track)) + "%";
        button(left(), innerWidth(), name(track) + "  " + percent, null,
                track.equals(MusicSettings.MASTER) ? Ui.Style.ACCENT : Ui.Style.NORMAL, () -> {
                    if (AdminSettingsScreen.canManage()) minecraft.setScreen(new TrackScreen(this, track));
                });
        gap(25);
    }
    @Override protected String saveLabel() { return "Done"; }
    @Override protected void save() { cancel(); }
    @Override public void onClose() { cancel(); }

    public static final class TrackScreen extends FormScreen {
        private final MusicAdminScreen list;
        private final String track, original;
        private final long expectedRevision;
        private String draft, message = "Save applies this volume to everyone immediately.";
        private boolean pending;
        private long pendingSince;
        TrackScreen(MusicAdminScreen list, String track) {
            super(list, name(track));
            this.list = list; this.track = track;
            expectedRevision = list.data.getLong("revision");
            original = number(value(list.data, track)); draft = original;
        }
        private void paragraph(String text) {
            while (!text.isEmpty()) {
                String line = font.plainSubstrByWidth(text, innerWidth());
                if (line.isEmpty()) break;
                note(line); text = text.substring(line.length());
            }
        }
        @Override protected int panelHeight() { return 300; }
        @Override protected void build() {
            paragraph("Server volume multiplier: 0-200%. 100% preserves the audio's existing level.");
            paragraph("The overall and track levels multiply together, then your personal sound settings apply.");
            gap(9);
            label("Volume", left(), cy + 6);
            field("music-percent", left() + 75, cy, innerWidth() - 95, draft, "Percentage", 12,
                    text -> draft = text).active = !pending;
            label("%", right() - 12, cy + 6);
            gap(28);
            int third = (innerWidth() - 8) / 3;
            button(left(), third, "Mute", null, Ui.Style.NORMAL, () -> preset("0"));
            button(left() + third + 4, third, "Half", null, Ui.Style.NORMAL, () -> preset("50"));
            button(left() + 2 * (third + 4), third, "Normal", null, Ui.Style.NORMAL, () -> preset("100"));
            gap(28);
            paragraph("Presets fill the draft. Muting keeps the track in the rotation, playing silently.");
            paragraph("Volume changes are saved with the server world and sent to players when they join.");
            gap(8);
            paragraph(message);
        }
        private void preset(String text) { if (!pending) { draft = text; relayout(); } }
        @Override protected String saveLabel() { return pending ? "Saving..." : "Save"; }
        @Override protected void save() {
            if (pending || !AdminSettingsScreen.canManage()) return;
            double percent;
            try { percent = Double.parseDouble(draft.trim()); }
            catch (NumberFormatException error) { percent = Double.NaN; }
            if (!MusicSettings.valid(percent)) { message = "Enter a percentage from 0 to 200."; relayout(); return; }
            pending = true; pendingSince = Util.getMillis(); message = "Saving on server...";
            relayout();
            Net.sendToServer(new MusicSettings.Action(track, expectedRevision, percent));
        }
        void receive(MusicSettings.Sync sync) {
            list.data = sync.state().copy();
            if (sync.message().isEmpty()) return;
            message = sync.message(); pending = false;
            if (message.startsWith("Saved.")) {
                list.message = message; minecraft.setScreen(list);
            } else relayout();
        }
        @Override public void tick() {
            if (pending && Util.getMillis() - pendingSince > 10000) {
                pending = false; message = "No reply yet. Close and reopen this track to check server values."; relayout();
            }
        }
        @Override protected void cancel() {
            if (pending) return;
            if (!draft.equals(original)) minecraft.setScreen(new ConfirmScreen(discard -> {
                minecraft.setScreen(discard ? list : this);
            }, Component.literal("Discard volume draft?"), Component.literal("Your unsaved volume change will be lost.")));
            else minecraft.setScreen(list);
        }
        @Override public void onClose() { cancel(); }
    }
}
