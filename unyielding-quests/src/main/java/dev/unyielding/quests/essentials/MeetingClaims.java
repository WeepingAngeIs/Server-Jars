package dev.unyielding.quests.essentials;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** OPAC public API. Full access is intentionally stricter than interaction exceptions.
 * No full-pass grants, admin bypasses, cached answers or unknown-provider fallback. */
final class MeetingClaims {
    static boolean hasAccess(MinecraftServer server, UUID player, ResourceLocation dimension, int x, int z) {
        if (!ModList.get().isLoaded("openpartiesandclaims")) return false;
        // Entity-aware OPAC checks include player-specific claim restrictions that the
        // UUID-only overload cannot apply. An offline player cannot be verified.
        var online = server.getPlayerList().getPlayer(player);
        if (online == null) return false;
        try {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object instance = api.getMethod("get", MinecraftServer.class).invoke(null, server);
            Object protection = api.getMethod("getChunkProtection").invoke(instance);
            Class<?> contract = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            return Boolean.TRUE.equals(contract.getMethod("hasChunkAccess", net.minecraft.world.entity.Entity.class, ResourceLocation.class, int.class, int.class)
                    .invoke(protection, online, dimension, x, z));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return false;
        }
    }
}
