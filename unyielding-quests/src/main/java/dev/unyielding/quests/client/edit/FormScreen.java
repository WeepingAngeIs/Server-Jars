package dev.unyielding.quests.client.edit;

import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrolling form drawn with the mod's own look. Subclasses add rows in {@link #build()} using the helpers;
 * the form is rebuilt whenever it scrolls or its model changes ({@link #relayout()}).
 */
public abstract class FormScreen extends Screen {
    protected final Screen parent;
    protected int px, py, pw, ph, top, bottom;
    protected int scroll;
    private int contentH;
    /** Layout cursor (screen y of the next row, scroll already applied). */
    protected int cy;
    private final List<Field> fields = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private String focusKey;
    private int focusCursor;
    private boolean building;
    private final List<AbstractWidget> footer = new ArrayList<>();

    private record Field(EditBox box, String key, int x, int y, int w, int h) {
    }

    private record Text(String text, int x, int y, int color, boolean bold, float scale) {
    }

    protected FormScreen(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected int panelWidth() {
        return Math.min(380, width - 16);
    }

    /** Fixed panel height, or -1 for the whole screen. */
    protected int panelHeight() {
        return -1;
    }

    @Override
    protected void init() {
        Theme.ensureLoaded();
        pw = panelWidth();
        ph = panelHeight() > 0 ? Math.min(panelHeight(), height - 16) : height - 16;
        px = (width - pw) / 2;
        py = panelHeight() > 0 ? (height - ph) / 2 : 8;
        top = py + 30;
        bottom = py + ph - 30;
        relayout();
    }

    /** Clears and rebuilds every widget (keeps the focused text field focused). */
    protected void relayout() {
        if (building) return;
        building = true;
        EditBox focused = null;
        for (Field f : fields) if (f.box().isFocused()) {
            focusKey = f.key();
            focusCursor = f.box().getCursorPosition();
            focused = f.box();
        }
        clearWidgets();
        fields.clear();
        texts.clear();
        footer.clear();
        cy = top + 8 - scroll;
        build();
        contentH = cy + scroll - top + 4;
        int max = Math.max(0, contentH - (bottom - top));
        if (scroll > max) {
            scroll = max;
            building = false;
            relayout();
            return;
        }
        // footer buttons
        footer.add(addRenderableWidget(new SpriteButton(px + pw - 14 - 70, py + ph - 26, 70, 20, Component.literal(saveLabel()), Ui.ICON_CHECK, Ui.Style.ACCENT, b -> save())));
        footer.add(addRenderableWidget(new SpriteButton(px + pw - 14 - 70 - 66, py + ph - 26, 62, 20, Component.literal("Cancel"), null, Ui.Style.NORMAL, b -> cancel())));
        footer.add(addRenderableWidget(SpriteButton.iconButton(px + pw - 24, py + 6, 18, Ui.ICON_CLOSE, Component.literal("Close"), b -> cancel())));
        setFocused(null);
        if (focusKey != null) {
            for (Field f : fields) if (f.key().equals(focusKey)) {
                setFocused(f.box());
                f.box().setFocused(true);
                f.box().setCursorPosition(Math.min(focusCursor, f.box().getValue().length()));
                f.box().setHighlightPos(f.box().getCursorPosition());
            }
        }
        building = false;
    }

    protected String saveLabel() {
        return "Save";
    }

    protected abstract void build();

    protected abstract void save();

    protected void cancel() {
        minecraft.setScreen(parent);
    }

    // ---- row helpers -------------------------------------------------------

    protected int left() {
        return px + 14;
    }

    protected int right() {
        return px + pw - 14;
    }

    protected int innerWidth() {
        return right() - left();
    }

    protected void gap(int h) {
        cy += h;
    }

    protected void heading(String text) {
        texts.add(new Text(text.toUpperCase(), left(), cy, Theme.heading, true, 1f));
        cy += 13;
    }

    protected void note(String text) {
        texts.add(new Text(text, left(), cy, Theme.textMuted, false, 1f));
        cy += 10;
    }

    protected void label(String text, int x, int y) {
        texts.add(new Text(text, x, y, Theme.textDim, false, 1f));
    }

    /** Label on the left, control area starts at {@code left() + labelW}. */
    protected int labelled(String text) {
        texts.add(new Text(text, left(), cy + 6, Theme.textDim, false, 1f));
        return left() + 70;
    }

    protected EditBox field(String key, int x, int w, String value, String hint, Consumer<String> onChange) {
        return field(key, x, cy, w, value, hint, 256, onChange);
    }

    protected EditBox field(String key, int x, int y, int w, String value, String hint, int max, Consumer<String> onChange) {
        EditBox b = new EditBox(font, x + 5, y + 6, w - 10, 10, Component.literal(hint));
        b.setBordered(false);
        b.setMaxLength(max);
        b.setTextColor(Theme.text);
        b.setHint(Component.literal(hint).withStyle(s -> s.withColor(Theme.textMuted)));
        b.setValue(value == null ? "" : value);
        b.moveCursorToStart(false);
        b.setResponder(onChange);
        addRenderableWidget(b);
        fields.add(new Field(b, key, x, y, w, 20));
        return b;
    }

    protected SpriteButton button(int x, int w, String label, ResourceLocation icon, Ui.Style style, Runnable r) {
        return button(x, cy, w, label, icon, style, r);
    }

    protected SpriteButton button(int x, int y, int w, String label, ResourceLocation icon, Ui.Style style, Runnable r) {
        SpriteButton b = new SpriteButton(x, y, w, 20, Component.literal(label), icon, style, bb -> {
            r.run();
            Ui.play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f, 0.25f);
        });
        addRenderableWidget(b);
        return b;
    }

    protected SpriteButton toggle(int x, int w, String label, boolean on, Consumer<Boolean> set) {
        SpriteButton b = new SpriteButton(x, cy, w, 20, Component.literal((on ? "✔ " : "✘ ") + label), null, on ? Ui.Style.ACCENT : Ui.Style.NORMAL, bb -> {
            set.accept(!on);
            relayout();
        });
        addRenderableWidget(b);
        return b;
    }

    /** A button that cycles through options; shows the current one. */
    protected SpriteButton cycle(int x, int w, String prefix, String[] options, String current, Consumer<String> set) {
        int idx = 0;
        for (int i = 0; i < options.length; i++) if (options[i].equals(current)) idx = i;
        String shown = prefix + options[idx];
        int next = (idx + 1) % options.length;
        SpriteButton b = new SpriteButton(x, cy, w, 20, Component.literal(shown), null, Ui.Style.NORMAL, bb -> {
            set.accept(options[next]);
            relayout();
        });
        b.setTooltip(Tooltip.create(Component.literal("Click to change")));
        addRenderableWidget(b);
        return b;
    }

    protected void tip(AbstractWidget w, String text) {
        w.setTooltip(Tooltip.create(Component.literal(text)));
    }

    protected void card(int h) {
        texts.add(new Text("\u0000card", left(), cy, h, false, 1f));
    }

    // ---- rendering -----------------------------------------------------------

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
        Ui.panel(g, Ui.HEADER, px, py, pw, 28);
        g.drawString(font, Component.literal(getTitle().getString()).withStyle(ChatFormatting.BOLD), px + 12, py + 10, Theme.text, true);
        g.fill(px + 6, bottom, px + pw - 6, bottom + 1, 0x22FFFFFF);
        g.enableScissor(px + 2, top, px + pw - 2, bottom);
        for (Text t : texts) {
            if (t.text().equals("\u0000card")) {
                Ui.blit(g, Ui.CARD, t.x(), t.y(), innerWidth(), t.color());
                continue;
            }
            Component c = t.bold() ? Component.literal(t.text()).withStyle(ChatFormatting.BOLD) : Component.literal(t.text());
            g.drawString(font, c, t.x(), t.y(), t.color(), t.bold());
        }
        for (Field f : fields) Ui.blit(g, f.box().isFocused() ? Ui.FIELD_FOCUSED : Ui.FIELD, f.x(), f.y(), f.w(), f.h());
        boolean inBody = my >= top && my < bottom;
        for (var w : renderables) {
            if (w instanceof AbstractWidget aw && !footer.contains(aw)) {
                if (aw.getY() + aw.getHeight() < top || aw.getY() > bottom) continue;
                aw.render(g, inBody ? mx : -1000, inBody ? my : -1000, pt);
            }
        }
        g.disableScissor();
        for (AbstractWidget aw : footer) aw.render(g, mx, my, pt);
        int view = bottom - top;
        if (contentH > view) {
            int th = Math.max(16, view * view / contentH);
            int ty = top + (int) ((view - th) * (scroll / (float) Math.max(1, contentH - view)));
            Ui.blit(g, Ui.SCROLL_THUMB, px + pw - 6, ty, 3, th);
        }
        renderExtra(g, mx, my, pt);
    }

    protected void renderExtra(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        boolean inBody = my >= top && my < bottom;
        for (var w : children()) {
            if (w instanceof AbstractWidget aw) {
                boolean bodyWidget = !footer.contains(aw);
                if (bodyWidget && !inBody) continue;
                if (aw.mouseClicked(mx, my, button)) {
                    setFocused(aw);
                    if (button == 0) setDragging(true);
                    return true;
                }
            }
        }
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, contentH - (bottom - top));
        int n = (int) Mth.clamp(scroll - sy * 20, 0, max);
        if (n != scroll) {
            scroll = n;
            relayout();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    /** Item id typed by the user -> colour (red when the item is not in this modpack). */
    protected static int itemColor(String id) {
        if (id == null || id.isBlank()) return Theme.textMuted;
        var rl = ResourceLocation.tryParse(id.trim());
        return rl != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(rl) ? Theme.good : Theme.bad;
    }

    protected static String heldItemId() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return "";
        var s = mc.player.getMainHandItem();
        if (s.isEmpty()) s = mc.player.getOffhandItem();
        if (s.isEmpty()) return "";
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
    }

    protected static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    protected static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
