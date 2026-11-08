package dev.unyielding.quests.client;

import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import dev.unyielding.quests.net.Net;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Server-owned feature switches. Nothing is changed locally before the server acknowledges it. */
public final class AdminSettingsScreen extends Screen {
    private record Row(String key, List<FormattedCharSequence> title, List<FormattedCharSequence> detail,
                       int offset, int height, SpriteButton toggle) { }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<AbstractWidget> footer = new ArrayList<>();
    private int px, py, pw, ph, top, bottom, contentHeight;
    private int seenVersion = -1;
    private float scroll, targetScroll;
    private long lastFrame;
    private String pendingKey;
    private String message = "Changes save automatically for everyone.";
    private boolean error;

    public AdminSettingsScreen(Screen parent) {
        super(Component.literal("Server settings"));
        this.parent = parent;
    }

    public static boolean canManage() {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasPermissions(2);
    }

    public static void requestOpen() {
        if (canManage()) Net.sendToServer(new Net.AdminSettingsAction("__open", false));
    }

    @Override
    protected void init() {
        Theme.ensureLoaded();
        pw = Math.min(460, width - 16);
        ph = Math.min(360, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        top = py + 52;
        bottom = py + ph - 37;
        lastFrame = Util.getMillis();
        receiveChanges();
        buildControls();
    }

    private void buildControls() {
        clearWidgets();
        rows.clear();
        footer.clear();
        int offset = 2;
        int textWidth = pw - 112;
        for (Tag value : ClientData.adminSettings.getList("settings", Tag.TAG_COMPOUND)) {
            CompoundTag setting = (CompoundTag) value;
            String key = setting.getString("key");
            String title = setting.getString("title");
            String description = setting.getString("description");
            boolean enabled = setting.getBoolean("value");
            List<FormattedCharSequence> titleLines = font.split(Component.literal(title), textWidth);
            List<FormattedCharSequence> detailLines = font.split(Component.literal(description), textWidth);
            int rowHeight = Math.max(46, 15 + 11 * (titleLines.size() + detailLines.size()));
            SpriteButton toggle = new SpriteButton(px + pw - 78, top + offset + 10, 58, 20,
                    Component.literal(enabled ? "ON" : "OFF"), enabled ? Ui.ICON_CHECK : Ui.ICON_LOCK,
                    enabled ? Ui.Style.ACCENT : Ui.Style.NORMAL, button -> {
                        if (pendingKey != null || !canManage()) return;
                        pendingKey = key;
                        message = "Saving on server...";
                        error = false;
                        button.setMessage(Component.literal("..."));
                        Net.sendToServer(new Net.AdminSettingsAction(key, !enabled));
                    }) {
                @Override
                protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
                    return Component.literal(title + ": " + getMessage().getString() + ". " + description);
                }
            };
            toggle.setTooltip(Tooltip.create(Component.literal(title + ": " + (enabled ? "ON" : "OFF"))));
            addRenderableWidget(toggle);
            rows.add(new Row(key, titleLines, detailLines, offset, rowHeight, toggle));
            offset += rowHeight + 6;
        }
        contentHeight = offset + 2;
        targetScroll = Mth.clamp(targetScroll, 0, maxScroll());
        scroll = Mth.clamp(scroll, 0, maxScroll());
        footer.add(addRenderableWidget(SpriteButton.iconButton(px + pw - 25, py + 6, 18,
                Ui.ICON_CLOSE, Component.literal("Close settings"), button -> onClose())));
        footer.add(addRenderableWidget(new SpriteButton(px + pw - 79, py + ph - 29, 65, 20,
                Component.literal("Done"), Ui.ICON_CHECK, Ui.Style.NORMAL, button -> onClose())));
        int shortcutWidth = (pw - 36) / 3;
        footer.add(addRenderableWidget(new SpriteButton(px + 14, py + 32, shortcutWidth, 18,
                Component.literal("Spin odds"), null, Ui.Style.ACCENT,
                button -> SpinOddsScreen.requestOpen())));
        footer.add(addRenderableWidget(new SpriteButton(px + 18 + shortcutWidth, py + 32, shortcutWidth, 18,
                Component.literal("Config values"), null, Ui.Style.NORMAL,
                button -> ConfigValuesScreen.requestOpen())));
        footer.add(addRenderableWidget(new SpriteButton(px + 22 + shortcutWidth * 2, py + 32, shortcutWidth, 18,
                Component.literal("Loot tables"), null, Ui.Style.NORMAL,
                button -> dev.unyielding.quests.client.edit.LootTablesScreen.requestOpen())));
        updateControls();
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - (bottom - top));
    }

    private void updateControls() {
        for (Row row : rows) {
            SpriteButton toggle = row.toggle();
            toggle.setY(top + row.offset() + 10 - Math.round(scroll));
            toggle.visible = toggle.getY() >= top && toggle.getY() + toggle.getHeight() <= bottom;
            toggle.active = toggle.visible && pendingKey == null && canManage();
        }
    }

    @Override
    public void tick() {
        if (receiveChanges()) buildControls();
        if (!canManage()) {
            message = "Administrator permission is required.";
            error = true;
        }
        updateControls();
    }

    private boolean receiveChanges() {
        if (seenVersion == ClientData.adminSettingsVersion) return false;
        boolean awaitingSave = pendingKey != null;
        seenVersion = ClientData.adminSettingsVersion;
        pendingKey = null;
        String reply = ClientData.adminSettings.getString("message");
        if (!reply.isBlank()) message = reply;
        else if (awaitingSave) message = "Saved on server.";
        error = ClientData.adminSettings.getBoolean("error");
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, 0.25f));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float elapsed = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        scroll = UiConfig.animations() ? Ui.approach(scroll, targetScroll, 18f, elapsed) : targetScroll;
        updateControls();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Ui.panel(graphics, px, py, pw, ph);
        Ui.panel(graphics, Ui.HEADER, px, py, pw, 30);
        Ui.icon(graphics, Ui.ICON_GEAR, px + 12, py + 11, Theme.accent);
        graphics.drawString(font, getTitle(), px + 26, py + 10, Theme.text, true);
        graphics.enableScissor(px + 7, top, px + pw - 7, bottom);
        for (Row row : rows) {
            int y = top + row.offset() - Math.round(scroll);
            if (y + row.height() < top || y >= bottom) continue;
            Ui.blit(graphics, Ui.CARD, px + 10, y, pw - 23, row.height());
            int lineY = y + 8;
            for (FormattedCharSequence line : row.title()) {
                graphics.drawString(font, line, px + 18, lineY, Theme.text, true);
                lineY += 11;
            }
            lineY += 3;
            for (FormattedCharSequence line : row.detail()) {
                graphics.drawString(font, line, px + 18, lineY, Theme.textDim, false);
                lineY += 11;
            }
            row.toggle().render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        graphics.fill(px + 12, bottom + 2, px + pw - 12, bottom + 3, 0x35FFFFFF);
        if (maxScroll() > 0) {
            int view = bottom - top;
            int thumb = Math.max(16, view * view / contentHeight);
            int thumbY = top + Math.round((view - thumb) * scroll / maxScroll());
            Ui.blit(graphics, Ui.SCROLL_THUMB, px + pw - 8, thumbY, 3, thumb);
        }
        String status = font.plainSubstrByWidth(message, pw - 108);
        graphics.drawString(font, status, px + 14, py + ph - 23, error ? Theme.bad : Theme.textDim, false);
        for (AbstractWidget button : footer) button.render(graphics, mouseX, mouseY, partialTick);
        if (!status.equals(message) && mouseX >= px + 14 && mouseX < px + pw - 94
                && mouseY >= py + ph - 29 && mouseY < py + ph - 9) {
            graphics.renderComponentTooltip(font, List.of(Component.literal(message)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= px && mouseX < px + pw && mouseY >= top && mouseY < bottom) {
            targetScroll = Mth.clamp(targetScroll - (float) vertical * 32, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            targetScroll = Mth.clamp(targetScroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 90 : -90), 0, maxScroll());
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
