package dev.unyielding.jail;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Refuse custody while the pinned Storage Plus player has an active pocket operation. */
final class JailStorageState {
    private static final String POCKET = "dev.anya.storage.pocket.PocketService";
    private static final String EDITOR = "dev.anya.storage.pocket.RoomEditor";

    private JailStorageState() {}

    static void verify(ServerPlayer player) throws IOException {
        if (!player.server.isSameThread()) throw new IOException("Storage state requires the server thread");
        Object insideValue = JailHumanAudit.call(POCKET,null,"inPocket",
                new Class<?>[]{Level.class},player.level());
        Object recoveryValue = JailHumanAudit.call(POCKET,null,"recovering",
                new Class<?>[]{UUID.class},player.getUUID());
        if (!(insideValue instanceof Boolean inside) || !(recoveryValue instanceof Boolean recovering))
            throw new IOException("Unknown Storage pocket status");
        Object lease = JailHumanAudit.call(POCKET,null,"lease",
                new Class<?>[]{UUID.class},player.getUUID());
        try {
            var modes = Class.forName(EDITOR).getDeclaredField("MODES");
            modes.setAccessible(true);
            Object value = modes.get(null);
            if (!(value instanceof Map<?,?> map)) throw new IOException("Unknown Storage room editor state");
            if (inside || recovering || lease != null || map.containsKey(player.getUUID()))
                throw new IOException("Active Storage pocket or room editing requires review");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IOException("Cannot inspect the pinned Storage pocket state",failure);
        }
    }
}
