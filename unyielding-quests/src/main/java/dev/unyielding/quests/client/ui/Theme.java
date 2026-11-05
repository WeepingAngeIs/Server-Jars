package dev.unyielding.quests.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Colours used by the quest book, party screen and toasts.
 * Defaults live here; assets/unyieldingquests/ui/theme.json (or a resource pack) can override any of them.
 * Values are "#RRGGBB" or "#AARRGGBB".
 */
public final class Theme {
    public static int accent = 0xFFE84A3E;
    public static int accentText = 0xFFFF8A7A;
    public static int gold = 0xFFFFCE40;
    public static int text = 0xFFF2F4F8;
    public static int textDim = 0xFFB8C0D0;
    public static int textMuted = 0xFF7C8699;
    public static int heading = 0xFFFFCE40;
    public static int good = 0xFF6EDB8A;
    public static int bad = 0xFFFF7070;

    public static int locked = 0xFF5C6370;
    public static int available = 0xFF4FA3F7;
    public static int inProgress = 0xFFF2B23A;
    public static int done = 0xFF4CC26A;
    public static int claim = 0xFFFFD54A;
    public static int nodeFill = 0xFF1B2029;

    public static int lineLocked = 0xFF3A404C;
    public static int lineOpen = 0xFF6C7891;
    public static int lineDone = 0xFF3F9A55;
    public static int gridDot = 0x22FFFFFF;
    public static int backdrop = 0x99080A0E;
    public static int canvas = 0x40000000;

    private static boolean loaded;

    private Theme() {
    }

    public static void ensureLoaded() {
        if (!loaded) reload();
    }

    /** Reads theme.json from the active resource packs (called on resource reload). */
    public static void reload() {
        loaded = true;
        try {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(UnyieldingQuests.MODID, "ui/theme.json");
            var res = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (res.isEmpty()) return;
            try (Reader r = res.get().openAsReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                for (Field f : Theme.class.getFields()) {
                    if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers())) continue;
                    String key = snake(f.getName());
                    if (!o.has(key)) continue;
                    String v = o.get(key).getAsString().trim();
                    if (v.startsWith("#")) v = v.substring(1);
                    long c = Long.parseLong(v, 16);
                    if (v.length() <= 6) c |= 0xFF000000L;
                    f.setInt(null, (int) c);
                }
            }
        } catch (Exception e) {
            UnyieldingQuests.LOGGER.warn("Could not read unyieldingquests:ui/theme.json: {}", e.toString());
        }
    }

    private static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
