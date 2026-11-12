package dev.unyielding.quests.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.unyielding.quests.client.edit.FormScreen;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.settings.ConfigEditor;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.math.BigDecimal;
import java.util.Arrays;

/** Editable server drafts. A save changes one complete armor group or raid tier. */
public final class BalanceSettingsScreen extends FormScreen {
    private static final Gson JSON = new Gson();
    private static final String[] ARMOR_LABELS = {"Armor points", "Armor toughness", "Knockback resistance"};
    private static final String[] RAID_LABELS = {"Guarantee 1 perfect IV", "Guarantee 2 perfect IVs", "Guarantee 3 perfect IVs"};
    private static final class Draft {
        final String expected;
        final String[] text = new String[3];
        boolean dirty;
        Draft(String value) {
            expected = value;
            var array = JsonParser.parseString(value).getAsJsonArray();
            for (int i = 0; i < 3; i++) text[i] = array.get(i).getAsBigDecimal().stripTrailingZeros().toPlainString();
        }
    }
    private final boolean raid;
    private final Draft[] drafts;
    private CompoundTag data;
    private int group, pendingGroup = -1, reloadGroup = -1;
    private boolean pendingToggle, error;
    private long pendingSince;
    private int previewY;
    private String message = "Edit values, then Save to apply them on the server.";

