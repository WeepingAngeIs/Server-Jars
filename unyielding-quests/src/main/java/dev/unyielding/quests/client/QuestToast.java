package dev.unyielding.quests.client;

import dev.unyielding.quests.client.ui.Theme;
import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.quest.QuestFile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** "Quest complete" popup. */
public class QuestToast implements Toast {
    private final QuestFile.Quest quest;
    private final String title;
    private boolean played;

    public QuestToast(QuestFile.Quest quest) {
        this.quest = quest;
        this.title = Gfx.strip(Gfx.codes(quest.displayTitle()));
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent toasts, long time) {
        Theme.ensureLoaded();
        if (!played) {
            played = true;
            Ui.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4f, 0.5f);
        }
        Font font = Minecraft.getInstance().font;
        Ui.blit(g, Ui.TOAST, 0, 0, width(), height());
        net.minecraft.resources.ResourceLocation art = QuestScreen.questArt(quest.id);
        if (art != null) {
            ArtAnim.draw(g, art, 10, 8, 16, "toast:" + quest.id, true);
        } else {
            g.renderItem(QuestScreen.questIcon(quest), 10, 8);
        }
        g.drawString(font, Component.literal("Quest complete!").withStyle(ChatFormatting.BOLD), 35, 7, Theme.good, false);
        g.drawString(font, font.plainSubstrByWidth(title, width() - 42), 35, 18, Theme.text, false);
        // a soft shine sweeping across once
        long t = Math.min(time, 1200);
        if (t < 1200) {
            int sx = (int) (-40 + (width() + 80) * (t / 1200f));
            for (int i = 0; i < 14; i++) g.fill(sx + i, 2, sx + i + 1, height() - 2, Ui.alpha(0x30FFFFFF, 1f - Math.abs(i - 7) / 7f));
        }
        return time > 5000 ? Visibility.HIDE : Visibility.SHOW;
    }
}
