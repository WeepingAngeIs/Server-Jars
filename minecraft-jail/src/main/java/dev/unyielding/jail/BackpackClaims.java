package dev.unyielding.jail;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** One immutable, thread-visible lookup for provider handlers. Durable per-bag markers keep a
 * damaged custody journal from unlocking its bags or interfering with unrelated backpacks. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class BackpackClaims {
    private static volatile Map<UUID,UUID> held=Map.of();
    private static final UUID UNCERTAIN=new UUID(0,0);
    private static final System.Logger LOG=System.getLogger(BackpackClaims.class.getName());
    private BackpackClaims(){}

    static UUID get(UUID bag){return bag==null?null:held.get(bag);}
    static void stopped(){held=Map.of();}

    private static Path directory(MinecraftServer server){return JailIntegrated.root(server).resolve("backpack-locks");}
    private static void forceDirectory(Path path)throws IOException {
        try(var channel=FileChannel.open(path,StandardOpenOption.READ)){channel.force(true);}
    }

    @SubscribeEvent public static void start(ServerStartedEvent event){
        MinecraftServer server=event.getServer();
        Path dir=directory(server);
        Map<UUID,UUID> found=new HashMap<>();
        if(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))try(var paths=Files.list(dir)){
            for(Path file:paths.toList()){
                UUID bag;
                try{bag=UUID.fromString(file.getFileName().toString());}catch(IllegalArgumentException other){continue;}
                found.put(bag,UNCERTAIN);
                if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)==36)try{
                    found.put(bag,UUID.fromString(Files.readString(file,StandardCharsets.US_ASCII)));
                }catch(IllegalArgumentException unreadable){LOG.log(System.Logger.Level.WARNING,"Malformed custody marker for backpack "+bag);}
            }
        }catch(IOException failure){LOG.log(System.Logger.Level.ERROR,"Could not read all backpack custody markers",failure);}
        try {
            if(JailData.get(server).integratedCustodyInitialized()){
                var plans=JailIntegrated.backpackPlans(server);
                for(var entry:plans.list())if(!JailEscrowJournal.terminal(entry.state())){
                    for(UUID bag:JailBackpacks.ids(JailNbt.decode(entry.snapshot()).getCompound(JailBackpacks.RECORDS)))
                        found.merge(bag,entry.id(),(old,tx)->old.equals(tx)?tx:UNCERTAIN);
                }
                for(var claim:Map.copyOf(found).entrySet()){
                    var plan=plans.read(claim.getValue());
                    if(plan!=null&&JailEscrowJournal.terminal(plan.state())
                            && JailBackpacks.ids(JailNbt.decode(plan.snapshot()).getCompound(JailBackpacks.RECORDS)).contains(claim.getKey())){
                        removeMarker(dir,claim.getKey(),plan.id());
                        found.remove(claim.getKey());
                    }
                }
            }
        }catch(IOException|RuntimeException failure){LOG.log(System.Logger.Level.ERROR,"Backpack custody history needs review; identified backpacks stay locked",failure);}
        held=Map.copyOf(found);
    }

    static void reserve(MinecraftServer server,Set<UUID> bags,UUID tx)throws IOException {
        if(bags.isEmpty())return;
        Map<UUID,UUID> next=new HashMap<>(held);
        for(UUID bag:bags)if(next.putIfAbsent(bag,tx)!=null)throw new IOException("Backpack is already reserved");
        held=Map.copyOf(next);
        Path dir=directory(server);
        if(Files.notExists(dir,LinkOption.NOFOLLOW_LINKS)){Files.createDirectory(dir);forceDirectory(dir.getParent());}
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsafe backpack custody directory");
        for(UUID bag:bags){
            Path file=dir.resolve(bag.toString());
            Files.writeString(file,tx.toString(),StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            JailVanillaPort.force(file);
        }
    }
    private static void removeMarker(Path dir,UUID bag,UUID tx)throws IOException {
        Path file=dir.resolve(bag.toString());
        if(Files.notExists(file,LinkOption.NOFOLLOW_LINKS))return;
        if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                || Files.size(file)!=36 || !Files.readString(file,StandardCharsets.US_ASCII).equals(tx.toString()))
            throw new IOException("Foreign backpack custody marker");
        Files.delete(file);forceDirectory(dir);
    }
    static void release(MinecraftServer server,UUID tx)throws IOException {
        Map<UUID,UUID> next=new HashMap<>(held);
        for(var claim:held.entrySet())if(tx.equals(claim.getValue())){
            removeMarker(directory(server),claim.getKey(),tx);
            next.remove(claim.getKey());
        }
        held=Map.copyOf(next);
    }
}
