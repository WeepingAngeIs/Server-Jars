package dev.unyielding.quests.client.edit;

import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** "Are you sure?" with the mod's look. */
public class ConfirmDialog extends Screen {
    private final Screen parent;
    private final String text, yes;
    private final Runnable onYes;
    private int px, py, pw, ph;

    public ConfirmDialog(Screen parent, String title, String text, String yes, Runnable onYes) {
        super(Component.literal(title));
        this.parent = parent;
        this.text = text;
        this.yes = yes;
        this.onYes = onYes;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        Theme.ensureLoaded();
        pw = Math.min(300, width - 16);
        List<FormattedCharSequence> lines = font.split(Component.literal(text), pw - 28);
        ph = 70 + lines.size() * 10;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        addRenderableWidget(new SpriteButton(px + pw - 14 - 80, py + ph - 26, 80, 20, Component.literal(yes), null, Ui.Style.ACCENT, b -> {
            minecraft.setScreen(parent);
            onYes.run();
        }));
        addRenderableWidget(new SpriteButton(px + 14, py + ph - 26, 70, 20, Component.literal("Cancel"), null, Ui.Style.NORMAL, b -> minecraft.setScreen(parent)));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        if (minecraft.level == null) renderPanorama(g, pt);
        if (UiConfig.blur()) renderBlurredBackground(pt);
        g.fill(0, 0, width, height, Ui.alpha(Theme.backdrop, 0.55f + 0.45f * UiConfig.opacity()));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        Ui.panel(g, px, py, pw, ph);
        Ui.panel(g, Ui.HEADER, px, py, pw, 26);
        g.drawString(font, Component.literal(getTitle().getString()).withStyle(ChatFormatting.BOLD), px + 12, py + 9, Theme.text, true);
        int y = py + 34;
        for (FormattedCharSequence l : font.split(Component.literal(text), pw - 28)) {
            g.drawString(font, l, px + 14, y, Theme.textDim, false);
            y += 10;
        }
        for (var w : renderables) w.render(g, mx, my, pt);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
