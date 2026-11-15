package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Disposable-server smoke: checks the adapter with an actual connected player. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class OpacOnlineSmoke {
    private static boolean finished;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("uq.opacOnline")) return;
        var server = event.getServer();
        var player = server.getPlayerList().getPlayerByName("Dev");
        if (player == null || server.getTickCount() < 100) return;
        finished = true;
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) throw new IllegalStateException("OPAC missing");
            var dim = ResourceLocation.parse("minecraft:overworld");
            var apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var api = apiClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            var manager = apiClass.getMethod("getServerClaimsManager").invoke(api);
            var managerApi = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            var claim = managerApi.getMethod("claim", ResourceLocation.class, UUID.class, int.class, int.class, int.class, boolean.class);
            var unclaim = managerApi.getMethod("unclaim", ResourceLocation.class, int.class, int.class);
            var adapter = Class.forName("dev.unyielding.quests.essentials.MeetingClaims").getDeclaredMethod("hasAccess", MinecraftServer.class, UUID.class, ResourceLocation.class, int.class, int.class);
            adapter.setAccessible(true);
            int x = 12000, z = 12000;
            check(Boolean.TRUE.equals(adapter.invoke(null, server, player.getUUID(), dim, x, z)), "connected player denied unclaimed chunk");
            claim.invoke(manager, dim, player.getUUID(), 0, x, z, false);
            check(Boolean.TRUE.equals(adapter.invoke(null, server, player.getUUID(), dim, x, z)), "connected owner denied");
            unclaim.invoke(manager, dim, x, z);
            claim.invoke(manager, dim, UUID.randomUUID(), 0, x, z, false);
            check(Boolean.FALSE.equals(adapter.invoke(null, server, player.getUUID(), dim, x, z)), "connected stranger allowed");
            unclaim.invoke(manager, dim, x, z);
            check(Boolean.TRUE.equals(adapter.invoke(null, server, player.getUUID(), dim, x, z)), "unclaim did not restore access");
            Files.writeString(Path.of("/tmp/uq-opac-online-result"), "passed: connected player entity, owner, stranger, unclaim\n");
        } catch (Throwable error) {
            UnyieldingQuests.LOGGER.error("OPAC online smoke failed", error);
            try { Files.writeString(Path.of("/tmp/uq-opac-online-result"), "failed: " + error + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
