package dev.unyielding.quests.client.tutorial;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Reads NeoOrigins' synchronized completion flag without changing its selection flow. */
public final class OriginGate {
    private static Method completed;
    private static boolean unavailable;

    private OriginGate() {
    }

    public static void reset() {
        completed = null;
        unavailable = false;
    }

    public static boolean ready() {
        if (!ModList.get().isLoaded("neoorigins")) return true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || unavailable) return false;
        try {
            if (completed == null) {
                Class<?> state = Class.forName("com.cyberday1.neoorigins.client.ClientOriginState");
                Method method = state.getMethod("isHadAllOrigins");
                if (method.getReturnType() != boolean.class || !Modifier.isStatic(method.getModifiers())) {
                    throw new ReflectiveOperationException("Unexpected NeoOrigins completion method");
                }
                completed = method;
            }
            return Boolean.TRUE.equals(completed.invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            unavailable = true;
            UnyieldingQuests.LOGGER.warn("[tutorial] Waiting because NeoOrigins selection completion could not be read", error);
            return false;
        }
    }
}
