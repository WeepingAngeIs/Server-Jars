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
        var diagnostic = new TreeSet<String>();
        var meta = new TreeSet<String>();
        try {
            for(var info : ModList.get().getMods()) {
                String id = info.getModId();
                if(Set.of("minecraft","neoforge","uq_pack249_census","minecraftjail","uq_bundle_smoke").contains(id)) continue;
                var owner=info.getOwningFile().getFile();
                var path=owner.getFilePath();
                if(!hashes.containsKey(path)) {
                    var sha=MessageDigest.getInstance("SHA-512");
                    try(var stream=Files.newInputStream(path)) {
                        byte[] buffer=new byte[65536]; int n;
                        while((n=stream.read(buffer))!=-1) sha.update(buffer,0,n);
                    }
                    hashes.put(path,HexFormat.of().formatHex(sha.digest()));
                }
                String fingerprint=hashes.get(path);
                if(RuntimeFileIdentity.generated(id)) {
                    var origin=owner.getDiscoveryAttributes();
                    String parent=origin.parent()==null ? "" : origin.parent().getFilePath().toUri().toString();
                    String reader=origin.reader()==null ? "" : origin.reader().getClass().getName();
                    String locator=origin.dependencyLocator()==null ? "" : origin.dependencyLocator().getClass().getName();
                    meta.add(String.join("\t",id,owner.getClass().getName(),path.toUri().toString(),fingerprint,parent,reader,locator));
                    for(var entry : RuntimeFileIdentity.entries(path).entrySet()) {
                        if(entry.getKey().contains("\t") || entry.getKey().contains("\n")) throw new IllegalArgumentException("Invalid diagnostic entry");
                        diagnostic.add(id+"\t"+entry.getKey()+"\t"+entry.getValue());
                    }
                    fingerprint=RuntimeFileIdentity.digest(id,path);
                }
                rows.add(id+"="+info.getVersion()+":"+fingerprint);
            }
            var output=Path.of(System.getenv("PACK_CENSUS"));
            Files.write(output.resolveSibling("generated-origins.tsv"),meta);
            Files.write(output.resolveSibling("generated-entry-digests.tsv"),diagnostic);
            Files.write(output,rows);
            try {
                SyntheticPlayerProjection.record(event.getServer(),output.resolveSibling("synthetic-constructor.snbt"));
            } catch(Exception | LinkageError error) {
                // Startup and the file census are separate from this diagnostic.
                Files.writeString(output.resolveSibling("synthetic-projection-error.txt"),error.toString());
            }
        } catch(Exception e) { throw new IllegalStateException("Unable to verify loaded pack identity",e); }
    }
}
