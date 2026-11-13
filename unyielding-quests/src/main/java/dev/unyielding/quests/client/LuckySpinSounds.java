package dev.unyielding.quests.client;

import dev.unyielding.quests.client.ui.Ui;
import dev.unyielding.quests.spin.LuckySpin;
import net.minecraft.sounds.SoundEvents;

/** The same, deliberately quiet result cues for all Lucky Spin screens. */
final class LuckySpinSounds {
    private LuckySpinSounds() {}

    static void play(LuckySpin.Tier tier) {
        if (tier == LuckySpin.Tier.BASIC) return;
        if (tier == LuckySpin.Tier.LEGENDARY) {
            // Preserve the existing top-prize fanfare at half its former volume.
            Ui.play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.45f);
            Ui.play(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 0.40f);
        } else {
            Ui.play(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.30f);
        }
    }
}
