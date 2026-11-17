package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.api.storage.adapter.flatfile.*;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import com.google.gson.*;

/** Test-only native disk staging. The durable fence is never retired, even after a clean staged return. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class HandoffCoordinator implements AutoCloseable {
    public enum Step { FENCE_DURABLE, RESERVATION_DURABLE, EMPTY_WRITTEN, HELD_DURABLE, RETURN_DURABLE, RETURN_WRITTEN }
    @FunctionalInterface public interface Fault { void hit(Step step)throws Exception; }
    private final UUID tx,owner;
    private final String provider;
    private final RegistryAccess registries;
    private final OneToOneFileStoreAdapter adapter;
    private final PartyCustodyGuard guard;
    private final CobblemonSnapshotCodec codec;
    private final Path current;
    private PartyCustodyGuard.Snapshot original,empty;
    private HandoffCoordinator(UUID tx,UUID owner,String provider,RegistryAccess registries,OneToOneFileStoreAdapter adapter,PartyCustodyGuard guard) {
        this.tx=tx;this.owner=owner;this.provider=provider;this.registries=registries;this.adapter=adapter;this.guard=guard;this.codec=new CobblemonSnapshotCodec(registries);
        this.current=adapter.getFile(PlayerPartyStore.class,owner).toPath().toAbsolutePath().normalize();
    }
    public static HandoffCoordinator start(Path directory,String binding,UUID tx,RegistryAccess registries,FileBackedPokemonStoreFactory factory,OneToOneFileStoreAdapter adapter,PlayerPartyStore party,PCStore pc,Fault fault)throws Exception {
        return (HandoffCoordinator)NativeFence.exclusive(()->{
            UUID owner=party.getPlayerUUID();
            if(factory.getClass()!=FileBackedPokemonStoreFactory.class || (adapter.getClass()!=NBTStoreAdapter.class&&adapter.getClass()!=JSONStoreAdapter.class)
                || field(factory,"adapter")!=adapter || !((List<?>)field(adapter,"children")).isEmpty()
                || factory.getPlayerParty(owner,registries)!=party || factory.getPC(owner,registries)!=pc)throw new IOException("Unreviewed or mismatched concrete provider");
            String provider=providerIdentity(adapter);
            var codec=new CobblemonSnapshotCodec(registries);var source=codec.capture(owner,provider,party,pc);var empty=codec.empty(source);
            if(!empty.pcIds().isEmpty())throw new IOException("Initial live fence increment requires one empty PC box");
            List<Object> aliases=new ArrayList<>();aliases.add(party);aliases.add(pc);for(var p:party)aliases.add(p);aliases.addAll(pc.getBoxes());
            // Never erase a source until the independent ownership tombstone and full snapshot both commit.
            NativeFence.enroll(tx,source,aliases.toArray());fault.hit(Step.FENCE_DURABLE);
            PartyCustodyGuard guard=PartyCustodyGuard.create(directory,binding);
            var c=new HandoffCoordinator(tx,owner,provider,registries,adapter,guard);c.original=source;c.empty=empty;
            try {
                guard.reserve(tx,source,guard.receipt());fault.hit(Step.RESERVATION_DURABLE);
                c.writeExact(empty);fault.hit(Step.EMPTY_WRITTEN);
                guard.sealEmpty(tx,empty,guard.receipt());fault.hit(Step.HELD_DURABLE);return c;
            }catch(Throwable failure){guard.close();throw failure;}
        });
    }
    public static HandoffCoordinator open(Path directory,String binding,UUID owner,RegistryAccess registries,OneToOneFileStoreAdapter adapter)throws Exception {
        var e=NativeFence.entry(owner);
        if(!e.provider().equals(providerIdentity(adapter)))throw new IOException("Recovery provider mismatch");
        var guard=PartyCustodyGuard.open(directory,binding);
        return new HandoffCoordinator(e.transaction(),owner,e.provider(),registries,adapter,guard);
    }
    public PartyCustodyGuard.Phase phase()throws IOException{return guard.phase(tx);}
    public GenerationJournal.Receipt receipt()throws IOException{return guard.receipt();}
    public PartyCustodyGuard.Snapshot owned()throws IOException{return guard.loadOwned(tx,owner,provider,guard.receipt());}
    /** Verify current native bytes without letting a native backup loader choose an older generation. */
    public void verifyNative(PartyCustodyGuard.Snapshot expected)throws Exception {
        verifyFile(current,payload(expected,false));
        verifyFile(adapter.getFile(PCStore.class,owner).toPath(),payload(expected,true));
    }
    private void verifyFile(Path path,Object payload)throws Exception {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(path))throw new IOException("Native destination missing or unsafe");
        if(adapter.getClass()==NBTStoreAdapter.class){if(!NbtIo.readCompressed(path,NbtAccounter.create(4L*1024*1024)).equals(payload))throw new IOException("Native destination mismatch: "+path.getFileName());}
        else if(!JsonParser.parseString(Files.readString(path)).equals(payload))throw new IOException("Native destination mismatch: "+path.getFileName());
    }
    public void stageReturn(Fault fault)throws Exception {
        NativeFence.exclusive(()->{
            if(guard.phase(tx)!=PartyCustodyGuard.Phase.SEALED)throw new IOException("No replay or ambiguous return");
            var active=owned();verifyNative(active); // refuse changed or occupied disk before changing managed authority
            guard.restore(tx,active,guard.receipt());fault.hit(Step.RETURN_DURABLE);
            var returned=owned();NativeFence.verifySource(tx,returned);
            writeExact(returned);fault.hit(Step.RETURN_WRITTEN);
            // Intentionally NO native cache publication and NO retirement/unlock of the durable fence.
            return null;
        });
    }
    private Object payload(PartyCustodyGuard.Snapshot expected,boolean pc)throws Exception {
        return NativeFence.inspect(owner,()->{
            var stores=codec.decode(expected);
            var store=pc?stores.pc():stores.party();
            return adapter.getClass()==NBTStoreAdapter.class?store.saveToNBT(new CompoundTag(),registries):store.saveToJSON(new JsonObject(),registries);
        });
    }
    private void writeExact(PartyCustodyGuard.Snapshot expected)throws Exception {
        Object payload=payload(expected,false);
        NativeFence.write(owner,adapter,current,payload,()->{adapter.save(PlayerPartyStore.class,owner,payload);return null;});
        // This is synchronous reviewed adapter IO under the global method lock, NOT the native queue's void save.
        try(var channel=FileChannel.open(current,StandardOpenOption.WRITE)){channel.force(true);}
        try(var directory=FileChannel.open(current.getParent(),StandardOpenOption.READ)){directory.force(true);}
        verifyNative(expected);
    }
    public static String providerIdentity(OneToOneFileStoreAdapter adapter)throws Exception {
        if((adapter.getClass()!=NBTStoreAdapter.class&&adapter.getClass()!=JSONStoreAdapter.class)||!((List<?>)field(adapter,"children")).isEmpty()
                ||Boolean.TRUE.equals(field(adapter,"useNestedFolders"))||!Boolean.TRUE.equals(field(adapter,"folderPerClass")))throw new IOException("Unreviewed concrete provider layout");
        Path root=Path.of((String)field(adapter,"rootFolder")).toAbsolutePath().normalize();
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||!root.equals(root.toRealPath()))throw new IOException("Provider path not canonical");
        String identity=adapter.getClass().getName()+":"+root+":"+field(adapter,"fileExtension")+":false:true";
        return adapter.getClass().getSimpleName()+":"+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    public static Object field(Object o,String name)throws Exception {
        for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    @Override public void close()throws IOException{guard.close();}
}