    public BalanceSettingsScreen(Screen parent, CompoundTag data, boolean raid) {
        super(parent, raid ? "Raid Den IV odds" : "Armor Balance");
        this.raid = raid;
        this.data = data;
        drafts = new Draft[raid ? 7 : 1];
        for (int i = 0; i < drafts.length; i++) drafts[i] = new Draft(values(data, i));
    }
    public static void requestArmor() { request(false); }
    public static void requestRaid() { request(true); }
    private static void request(boolean raid) {
        if (AdminSettingsScreen.canManage()) Net.sendToServer(new ConfigEditor.Action(raid ? "__open_raid" : "__open_armor", "", ""));
    }
    private String values(CompoundTag data, int index) {
        if (!raid) return data.getString("armor_values");
        for (Tag raw : data.getList("raid_tiers", Tag.TAG_COMPOUND)) {
            CompoundTag tier = (CompoundTag) raw;
            if (tier.getInt("tier") == index + 1) return tier.getString("value");
        }
        throw new IllegalArgumentException("Raid tier is missing from the server settings.");
    }
    private String value(String key) {
        for (Tag raw : data.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            if (row.getString("key").equals(key)) return row.getString("value");
        }
        return "null";
    }
    private boolean busy() { return pendingGroup >= 0 || reloadGroup >= 0 || pendingToggle; }
    public void receive(CompoundTag next, boolean reload) {
        data = next;
        if (reload && reloadGroup >= 0) {
            drafts[reloadGroup] = new Draft(values(data, reloadGroup));
            reloadGroup = -1;
            message = "Reloaded this page from the server.";
            error = false;
        } else if (pendingGroup >= 0 || pendingToggle) {
            message = data.getString("message");
            error = !message.startsWith("Saved.");
            if (!error && pendingGroup >= 0) drafts[pendingGroup] = new Draft(values(data, pendingGroup));
            pendingGroup = -1;
            pendingToggle = false;
        } else updateRelatedSettings(next);
        relayout();
    }
    void updateRelatedSettings(CompoundTag next) {
        data = next;
        for (int i = 0; i < drafts.length; i++) {
            String latest = values(data, i);
            if (!drafts[i].dirty) drafts[i] = new Draft(latest);
            else if (!JsonParser.parseString(latest).equals(JsonParser.parseString(drafts[i].expected))) {
                message = "Server values changed. Reload the edited page before saving.";
                error = true;
            }
        }
    }
    @Override protected int panelHeight() { return 420; }
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
        int half = (innerWidth() - 6) / 2;
        if (raid) {
            boolean enabled = Boolean.parseBoolean(value("raid_ivs.enabled"));
            toggle(left(), innerWidth(), "Custom raid IV rolls: " + (enabled ? "ON" : "OFF"), enabled, this::setEnabled);
            gap(25);
            paragraph("Applies to future raid reward Pokemon. Existing Pokemon keep their IVs.");
            if (!enabled) paragraph("OFF uses Raid Dens' own IV rules. Your custom odds are kept.");
            gap(5);
            note("Choose raid tier (stars):");
            int width = (innerWidth() - 18) / 7;
            for (int i = 0; i < 7; i++) {
                final int index = i;
                button(left() + i * (width + 3), width, (i + 1) + (drafts[i].dirty ? "*" : ""), null,
                        group == i ? Ui.Style.ACCENT : Ui.Style.NORMAL, () -> select(index));
            }
            gap(27);
            heading("Tier " + (group + 1) + " raid rewards");
            if (group == 6) {
                paragraph("Raid Dens rolls the reward IVs. A bonus can raise additional IVs to 31.");
                gap(6);
                button(left(), innerWidth(), "Edit tier 7 bonus", null, Ui.Style.NORMAL, () -> openValues("tier_seven."));
                return;
            }
        } else {
            paragraph("100% is normal strength. 50% is half strength. Changes apply server-wide.");
            paragraph("Mod and item exceptions can override armor points and toughness.");
            gap(8);
        }
        String[] labels = raid ? RAID_LABELS : ARMOR_LABELS;
        for (int i = 0; i < 3; i++) {
            final int index = i;
            label(labels[i], left(), cy + 6);
            field("balance-" + group + "-" + i, right() - 85, cy, 70, drafts[group].text[i], labels[i] + " percent", 18,
                    text -> { drafts[group].text[index] = text; drafts[group].dirty = true; });
            label("%", right() - 10, cy + 6);
            gap(25);
        }
        previewY = cy;
        gap(raid ? 30 : 16);
        if (raid) {
            paragraph("Odds are separate slices of 100%. The remainder gets no guaranteed perfect IVs.");
            paragraph("A perfect IV is 31. Ordinary IV cap: " + value("raid_ivs.normal_iv_max") + ".");
            paragraph("If the cap is 31, ordinary rolls can also produce perfect IVs.");
            gap(5);
            button(left(), half, "Edit ordinary IV cap", null, Ui.Style.NORMAL,
                    () -> openValues("raid_ivs.normal_iv_max"));
            button(left() + half + 6, half, "Reload this tier", null, Ui.Style.NORMAL, this::reload);
        } else {
            button(left(), half, "Half armor preset", null, Ui.Style.NORMAL,
                    () -> preset(new String[]{"50", "50", "100"}));
            button(left() + half + 6, half, "Normal armor preset", null, Ui.Style.NORMAL,
                    () -> preset(new String[]{"100", "100", "100"}));
            gap(25);
            paragraph("Presets fill the draft. Save applies the three values together.");
            gap(5);
            button(left(), half, "Mod exceptions", null, Ui.Style.NORMAL, () -> openValues("armor.mod_overrides"));
            button(left() + half + 6, half, "Item exceptions", null, Ui.Style.NORMAL, () -> openValues("armor.item_overrides"));
            gap(25);
            button(left(), innerWidth(), "Reload server values", null, Ui.Style.NORMAL, this::reload);
        }
        gap(28);
        paragraph(message);
        gap(3);
    }
    private void select(int next) {
        if (busy()) return;
        group = next;
        message = "Each raid tier saves separately. Other drafts are kept.";
        error = false;
        relayout();
    }
    private void preset(String[] text) {
        if (busy()) return;
        System.arraycopy(text, 0, drafts[group].text, 0, 3);
        drafts[group].dirty = true;
        message = "Preset preview only. Save applies it on the server.";
        error = false;
        relayout();
    }
    private void openValues(String filter) {
        if (!busy() && AdminSettingsScreen.canManage()) ConfigValuesScreen.openFiltered(this, data, filter);
    }
    private void setEnabled(boolean enabled) {
        if (busy() || !AdminSettingsScreen.canManage()) return;
        pendingToggle = true;
        pendingSince = Util.getMillis();
        message = "Saving raid switch on the server...";
        Net.sendToServer(new ConfigEditor.Action("raid_ivs.enabled", value("raid_ivs.enabled"), Boolean.toString(enabled)));
    }
    private void reload() {
        if (busy()) return;
        Runnable request = () -> {
            reloadGroup = group;
            pendingSince = Util.getMillis();
            message = "Loading server values...";
            request(raid);
            relayout();
        };
        if (drafts[group].dirty) minecraft.setScreen(new ConfirmScreen(confirmed -> {
            minecraft.setScreen(this);
            if (confirmed) request.run();
        }, Component.literal("Discard this page's unsaved changes?"), Component.literal("Reload uses the saved server values. Other raid tiers are kept.")));
        else request.run();
    }
    private double[] entered() {
        double[] result = new double[3];
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            BigDecimal value;
            try { value = new BigDecimal(drafts[group].text[i].trim()); }
            catch (NumberFormatException failure) { throw new IllegalArgumentException("Enter a valid percentage in each field."); }
            if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(raid ? 100 : 300)) > 0)
                throw new IllegalArgumentException("Percentages must be between 0 and " + (raid ? 100 : 300) + ".");
            result[i] = value.doubleValue();
            total = total.add(value);
        }
        if (raid && total.compareTo(BigDecimal.valueOf(100)) > 0)
            throw new IllegalArgumentException("The three raid chances must total 100% or less.");
        return result;
    }
    @Override protected void save() {
        if (busy() || !AdminSettingsScreen.canManage()) return;
        try {
            if (!drafts[group].dirty) { message = "No changes to save."; error = false; }
            else {
                double[] next = entered();
                pendingGroup = group;
                pendingSince = Util.getMillis();
                message = "Saving on the server...";
                error = false;
                Net.sendToServer(new ConfigEditor.Action(raid ? "__raid_odds:" + (group + 1) : "__armor", drafts[group].expected, JSON.toJson(next)));
            }
        } catch (RuntimeException failure) { message = failure.getMessage(); error = true; }
        relayout();
    }
    @Override public void tick() {
        if (!AdminSettingsScreen.canManage()) {
            boolean refresh = !message.equals("Administrator permission is required.");
            pendingGroup = reloadGroup = -1;
            pendingToggle = false;
            message = "Administrator permission is required.";
            error = true;
            if (refresh) relayout();
        } else if (busy() && Util.getMillis() - pendingSince > 20000L) {
            pendingGroup = reloadGroup = -1;
            pendingToggle = false;
            message = "Server reply delayed. Reload before retrying.";
            error = true;
            relayout();
        }
        for (var child : children()) if (child instanceof AbstractWidget widget)
            widget.active = !busy() && AdminSettingsScreen.canManage();
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) renderPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, 0.25f));
    }
    @Override protected void renderExtra(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (raid && group == 6) return;
        graphics.enableScissor(px + 2, top, px + pw - 2, bottom);
        try {
            double[] next = entered();
            String preview = raid ? "No guaranteed perfect IV: " + BigDecimal.valueOf(100 - Arrays.stream(next).sum()).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%"
                    : "Allowed range: 0% to 300%";
            graphics.drawString(font, font.plainSubstrByWidth(preview, innerWidth()), left(), previewY, Theme.good, false);
        } catch (RuntimeException failure) {
            graphics.drawString(font, font.plainSubstrByWidth(failure.getMessage(), innerWidth()), left(), previewY, Theme.bad, false);
        }
        graphics.disableScissor();
        String state = busy() ? "Waiting..." : error ? "Check details" : drafts[group].dirty ? "Unsaved" : "Server values";
        graphics.drawString(font, state, left(), py + ph - 20, error ? Theme.bad : Theme.textDim, false);
    }
    @Override protected void cancel() {
        if (busy()) return;
        if (Arrays.stream(drafts).anyMatch(draft -> draft.dirty)) minecraft.setScreen(new ConfirmScreen(
                confirmed -> minecraft.setScreen(confirmed ? parent : this), Component.literal("Discard unsaved balance settings?"),
                Component.literal("Saved server settings stay unchanged. Unsaved drafts will be discarded.")));
        else super.cancel();
    }
    @Override public void onClose() { cancel(); }
}
