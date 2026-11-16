package dev.unyielding.jail;

import dev.unyielding.jail.Loc;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Observes accepted boundary crossings; only certified managed custody may start an escape consequence. */
public final class JailEscape {
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath("unyieldingquests", "jail_escape");
    private static final Map<ServerPlayer, Step> ACTIVE = new WeakHashMap<>();
    private JailEscape() {}

    private record Step(JailData.Assignment assignment, String dimension, double x, double y, double z,
                        double requestedX, double requestedY, double requestedZ) {}

    /** Called only on the server thread before processing a movement packet. */
    public static void begin(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        if (player == null || player.server == null || !player.server.isSameThread()) return;
        ACTIVE.remove(player);
        if (!player.isAlive() || player.isPassenger() || !packet.hasPosition()) return;
        var assignment = JailData.get(player.server).assignment(player.getUUID());
        if (assignment == null) return;
        Loc jail = assignment.jail();
        String dimension = player.level().dimension().location().toString();
        var box = player.getBoundingBox();
        if (!dimension.equals(jail.dim()) || JailEscapeBoundary.fullyOutside(box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ, jail.x(), jail.y(), jail.z())) return;
        ACTIVE.put(player, new Step(assignment, dimension, player.getX(), player.getY(), player.getZ(),
                packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ())));
    }

    /** Clears a pending sample on teleport, respawn, death, logout or management transfer. */
    static void invalidate(ServerPlayer player) {
        if (player != null && player.server != null && player.server.isSameThread()) ACTIVE.remove(player);
    }

    /** Called after the same packet has been handled on the server thread. */
    public static void finish(ServerPlayer player) {
        if (player == null || player.server == null || !player.server.isSameThread()) return;
        Step step = ACTIVE.remove(player);
        if (step == null || !player.isAlive() || player.isPassenger()) return;
        if (JailData.get(player.server).assignment(player.getUUID()) != step.assignment()) return;
        Loc jail = step.assignment().jail();
        String dimension = player.level().dimension().location().toString();
        var box = player.getBoundingBox();
        if (!step.dimension().equals(dimension) || !JailEscapeBoundary.fullyOutside(box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ, jail.x(), jail.y(), jail.z())) return;
        double dx = player.getX() - step.x(), dy = player.getY() - step.y(), dz = player.getZ() - step.z();
        // Reject server corrections, large teleports and packets that did not result in their requested position.
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || dx*dx + dy*dy + dz*dz > 4.0 || dx*dx + dy*dy + dz*dz < 1.0e-8
                || Math.abs(player.getX() - step.requestedX()) > 0.01
                || Math.abs(player.getY() - step.requestedY()) > 0.01
                || Math.abs(player.getZ() - step.requestedZ()) > 0.01) return;
        if (!JailPlacement.intact(player.server, jail, step.assignment().geometry()) || !JailCommands.safe(player, jail)) return;
        var advancement = player.server.getAdvancements().get(ADVANCEMENT);
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone())
            player.getAdvancements().award(advancement, "escape");
        JailEscapeCustody.attempt(player);
    }
}
