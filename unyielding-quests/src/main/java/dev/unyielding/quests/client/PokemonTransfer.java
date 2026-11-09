package dev.unyielding.quests.client;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.settings.PokemonEditor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One bounded, ordered menu snapshot at a time; partial transfers never reach the editor. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID,value=Dist.CLIENT)
public final class PokemonTransfer {
    private static final long TIMEOUT=30_000_000_000L;
    private static final Set<String> finished=new LinkedHashSet<>();
    private static Transfer active;
    private static final class Transfer {
        final String token,id;final int total,declaredBytes;final long revision,started;final boolean open;
        final StringBuilder text=new StringBuilder(PokemonEditor.SNAPSHOT_PART_CHARS);
        int next,bytes;
        Transfer(String token,String id,int total,int bytes,long revision,boolean open) {
            this.token=token;this.id=id;this.total=total;declaredBytes=bytes;this.revision=revision;this.open=open;started=System.nanoTime();
        }
    }
    private PokemonTransfer() { }

    public static PokemonEditor.Sync accept(PokemonEditor.Sync packet) {
        if(active!=null&&System.nanoTime()-active.started>TIMEOUT){remember(active.token);active=null;}
        CompoundTag data=packet.data();
        if(!data.getString("view").equals("transfer")) {
            if(active!=null){remember(active.token);active=null;}
            return packet;
        }
        String token=data.getString("token");
        if(finished.contains(token))return null;
        String id=data.getString("id");
        try {
            if(token.length()!=36||!UUID.fromString(token).toString().equals(token)||id.length()>200)
                throw new IllegalArgumentException("Invalid snapshot transfer.");
            int index=data.getInt("index"),total=data.getInt("total"),declaredBytes=data.getInt("bytes");
            String text=data.getString("text");long revision=data.getLong("revision");
            if(index<0||index>=total||total<1||total>PokemonEditor.SNAPSHOT_MAX_PARTS||declaredBytes<1
                    ||declaredBytes>PokemonEditor.SNAPSHOT_MAX_BYTES||text.isEmpty()||text.length()>PokemonEditor.SNAPSHOT_PART_CHARS)
                throw new IllegalArgumentException("Snapshot exceeds its transfer limits.");
            if(index==0) {
                if(active!=null)remember(active.token);
                active=new Transfer(token,id,total,declaredBytes,revision,packet.open());
            }
            // Late fragments from an abandoned transfer must not replace the current menu.
            if(active==null||!active.token.equals(token))return null;
            Transfer transfer=active;
            if(index!=transfer.next||total!=transfer.total||declaredBytes!=transfer.declaredBytes
                    ||revision!=transfer.revision||packet.open()!=transfer.open||!id.equals(transfer.id))
                throw new IllegalArgumentException("Snapshot transfer was interrupted.");
            int bytes=PokemonEditor.utf8Size(text);
            if(transfer.bytes+bytes>transfer.declaredBytes||transfer.text.length()+text.length()>PokemonEditor.SNAPSHOT_MAX_BYTES)
                throw new IllegalArgumentException("Snapshot exceeds its transfer limits.");
            transfer.text.append(text);transfer.bytes+=bytes;transfer.next++;
            if(transfer.next!=transfer.total)return null;
            active=null;remember(token);
            if(transfer.bytes!=transfer.declaredBytes)throw new IllegalArgumentException("Incomplete snapshot transfer.");
            String complete=transfer.text.toString();
            checkNesting(complete);
            CompoundTag original=TagParser.parseTag(complete);
            if(!Set.of("species","search","error").contains(original.getString("view"))
                    ||!id.equals(original.getString("id"))||revision!=original.getLong("revision"))
                throw new IllegalArgumentException("Snapshot metadata does not match.");
            return new PokemonEditor.Sync(original,transfer.open);
        } catch(Exception failure) {
            if(active!=null&&active.token.equals(token))active=null;
            remember(token);
            CompoundTag error=new CompoundTag();error.putString("view","error");error.putBoolean("error",true);
            error.putString("id",id.length()>200?"":id);error.putLong("revision",data.getLong("revision"));
            error.putString("message","Could not load Pokemon data. Reopen this Pokemon to try again.");
            return new PokemonEditor.Sync(error,false);
        }
    }

    private static void checkNesting(String text) {
        char quote=0;boolean escaped=false;int depth=0;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(quote!=0) {
                if(escaped)escaped=false;
                else if(c=='\\')escaped=true;
                else if(c==quote)quote=0;
            } else if(c=='\"'||c=='\'')quote=c;
            else if(c=='{'||c=='['){if(++depth>64)throw new IllegalArgumentException("Snapshot is too deeply nested.");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Invalid snapshot structure.");}
        }
        if(depth!=0||quote!=0)throw new IllegalArgumentException("Incomplete snapshot structure.");
    }
    private static void remember(String token) {
        if(token.length()>64)return;
        finished.add(token);while(finished.size()>8)finished.remove(finished.iterator().next());
    }
    public static void clear(){active=null;finished.clear();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
}
