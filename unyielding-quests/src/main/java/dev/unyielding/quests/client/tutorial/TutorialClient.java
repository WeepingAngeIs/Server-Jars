package dev.unyielding.quests.client.tutorial;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

/** Queues the guide without taking focus from another mod's onboarding or a player menu. */
public final class TutorialClient {
    private static final TutorialDelay DELAY = new TutorialDelay();
    private static CompoundTag pending;
    private static TutorialScreen resume;
    private static TutorialScreen active;
    private static int originPoll;
    private static boolean originReady;

    private TutorialClient() {
    }

    public static void receive(CompoundTag tag, boolean automatic) {
        pending = tag.copy();
        resume = null;
        active = null;
        originPoll = 0;
        originReady = false;
        DELAY.request(automatic);
    }

    public static void tick() {
        if (pending == null && resume == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.player != null && mc.level != null && mc.getConnection() != null;
        if (inWorld && originPoll-- <= 0) {
            originReady = OriginGate.ready();
            originPoll = 19;
        }
        boolean ready = inWorld && originReady && mc.player.isAlive()
                && mc.screen == null && mc.getOverlay() == null;
        if (!DELAY.tick(ready)) return;
        TutorialScreen screen = resume != null ? resume : new TutorialScreen(pending);
        pending = null;
        resume = null;
        active = screen;
        mc.setScreen(screen);
    }

    static void interrupted(TutorialScreen screen) {
        // A late origin, starter, or other screen takes priority. Continue the same guide afterward.
        if (screen == active && pending == null && resume == null) {
            active = null;
            resume = screen;
            DELAY.request(false);
        }
    }

    public static void reset() {
        pending = null;
        resume = null;
        active = null;
        originPoll = 0;
        originReady = false;
        DELAY.clear();
        OriginGate.reset();
    }
}
