package dev.unyielding.quests.client;

import com.google.gson.Gson;
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

/** Percentage-based drafts; the server saves one complete set of odds at a time. */
public final class SpinOddsScreen extends FormScreen {
    private static final Gson JSON = new Gson();
    private static final String[] KEYS = {"basic", "good", "paradox", "mythical", "legendary"};
    private static final String[] LABELS = {"Basic items", "Good items", "Paradox Pokemon", "Mythical Pokemon", "Legendary Pokemon"};
    private static final String[] GROUPS = {"quest", "daily"};

    private static final class Draft {
        int[] expected;
        final String[] percentages = new String[5];
        boolean dirty;
        boolean rounded;

        Draft(int[] weights) {
            expected = weights;
            int[] displayed = percentages(weights);
            for (int i = 0; i < displayed.length; i++) percentages[i] = decimal(displayed[i]);
            long total = Arrays.stream(weights).asLongStream().sum();
            for (int i = 0; i < weights.length; i++)
                if (weights[i] * 10000L != displayed[i] * total) rounded = true;
        }
    }

    private CompoundTag data;
    private final Draft[] drafts = new Draft[2];
    private int group;
    private int pendingGroup = -1, reloadGroup = -1;
    private int previewY;
    private long pendingSince;
    private String message = "Edit percentages, then Save this tab.";
    private boolean error;

    public SpinOddsScreen(Screen parent, CompoundTag data) {
        super(parent, "Lucky Spin odds");
        this.data = data;
        for (int i = 0; i < drafts.length; i++) drafts[i] = new Draft(weights(data, i));
    }

    public static void requestOpen() {
        if (AdminSettingsScreen.canManage()) Net.sendToServer(new ConfigEditor.Action("__open_odds", "", ""));
    }

    private static int[] weights(CompoundTag data, int group) {
        int[] result = new int[5];
        String prefix = "quests." + (group == 0 ? "lucky_spin" : "daily_login") + ".chance_";
        for (Tag raw : data.getList("rows", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) raw;
            for (int i = 0; i < KEYS.length; i++) {
                if (row.getString("key").equals(prefix + KEYS[i])) result[i] = Integer.parseInt(row.getString("value"));
            }
        }
        return result;
    }

    /** Largest-remainder rounding keeps the five displayed percentages at exactly 100.00. */
    private static int[] percentages(int[] weights) {
        long total = Arrays.stream(weights).asLongStream().sum();
        int[] result = new int[5];
        if (total <= 0) return result;
        long[] remainders = new long[5];
        int assigned = 0;
        for (int i = 0; i < result.length; i++) {
            long scaled = weights[i] * 10000L;
            result[i] = (int) (scaled / total);
            remainders[i] = scaled % total;
            assigned += result[i];
        }
        while (assigned < 10000) {
            int best = 0;
            for (int i = 1; i < remainders.length; i++) if (remainders[i] > remainders[best]) best = i;
            result[best]++;
            remainders[best] = -1;
            assigned++;
        }
        return result;
    }

    private static String decimal(int hundredths) {
        return BigDecimal.valueOf(hundredths, 2).stripTrailingZeros().toPlainString();
    }

    private int[] entered() {
        int[] weights = new int[5];
        for (int i = 0; i < weights.length; i++) {
            String text = drafts[group].percentages[i].trim();
            if (!text.matches("(?:[0-9]{1,3})(?:\\.[0-9]{1,2})?"))
                throw new IllegalArgumentException("Use percentages with up to 2 decimals.");
            weights[i] = new BigDecimal(text).movePointRight(2).intValueExact();
            if (weights[i] > 10000) throw new IllegalArgumentException("Each percentage must be between 0 and 100.");
        }
        return weights;
    }

