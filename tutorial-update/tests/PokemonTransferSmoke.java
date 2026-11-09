package dev.unyielding.bundlesmoke;

import dev.unyielding.quests.client.PokemonTransfer;
import dev.unyielding.quests.settings.PokemonEditor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Roundtrip the production codecs, including NBT's per-string and packet size boundaries. */
final class PokemonTransferSmoke {
    static void exercise(Minecraft mc,Map<String,Object> report) {
        PokemonTransfer.clear();
        CompoundTag small=snapshot("small");
        List<PokemonEditor.Sync> direct=PokemonEditor.snapshotPackets(small,true);
        require(direct.size()==1&&!direct.getFirst().data().getString("view").equals("transfer"),"Small snapshots unnecessarily fragmented");
        require(PokemonTransfer.accept(roundtrip(mc,direct.getFirst())).data().equals(small),"Small snapshot codec roundtrip failed");

        // Under the S2C payload limit, but beyond NBT's 65,535-byte individual string limit.
        CompoundTag unicode=snapshot("\u754c\uD83D\uDE00\\\"".repeat(12000));
        verify(mc,unicode);
        CompoundTag large=snapshot("x".repeat(900000));large.putString("spawns_json","y".repeat(900000));
        verify(mc,large);

        List<PokemonEditor.Sync> broken=PokemonEditor.snapshotPackets(large,true);
        PokemonTransfer.clear();require(PokemonTransfer.accept(roundtrip(mc,broken.get(0)))==null,"A partial snapshot reached the menu");
        PokemonEditor.Sync interrupted=PokemonTransfer.accept(roundtrip(mc,broken.get(2)));
        require(interrupted!=null&&interrupted.data().getBoolean("error"),"Out-of-order snapshot was accepted");
        require(PokemonTransfer.accept(roundtrip(mc,broken.get(1)))==null,"Abandoned snapshot fragment revived a failed transfer");

        List<PokemonEditor.Sync> abandoned=PokemonEditor.snapshotPackets(large,true);
        PokemonTransfer.clear();PokemonTransfer.accept(roundtrip(mc,abandoned.getFirst()));
        require(PokemonTransfer.accept(new PokemonEditor.Sync(small,false)).data().equals(small),"A normal reply did not replace an old transfer");
        require(PokemonTransfer.accept(roundtrip(mc,abandoned.get(1)))==null,"Stale fragment replaced a later menu reply");

        CompoundTag invalid=PokemonEditor.snapshotPackets(large,true).getFirst().data().copy();
        invalid.putInt("bytes",PokemonEditor.SNAPSHOT_MAX_BYTES+1);
        require(PokemonTransfer.accept(new PokemonEditor.Sync(invalid,true)).data().getBoolean("error"),"Oversized transfer declaration was accepted");
        invalid.putInt("bytes",10);invalid.putString("token",UUID.randomUUID().toString());invalid.putInt("total",PokemonEditor.SNAPSHOT_MAX_PARTS+1);
        require(PokemonTransfer.accept(new PokemonEditor.Sync(invalid,true)).data().getBoolean("error"),"Unbounded transfer part count was accepted");

        CompoundTag tooLarge=snapshot("x".repeat(PokemonEditor.SNAPSHOT_MAX_BYTES+1));
        var refused=PokemonEditor.snapshotPackets(tooLarge,true);
        require(refused.size()==1&&refused.getFirst().data().getBoolean("error"),"Server did not bound outgoing snapshot memory");

        // The production client chunks C2S documents into at most 6,000 UTF-16 units.
        var action=new PokemonEditor.Action("part:save_species:0:2","cobblemon:pikachu",7,"\u754c".repeat(6000));
        RegistryFriendlyByteBuf c2s=new RegistryFriendlyByteBuf(Unpooled.buffer(),mc.level.registryAccess());
        try {
            PokemonEditor.Action.CODEC.encode(c2s,action);require(c2s.readableBytes()<32767,"C2S chunk exceeds Minecraft's payload limit");
            require(PokemonEditor.Action.CODEC.decode(c2s).equals(action),"C2S Unicode chunk codec roundtrip failed");
        } finally {c2s.release();PokemonTransfer.clear();}
        report.put("large_unicode_snapshots_roundtrip_production_codecs_below_nbt_and_packet_limits","passed");
        report.put("snapshot_memory_order_metadata_stale_fragment_and_c2s_chunk_bounds","passed");
    }
    private static CompoundTag snapshot(String text) {
        CompoundTag tag=new CompoundTag();tag.putString("view","species");tag.putString("id","cobblemon:pikachu");
        tag.putString("species_json",text);tag.putString("spawns_json","[]");tag.putLong("revision",7);return tag;
    }
    private static void verify(Minecraft mc,CompoundTag original) {
        PokemonTransfer.clear();List<PokemonEditor.Sync> packets=PokemonEditor.snapshotPackets(original,true);
        require(packets.size()>1,"Large NBT strings were sent without fragmentation");PokemonEditor.Sync complete=null;
        for(int i=0;i<packets.size();i++) {
            String text=packets.get(i).data().getString("text");
            require(text.length()<=PokemonEditor.SNAPSHOT_PART_CHARS,"Outgoing snapshot chunk too large");
            require(!Character.isHighSurrogate(text.charAt(text.length()-1)),"Outgoing snapshot split a surrogate pair");
            require(!Character.isLowSurrogate(text.charAt(0)),"Outgoing snapshot split a surrogate pair");
            complete=PokemonTransfer.accept(roundtrip(mc,packets.get(i)));
            require(i==packets.size()-1||complete==null,"Incomplete snapshot reached the menu");
        }
        require(complete!=null&&complete.open()&&complete.data().equals(original),"Large snapshot changed during transfer");
        require(PokemonTransfer.accept(roundtrip(mc,packets.getLast()))==null,"Completed snapshot replay was accepted");
    }
    private static PokemonEditor.Sync roundtrip(Minecraft mc,PokemonEditor.Sync packet) {
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),mc.level.registryAccess());
        try {
            PokemonEditor.Sync.CODEC.encode(buffer,packet);require(buffer.readableBytes()<200000,"S2C snapshot chunk exceeds its packet budget");
            return PokemonEditor.Sync.CODEC.decode(buffer);
        } finally {buffer.release();}
    }
}
