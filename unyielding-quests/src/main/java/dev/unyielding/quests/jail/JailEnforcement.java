package dev.unyielding.quests.jail;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.essentials.Loc;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative restrictions. Console recovery remains available. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class JailEnforcement {
    private static final ThreadLocal<UUID> INTERNAL_TELEPORT = new ThreadLocal<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    private JailEnforcement() {}

    private static boolean jailed(ServerPlayer player) {
        return player.server != null && JailData.get(player.server).assignment(player.getUUID()) != null;
    }
    private static boolean jailedEntity(net.minecraft.world.entity.Entity entity) {
        return entity instanceof ServerPlayer player && jailed(player);
    }
    private static boolean internal(ServerPlayer player) {
        return player.getUUID().equals(INTERNAL_TELEPORT.get());
    }

    /** Scoped exemption only for trusted server-side jail management and recovery. */
    public static boolean teleportAuthorized(ServerPlayer player, Loc location) {
        UUID previous = INTERNAL_TELEPORT.get();
        INTERNAL_TELEPORT.set(player.getUUID());
        try {
            if (!location.teleport(player)) return false;
            return player.level().dimension().location().toString().equals(location.dim())
                    && player.distanceToSqr(location.x(), location.y(), location.z()) < 0.0001;
        }
        finally { if (previous == null) INTERNAL_TELEPORT.remove(); else INTERNAL_TELEPORT.set(previous); }
    }

    @SubscribeEvent public static void command(CommandEvent event) {
        var entity = event.getParseResults().getContext().getSource().getEntity();
        if (jailedEntity(entity)) {
            event.setCanceled(true);
            ((ServerPlayer) entity).sendSystemMessage(Component.literal("Commands are unavailable while jailed. Ask an administrator for help."));
        }
    }
    @SubscribeEvent public static void teleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && jailed(player) && !internal(player)) event.setCanceled(true);
    }
    @SubscribeEvent public static void dimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && jailed(player) && !internal(player)) event.setCanceled(true);
    }
    @SubscribeEvent public static void mount(EntityMountEvent event) {
        if (event.isMounting() && jailedEntity(event.getEntityMounting())) event.setCanceled(true);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (jailedEntity(event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (jailedEntity(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (jailedEntity(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (jailedEntity(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) enforce(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) enforce(player);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) enforce(player);
    }
    private static void enforce(ServerPlayer player) {
        if (player.server == null) return;
        var assignment = JailData.get(player.server).assignment(player.getUUID());
        if (assignment == null) { WARNED.remove(player.getUUID()); return; }
        if (!player.isAlive()) return;
        if (player.isPassenger()) player.stopRiding();
        Loc jail = assignment.jail();
        boolean inside = JailBoundary.contains(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), jail.dim(), jail.x(), jail.y(), jail.z());
        if (inside) return;
        // No chunk tickets, searching, or repeated teleports into damaged prisons.
        if (!JailPlacement.intact(player.server, jail) || !JailCommands.safe(player, jail)) {
            player.setDeltaMovement(0, 0, 0);
            long now = player.server.getTickCount();
            long last = WARNED.getOrDefault(player.getUUID(), -1200L);
            if (now - last >= 1200) {
                WARNED.put(player.getUUID(), now);
                player.sendSystemMessage(Component.literal("Jail location is unavailable or unsafe. An administrator must use console recovery."));
                UnyieldingQuests.LOGGER.error("Jailed player {} is outside an unavailable or unsafe prison; console recovery required", player.getUUID());
            }
            return;
        }
        teleportAuthorized(player, jail);
    }
}
