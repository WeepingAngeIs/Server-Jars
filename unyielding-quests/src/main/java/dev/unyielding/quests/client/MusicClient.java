package dev.unyielding.quests.client;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Immutable published snapshot, also read by Reactive Music's audio thread. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID, value = Dist.CLIENT)
public final class MusicClient {
    private static volatile CompoundTag state;
    public static void apply(CompoundTag value) { state = value.copy(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { state = null; }
    public static void clear() { state = null; }
    public static float adjustGain(float original, String song) {
        CompoundTag settings = state;
        if (settings == null || original <= -79.9f || !Float.isFinite(original)) return original;
        CompoundTag tracks = settings.getCompound("volumes");
        double track = song != null && tracks.contains(song) ? tracks.getDouble(song) : 100;
        double scale = settings.getDouble("master") / 100 * track / 100;
        if (!Double.isFinite(scale) || scale <= 0) return -80;
        // Preserve intentional complete fade-outs and the player's own mute setting.
        if (original <= -50f) return original;
        return (float)Math.max(-80, Math.min(6, original + 20 * Math.log10(scale)));
    }
    public static float reactiveGain(float original) {
        if (state == null) return original;
        try {
            Class<?> music = Class.forName("circuitlord.reactivemusic.ReactiveMusic");
            Object pack = music.getField("currentSongpack").get(null);
            if (pack == null) return original;
            Object config = pack.getClass().getField("config").get(pack);
            if (!"Cobblemon Unyielding".equals(config.getClass().getField("name").get(config))) return original;
            String song = (String)Class.forName("circuitlord.reactivemusic.PlayerThread").getField("currentSong").get(null);
            return adjustGain(original, song);
        } catch (ReflectiveOperationException | LinkageError ignored) { return original; }
    }
}
