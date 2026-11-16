package dev.unyielding.quests.jail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JailOriginTransactionTest {
    @TempDir Path temp;
    static final class Port implements JailOriginTransaction.Port {
        final UUID owner=UUID.randomUUID();
        boolean online=true,conflict,callbackFail,callbackMutate,failBeforeSave,failAfterSave,custodyValid=true;
        int changes,saves,progress=7;
        JailOriginTransaction.View live=new JailOriginTransaction.View(new byte[]{1,7},null,JailOriginTransaction.Receipt.NONE),disk=live;
        public UUID owner(){return owner;}public boolean online(){return online;}
        public byte[] prepareIntent(UUID id,byte[] digest)throws IOException{if(conflict)throw new IOException("claim conflict");return new byte[]{42};}
        public void validateCustody(JailEscrowJournal.Entry entry,boolean releaseStarted)throws IOException{if(!custodyValid)throw new IOException("custody changed");}
        public JailOriginTransaction.View current(){return live;}public JailOriginTransaction.View durable(){return disk;}
        public JailOriginTransaction.View planTemporary(byte[] b,UUID id)throws IOException{if(conflict)throw new IOException("claim conflict");return new JailOriginTransaction.View(new byte[]{2,(byte)progress},id,JailOriginTransaction.Receipt.TEMPORARY);}
        public JailOriginTransaction.View planOriginal(byte[] b,UUID id)throws IOException{if(conflict)throw new IOException("claim conflict");return new JailOriginTransaction.View(new byte[]{1,(byte)progress},id,JailOriginTransaction.Receipt.ORIGINAL);}
        public void makeTemporary(byte[] b,UUID id)throws IOException{changes++;live=planTemporary(b,id);if(callbackMutate)live=new JailOriginTransaction.View(new byte[]{2,99},id,JailOriginTransaction.Receipt.TEMPORARY);if(callbackFail)throw new IOException("callback failed");}
        public void restoreOriginal(byte[] b,UUID id)throws IOException{changes++;live=planOriginal(b,id);if(callbackFail)throw new IOException("callback failed");}
        public void persistAndVerify()throws IOException{saves++;if(failBeforeSave)throw new IOException("save before");disk=live;if(failAfterSave)throw new IOException("save after");}
        public boolean isTemporary(byte[] b,UUID id,JailOriginTransaction.View v){return !conflict&&v.state()[0]==2&&id.equals(v.transaction())&&v.receipt()==JailOriginTransaction.Receipt.TEMPORARY;}
        public boolean isOriginal(byte[] b,UUID id,JailOriginTransaction.View v){return !conflict&&v.state()[0]==1&&id.equals(v.transaction())&&v.receipt()==JailOriginTransaction.Receipt.ORIGINAL;}
        public boolean isPrepared(byte[] b,JailOriginTransaction.View v){return !conflict&&v.state()[0]==1&&v.transaction()==null&&v.receipt()==JailOriginTransaction.Receipt.NONE;}
    }
    private static void held(JailEscrowJournal inventory,UUID id,UUID owner)throws Exception {
        inventory.begin(id,owner,new byte[]{11,22,33});inventory.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);inventory.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
    }
    @Test void terminalReviewStillBindsTheSameOwnerAndImmutableItemSource()throws Exception {
        Path o=temp.resolve("origin"),i=temp.resolve("items"),f=temp.resolve("foreign"),d=temp.resolve("different");for(Path path:new Path[]{o,i,f,d})JailEscrowJournal.provisionNew(path);Port port=new Port();UUID id=UUID.randomUUID();
        try(var origins=new JailEscrowJournal(o);var items=new JailEscrowJournal(i);var foreign=new JailEscrowJournal(f);var different=new JailEscrowJournal(d)){
            held(items,id,port.owner);JailOriginTransaction.temporaryForDisposableTest(origins,items,id,port,at->{});JailOriginTransaction.originalForDisposableTest(origins,items,id,port,at->{});
            items.transition(id,JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);items.transition(id,JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
            assertArrayEquals(new byte[]{42},JailOriginTransaction.intentForBoundReview(origins,items,id,port.owner));
            assertThrows(IOException.class,()->JailOriginTransaction.intentForBoundReview(origins,items,id,UUID.randomUUID()));
            held(foreign,id,UUID.randomUUID());assertThrows(IOException.class,()->JailOriginTransaction.intentForBoundReview(origins,foreign,id,port.owner));
            different.begin(id,port.owner,new byte[]{99});assertThrows(IOException.class,()->JailOriginTransaction.intentForBoundReview(origins,different,id,port.owner));
        }
    }
    @Test void allTemporaryBoundariesReopenAndNeverRepeatCallbacks()throws Exception {
        for(var boundary:JailOriginTransaction.Boundary.values()) {
            Path o=temp.resolve("o-"+boundary),i=temp.resolve("i-"+boundary);JailEscrowJournal.provisionNew(o);JailEscrowJournal.provisionNew(i);Port port=new Port();UUID id=UUID.randomUUID();
            try(var inv=new JailEscrowJournal(i);var origins=new JailEscrowJournal(o)) {
                held(inv,id,port.owner());
                assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origins,inv,id,port,at->{if(at==boundary)throw new IOException("interrupt");}));
            }
            port.live=port.disk;
            try(var inv=new JailEscrowJournal(i);var origins=new JailEscrowJournal(o)) {
                var expected=switch(boundary){case BEFORE_PREPARE->JailOriginTransaction.Inspection.NO_RECORD;case PREPARED->JailOriginTransaction.Inspection.ORIGINAL_PREPARED;
                    case PLAYER_AND_CLAIMS_DURABLE,BEFORE_COMPLETE->JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW;case COMPLETE->JailOriginTransaction.Inspection.TEMPORARY_VERIFIED;default->JailOriginTransaction.Inspection.REVIEW_REQUIRED;};
                int changes=port.changes,saves=port.saves;assertEquals(expected,JailOriginTransaction.inspect(origins,inv,id,port));assertEquals(changes,port.changes);assertEquals(saves,port.saves);
                if(boundary!=JailOriginTransaction.Boundary.BEFORE_PREPARE){assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origins,inv,id,port,at->{}));assertEquals(changes,port.changes);}
                if(expected==JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW){JailOriginTransaction.confirmSavedForDisposableTest(origins,inv,id,port);assertEquals(changes,port.changes);}
                assertThrows(IOException.class,()->JailOriginTransaction.assertInventoryReleaseReady(origins,inv,id,port,false));
            }
        }
    }
    @Test void originalRestorationBoundariesRetainProgressAndDoNotReplay()throws Exception {
        for(var boundary:JailOriginTransaction.Boundary.values()) {
            if(boundary==JailOriginTransaction.Boundary.PREPARED)continue; // no second intent on restore
            Path o=temp.resolve("o-"+boundary),i=temp.resolve("i-"+boundary);JailEscrowJournal.provisionNew(o);JailEscrowJournal.provisionNew(i);Port port=new Port();UUID id=UUID.randomUUID();
            try(var inv=new JailEscrowJournal(i);var origins=new JailEscrowJournal(o)) {
                held(inv,id,port.owner());JailOriginTransaction.temporaryForDisposableTest(origins,inv,id,port,at->{});
                port.progress=19;port.disk=port.live=new JailOriginTransaction.View(new byte[]{2,19},id,JailOriginTransaction.Receipt.TEMPORARY);
                assertThrows(IOException.class,()->JailOriginTransaction.originalForDisposableTest(origins,inv,id,port,at->{if(at==boundary)throw new IOException("interrupt");}));
            }
            port.live=port.disk;
            try(var inv=new JailEscrowJournal(i);var origins=new JailEscrowJournal(o)) {
                int changes=port.changes;
                var expected=switch(boundary){case BEFORE_PREPARE->JailOriginTransaction.Inspection.TEMPORARY_VERIFIED;
                    case PLAYER_AND_CLAIMS_DURABLE,BEFORE_COMPLETE->JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW;case COMPLETE->JailOriginTransaction.Inspection.COMPLETE_RECORDED;default->JailOriginTransaction.Inspection.REVIEW_REQUIRED;};
                assertEquals(expected,JailOriginTransaction.inspect(origins,inv,id,port));
                if(boundary==JailOriginTransaction.Boundary.BEFORE_PREPARE){assertEquals(JailOriginTransaction.Result.VERIFIED,JailOriginTransaction.originalForDisposableTest(origins,inv,id,port,at->{}));changes++;}
                else if(boundary==JailOriginTransaction.Boundary.COMPLETE)assertEquals(JailOriginTransaction.Result.ALREADY_COMPLETE,JailOriginTransaction.originalForDisposableTest(origins,inv,id,port,at->fail("repeat callback")));
                else assertThrows(IOException.class,()->JailOriginTransaction.originalForDisposableTest(origins,inv,id,port,at->{}));
                if(expected==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW)JailOriginTransaction.confirmSavedForDisposableTest(origins,inv,id,port);
                assertEquals(changes,port.changes);assertEquals(19,port.live.state()[1]);
                if(origins.read(id).state()==JailEscrowJournal.State.COMPLETE)JailOriginTransaction.assertInventoryReleaseReady(origins,inv,id,port,false);
                else assertThrows(IOException.class,()->JailOriginTransaction.assertInventoryReleaseReady(origins,inv,id,port,false));
                assertNotNull(origins.read(id).snapshot());
            }
        }
    }
    @Test void callbackFailureMutationAndSaveFailuresHoldWithoutAutomaticUndo()throws Exception {
        for(String fault:new String[]{"throw","mutate","saveBefore","saveAfter"}) {
            Path o=temp.resolve("o"+fault),i=temp.resolve("i"+fault);JailEscrowJournal.provisionNew(o);JailEscrowJournal.provisionNew(i);Port p=new Port();UUID id=UUID.randomUUID();
            try(var inv=new JailEscrowJournal(i);var origin=new JailEscrowJournal(o)) {
                held(inv,id,p.owner());p.callbackFail=fault.equals("throw");p.callbackMutate=fault.equals("mutate");
                assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origin,inv,id,p,at->{if(at==JailOriginTransaction.Boundary.BEFORE_SAVE){p.failBeforeSave=fault.equals("saveBefore");p.failAfterSave=fault.equals("saveAfter");}}));
                assertEquals(JailEscrowJournal.State.REMOVAL_STARTED,origin.read(id).state());int changes=p.changes;
                assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origin,inv,id,p,at->{}));assertEquals(changes,p.changes);
                assertThrows(IOException.class,()->JailOriginTransaction.assertInventoryReleaseReady(origin,inv,id,p,false));
                if(fault.equals("mutate"))assertEquals(99,p.live.state()[1]);
            }
        }
    }
    @Test void offlineConflictingClaimsAndCustodyMismatchRefuseBeforeMutation()throws Exception {
        for(String failure:new String[]{"offline","conflict","custody"}) {
            Path o=temp.resolve("o"+failure),i=temp.resolve("i"+failure);JailEscrowJournal.provisionNew(o);JailEscrowJournal.provisionNew(i);Port p=new Port();UUID id=UUID.randomUUID();
            try(var inv=new JailEscrowJournal(i);var origin=new JailEscrowJournal(o)) {
                held(inv,id,p.owner());p.online=!failure.equals("offline");p.conflict=failure.equals("conflict");p.custodyValid=!failure.equals("custody");
                assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origin,inv,id,p,at->{}));assertEquals(0,p.changes);assertTrue(origin.list().isEmpty());
            }
        }
    }
    @Test void aLaterClaimConflictNeverStealsOrRestoresOriginAutomatically()throws Exception {
        Path o=temp.resolve("origins"),i=temp.resolve("inventory");JailEscrowJournal.provisionNew(o);JailEscrowJournal.provisionNew(i);Port p=new Port();UUID id=UUID.randomUUID();
        try(var inv=new JailEscrowJournal(i);var origin=new JailEscrowJournal(o)) {
            held(inv,id,p.owner());JailOriginTransaction.temporaryForDisposableTest(origin,inv,id,p,at->{});p.conflict=true;byte[] current=p.live.state();
            assertThrows(IOException.class,()->JailOriginTransaction.originalForDisposableTest(origin,inv,id,p,at->{}));assertEquals(1,p.changes);assertArrayEquals(current,p.live.state());
            assertEquals(JailEscrowJournal.State.HELD,origin.read(id).state());assertEquals(JailOriginTransaction.Inspection.REVIEW_REQUIRED,JailOriginTransaction.inspect(origin,inv,id,p));
        }
    }
    @Test void anotherCustodySnapshotWithTheSameIdentityCannotAuthorizeRelease()throws Exception {
        Path o=temp.resolve("origins"),i=temp.resolve("items"),substitute=temp.resolve("substitute");
        JailEscrowJournal.provisionNew(o);JailEscrowJournal.provisionNew(i);JailEscrowJournal.provisionNew(substitute);Port p=new Port();UUID id=UUID.randomUUID();
        try(var origins=new JailEscrowJournal(o);var inventory=new JailEscrowJournal(i);var wrong=new JailEscrowJournal(substitute)){
            held(inventory,id,p.owner());JailOriginTransaction.temporaryForDisposableTest(origins,inventory,id,p,at->{});
            wrong.begin(id,p.owner(),new byte[]{77,88});wrong.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);wrong.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
            int callbacks=p.changes;assertThrows(IOException.class,()->JailOriginTransaction.originalForDisposableTest(origins,wrong,id,p,at->{}));
            assertEquals(callbacks,p.changes);assertEquals(JailOriginTransaction.Inspection.REVIEW_REQUIRED,JailOriginTransaction.inspect(origins,wrong,id,p));
            assertEquals(JailEscrowJournal.State.HELD,origins.read(id).state());
        }
    }
}
