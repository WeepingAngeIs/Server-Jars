package dev.unyielding.jail;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Fault coverage uses the same non-Minecraft origin fixture as the existing origin transaction tests. */
class JailRetainedRecoveryTest {
    @TempDir Path temp;
    private void provision(String... names)throws Exception{for(String name:names)JailEscrowJournal.provisionNew(temp.resolve(name));}
    private static void hold(JailEscrowJournal journal,UUID id,UUID owner)throws Exception{
        journal.begin(id,owner,new byte[]{11,22,33});journal.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);journal.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
    }
    @Test void unchangedReservationsCanBeClosedWithoutNativeWritesAtEveryParentBoundary()throws Exception{
        for(var state:new JailEscrowJournal.State[]{JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED}){
            provision(state.name());var port=new JailOriginTransactionTest.Port();UUID id=UUID.randomUUID();
            try(var confinement=new JailEscrowJournal(temp.resolve(state.name()))){
                confinement.begin(id,port.owner(),new byte[]{11,22,33});
                if(state!=JailEscrowJournal.State.PREPARED)confinement.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
                if(state==JailEscrowJournal.State.HELD||state==JailEscrowJournal.State.RESTORE_STARTED)confinement.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
                if(state==JailEscrowJournal.State.RESTORE_STARTED)confinement.transition(id,JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
                JailRetainedRecovery.unchanged(confinement,null,id,port,JailOriginTransaction.hash(port.current().state()),()->{});
                assertTrue(JailEscrowJournal.terminal(confinement.read(id).state()));assertEquals(0,port.changes);assertEquals(0,port.saves);
                assertThrows(IOException.class,()->JailRetainedRecovery.unchanged(confinement,null,id,port,JailOriginTransaction.hash(port.current().state()),()->{}));
                assertEquals(0,port.changes);assertEquals(0,port.saves);
            }
        }
    }
    @Test void independentRefusalOrChangedSaveNeverClosesReservation()throws Exception{
        provision("independent","save");
        for(String mode:new String[]{"independent","save"}){
            var port=new JailOriginTransactionTest.Port();UUID id=UUID.randomUUID();
            try(var confinement=new JailEscrowJournal(temp.resolve(mode))){
                hold(confinement,id,port.owner());byte[] before=JailOriginTransaction.hash(port.current().state());
                if(mode.equals("save"))port.disk=new JailOriginTransaction.View(new byte[]{1,99},null,JailOriginTransaction.Receipt.NONE);
                assertThrows(IOException.class,()->JailRetainedRecovery.unchanged(confinement,null,id,port,before,()->{if(mode.equals("independent"))throw new IOException("assignment appeared");}));
                assertEquals(JailEscrowJournal.State.HELD,confinement.read(id).state());assertEquals(0,port.changes);assertEquals(0,port.saves);
            }
        }
    }
    @Test void begunButUnmutatedOriginIsNotAssumedSafeToCancelOrReplay()throws Exception{
        provision("uncertain-parent","uncertain-origin");var port=new JailOriginTransactionTest.Port();UUID id=UUID.randomUUID();
        try(var confinement=new JailEscrowJournal(temp.resolve("uncertain-parent"));var origins=new JailEscrowJournal(temp.resolve("uncertain-origin"))){
            hold(confinement,id,port.owner());byte[] before=JailOriginTransaction.hash(port.current().state());
            assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origins,confinement,id,port,at->{if(at==JailOriginTransaction.Boundary.STARTED)throw new IOException("interrupted");}));
            assertThrows(IOException.class,()->JailRetainedRecovery.unchanged(confinement,origins,id,port,before,()->{}));
            assertThrows(IOException.class,()->JailRetainedRecovery.original(origins,confinement,id,port,at->{}));
            assertEquals(0,port.changes);assertEquals(JailEscrowJournal.State.REMOVAL_STARTED,origins.read(id).state());
        }
    }
    @Test void savedTemporaryOriginWithoutAssignmentCanBeConfirmedAndReturnedOnce()throws Exception{
        provision("partial-parent","partial-origin");var port=new JailOriginTransactionTest.Port();UUID id=UUID.randomUUID();
        try(var confinement=new JailEscrowJournal(temp.resolve("partial-parent"));var origins=new JailEscrowJournal(temp.resolve("partial-origin"))){
            hold(confinement,id,port.owner());
            assertThrows(IOException.class,()->JailOriginTransaction.temporaryForDisposableTest(origins,confinement,id,port,at->{if(at==JailOriginTransaction.Boundary.PLAYER_AND_CLAIMS_DURABLE)throw new IOException("assignment not reached");}));
            assertEquals(1,port.changes);JailRetainedRecovery.original(origins,confinement,id,port,at->{});
            assertEquals(2,port.changes);assertEquals(JailEscrowJournal.State.COMPLETE,origins.read(id).state());
            JailRetainedRecovery.original(origins,confinement,id,port,at->fail("callback replay"));assertEquals(2,port.changes);
        }
    }
    @Test void interruptedSavedReturnConfirmsOnlyPhaseAndNeverRepeatsNativeReturn()throws Exception{
        provision("returned-parent","returned-origin");var port=new JailOriginTransactionTest.Port();UUID id=UUID.randomUUID();
        try(var confinement=new JailEscrowJournal(temp.resolve("returned-parent"));var origins=new JailEscrowJournal(temp.resolve("returned-origin"))){
            hold(confinement,id,port.owner());JailOriginTransaction.temporaryForDisposableTest(origins,confinement,id,port,at->{});
            assertThrows(IOException.class,()->JailOriginTransaction.originalForDisposableTest(origins,confinement,id,port,at->{if(at==JailOriginTransaction.Boundary.PLAYER_AND_CLAIMS_DURABLE)throw new IOException("commit lost");}));
            assertEquals(2,port.changes);JailRetainedRecovery.original(origins,confinement,id,port,at->{});
            assertEquals(2,port.changes);assertEquals(JailEscrowJournal.State.COMPLETE,origins.read(id).state());
            confinement.transition(id,JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
            JailRetainedRecovery.original(origins,confinement,id,port,at->fail("callback replay"));assertEquals(2,port.changes);
        }
    }
}
