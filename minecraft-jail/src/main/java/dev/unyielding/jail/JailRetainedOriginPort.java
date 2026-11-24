package dev.unyielding.jail;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Uses the existing certified origin/claim adapter, bound to confinement rather than confiscation. */
final class JailRetainedOriginPort extends JailCertifiedOriginPort {
    private final JailRetainedInventory retained;
    JailRetainedOriginPort(ServerPlayer player,JailRetainedInventory inventory,UUID id,UUID retired,byte[] existing)throws IOException{
        super(player,inventory,id,JailIntegrated.retiredOrigin(player,id,retired),existing);retained=inventory;
    }
    @Override public void validateCustody(JailEscrowJournal.Entry confinement,boolean releaseStarted)throws IOException{
        var plan=JailNbt.decode(confinement.snapshot());
        if(!confinement.owner().equals(owner())||!confinement.id().equals(retained.transaction)
                ||!JailRetainedCustody.contract(plan))throw new IOException("Unknown retained-confinement binding");
        retained.verify(plan.getByteArray("retained_inventory_digest"),true);
    }
}
