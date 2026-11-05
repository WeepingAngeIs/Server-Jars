package dev.unyielding.quests.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A normal clickable button drawn with the mod's sprites (and an optional icon). */
public class SpriteButton extends Button {
    private Ui.Style style;
    private final ResourceLocation icon;
    private final boolean iconOnly;

    public SpriteButton(int x, int y, int w, int h, Component label, ResourceLocation icon, Ui.Style style, OnPress onPress) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        this.style = style;
        this.icon = icon;
        this.iconOnly = false;
    }

    /** Square icon button; the label becomes its tooltip. */
    public static SpriteButton iconButton(int x, int y, int size, ResourceLocation icon, Component tooltip, OnPress onPress) {
        SpriteButton b = new SpriteButton(x, y, size, size, tooltip, icon, Ui.Style.NORMAL, onPress, true);
        b.setTooltip(Tooltip.create(tooltip));
        return b;
    }

    private SpriteButton(int x, int y, int w, int h, Component label, ResourceLocation icon, Ui.Style style, OnPress onPress, boolean iconOnly) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        this.style = style;
        this.icon = icon;
        this.iconOnly = iconOnly;
    }

    public SpriteButton style(Ui.Style s) {
        this.style = s;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Ui.button(g, mc.font, getX(), getY(), width, height, iconOnly ? null : getMessage(), icon, style, isHoveredOrFocused(), active);
    }
}
