package dev.unyielding.jail.party;

import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.io.*;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;

/** Native boundary for the experimental managed gateway. No registration, packet hooks or player mutation. */
public final class CobblemonSnapshotCodec {
    public record Stores(PlayerPartyStore party, PCStore pc) {}
    private final RegistryAccess registries;
    public CobblemonSnapshotCodec(RegistryAccess registries) { this.registries=Objects.requireNonNull(registries); }

    public PartyCustodyGuard.Snapshot capture(UUID owner,String provider,PlayerPartyStore party,PCStore pc)throws IOException {
        if(party.getClass()!=PlayerPartyStore.class||pc.getClass()!=PCStore.class||!party.getUuid().equals(owner)
                ||!party.getPlayerUUID().equals(owner)||!pc.getUuid().equals(owner)||pc.getBoxes().size()!=1
                ||pc.getBackupStore().iterator().hasNext())throw new IOException("Unknown/overflow/foreign native store topology");
        CompoundTag p=party.saveToNBT(new CompoundTag(),registries).copy(),c=pc.saveToNBT(new CompoundTag(),registries).copy();
        var result=new PartyCustodyGuard.Snapshot(owner,provider,encode(p),identities(party),encode(c),identities(pc));
        // Native reconstruction must preserve complete state and identity. It is never published or initialized.
        Stores reconstructed=decodeUnchecked(result);
        if(!p.equals(reconstructed.party.saveToNBT(new CompoundTag(),registries))||!c.equals(reconstructed.pc.saveToNBT(new CompoundTag(),registries))
                ||!result.partyIds().equals(identities(reconstructed.party))||!result.pcIds().equals(identities(reconstructed.pc)))
            throw new IOException("Native full snapshot roundtrip changed state");
        return result;
    }
    /** Derive empty-state bytes from a detached verified clone, never clear player-owned stores. */
    public PartyCustodyGuard.Snapshot empty(PartyCustodyGuard.Snapshot original)throws IOException {
        Stores clone=decode(original);clone.party.clearParty();
        return capture(original.owner(),original.provider(),clone.party,clone.pc);
    }
    public Stores decode(PartyCustodyGuard.Snapshot snapshot)throws IOException {
        Stores result=decodeUnchecked(snapshot);
        if(!Arrays.equals(snapshot.party(),encode(result.party.saveToNBT(new CompoundTag(),registries)))
                ||!Arrays.equals(snapshot.pc(),encode(result.pc.saveToNBT(new CompoundTag(),registries)))
                ||!snapshot.partyIds().equals(identities(result.party))||!snapshot.pcIds().equals(identities(result.pc)))
            throw new IOException("Serialized identity manifest does not match native state");
        return result;
    }
    private Stores decodeUnchecked(PartyCustodyGuard.Snapshot snapshot)throws IOException {
        try {
            var party=new PlayerPartyStore(snapshot.owner());var pc=new PCStore(snapshot.owner());
            party.loadFromNBT(decodeNBT(snapshot.party()),registries);pc.loadFromNBT(decodeNBT(snapshot.pc()),registries);
            if(!party.getUuid().equals(snapshot.owner())||!party.getPlayerUUID().equals(snapshot.owner())||!pc.getUuid().equals(snapshot.owner())
                    ||pc.getBoxes().size()!=1||pc.getBackupStore().iterator().hasNext())throw new IOException("Restored native topology differs");
            return new Stores(party,pc);
        }catch(RuntimeException failure){throw new IOException("Native decode failed",failure);}
    }
    private static Set<UUID> identities(PokemonStore<?> store)throws IOException {
        Set<UUID> result=new HashSet<>();for(Pokemon pokemon:store)if(pokemon!=null&&!result.add(pokemon.getUuid()))throw new IOException("Duplicate native Pokemon UUID");return result;
    }
    private static byte[] encode(CompoundTag tag)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeByte(10);out.writeUTF("");payload(tag,out);}
        byte[] result=bytes.toByteArray();if(!tag.equals(decodeNBT(result)))throw new IOException("Canonical NBT loss");return result;
    }
    private static CompoundTag decodeNBT(byte[] bytes)throws IOException {
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){CompoundTag result=NbtIo.read(in,NbtAccounter.create(4L*1024*1024));if(result==null||in.available()!=0)throw new IOException("Invalid/trailing NBT");return result;}
    }
    private static void payload(Tag tag,DataOutput out)throws IOException {
        if(tag instanceof CompoundTag compound){for(String key:new TreeSet<>(compound.getAllKeys())){Tag value=compound.get(key);out.writeByte(value.getId());out.writeUTF(key);payload(value,out);}out.writeByte(0);}
        else if(tag instanceof ListTag list){out.writeByte(list.getElementType());out.writeInt(list.size());for(Tag value:list)payload(value,out);}else tag.write(out);
    }
}
