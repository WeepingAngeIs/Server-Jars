package dev.unyielding.quests.client;

import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.spin.LuckySpin;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Lucky Spin result shown as a popup when the quest book is not open. */
public class SpinToast implements Toast {
    private final LuckySpin.Spin spin;
    private boolean played;

    public SpinToast(LuckySpin.Spin spin) {
        this.spin = spin;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        Theme.ensureLoaded();
        if (!played) {
            played = true;
            Ui.play(spin.tier().isPokemon() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 1.3f, 0.5f);
        }
        Font font = Minecraft.getInstance().font;
        Ui.blit(g, Ui.TOAST, 0, 0, width(), height());
        var prize = spin.strip().isEmpty() ? null : spin.strip().get(Math.min(spin.index(), spin.strip().size() - 1));
        if (prize != null) g.renderItem(prize.stack(), 10, 8);
        g.drawString(font, Component.literal("Lucky Spin: " + spin.tier().label).withStyle(ChatFormatting.BOLD), 35, 7, spin.tier().color, false);
        g.drawString(font, font.plainSubstrByWidth(spin.name(), width() - 42), 35, 18, Theme.text, false);
        return time > 6000 ? Visibility.HIDE : Visibility.SHOW;
    }
}
