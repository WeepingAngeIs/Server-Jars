package dev.unyielding.quests.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.unyielding.quests.client.ui.UiConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws hand-drawn quest/chapter pictures. A picture can be animated by stacking its frames
 * top to bottom in one PNG (e.g. 32x128 = four 32x32 frames). Frames play forward and back
 * (0,1,2,3,2,1) at 180 ms each. A normal square PNG is a still picture.
 */
public final class ArtAnim {
    public enum Motion implements net.neoforged.neoforge.common.TranslatableEnum {
        HOVER, ALWAYS, OFF;

        @Override
        public net.minecraft.network.chat.Component getTranslatedName() {
            return net.minecraft.network.chat.Component.translatable("unyieldingquests.picture_motion." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private static final long STEP_MS = 180;
    private static final int MAX_FRAMES = 16;
    private static final Map<ResourceLocation, Integer> FRAMES = new HashMap<>();
    private static final Map<Object, long[]> RUNS = new HashMap<>();

    private ArtAnim() {
    }

    static void clear() {
        FRAMES.clear();
        RUNS.clear();
    }

    /** Number of frames in a picture: its height divided by its width (1 for a square picture). */
    static int frames(ResourceLocation rl) {
        return FRAMES.computeIfAbsent(rl, k -> {
            var res = Minecraft.getInstance().getResourceManager().getResource(k);
            if (res.isEmpty()) return 1;
            try (InputStream in = res.get().open()) {
                byte[] h = in.readNBytes(24);
                if (h.length < 24 || h[1] != 'P' || h[2] != 'N' || h[3] != 'G') return 1;
                int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
                int ht = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
                if (w <= 0 || ht <= w || ht % w != 0) return 1;
                return Math.min(ht / w, MAX_FRAMES);
            } catch (Exception e) {
                return 1;
            }
        });
    }

    private static int pingPong(int n, long elapsed) {
        int len = 2 * n - 2;
        int i = (int) Math.floorMod(elapsed / STEP_MS, (long) len);
        return i < n ? i : len - i;
    }

    /**
     * Draws picture {@code rl} into a size x size square at x,y.
     *
     * @param key    stable id for this spot (quest id, chapter name), so each picture keeps its own timing
     * @param active true when the player is pointing at it / it is the open quest (it moves in "hover" mode)
     */
    public static void draw(GuiGraphics g, ResourceLocation rl, int x, int y, int size, Object key, boolean active) {
        int n = frames(rl);
        int f = 0;
        if (n > 1) {
            Motion m = UiConfig.animations() ? UiConfig.pictureMotion() : Motion.OFF;
            long now = Util.getMillis();
            if (m == Motion.ALWAYS) {
                f = pingPong(n, now + Math.floorMod(key.hashCode() * 2654435761L, 1080L));
            } else if (m == Motion.HOVER) {
                if (active) {
                    long[] run = RUNS.get(key);
                    if (run == null || now - run[1] > 1500) {
                        run = new long[]{now, now};
                        RUNS.put(key, run);
                    }
                    run[1] = now;
                    f = pingPong(n, now - run[0]);
                } else if (!RUNS.isEmpty()) {
                    RUNS.remove(key);
                }
            }
        }
        RenderSystem.enableBlend();
        if (n > 1) g.blit(rl, x, y, size, size, 0f, (float) (f * size), size, size, size, size * n);
        else g.blit(rl, x, y, 0, 0, size, size, size, size);
    }
}
