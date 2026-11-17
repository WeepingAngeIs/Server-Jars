package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.PokemonStoreManager;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.*;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Exact global provider observation, never a replacement factory or converter fallback permission. */
public final class GlobalProfile {
    private static PokemonStoreManager manager;
    private static FileBackedPokemonStoreFactory factory;
    private static NBTStoreAdapter adapter;
    private static Object child;
    private static Path world,root;
    private static String layout,identity;
    private GlobalProfile(){}
    public static void bind(MinecraftServer server)throws Exception {
        if(!Boolean.getBoolean("uq.partySession")||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")))throw new IOException("Disposable session profile required");
        if(manager!=null)throw new IOException("No provider rebinding");
        world=server.getWorldPath(LevelResource.ROOT).toRealPath();
        if(!world.getFileName().toString().equals("jail_integrated_smoke"))throw new IOException("Not the disposable world");
        PokemonStoreManager m=Cobblemon.INSTANCE.getStorage();List<Object> fs=new ArrayList<>();
        for(Object f:(Iterable<?>)HandoffCoordinator.field(m,"factories"))fs.add(f);
        if(m.getClass()!=PokemonStoreManager.class||fs.size()!=1||fs.getFirst().getClass()!=FileBackedPokemonStoreFactory.class)throw new IOException("Unreviewed global factory topology: "+fs);
        FileBackedPokemonStoreFactory f=(FileBackedPokemonStoreFactory)fs.getFirst();Object a=HandoffCoordinator.field(f,"adapter");
        if(a.getClass()!=NBTStoreAdapter.class)throw new IOException("This global integration is NBT only");
        NBTStoreAdapter n=(NBTStoreAdapter)a;List<?> children=(List<?>)HandoffCoordinator.field(n,"children");
        if(children.size()!=1||!children.getFirst().getClass().getName().equals("com.cobblemon.mod.common.api.storage.adapter.conversions.ReforgedConversion"))throw new IOException("Unreviewed conversion topology");
        Path r=Path.of((String)HandoffCoordinator.field(n,"rootFolder")).toAbsolutePath().normalize();
        if(!r.startsWith(world)||r.equals(world))throw new IOException("Native root is outside disposable world: "+r);
        // Existing native access creates its own directories. This code does not relocate the provider.
        if(!Files.isDirectory(r,LinkOption.NOFOLLOW_LINKS)||!r.equals(r.toRealPath()))throw new IOException("Native root absent or noncanonical: "+r);
        manager=m;factory=f;adapter=n;child=children.getFirst();root=r;layout=layout(n);
        identity="GlobalNBT:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(layout.getBytes(StandardCharsets.UTF_8)));
        require(n);
    }
    private static String layout(OneToOneFileStoreAdapter<?> a)throws Exception {
        return a.getClass().getName()+":"+Path.of((String)HandoffCoordinator.field(a,"rootFolder")).toAbsolutePath().normalize()+":"+HandoffCoordinator.field(a,"fileExtension")+":"+HandoffCoordinator.field(a,"useNestedFolders")+":"+HandoffCoordinator.field(a,"folderPerClass")+":ReforgedConversion-denied-for-enrolled";
    }
    public static void require(Object a)throws Exception {
        if(manager==null||a!=adapter||Cobblemon.INSTANCE.getStorage()!=manager||HandoffCoordinator.field(factory,"adapter")!=adapter||!layout.equals(layout(adapter))||!root.equals(root.toRealPath()))throw new IOException("Global provider binding changed");
        List<?> children=(List<?>)HandoffCoordinator.field(adapter,"children");if(children.size()!=1||children.getFirst()!=child)throw new IOException("Conversion identity changed");
        List<Object> fs=new ArrayList<>();for(Object f:(Iterable<?>)HandoffCoordinator.field(manager,"factories"))fs.add(f);
        if(fs.size()!=1||fs.getFirst()!=factory)throw new IOException("Global provider order changed");
    }
    public static boolean reviewed(Object a)throws Exception {require(a);return true;}
    public static String identity(OneToOneFileStoreAdapter<?> a)throws Exception {require(a);return identity;}
    public static PokemonStoreManager manager(){return manager;}
    public static FileBackedPokemonStoreFactory factory(){return factory;}
    public static NBTStoreAdapter adapter(){return adapter;}
    public static String description(){return layout;}
    public static Path party(UUID owner){return adapter.getFile(PlayerPartyStore.class,owner).toPath();}
}
