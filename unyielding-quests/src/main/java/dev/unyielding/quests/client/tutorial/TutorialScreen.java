package dev.unyielding.quests.client.tutorial;

import dev.unyielding.quests.client.ClientInit;
import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A translucent, scrollable player guide using the quest book's existing palette and controls. */
public final class TutorialScreen extends Screen {
    private record Entry(String usage, String description) {}
    private record Page(String title, String description, List<Entry> entries, boolean directory) {}

    private final List<Page> pages = new ArrayList<>();
    private int page;
    private int panelX, panelY, panelW, panelH, bodyTop, bodyBottom;
    private int contentHeight;
    private float scroll;
    private float targetScroll;
    private long lastFrame;
    private long opened;
    private long pageOpened;
    private boolean dismissed;
    private EditBox search;
    private String query = "";
    private SpriteButton back;
    private SpriteButton next;

    public TutorialScreen(CompoundTag data) {
        super(Component.literal("Unyielding player guide"));
        pages.add(new Page("Welcome to Unyielding",
                "Your quick guide to the commands and controls available on this server.",
                List.of(
                        new Entry(questKey() + "  ·  Your quest book",
                                "Follow chapters, track tasks and collect rewards. You can also type /quests."),
                        new Entry("/  ·  Commands",
                                "Open chat with / and type a command. Press Tab for suggestions. Replace <name> with your own value."),
                        new Entry("/tutorial  ·  Come back anytime",
                                "Use Back and Next to explore the guide. Later closes it so you can return when you are ready.")
                ), false));
        for (Tag value : data.getList("pages", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            List<Entry> entries = entries(tag, "entries");
            if (!entries.isEmpty()) {
                pages.add(new Page(tag.getString("title"), tag.getString("description"), entries, false));
            }
        }
        pages.add(new Page("All player commands",
                data.getBoolean("directory_truncated")
                        ? "This server has a large command list. Search this index, and use /help or Tab in chat for additional commands."
                        : "Search the commands this server makes available to players. Press Tab in chat to fill in names and other values.",
                entries(data, "directory"), true));
        pages.add(new Page("Choose your first Pokémon",
                "Close this guide, then open starter selection when you are ready to begin.",
                List.of(
                        new Entry(starterKey() + "  ·  Choose your starter",
                                "Press " + starterKey() + " after closing the guide. Pick your first Pokémon in the starter menu."),
                        new Entry("Already have a starter?",
                                "The same key opens your Pokémon summary after you have chosen."),
                        new Entry("Make yourself at home",
                                "Try your quest book first. Use /tutorial whenever you want another look at the commands.")
                ), false));
    }

    private static List<Entry> entries(CompoundTag tag, String key) {
        List<Entry> result = new ArrayList<>();
        for (Tag value : tag.getList(key, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            result.add(new Entry(entry.getString("usage"), entry.getString("description")));
        }
        return List.copyOf(result);
    }

    private static String questKey() {
        return ClientInit.OPEN_QUESTS.getTranslatedKeyMessage().getString();
    }

    public static String starterKey() {
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if ("key.cobblemon.summary".equals(mapping.getName())) {
                return mapping.isUnbound() ? "Summary key (set in Controls)" : mapping.getTranslatedKeyMessage().getString();
            }
        }
        return "H";
    }

    @Override
    protected void init() {
        Theme.ensureLoaded();
        dismissed = false;
        panelW = Math.min(520, width - 20);
        panelH = Math.min(350, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        bodyTop = panelY + 58;
        bodyBottom = panelY + panelH - 49;
        opened = Util.getMillis();
        buildControls();
    }

    private void buildControls() {
        clearWidgets();
        search = null;
        if (pages.get(page).directory()) {
            search = new EditBox(font, panelX + 14, bodyTop, panelW - 28, 19, Component.literal("Search player commands"));
            search.setMaxLength(100);
            search.setHint(Component.literal("Search commands…"));
            search.setValue(query);
            search.setResponder(value -> {
                query = value;
                scroll = 0;
                targetScroll = 0;
            });
            addRenderableWidget(search);
        }
        int buttonY = panelY + panelH - 29;
        addRenderableWidget(new SpriteButton(panelX + 12, buttonY, 64, 19,
                Component.literal("Later"), null, Ui.Style.NORMAL, button -> onClose()));
        back = addRenderableWidget(new SpriteButton(panelX + panelW - 156, buttonY, 66, 19,
                Component.literal("Back"), null, Ui.Style.NORMAL, button -> changePage(-1)));
        next = addRenderableWidget(new SpriteButton(panelX + panelW - 84, buttonY, 72, 19,
                Component.literal(page == pages.size() - 1 ? "Let's play" : "Next"), null, Ui.Style.GOLD,
                button -> {
                    if (page == pages.size() - 1) onClose();
                    else changePage(1);
                }));
        back.active = page > 0;
        pageOpened = Util.getMillis();
        lastFrame = pageOpened;
    }

    private void changePage(int change) {
        int selected = Mth.clamp(page + change, 0, pages.size() - 1);
        if (selected == page) return;
        page = selected;
        scroll = 0;
        targetScroll = 0;
        query = "";
        buildControls();
    }

    private List<Entry> visibleEntries() {
        Page current = pages.get(page);
        if (!current.directory() || query.isBlank()) return current.entries();
        String needle = query.toLowerCase(Locale.ROOT).trim();
        return current.entries().stream()
                .filter(entry -> (entry.usage() + " " + entry.description()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible. Minecraft's default screen background would add an opaque menu or blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float delta = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float fade = UiConfig.animations() ? 0.1f + 0.9f * Ui.easeOut((now - opened) / 220f) : 1f;
        float pageFade = UiConfig.animations() ? 0.3f + 0.7f * Ui.easeOut((now - pageOpened) / 180f) : 1f;
        scroll = Ui.approach(scroll, targetScroll, 16f, delta);
        float opacity = Math.min(0.88f, UiConfig.opacity());

        graphics.fill(0, 0, width, height, Ui.alpha(0x1C050810, fade));
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, Ui.alpha(0xFF111723, opacity * fade));
        graphics.renderOutline(panelX, panelY, panelW, panelH, Ui.alpha(Theme.textMuted, 0.42f * fade));
        graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 3, Ui.alpha(Theme.accent, fade));
        graphics.fill(panelX + 12, panelY + 50, panelX + panelW - 12, panelY + 51, Ui.alpha(Theme.textMuted, 0.25f * fade));

        Page current = pages.get(page);
        graphics.drawString(font, "PLAYER GUIDE", panelX + 14, panelY + 11, Ui.alpha(Theme.accentText, fade), false);
        String count = (page + 1) + " / " + pages.size();
        graphics.drawString(font, count, panelX + panelW - 14 - font.width(count), panelY + 11,
                Ui.alpha(Theme.textDim, fade), false);
        float titleScale = Math.min(1.32f, (panelW - 30f) / Math.max(1, font.width(current.title())));
        Ui.textScaled(graphics, font, Component.literal(current.title()), panelX + 14, panelY + 28,
                titleScale, Ui.alpha(Theme.text, fade), true);

        int contentTop = bodyTop + (current.directory() ? 27 : 0);
        graphics.enableScissor(panelX + 10, contentTop, panelX + panelW - 10, bodyBottom);
        int y = contentTop - Math.round(scroll);
        int left = panelX + 16;
        int textWidth = panelW - 38;
        y = paragraph(graphics, current.description(), left, y, textWidth, Theme.textDim, fade * pageFade) + 12;
        List<Entry> entries = visibleEntries();
        for (Entry entry : entries) {
            int commandLines = Math.max(1, font.split(Component.literal(entry.usage()), textWidth - 16).size());
            int detailLines = Math.max(1, font.split(Component.literal(entry.description()), textWidth - 16).size());
            int rowHeight = 13 + (commandLines + detailLines) * 11;
            if (y + rowHeight >= contentTop && y < bodyBottom) {
                graphics.fill(left - 2, y - 3, left + textWidth + 2, y + rowHeight - 5,
                        Ui.alpha(0xFF202C3D, 0.54f * fade * pageFade));
                graphics.fill(left - 2, y - 3, left, y + rowHeight - 5, Ui.alpha(Theme.gold, 0.56f * fade * pageFade));
                int lineY = paragraph(graphics, entry.usage(), left + 8, y + 2, textWidth - 16,
                        Theme.gold, fade * pageFade);
                paragraph(graphics, entry.description(), left + 8, lineY + 4, textWidth - 16,
                        Theme.text, fade * pageFade);
            }
            y += rowHeight + 5;
        }
        if (entries.isEmpty()) {
            y = paragraph(graphics, "No matching commands. Try a shorter name.", left, y, textWidth,
                    Theme.textDim, fade * pageFade);
        }
        contentHeight = y + Math.round(scroll) - contentTop + 2;
        graphics.disableScissor();
        targetScroll = Mth.clamp(targetScroll, 0, maxScroll());
        if (maxScroll() > 0) {
            int available = bodyBottom - contentTop;
            int thumb = Math.max(16, available * available / contentHeight);
            int thumbY = contentTop + Math.round((available - thumb) * scroll / maxScroll());
            graphics.fill(panelX + panelW - 9, contentTop, panelX + panelW - 7, bodyBottom, 0x333D4B60);
            graphics.fill(panelX + panelW - 9, thumbY, panelX + panelW - 7, thumbY + thumb, Ui.alpha(Theme.gold, 0.8f));
        }

        int progressY = panelY + panelH - 45;
        graphics.fill(panelX + 12, progressY, panelX + panelW - 12, progressY + 2, 0x554A5568);
        int fillWidth = (panelW - 24) * (page + 1) / pages.size();
        graphics.fill(panelX + 12, progressY, panelX + 12 + fillWidth, progressY + 2, Ui.alpha(Theme.gold, fade));
        String hint = current.directory() ? entries.size() + " commands · /tutorial to reopen" : "Reopen anytime with /tutorial";
        Ui.textScaled(graphics, font, Component.literal(hint), panelX + 14, panelY + panelH - 40,
                0.75f, Ui.alpha(Theme.textDim, fade), false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int paragraph(GuiGraphics graphics, String text, int x, int y, int availableWidth, int color, float fade) {
        for (FormattedCharSequence line : font.split(Component.literal(text), availableWidth)) {
            graphics.drawString(font, line, x, y, Ui.alpha(color, fade), true);
            y += 11;
        }
        return y;
    }

    private int maxScroll() {
        int contentTop = bodyTop + (pages.get(page).directory() ? 27 : 0);
        return Math.max(0, contentHeight - (bodyBottom - contentTop));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= bodyTop && mouseY < bodyBottom) {
            targetScroll = Mth.clamp(targetScroll - (float) vertical * 26f, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (search == null || !search.isFocused()) {
            if (key == GLFW.GLFW_KEY_RIGHT) {
                next.onPress();
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                changePage(-1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                targetScroll = Mth.clamp(targetScroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 90 : -90), 0, maxScroll());
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public Component getNarrationMessage() {
        Page current = pages.get(page);
        return Component.literal(current.title() + ". " + current.description() + ". Page " + (page + 1) + " of " + pages.size());
    }

    @Override
    public void onClose() {
        dismissed = true;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        if (!dismissed) TutorialClient.interrupted(this);
    }

    // Used by the excluded development smoke runner; no commands are executed by these methods.
    public int pageIndex() { return page; }
    public int pageCount() { return pages.size(); }
    public boolean directoryPage() { return pages.get(page).directory(); }
    public int displayedEntries() { return visibleEntries().size(); }
    public void searchCommands(String text) { if (search != null) search.setValue(text); }
}
