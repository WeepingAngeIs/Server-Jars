package dev.unyielding.quests.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Shared drawing helpers for the restyled screens. All art comes from sprites so it can be redrawn. */
public final class Ui {
    private Ui() {
    }

    public static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(UnyieldingQuests.MODID, name);
    }

    public static final ResourceLocation PANEL = sprite("panel");
    public static final ResourceLocation CARD = sprite("card");
    public static final ResourceLocation HEADER = sprite("header");
    public static final ResourceLocation GOAL = sprite("goal");
    public static final ResourceLocation SLOT = sprite("slot");
    public static final ResourceLocation BAR_BG = sprite("bar_bg");
    public static final ResourceLocation BAR_FILL = sprite("bar_fill");
    public static final ResourceLocation CHIP = sprite("chip");
    public static final ResourceLocation ROW_HOVER = sprite("row_hover");
    public static final ResourceLocation ROW_SELECTED = sprite("row_selected");
    public static final ResourceLocation FIELD = sprite("field");
    public static final ResourceLocation FIELD_FOCUSED = sprite("field_focused");
    public static final ResourceLocation SCROLL_THUMB = sprite("scroll_thumb");
    public static final ResourceLocation TOAST = sprite("toast");
    public static final ResourceLocation BADGE_CHECK = sprite("badge_check");
    public static final ResourceLocation BADGE_LOCK = sprite("badge_lock");
    public static final ResourceLocation BADGE_ALERT = sprite("badge_alert");

    public static final ResourceLocation ICON_CLOSE = sprite("icon_close");
    public static final ResourceLocation ICON_TEAM = sprite("icon_team");
    public static final ResourceLocation ICON_GIFT = sprite("icon_gift");
    public static final ResourceLocation ICON_CROWN = sprite("icon_crown");
    public static final ResourceLocation ICON_STAR = sprite("icon_star");
    public static final ResourceLocation ICON_GEAR = sprite("icon_gear");
    public static final ResourceLocation ICON_CHAT = sprite("icon_chat");
    public static final ResourceLocation ICON_JOIN = sprite("icon_join");
    public static final ResourceLocation ICON_CHECK = sprite("icon_check");
    public static final ResourceLocation ICON_LOCK = sprite("icon_lock");
    public static final ResourceLocation ICON_QUESTS = sprite("icon_quests");
    public static final ResourceLocation ICON_BOOK = sprite("icon_book");
    public static final ResourceLocation ICON_SPIN = sprite("icon_spin");

    /** Button looks. */
    public enum Style {
        NORMAL("button", "button_hover"), ACCENT("button_accent", "button_accent_hover"), GOLD("button_gold", "button_gold_hover"),
        TAB("tab", "tab_hover"), TAB_SELECTED("tab_selected", "tab_selected");

        public final ResourceLocation normal, hover;

        Style(String n, String h) {
            normal = sprite(n);
            hover = sprite(h);
        }
    }

    public static final ResourceLocation BUTTON_DISABLED = sprite("button_disabled");

    // ---- sprites ---------------------------------------------------------

    public static void blit(GuiGraphics g, ResourceLocation s, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(s, x, y, w, h);
    }

    /** Draws a sprite coloured and faded by an ARGB value (white = unchanged). */
    public static void blit(GuiGraphics g, ResourceLocation s, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        float a = (argb >>> 24) / 255f, r = (argb >> 16 & 255) / 255f, gg = (argb >> 8 & 255) / 255f, b = (argb & 255) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gg, b, a);
        g.blitSprite(s, x, y, w, h);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** A window background; follows the player's opacity setting. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        blit(g, PANEL, x, y, w, h, alpha(0xFFFFFFFF, UiConfig.opacity()));
    }

    public static void panel(GuiGraphics g, ResourceLocation s, int x, int y, int w, int h) {
        blit(g, s, x, y, w, h, alpha(0xFFFFFFFF, UiConfig.opacity()));
    }

    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, ResourceLocation icon,
                              Style style, boolean hovered, boolean enabled) {
        ResourceLocation s = !enabled ? BUTTON_DISABLED : hovered ? style.hover : style.normal;
        blit(g, s, x, y, w, h);
        int textColor = !enabled ? Theme.textMuted : style == Style.GOLD ? 0xFF3A2600 : Theme.text;
        int iw = icon != null ? nativeW(icon) : 0;
        int ih = icon != null ? nativeH(icon) : 0;
        int lw = label == null ? 0 : font.width(label);
        int gap = icon != null && lw > 0 ? 4 : 0;
        int cx = x + (w - (iw + gap + lw)) / 2;
        int ty = y + (h - 8) / 2;
        if (icon != null) {
            int ic = style == Style.GOLD ? 0xFF3A2600 : enabled ? Theme.text : Theme.textMuted;
            blit(g, icon, cx, y + (h - ih) / 2, iw, ih, ic);
        }
        if (label != null) g.drawString(font, label, cx + iw + gap, ty, textColor, style != Style.GOLD && enabled);
    }

    /** Icons are drawn at their own size; looked up once from the sprite. */
    public static int nativeW(ResourceLocation icon) {
        try {
            return Minecraft.getInstance().getGuiSprites().getSprite(icon).contents().width();
        } catch (Exception e) {
            return 7;
        }
    }

    public static int nativeH(ResourceLocation icon) {
        try {
            return Minecraft.getInstance().getGuiSprites().getSprite(icon).contents().height();
        } catch (Exception e) {
            return 7;
        }
    }

    public static void icon(GuiGraphics g, ResourceLocation icon, int x, int y, int argb) {
        blit(g, icon, x, y, nativeW(icon), nativeH(icon), argb);
    }

    // ---- bars / chips -------------------------------------------------------

    public static void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
        blit(g, BAR_BG, x, y, w, h);
        int fw = (int) Math.round(w * Mth.clamp(frac, 0, 1));
        if (fw >= 3) blit(g, BAR_FILL, x, y, fw, h, color);
    }

    /** Small rounded label; returns its width. */
    public static int chip(GuiGraphics g, Font font, int x, int y, String text, int bg, int fg) {
        int w = font.width(text) + 8;
        blit(g, CHIP, x, y, w, 11, bg);
        g.drawString(font, text, x + 4, y + 2, fg, false);
        return w;
    }

    public static int chipWidth(Font font, String text) {
        return font.width(text) + 8;
    }

    // ---- text -----------------------------------------------------------------

    public static void textScaled(GuiGraphics g, Font font, Component text, float x, float y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    public static void textScaled(GuiGraphics g, Font font, FormattedCharSequence text, float x, float y, float scale, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    // ---- colours / animation ---------------------------------------------------

    public static int alpha(int argb, float a) {
        int na = Mth.clamp((int) ((argb >>> 24) * a), 0, 255);
        return (na << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int aa = (int) Mth.lerp(t, a >>> 24, b >>> 24), r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255),
                gg = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bb = (int) Mth.lerp(t, a & 255, b & 255);
        return aa << 24 | r << 16 | gg << 8 | bb;
    }

    public static int darker(int argb, float f) {
        return mix(argb, 0xFF000000 | 0, f) & 0x00FFFFFF | (argb & 0xFF000000);
    }

    /** Seconds since start, for looping animations (0 when animations are off). */
    public static float time() {
        return UiConfig.animations() ? (Util.getMillis() % 3_600_000L) / 1000f : 0f;
    }

    /** 0..1 pulse. */
    public static float pulse(float speed) {
        if (!UiConfig.animations()) return 0.5f;
        return 0.5f + 0.5f * Mth.sin(time() * speed);
    }

    /** Moves cur towards target; frame-rate independent smoothing. */
    public static float approach(float cur, float target, float rate, float dtSeconds) {
        if (!UiConfig.animations()) return target;
        float k = 1f - (float) Math.exp(-rate * dtSeconds);
        return cur + (target - cur) * k;
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static void play(Holder<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }

    public static void play(SoundEvent sound, float pitch, float volume) {
        if (!UiConfig.sounds()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
