package dev.unyielding.jail;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JailOwnDamageTickTest {
    @Test void onlyTheBoundHeldDeathCanSupplyItsOwnDamageTick() throws Exception {
        UUID owner=UUID.randomUUID(),custody=UUID.randomUUID(),attempt=UUID.randomUUID();
        byte[] digest={1,2,3};
        Loc jail=new Loc("minecraft:overworld",1.5,64,2.5,0,0);
        var state=new CompoundTag();
        state.putUUID("custody",custody);state.putByteArray("digest",digest);state.put("jail",jail.toTag());
        state.putInt("deaths_before",3);state.putLong("insanelib_own_damage_tick",42L);
        var entry=new JailEscrowJournal.Entry(attempt,owner,JailEscrowJournal.State.HELD,JailNbt.encode(state));
        assertEquals(Long.valueOf(42L),JailEscapeCustody.verifiedDamageTick(entry,owner,custody,digest,jail,4));
        assertNull(JailEscapeCustody.verifiedDamageTick(entry,UUID.randomUUID(),custody,digest,jail,4));
        assertNull(JailEscapeCustody.verifiedDamageTick(entry,owner,UUID.randomUUID(),digest,jail,4));
        assertNull(JailEscapeCustody.verifiedDamageTick(entry,owner,custody,new byte[]{1},jail,4));
        assertNull(JailEscapeCustody.verifiedDamageTick(entry,owner,custody,digest,new Loc("minecraft:overworld",2.5,64,2.5,0,0),4));
        assertNull(JailEscapeCustody.verifiedDamageTick(entry,owner,custody,digest,jail,3));
        assertNull(JailEscapeCustody.verifiedDamageTick(new JailEscrowJournal.Entry(attempt,owner,JailEscrowJournal.State.PREPARED,entry.snapshot()),owner,custody,digest,jail,4));
        state.remove("insanelib_own_damage_tick");
        assertNull(JailEscapeCustody.verifiedDamageTick(new JailEscrowJournal.Entry(attempt,owner,JailEscrowJournal.State.HELD,JailNbt.encode(state)),owner,custody,digest,jail,4));
        state.putInt("insanelib_own_damage_tick",42);
        assertNull(JailEscapeCustody.verifiedDamageTick(new JailEscrowJournal.Entry(attempt,owner,JailEscrowJournal.State.HELD,JailNbt.encode(state)),owner,custody,digest,jail,4));
    }
}
