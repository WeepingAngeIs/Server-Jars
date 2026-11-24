package dev.unyielding.jail;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Read-only checks for the exact, separately fingerprinted Unyielding companion. */
final class JailUnyieldingSwapState {
    private JailUnyieldingSwapState() {}

    static boolean blocksRequest(UUID player,UUID receiver,UUID sender,boolean alreadyInCustody) {
        return sender.equals(player) || !alreadyInCustody && receiver.equals(player);
    }

    static void verify(ServerPlayer player) throws IOException {
        if (!player.server.isSameThread()) throw new IOException("Teleport request audit requires the server thread");
        boolean alreadyInCustody=JailData.get(player.server).integratedCustodyInitialized()
                && JailIntegrated.activeTransaction(player.server,player.getUUID())!=null;
        try {
            Class<?> owner=Class.forName("dev.unyielding.quests.essentials.TeleCmds");
            Class<?> requestType=Class.forName("dev.unyielding.quests.essentials.TeleCmds$Request");
            var field=owner.getDeclaredField("REQUESTS");
            if (!Modifier.isStatic(field.getModifiers()) || !field.trySetAccessible())
                throw new IOException("Cannot inspect Unyielding teleport requests");
            Object value=field.get(null);
            if (!(value instanceof Map<?,?> requests)) throw new IOException("Unknown Unyielding teleport-request storage");
            var from=requestType.getDeclaredMethod("from");
            var here=requestType.getDeclaredMethod("here");
            var time=requestType.getDeclaredMethod("time");
            if (!from.trySetAccessible() || !here.trySetAccessible() || !time.trySetAccessible())
                throw new IOException("Cannot inspect an Unyielding teleport request");
            for (var entry:requests.entrySet()) {
                if (!(entry.getKey() instanceof UUID receiver) || !(entry.getValue() instanceof List<?> pending))
                    throw new IOException("Malformed Unyielding teleport-request storage");
                for (Object request:pending) {
                    if (request==null || request.getClass()!=requestType)
                        throw new IOException("Unknown Unyielding teleport request");
                    Object sender=from.invoke(request), hereValue=here.invoke(request), timeValue=time.invoke(request);
                    if (!(sender instanceof UUID fromId) || !(hereValue instanceof Boolean)
                            || !(timeValue instanceof Long stamp) || stamp<0)
                        throw new IOException("Malformed Unyielding teleport request");
                    // A different player can accept an outgoing request after admission.
                    // Incoming requests cannot be accepted by an already quarantined
                    // player because the existing command/payload fences remain active.
                    if (blocksRequest(player.getUUID(),receiver,fromId,alreadyInCustody))
                        throw new IOException("Resolve pending Unyielding teleport requests before custody; requests were not changed");
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IOException("Cannot audit the pinned Unyielding teleport requests",failure);
        }
    }
}
