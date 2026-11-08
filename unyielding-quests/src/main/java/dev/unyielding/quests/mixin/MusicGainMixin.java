package dev.unyielding.quests.mixin;

import dev.unyielding.quests.client.MusicClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

@Pseudo
@Mixin(targets = "rm_javazoom.jl.player.JavaSoundAudioDevice", remap = false)
public abstract class MusicGainMixin {
    @Shadow(remap = false) private SourceDataLine source;
    // Both regular volume updates and a newly opened audio line pass through this method.
    @ModifyVariable(method = "setGain", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 1, remap = false)
    private float unyielding$musicVolume(float original) {
        float adjusted = MusicClient.reactiveGain(original);
        if (source != null && source.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl control = (FloatControl)source.getControl(FloatControl.Type.MASTER_GAIN);
            adjusted = Math.max(control.getMinimum(), Math.min(control.getMaximum(), adjusted));
        }
        return adjusted;
    }
}
