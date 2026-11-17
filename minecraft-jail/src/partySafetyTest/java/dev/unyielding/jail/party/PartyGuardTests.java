package dev.unyielding.jail.party;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.unyielding.jail.party.GenerationJournal.*;

/** Executable JDK-only synthetic tests; each named case fails on any missing assertion. */
public final class PartyGuardTests {
    static Path root;static int next;static final List<String> passed=new ArrayList<>();
    static final String BIND="reviewed-runtime:synthetic-owner-gateway-v1";
    static final UUID OWNER=UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),ID=UUID.fromString("11223344-5566-7788-99aa-bbccddeeff00"),TX=UUID.fromString("22334455-6677-8899-aabb-ccddeeff0011");
    interface Action {void run()throws Exception;}
    static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void denied(Action a)throws Exception{try{a.run();}catch(IOException|IllegalArgumentException expected){return;}throw new AssertionError("Expected refusal");}
    static void test(String name,Action a)throws Exception{a.run();passed.add(name);System.out.println("PASS "+name);}
    static Path path(){return root.resolve("case-"+(next++));}
    static PartyCustodyGuard.Snapshot full(){return new PartyCustodyGuard.Snapshot(OWNER,"pinned-provider",new byte[]{10,1,2,3},Set.of(ID),new byte[]{10,0},Set.of());}
    static PartyCustodyGuard.Snapshot empty(){return new PartyCustodyGuard.Snapshot(OWNER,"pinned-provider",new byte[]{10,0},Set.of(),new byte[]{10,0},Set.of());}
    static PartyCustodyGuard.Snapshot change(PartyCustodyGuard.Snapshot s,UUID owner,String provider){return new PartyCustodyGuard.Snapshot(owner,provider,s.party(),s.partyIds(),s.pc(),s.pcIds());}
    static Receipt held(PartyCustodyGuard g)throws Exception{var r=g.reserve(TX,full(),g.receipt());return g.sealEmpty(TX,empty(),r);}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("crash")){
            Path p=Path.of(args[1]);Step step=Step.valueOf(args[2]);
            try(var j=GenerationJournal.create(p,BIND)){j.commit(j.receipt(),new byte[]{4,5,6},hit->{if(hit==step)Runtime.getRuntime().halt(92);});}
            throw new AssertionError("Did not halt");
        }
        root=Path.of(args[0]);Files.createDirectories(root);
        test("exact_receipt_and_full_managed_restore",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){
            var original=full();var r=g.reserve(TX,original,g.receipt());require(r.generation()==1,"reserve gen");
            denied(()->g.loadOwned(TX,OWNER,"pinned-provider",r));
            var held=g.sealEmpty(TX,empty(),r);require(held.generation()==2,"seal gen");
            require(g.loadOwned(TX,OWNER,"pinned-provider",held).same(empty()),"managed empty");
            var returned=g.restore(TX,empty(),held);require(returned.generation()==3,"return gen");
            require(g.loadOwned(TX,OWNER,"pinned-provider",returned).same(original),"exact original");
        }});
        test("reservation_survives_reopen_blocks_uncommitted_load",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){g.reserve(TX,full(),g.receipt());}try(var g=PartyCustodyGuard.open(p,BIND)){denied(()->g.loadOwned(TX,OWNER,"pinned-provider",g.receipt()));}});
        test("sealed_and_restored_generations_survive_reopen",()->{Path p=path();Receipt r;try(var g=PartyCustodyGuard.create(p,BIND)){r=held(g);}try(var g=PartyCustodyGuard.open(p,BIND)){g.verify(r);g.restore(TX,empty(),r);}try(var g=PartyCustodyGuard.open(p,BIND)){require(g.phase(TX)==PartyCustodyGuard.Phase.RESTORED,"terminal phase");require(g.loadOwned(TX,OWNER,"pinned-provider",g.receipt()).same(full()),"restored snapshot");}});
        test("stale_backup_and_queued_generation_refused",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var reserved=g.reserve(TX,full(),g.receipt());var r=g.sealEmpty(TX,empty(),reserved);denied(()->g.authorizeCandidate(TX,full(),r));denied(()->g.restore(TX,empty(),reserved));require(g.receipt().equals(r),"stale action mutated");}});
        test("alternate_provider_and_foreign_owner_refused",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var r=held(g);denied(()->g.authorizeCandidate(TX,change(empty(),OWNER,"alternate"),r));denied(()->g.loadOwned(TX,UUID.randomUUID(),"pinned-provider",r));denied(()->g.loadOwned(UUID.randomUUID(),OWNER,"pinned-provider",r));}});
        test("occupied_or_changed_destination_never_overwritten",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var r=held(g);denied(()->g.restore(TX,full(),r));var changed=new PartyCustodyGuard.Snapshot(OWNER,"pinned-provider",new byte[]{10,9},Set.of(),empty().pc(),Set.of());denied(()->g.restore(TX,changed,r));require(g.receipt().equals(r),"conflict mutated generation");}});
        test("exclusive_uuid_across_owners_and_providers",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var r=held(g);denied(()->g.reserve(UUID.randomUUID(),change(full(),UUID.randomUUID(),"alternate"),r));require(g.receipt().equals(r),"reservation conflict mutated");}});
        test("party_pc_duplicate_manifest_refused",()->denied(()->new PartyCustodyGuard.Snapshot(OWNER,"pinned-provider",full().party(),Set.of(ID),empty().pc(),Set.of(ID))));
        test("terminal_tombstone_and_repeat_return_refused",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var r=held(g);var returned=g.restore(TX,empty(),r);denied(()->g.restore(TX,empty(),returned));denied(()->g.reserve(TX,full(),returned));denied(()->g.reserve(UUID.randomUUID(),full(),returned));require(g.receipt().equals(returned),"replay altered state");}});
        test("forged_receipt_refused",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var r=held(g);var forged=new Receipt(r.journal(),r.binding(),r.generation(),"0".repeat(64),r.stateHash());denied(()->g.restore(TX,empty(),forged));}});
        test("snapshot_defensive_copies",()->{byte[] b={1};var s=new PartyCustodyGuard.Snapshot(OWNER,"pinned-provider",b,Set.of(ID),new byte[]{2},Set.of());b[0]=8;s.party()[0]=9;require(s.party()[0]==1,"snapshot mutable");});
        test("one_writer_lock_and_existing_root_refusal",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){denied(()->PartyCustodyGuard.open(p,BIND));denied(()->PartyCustodyGuard.create(p,BIND));}});
        test("runtime_binding_refused",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){held(g);}denied(()->PartyCustodyGuard.open(p,"different-runtime"));});
        test("real_io_failure_has_no_receipt_even_when_queue_advances",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND);var pool=Executors.newSingleThreadExecutor()){
            var r=g.receipt();
            Future<Receipt> write=pool.submit(()->g.reserve(TX,full(),r,step->{if(step==Step.INTENT_FORCED)Files.createDirectory(p.resolve("DATA.tmp"));})); // Real write failure after intent, not a mocked return value.
            try{write.get(10,TimeUnit.SECONDS);throw new AssertionError("write unexpectedly succeeded");}catch(ExecutionException expected){require(expected.getCause() instanceof IOException,"error not propagated");}
            require(pool.submit(()->true).get(10,TimeUnit.SECONDS),"queue sentinel");denied(g::receipt);
        }denied(()->PartyCustodyGuard.open(p,BIND));});
        for(Step step:Step.values())test("io_exception_at_"+step,()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){
            var r=g.receipt();denied(()->g.reserve(TX,full(),r,hit->{if(hit==step)throw new IOException("injected "+step);}));denied(g::receipt);
        }if(step!=Step.CLEAN_FORCED)denied(()->PartyCustodyGuard.open(p,BIND));else try(var g=PartyCustodyGuard.open(p,BIND)){require(g.phase(TX)==PartyCustodyGuard.Phase.RESERVED,"verified completed reservation lost");denied(()->g.loadOwned(TX,OWNER,"pinned-provider",g.receipt()));}});
        for(Step step:Step.values())test("abrupt_process_exit_at_"+step,()->{Path p=path();Process child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-cp",System.getProperty("java.class.path"),PartyGuardTests.class.getName(),"crash",p.toString(),step.name()).inheritIO().start();
            require(child.waitFor(30,TimeUnit.SECONDS)&&child.exitValue()==92,"planned hard stop absent");
            if(step!=Step.CLEAN_FORCED)denied(()->GenerationJournal.open(p,BIND));else try(var j=GenerationJournal.open(p,BIND)){require(j.receipt().generation()==1&&Arrays.equals(j.state(),new byte[]{4,5,6}),"fully committed post-halt state changed");}
        });
        test("corrupt_current_does_not_fall_back",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){held(g);}Files.writeString(p.resolve("CURRENT"),"garbage");denied(()->PartyCustodyGuard.open(p,BIND));});
        test("missing_head_cannot_reinitialize",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){held(g);}Files.delete(p.resolve("CURRENT"));denied(()->PartyCustodyGuard.open(p,BIND));denied(()->PartyCustodyGuard.create(p,BIND));});
        test("pointer_rollback_with_new_generation_refused",()->{Path p=path();byte[] old;try(var g=PartyCustodyGuard.create(p,BIND)){old=Files.readAllBytes(p.resolve("CURRENT"));held(g);}Files.write(p.resolve("CURRENT"),old);denied(()->PartyCustodyGuard.open(p,BIND));});
        test("corrupt_generation_refused",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){held(g);}Path data=p.resolve("g-00000000000000000002.bin");byte[] b=Files.readAllBytes(data);b[b.length-1]^=1;Files.write(data,b);denied(()->PartyCustodyGuard.open(p,BIND));});
        test("missing_generation_refused",()->{Path p=path();try(var g=PartyCustodyGuard.create(p,BIND)){held(g);}Files.delete(p.resolve("g-00000000000000000002.bin"));denied(()->PartyCustodyGuard.open(p,BIND));});
        test("symlink_root_refused",()->{Path p=path();Path link=path();try(var g=PartyCustodyGuard.create(p,BIND)){}Files.createSymbolicLink(link,p);denied(()->PartyCustodyGuard.open(link,BIND));});
        test("two_racing_reservations_one_winner",()->{try(var g=PartyCustodyGuard.create(path(),BIND);var pool=Executors.newFixedThreadPool(2)){
            var r=g.receipt();var a=pool.submit(()->{try{g.reserve(UUID.randomUUID(),full(),r);return true;}catch(IOException expected){return false;}});
            var b=pool.submit(()->{try{g.reserve(UUID.randomUUID(),full(),r);return true;}catch(IOException expected){return false;}});
            require(a.get()!=b.get(),"not exactly one reservation winner");require(g.receipt().generation()==1,"race advanced twice");
        }});
        test("reservation_retained_after_refused_changed_pc",()->{try(var g=PartyCustodyGuard.create(path(),BIND)){var r=g.reserve(TX,full(),g.receipt());var wrong=new PartyCustodyGuard.Snapshot(OWNER,"pinned-provider",empty().party(),Set.of(),new byte[]{3},Set.of());denied(()->g.sealEmpty(TX,wrong,r));require(g.phase(TX)==PartyCustodyGuard.Phase.RESERVED,"bad handoff escaped quarantine");}});
        String json="{\"passed\":"+passed.size()+",\"failed\":0,\"admission_enabled\":false,\"tests\":["+String.join(",",passed.stream().map(s->"\""+s+"\"").toList())+"]}\n";
        Files.writeString(root.resolve("unit-results.json"),json);System.out.println(json);
    }
}
