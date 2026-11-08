package dev.unyielding.quests.mixin;

import dev.unyielding.quests.client.MusicClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "circuitlord.reactivemusic.PlayerThread", remap = false)
public abstract class MusicGainMixin {
    @ModifyArg(method = "processRealGain", at = @At(value = "INVOKE", target = "Lrm_javazoom/jl/player/JavaSoundAudioDevice;setGain(F)V"), index = 0, require = 1, remap = false)
    private float unyielding$musicVolume(float original) { return MusicClient.reactiveGain(original); }
}
