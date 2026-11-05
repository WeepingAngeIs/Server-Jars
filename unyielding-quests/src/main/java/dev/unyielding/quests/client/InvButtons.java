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
        try {
            if (!Config.INVENTORY_BUTTONS.get()) return false;
        } catch (IllegalStateException ignored) {
        }
        return UiConfig.invButtons() != UiConfig.InvButtons.OFF;
    }

    public static void onInit(ScreenEvent.Init.Post e) {
        Screen s = e.getScreen();
        if (!wants(s)) return;
        boolean topLeft = UiConfig.invButtons() == UiConfig.InvButtons.TOP_LEFT;
        AbstractContainerScreen<?> c = (AbstractContainerScreen<?>) s;
        int x = topLeft ? UiConfig.invX() : c.getGuiLeft() - SIZE - 3;
        int y = topLeft ? UiConfig.invY() : c.getGuiTop() + 4;
        x = Math.max(0, Math.min(x, s.width - SIZE));
        y = Math.max(0, Math.min(y, s.height - SIZE));
        String key = ClientInit.OPEN_QUESTS.getTranslatedKeyMessage().getString();
        e.addListener(new BallButton(x, y, Component.literal("Quest Book (" + key + ")"), b -> ClientInit.openQuests()));
        int w = SIZE, h = SIZE;
        if (UiConfig.teamsButton()) {
            int tx = topLeft ? x + SIZE + 2 : x;
            int ty = topLeft ? y : y + SIZE + 2;
            e.addListener(SpriteButton.iconButton(tx, ty, SIZE, Ui.ICON_TEAM, Component.literal("Teams"), b -> Minecraft.getInstance().setScreen(new TeamScreen())));
            if (topLeft) w += SIZE + 2;
            else h += SIZE + 2;
        }
        AREAS.put(s, new int[]{x, y, w, h});
    }

    /** Screen area the buttons occupy (for EMI/JEI to stay out of), or null. */
    public static int[] area(Screen s) {
        return AREAS.get(s);
    }

    /** Round button showing the Premier Ball, with a red dot when rewards are waiting. */
    public static final class BallButton extends Button {
        BallButton(int x, int y, Component tooltip, OnPress onPress) {
            super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION);
            setTooltip(Tooltip.create(tooltip));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isHoveredOrFocused();
            Ui.blit(g, hov ? Ui.Style.NORMAL.hover : Ui.Style.NORMAL.normal, getX(), getY(), width, height);
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