    public void receive(CompoundTag next, boolean reload) {
        data = next;
        if (reload && reloadGroup >= 0) {
            drafts[reloadGroup] = new Draft(weights(data, reloadGroup));
            reloadGroup = -1;
            message = "Reloaded this tab from the server.";
            error = false;
        } else if (pendingGroup >= 0) {
            message = data.getString("message");
            error = !message.startsWith("Saved.");
            if (!error) drafts[pendingGroup] = new Draft(weights(data, pendingGroup));
            pendingGroup = -1;
        }
        relayout();
    }

    void updateRelatedSettings(CompoundTag next) {
        data = next;
        for (int i = 0; i < drafts.length; i++) {
            int[] latest = weights(data, i);
            if (!drafts[i].dirty) drafts[i] = new Draft(latest);
            else if (!Arrays.equals(drafts[i].expected, latest)) {
                message = "Server odds changed. Reload the edited tab before saving.";
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
        button(left(), half, "Quest spins" + (drafts[0].dirty ? " *" : ""), null,
                group == 0 ? Ui.Style.ACCENT : Ui.Style.NORMAL, () -> select(0));
        button(left() + half + 6, half, "Daily spins" + (drafts[1].dirty ? " *" : ""), null,
                group == 1 ? Ui.Style.ACCENT : Ui.Style.NORMAL, () -> select(1));
        gap(26);
        paragraph(group == 0 ? "Each quest / Lucky Spin reward uses these odds."
                : "Daily login rewards and streak spins use these odds.");
        if (drafts[group].rounded) paragraph("These custom odds have finer precision. Saving edits rounds them to 0.01%.");
        gap(5);
        for (int i = 0; i < LABELS.length; i++) {
            final int index = i;
            label(LABELS[i], left(), cy + 6);
            field("odds-" + group + "-" + i, right() - 85, cy, 70, drafts[group].percentages[i],
                    LABELS[i] + " percent", 6, value -> {
                        drafts[group].percentages[index] = value;
                        drafts[group].dirty = true;
                    });
            label("%", right() - 10, cy + 6);
            gap(23);
        }
        previewY = cy;
        gap(27);
        note("0% disables a tier. Total must be 100%.");
        note("Displayed to 0.01%; unchanged odds are kept.");
        gap(5);
        button(left(), half, "Items only", null, Ui.Style.NORMAL,
                () -> preset(new String[]{"60", "40", "0", "0", "0"}));
        button(left() + half + 6, half, "Very rare", null, Ui.Style.NORMAL,
                () -> preset(new String[]{"60", "39.84", "0.1", "0.05", "0.01"}));
        gap(25);
        paragraph("Presets are previews until you Save. Each tab saves separately.");
        gap(6);
        button(left(), half, "Basic prize items", null, Ui.Style.NORMAL,
                () -> openValues("quests.lucky_spin.basic_items"));
        button(left() + half + 6, half, "Good prize items", null, Ui.Style.NORMAL,
                () -> openValues("quests.lucky_spin.good_items"));
        gap(25);
        button(left(), half, "More spin settings", null, Ui.Style.NORMAL,
                () -> openValues(group == 0 ? "quests.lucky_spin" : "quests.daily_login"));
        button(left() + half + 6, half, "Reload this tab", null, Ui.Style.NORMAL, this::reload);
        gap(25);
        paragraph(message);
        gap(3);
    }

    private boolean busy() { return pendingGroup >= 0 || reloadGroup >= 0; }

    private void openValues(String filter) {
        if (!busy() && AdminSettingsScreen.canManage()) ConfigValuesScreen.openFiltered(this, data, filter);
    }

    private void select(int next) {
        if (busy()) return;
        group = next;
        message = "Edit percentages, then Save this tab.";
        error = false;
        relayout();
    }

    private void preset(String[] values) {
        if (busy()) return;
        System.arraycopy(values, 0, drafts[group].percentages, 0, values.length);
        drafts[group].dirty = true;
        message = "Preset preview only. Save applies it to this tab.";
        error = false;
        relayout();
    }

    private void reload() {
        if (busy()) return;
        Runnable request = () -> {
            reloadGroup = group;
            pendingSince = Util.getMillis();
            message = "Loading current server odds...";
            Net.sendToServer(new ConfigEditor.Action("__open_odds", "", ""));
            relayout();
        };
        if (drafts[group].dirty) {
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                minecraft.setScreen(this);
                if (confirmed) request.run();
            }, Component.literal("Discard this tab's unsaved odds?"),
                    Component.literal("Reload replaces this tab with the current server values. The other tab is kept.")));
        } else request.run();
    }

    @Override protected void save() {
        if (busy() || !AdminSettingsScreen.canManage()) return;
        try {
            if (!drafts[group].dirty) {
                message = "No changes to save. Existing exact odds are kept.";
                error = false;
            } else {
                int[] next = entered();
                if (Arrays.stream(next).sum() != 10000) throw new IllegalArgumentException("The five percentages must add up to 100%.");
                if (drafts[group].rounded) {
                    minecraft.setScreen(new ConfirmScreen(confirmed -> {
                        minecraft.setScreen(this);
                        if (confirmed) submit(next);
                    }, Component.literal("Save these rounded percentages?"), Component.literal(
                            "Your previous custom odds have more than two decimal places. Saving uses the percentages shown, including any 0% tiers. Use Config values to retain finer precision.")));
                    return;
                }
                submit(next);
            }
        } catch (RuntimeException failure) {
            error = true;
            message = failure.getMessage();
        }
        relayout();
    }

    private void submit(int[] next) {
        if (!AdminSettingsScreen.canManage()) return;
        pendingGroup = group;
        pendingSince = Util.getMillis();
        message = "Saving this tab on the server...";
        error = false;
        Net.sendToServer(new ConfigEditor.Action("__odds:" + GROUPS[group], JSON.toJson(drafts[group].expected), JSON.toJson(next)));
        relayout();
    }

    @Override public void tick() {
        if (!AdminSettingsScreen.canManage()) {
            boolean refresh = !message.equals("Administrator permission is required.");
            pendingGroup = reloadGroup = -1;
            message = "Administrator permission is required.";
            error = true;
            if (refresh) relayout();
        } else if (busy() && Util.getMillis() - pendingSince > 20000L) {
            pendingGroup = reloadGroup = -1;
            message = "Server reply delayed. Reload this tab before retrying.";
            error = true;
            relayout();
        }
        for (var child : children()) if (child instanceof AbstractWidget widget) {
            widget.active = !busy() && AdminSettingsScreen.canManage();
        }
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) renderPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, 0.25f));
    }

    @Override protected void renderExtra(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(px + 2, top, px + pw - 2, bottom);
        try {
            int[] values = entered();
            int sum = Arrays.stream(values).sum();
            int pokemon = values[2] + values[3] + values[4];
            graphics.drawString(font, "Total: " + decimal(sum) + "%" + (sum == 10000 ? "  Ready" : "  Must equal 100%"),
                    left(), previewY, sum == 10000 ? Theme.good : Theme.bad, false);
            graphics.drawString(font, "Any Pokemon: " + decimal(pokemon) + "% per spin", left(), previewY + 12, Theme.textDim, false);
        } catch (RuntimeException failure) {
            graphics.drawString(font, font.plainSubstrByWidth(failure.getMessage(), innerWidth()), left(), previewY, Theme.bad, false);
        }
        graphics.disableScissor();
        String state = busy() ? "Waiting..." : error ? "Check details" : drafts[group].dirty ? "Unsaved" : "Server values";
        graphics.drawString(font, state, left(), py + ph - 20, error ? Theme.bad : Theme.textDim, false);
    }

    @Override protected void cancel() {
        if (busy()) return;
        if (Arrays.stream(drafts).anyMatch(draft -> draft.dirty)) {
            minecraft.setScreen(new ConfirmScreen(confirmed -> minecraft.setScreen(confirmed ? parent : this),
                    Component.literal("Discard unsaved spin odds?"), Component.literal("Saved server odds remain unchanged. Unsaved drafts in both tabs will be discarded.")));
        } else super.cancel();
    }

    @Override public void onClose() { cancel(); }
}
