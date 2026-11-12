package dev.unyielding.quests.client;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.client.ui.SpriteButton;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.client.ui.UiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** The Premier Ball (quest book) and Teams buttons on the inventory screens. */
public final class InvButtons {
    public static final int SIZE = 20;
    private static final Map<Screen, int[]> AREAS = new WeakHashMap<>();
    private static ItemStack ball;

    private InvButtons() {
    }

    /** The Premier Ball from Cobblemon (our own quest book item when Cobblemon is missing). */
    public static ItemStack icon() {
        if (ball == null) {
            Item i = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("cobblemon:premier_ball")).orElse(Items.AIR);
            if (i == Items.AIR) i = dev.unyielding.quests.Registration.QUEST_BOOK.get();
            ball = new ItemStack(i);
        }
        return ball;
    }

    static boolean wants(Screen s) {
        if (!(s instanceof InventoryScreen) && !(s instanceof CreativeModeInventoryScreen)) return false;
        if (!ClientData.inventoryButtonsEnabled) return false;
        try {
            if (!Config.INVENTORY_BUTTONS.get()) return false;
        } catch (IllegalStateException ignored) {
        }
        return UiConfig.invButtons() != UiConfig.InvButtons.OFF;
    }

    public static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!(s instanceof InventoryScreen) && !(s instanceof CreativeModeInventoryScreen)) return;
        if (!wants(s)) {
            e.addListener(new IconButton(4, 4, Component.literal("Lucky Spin"), true,
                    b -> Minecraft.getInstance().setScreen(new LuckySpinScreen(s))));
            AREAS.put(s, AdminInventoryButtons.onInit(e, new int[]{4, 4, SIZE, SIZE}));
            return;
        }
        boolean topLeft = UiConfig.invButtons() == UiConfig.InvButtons.TOP_LEFT;
        AbstractContainerScreen<?> c = (AbstractContainerScreen<?>) s;
        int x = topLeft ? UiConfig.invX() : c.getGuiLeft() - SIZE - 3;
        int y = topLeft ? UiConfig.invY() : c.getGuiTop() + 4;
        x = Math.max(0, Math.min(x, s.width - SIZE));
        y = Math.max(0, Math.min(y, s.height - SIZE));
        e.addListener(new BallButton(x, y, questButtonLabel(), b -> ClientInit.openQuests()));
        int w = SIZE, h = SIZE;
        if (UiConfig.teamsButton()) {
            int tx = topLeft ? x + SIZE + 2 : x;
            int ty = topLeft ? y : y + SIZE + 2;
            e.addListener(new IconButton(tx, ty, Component.literal("Teams"), false,
                    b -> Minecraft.getInstance().setScreen(new TeamScreen())));
            if (topLeft) w += SIZE + 2;
            else h += SIZE + 2;
        }
        int sx = topLeft ? Math.min(s.width - SIZE, x + w + 2) : x;
        int sy = topLeft ? y : Math.min(s.height - SIZE, y + h + 2);
        e.addListener(new IconButton(sx, sy, Component.literal("Lucky Spin"), true,
                b -> Minecraft.getInstance().setScreen(new LuckySpinScreen(s))));
        w = Math.max(w, sx + SIZE - x);
        h = Math.max(h, sy + SIZE - y);
        AREAS.put(s, AdminInventoryButtons.onInit(e, new int[]{x, y, w, h}));
    }

    /** Screen area the buttons occupy (for EMI/JEI to stay out of), or null. */
    public static int[] area(Screen s) {
        int[] area=AdminInventoryButtons.area(s, AREAS.get(s));
        return area;
    }

    public static Component questButtonLabel() {
        return Component.literal("Quest Book (" + ClientInit.questKeyLabel().getString() + ")");
    }

    /** Icon-only controls retain their normal clickable area and tooltips. */
    private static final class IconButton extends Button {
        private final boolean spin;
        IconButton(int x, int y, Component tooltip, boolean spin, OnPress onPress) {
            super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION);
            this.spin = spin;
            setTooltip(Tooltip.create(tooltip));
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            ResourceLocation icon = spin
                    ? Ui.sprite("icon_spin_frame_" + ((System.currentTimeMillis() / 180) % 4))
                    : Ui.ICON_TEAM;
            int size = isHoveredOrFocused() ? 18 : 16;
            Ui.blit(g, icon, getX() + (SIZE - size) / 2, getY() + (SIZE - size) / 2, size, size);
        }
    }

    /** Round button showing the Premier Ball, with a red dot when rewards are waiting. */
    public static final class BallButton extends Button {
        BallButton(int x, int y, Component tooltip, OnPress onPress) {
            super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            Component label = questButtonLabel();
            if (!getMessage().equals(label)) {
                setMessage(label);
                setTooltip(Tooltip.create(label));
            }
            boolean hov = isHoveredOrFocused();
            g.pose().pushPose();
            g.pose().translate(getX() + 2, getY() + 2, 0);
            if (hov) {
                g.pose().translate(8, 8, 0);
                g.pose().scale(1.12f, 1.12f, 1f);
                g.pose().translate(-8, -8, 0);
            }
            g.renderItem(icon(), 0, 0);
            g.pose().popPose();
            if (ClientData.unclaimedCount() > 0) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                Ui.blit(g, Ui.BADGE_ALERT, getX() + width - 7, getY() - 2, 9, 9);
                g.pose().popPose();
            }
        }
    }
}
