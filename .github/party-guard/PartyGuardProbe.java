package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import dev.unyielding.jail.party.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.unyielding.bundlesmoke.PartyStorageBoundaryProbe.*;

/** Regression hooks run only in the independently guarded synthetic native-storage fixture. */
public final class PartyGuardProbe {
    private static final String PROVIDER="reviewed-cobblemon-file-factory";
    static UUID tx(){return owner("guard-transaction");}
    static String binding(){return System.getenv("UQ_JAIL_SHA256")+":"+System.getenv("UQ_BUNDLE_SHA256")+":"+backend;}
    static Path directory(String suffix){return scratch.resolve("guard-"+suffix);}
    interface Action{void run()throws Exception;}
    static void deny(Action action)throws Exception{try{action.run();}catch(IOException|IllegalArgumentException expected){return;}throw new IllegalStateException("Guard accepted unsafe request");}
    static CobblemonSnapshotCodec codec(){return new CobblemonSnapshotCodec(player.registryAccess());}
    static PCStore pc(UUID id){var pc=new PCStore(id);pc.resize(1,false,null);return pc;}
    static PartyCustodyGuard.Snapshot source()throws Exception{
        UUID id=owner("fallback");var party=new PlayerPartyStore(id);party.loadFromNBT(expected("fallback-original"),player.registryAccess());
        return codec().capture(id,PROVIDER,party,pc(id));
    }
    static void sameSource(PartyCustodyGuard.Snapshot snapshot)throws Exception{
        require(snapshot.same(source()),"Guard full source bytes or UUID manifests changed");
        var stores=codec().decode(snapshot);same(expected("fallback-original"),stores.party(),"guard-exact-native-return");
        require(stores.party().get(0)!=null&&stores.party().get(5)!=null&&stores.party().get(1)==null,"Native original slot layout lost");
    }
    public static void beforeHalt()throws Exception{
        var source=source();var empty=codec().empty(source);
        try(var guard=PartyCustodyGuard.create(directory("managed"),binding())){
            var reserved=guard.reserve(tx(),source,guard.receipt());require(reserved.generation()==1,"reservation not committed first");
            deny(()->guard.loadOwned(tx(),source.owner(),PROVIDER,reserved));
            var held=guard.sealEmpty(tx(),empty,reserved);require(held.generation()==2,"missing empty generation receipt");
            require(guard.loadOwned(tx(),source.owner(),PROVIDER,held).same(empty),"Managed load not authoritative empty");
            deny(()->guard.authorizeCandidate(tx(),source,held));deny(()->guard.restore(tx(),empty,reserved));
            deny(()->guard.restore(tx(),source,held));require(guard.receipt().equals(held),"Occupied return advanced generation");
            var alternate=new PartyCustodyGuard.Snapshot(source.owner(),"alternate-provider",source.party(),source.partyIds(),source.pc(),source.pcIds());
            deny(()->guard.authorizeCandidate(tx(),alternate,held));
            var foreign=new PartyCustodyGuard.Snapshot(UUID.randomUUID(),"alternate-provider",source.party(),source.partyIds(),source.pc(),source.pcIds());
            deny(()->guard.reserve(UUID.randomUUID(),foreign,held));require(guard.receipt().equals(held),"UUID conflict altered state");
            Files.writeString(scratch.resolve("guard-receipt"),held.generation()+"\n"+held.recordHash()+"\n"+held.stateHash()+"\n");force(scratch.resolve("guard-receipt"));
        }
        evidence.put("guard_reservation_before_empty_generation",true);evidence.put("guard_complete_native_snapshot_preserved",true);
        evidence.put("guard_stale_receipt_refused",true);evidence.put("guard_occupied_destination_refused",true);
        evidence.put("guard_alternate_provider_refused",true);evidence.put("guard_exclusive_identity_reservation",true);
        // Use the previously demonstrated native same-UUID party/PC conflict, not a mocked manifest.
        var a=adapter("ownership");var f=factory(a,true);
        try{deny(()->codec().capture(owner("ownership"),PROVIDER,f.getPlayerParty(owner("ownership"),player.registryAccess()),f.getPC(owner("ownership"),player.registryAccess())));}finally{close(f);}
        evidence.put("guard_native_duplicate_party_pc_rejected",true);
        try(var guard=PartyCustodyGuard.create(directory("failed-write"),binding());var executor=Executors.newSingleThreadExecutor()){
            var reserved=guard.reserve(tx(),source,guard.receipt());
            Future<GenerationJournal.Receipt> write=executor.submit(()->guard.sealEmpty(tx(),empty,reserved,step->{
                if(step==GenerationJournal.Step.INTENT_FORCED)Files.createDirectory(directory("failed-write").resolve("DATA.tmp"));
            }));
            try{write.get(20,TimeUnit.SECONDS);throw new IllegalStateException("Receipt issued after failed write");}
            catch(ExecutionException expected){require(expected.getCause() instanceof IOException,"Failure not propagated");}
            require(executor.submit(()->true).get(10,TimeUnit.SECONDS),"Guard queue sentinel failed");deny(guard::receipt);
        }
        evidence.put("guard_io_failure_no_receipt_despite_queue_sentinel",true);
        // Existing fixture then hard-halts with its native removal queued. No graceful save.
    }
    public static void afterRestart()throws Exception{
        var source=source();
        try(var guard=PartyCustodyGuard.open(directory("managed"),binding())){
            var held=guard.receipt();require(Files.readString(scratch.resolve("guard-receipt")).equals(held.generation()+"\n"+held.recordHash()+"\n"+held.stateHash()+"\n"),"Receipt changed after independent restart");
            require(guard.phase(tx())==PartyCustodyGuard.Phase.SEALED,"Managed ownership lost");
            // This candidate was actually restored by native .old fallback across the real JVM restart.
            var a=adapter("fallback");var f=factory(a,true);
            try{var recovered=f.getPlayerParty(owner("fallback"),player.registryAccess());var candidate=codec().capture(source.owner(),PROVIDER,recovered,pc(source.owner()));sameSource(candidate);deny(()->guard.authorizeCandidate(tx(),candidate,held));}finally{close(f);}
            var empty=guard.loadOwned(tx(),source.owner(),PROVIDER,held);require(empty.partyIds().isEmpty(),"Owned empty load exposed reserved identity");
            var returned=guard.restore(tx(),empty,held);sameSource(guard.loadOwned(tx(),source.owner(),PROVIDER,returned));
            deny(()->guard.restore(tx(),empty,returned));deny(()->guard.authorizeCandidate(tx(),empty,returned));
            evidence.put("guard_backup_resurrection_refused_after_restart",true);evidence.put("guard_exact_managed_return_after_restart",true);evidence.put("guard_no_repeat_return_or_stale_empty_load",true);
        }
        deny(()->PartyCustodyGuard.open(directory("failed-write"),binding()));evidence.put("guard_uncertain_write_quarantined_after_real_halt",true);
    }
    public static void afterReleaseRestart()throws Exception{
        try(var guard=PartyCustodyGuard.open(directory("managed"),binding())){
            var r=guard.receipt();require(r.generation()==3&&guard.phase(tx())==PartyCustodyGuard.Phase.RESTORED,"Terminal generation changed");
            sameSource(guard.loadOwned(tx(),source().owner(),PROVIDER,r));
            deny(()->guard.restore(tx(),codec().empty(source()),r));deny(()->guard.reserve(UUID.randomUUID(),source(),r));
            require(guard.receipt().equals(r),"Terminal replay advanced generation");
        }
        deny(()->PartyCustodyGuard.open(directory("failed-write"),binding()));
        evidence.put("guard_exact_restored_state_survived_second_restart",true);evidence.put("guard_terminal_reservations_no_replay",true);
        evidence.put("guard_uncertain_write_still_quarantined",true);
    }
}
