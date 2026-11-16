package dev.unyielding.jail;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Same verified Warrior lifecycle, additionally observing exact live and saved XP. No XP confiscation. */
public final class JailXpOriginPort extends JailCertifiedOriginPort {
    public JailXpOriginPort(ServerPlayer p,JailVanillaCustody.InventoryPort inventory,UUID tx,UUID retired,byte[] existing)throws IOException {super(p,inventory,tx,retired,existing);}
    @Override protected void augmentView(CompoundTag saved,CompoundTag body)throws IOException {body.put("retained_xp",JailXp.encode(JailXp.read(saved)));}
}
