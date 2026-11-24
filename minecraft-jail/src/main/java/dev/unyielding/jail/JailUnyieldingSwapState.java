package dev.unyielding.jail;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Read-only checks for the exact, separately fingerprinted Unyielding companion. */
final class JailUnyieldingSwapState {
    private JailUnyieldingSwapState() {}

    static boolean blocksRequest(UUID player, UUID receiver, UUID sender, boolean alreadyInCustody) {
        return sender.equals(player) || !alreadyInCustody && receiver.equals(player);
    }

    static void verify(ServerPlayer player) throws IOException {
        if (!player.server.isSameThread()) throw new IOException("Teleport request audit requires the server thread");
        boolean alreadyInCustody = JailData.get(player.server).integratedCustodyInitialized()
                && JailIntegrated.activeTransaction(player.server, player.getUUID()) != null;
        JailCompanionTeleports.verify(player, alreadyInCustody);
    }
}
