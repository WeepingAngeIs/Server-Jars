package dev.unyielding.quests.settings;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.essentials.EssConfig;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Optional OPAC integration using its public full-pass API, not claim ownership or admin modes. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class AdminClaimBypass {
    private static final Set<UUID> GRANTED = new HashSet<>();
    private static Method apiGet, protectionGet, grant, revoke;
    private static boolean resolved, unavailable, warned;
    private AdminClaimBypass() {}

    private static Object protection(MinecraftServer server) {
        if (unavailable) return null;
        try {
            if (!resolved) {
                Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                Class<?> protection = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
                apiGet = api.getMethod("get", MinecraftServer.class);
                protectionGet = api.getMethod("getChunkProtection");
                grant = protection.getMethod("giveFullPass", UUID.class);
                revoke = protection.getMethod("removeFullPass", UUID.class);
                resolved = true;
            }
            return protectionGet.invoke(apiGet.invoke(null, server));
        } catch (ClassNotFoundException absent) {
            unavailable = true;
        } catch (ReflectiveOperationException | LinkageError failure) {
            warn(failure);
        }
        return null;
    }

    private static void warn(Throwable failure) {
        if (!warned) {
            warned = true;
            UnyieldingQuests.LOGGER.error("Could not apply automatic admin claim bypass; check the installed Open Parties and Claims version", failure);
        }
    }

    public static void reconcile(MinecraftServer server) {
        if (server == null || (!EssConfig.ADMIN_CLAIM_BYPASS.get() && GRANTED.isEmpty())) return;
        Object protection = protection(server);
        if (protection == null) return;
        Set<UUID> wanted = new HashSet<>();
        if (EssConfig.ADMIN_CLAIM_BYPASS.get()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                if (player.hasPermissions(2)) wanted.add(player.getUUID());
        }
        try {
            for (UUID id : Set.copyOf(GRANTED)) if (!wanted.contains(id)) {
                revoke.invoke(protection, id);
                GRANTED.remove(id);
            }
            for (UUID id : wanted) if (!GRANTED.contains(id)) {
                grant.invoke(protection, id);
                GRANTED.add(id);
            }
        } catch (ReflectiveOperationException failure) { warn(failure); }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Pre event) { reconcile(event.getServer()); }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !GRANTED.contains(player.getUUID())) return;
        Object protection = protection(player.getServer());
        if (protection == null) return;
        try { revoke.invoke(protection, player.getUUID()); GRANTED.remove(player.getUUID()); }
        catch (ReflectiveOperationException failure) { warn(failure); }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        Object protection = GRANTED.isEmpty() ? null : protection(event.getServer());
        if (protection != null) for (UUID id : Set.copyOf(GRANTED)) {
            try { revoke.invoke(protection, id); }
            catch (ReflectiveOperationException failure) { warn(failure); }
        }
        GRANTED.clear();
        warned = false;
    }
}
