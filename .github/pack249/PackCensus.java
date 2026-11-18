package dev.unyielding.pack249;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
@Mod("uq_pack249_census")
public final class PackCensus {
    public PackCensus() { NeoForge.EVENT_BUS.addListener(PackCensus::started); }
    private static void started(ServerStartedEvent event) {
        var rows = new TreeSet<String>();
        var hashes = new HashMap<Path,String>();
        try {
            for(var info : ModList.get().getMods()) {
                String id = info.getModId();
                if(Set.of("minecraft","neoforge","uq_pack249_census","minecraftjail","uq_bundle_smoke").contains(id)) continue;
                var path=info.getOwningFile().getFile().getFilePath();
                if(!hashes.containsKey(path)) {
                    var sha=MessageDigest.getInstance("SHA-512");
                    try(var stream=Files.newInputStream(path)) {
                        byte[] buffer=new byte[65536]; int n;
                        while((n=stream.read(buffer))!=-1) sha.update(buffer,0,n);
                    }
                    hashes.put(path,HexFormat.of().formatHex(sha.digest()));
                }
                rows.add(id+"="+info.getVersion()+":"+hashes.get(path));
            }
            Files.write(Path.of(System.getenv("PACK_CENSUS")),rows);
        } catch(Exception e) { throw new IllegalStateException("Unable to verify loaded pack identity",e); }
    }
}
