package dev.unyielding.jail.party.live;

import com.cobblemon.mod.common.api.storage.adapter.flatfile.OneToOneFileStoreAdapter;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;

/** Negative calls exercise actual native wrappers around the exact global adapter. */
@SuppressWarnings({"rawtypes","unchecked"})
public final class SessionWriteProbe {
    private SessionWriteProbe(){}
    static void denied(String name,NativeFence.Call call)throws Exception {
        try{call.run();throw new AssertionError("Native scope accepted "+name);}
        catch(NativeFence.Denied expected){SessionBridge.flag(name);}
        catch(Exception e){throw e;}catch(Throwable t){throw new IOException(t);}
    }
    public static void verify(HandoffCoordinator custody,RegistryAccess registries)throws Exception {
        var state=custody.owned();var receipt=custody.receipt();
        OneToOneFileStoreAdapter adapter=GlobalProfile.adapter();
        Path raw=adapter.getFile(PlayerPartyStore.class,state.owner()).toPath().toAbsolutePath();
        Path current=GlobalProfile.checkedFile(adapter,PlayerPartyStore.class,state.owner());
        SessionBridge.proof.put("native_current_spelling",raw.toString());
        SessionBridge.proof.put("native_current_canonical",current.toString());
        SessionBridge.proof.put("prior_path_predicate_would_refuse",!raw.equals(raw.normalize()));
        System.out.println("JAIL_NATIVE_PATH raw="+raw+" canonical="+current);
        byte[] before=Files.readAllBytes(current);Path backup=Path.of(current+".old");
        byte[] old=Files.exists(backup)?Files.readAllBytes(backup):null;
        Object frozen=NativeFence.payload(state,false);CompoundTag changed=((CompoundTag)frozen).copy();changed.putString("scope_probe","refuse");
        try{NativeFence.write(state,adapter,current,changed,()->{throw new AssertionError("Unfrozen write executed");});throw new AssertionError("Unfrozen payload accepted");}
        catch(IOException expected){SessionBridge.flag("global_scope_unfrozen_payload_refused");}
        CompletableFuture<Throwable> result=new CompletableFuture<>();
        NativeFence.write(state,adapter,current,frozen,()->{
            denied("global_scope_wrong_suffix_refused",()->{adapter.save(new File(current+".tmp"),frozen);return null;});
            denied("global_scope_backup_refused",()->{adapter.save(backup.toFile(),frozen);return null;});
            denied("global_scope_wrong_owner_refused",()->{adapter.save(PlayerPartyStore.class,UUID.randomUUID(),frozen);return null;});
            denied("global_scope_changed_payload_refused",()->{adapter.save(new File(raw+".temp"),changed);return null;});
            denied("global_scope_load_refused",()->adapter.load(PlayerPartyStore.class,state.owner(),registries));
            Path alias=Path.of(current+".temp");if(alias.equals(Path.of(raw+".temp")))alias=current.getParent().resolve(".").resolve(current.getFileName()+".temp");
            final Path different=alias;
            denied("global_scope_unissued_alias_refused",()->{adapter.save(different.toFile(),frozen);return null;});
            denied("global_scope_parent_traversal_refused",()->{adapter.save(current.getParent().resolve("unused/../"+current.getFileName()+".temp").toFile(),frozen);return null;});
            Thread.ofPlatform().name("jail-scope-negative-worker").start(()->{
                try{adapter.save(PlayerPartyStore.class,state.owner(),frozen);result.complete(null);}
                catch(Throwable refusal){result.complete(refusal);}
            });
            return null;
        });
        SessionBridge.require(result.get(20,TimeUnit.SECONDS) instanceof NativeFence.Denied,"Write scope leaked to worker");
        SessionBridge.flag("global_scope_worker_refused");
        denied("global_scope_after_exit_refused",()->{adapter.save(PlayerPartyStore.class,state.owner(),frozen);return null;});
        SessionBridge.require(Arrays.equals(before,Files.readAllBytes(current))&&(old==null?!Files.exists(backup):Arrays.equals(old,Files.readAllBytes(backup)))
            &&!Files.exists(Path.of(current+".temp"))&&!Files.exists(Path.of(current+".tmp"))&&receipt.equals(custody.receipt()),"Negative probes changed native bytes or receipt");
        custody.verifyNative(state);SessionBridge.flag("global_scope_negative_probes_preserve_exact_state");
    }
}
