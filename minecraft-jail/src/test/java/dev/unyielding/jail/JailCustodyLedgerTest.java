package dev.unyielding.jail;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JailCustodyLedgerTest {
    @TempDir Path temp;
    static final JailCustodyLedger.Anchor ANCHOR=new JailCustodyLedger.Anchor("minecraft:overworld",1000,5,1000);
    @Test void detentionLockerRebindsOnlyAfterReleaseAndRetainsBothHistories()throws Exception {
        Path path=temp.resolve("ledger");
        JailCustodyLedger.provisionNew(path);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID(),owner=UUID.randomUUID(),nextOwner=UUID.randomUUID();
        try(var ledger=new JailCustodyLedger(path)){
            var b=ledger.bindLocker(first,owner,"a/items","a/origins",new byte[32],ANCHOR,"admin");
            assertThrows(IOException.class,()->ledger.bindLocker(second,nextOwner,"b/items","b/origins",new byte[32],ANCHOR,"admin"));
            b=ledger.transition(first,b.revision(),JailCustodyLedger.State.RELEASED,"admin","Verified return");
            assertEquals(first,ledger.at(ANCHOR).id());
            ledger.bindLocker(second,nextOwner,"b/items","b/origins",new byte[32],ANCHOR,"admin");
            long revision=b.revision();
            assertThrows(IOException.class,()->ledger.transition(first,revision,JailCustodyLedger.State.QUARANTINED,"admin","Stale hold"));
        }
        try(var ledger=new JailCustodyLedger(path)){
            assertFalse(ledger.outstanding(owner));
            assertTrue(ledger.outstanding(nextOwner));
            assertEquals(2,ledger.audit(first).size());
            assertEquals(second,ledger.at(ANCHOR).id());
            assertEquals("a/items",ledger.get(first).items());
            assertEquals("b/items",ledger.get(second).items());
        }
    }
    @Test void durablePointersQuarantineAuditAndCompletionSurviveRestart()throws Exception {
        Path path=temp.resolve("ledger");JailCustodyLedger.provisionNew(path);UUID id=UUID.randomUUID(),owner=UUID.randomUUID();byte[] hash=JailOriginTransaction.hash(new byte[]{1,2,3});
        try(var ledger=new JailCustodyLedger(path)){var b=ledger.bind(id,owner,"old-custody/items","old-custody/origins",hash,ANCHOR,"console");assertEquals(JailCustodyLedger.State.QUARANTINED,b.state());assertTrue(ledger.outstanding(owner));hash[0]^=1;assertFalse(Arrays.equals(hash,b.digest()));b=ledger.transition(id,b.revision(),JailCustodyLedger.State.REVIEWED,"admin","Native proof verified");assertTrue(ledger.outstanding(owner));ledger.transition(id,b.revision(),JailCustodyLedger.State.RELEASED,"admin","Native journal completion verified");}
        try(var ledger=new JailCustodyLedger(path)){assertEquals(JailCustodyLedger.State.RELEASED,ledger.get(id).state());assertFalse(ledger.outstanding(owner));assertEquals(3,ledger.audit(id).size());assertEquals("old-custody/items",ledger.get(id).items());assertEquals(32,ledger.get(id).digest().length);}
    }
    @Test void staleMenusCrossPrisonerAnchorsAndRebindingCannotChangeCustodyIdentity()throws Exception {
        Path path=temp.resolve("ledger");JailCustodyLedger.provisionNew(path);UUID id=UUID.randomUUID(),owner=UUID.randomUUID();
        try(var ledger=new JailCustodyLedger(path)){var b=ledger.bind(id,owner,"a/items","a/origins",new byte[32],ANCHOR,"admin");var newAnchor=new JailCustodyLedger.Anchor("minecraft:overworld",0,5,0);var moved=ledger.relocate(id,b.revision(),newAnchor,"admin");assertThrows(IOException.class,()->ledger.transition(id,b.revision(),JailCustodyLedger.State.RELEASED,"stale","Must refuse"));assertEquals(owner,moved.owner());assertEquals(b.items(),moved.items());assertArrayEquals(b.digest(),moved.digest());assertEquals(JailCustodyLedger.State.QUARANTINED,moved.state());assertThrows(IOException.class,()->ledger.bind(UUID.randomUUID(),UUID.randomUUID(),"b/items","b/origins",new byte[32],newAnchor,"admin"));assertEquals(1,ledger.list().size());}
    }
    @Test void missingOrCorruptHistoryIsNeverReinitialized()throws Exception {
        Path path=temp.resolve("ledger");JailCustodyLedger.provisionNew(path);
        try(var ledger=new JailCustodyLedger(path)){ledger.bind(UUID.randomUUID(),UUID.randomUUID(),"a/items","a/origins",new byte[32],ANCHOR,"admin");}
        byte[] original=Files.readAllBytes(path.resolve("custody.v1"));byte[] damaged=original.clone();damaged[damaged.length-1]^=1;Files.write(path.resolve("custody.v1"),damaged);assertThrows(IOException.class,()->new JailCustodyLedger(path));assertThrows(IOException.class,()->JailCustodyLedger.provisionNew(path));
        Files.delete(path.resolve("custody.v1"));assertThrows(IOException.class,()->new JailCustodyLedger(path));assertThrows(IOException.class,()->JailCustodyLedger.provisionNew(path));assertFalse(Files.exists(path.resolve("custody.v1")));
    }
    @Test void truncatedFramesRefuseAndOriginalEscrowIsUntouched()throws Exception {
        Path originalEscrow=temp.resolve("source.opaque");Files.write(originalEscrow,new byte[]{7,8,9});Path path=temp.resolve("ledger");JailCustodyLedger.provisionNew(path);
        try(var ledger=new JailCustodyLedger(path)){ledger.bind(UUID.randomUUID(),UUID.randomUUID(),"a/items","a/origins",new byte[32],ANCHOR,"admin");}
        byte[] complete=Files.readAllBytes(path.resolve("custody.v1"));for(int cut:new int[]{0,3,7,9,complete.length-33,complete.length-1}){Files.write(path.resolve("custody.v1"),Arrays.copyOf(complete,cut));assertThrows(IOException.class,()->new JailCustodyLedger(path));assertArrayEquals(new byte[]{7,8,9},Files.readAllBytes(originalEscrow));}
    }
    @Test void independentOutstandingOwnersAndAuditInputs()throws Exception {
        Path path=temp.resolve("ledger");JailCustodyLedger.provisionNew(path);UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        try(var ledger=new JailCustodyLedger(path)){var first=ledger.bind(UUID.randomUUID(),a,"a/items","a/origins",new byte[32],ANCHOR,"admin");ledger.bind(UUID.randomUUID(),b,"b/items","b/origins",new byte[32],new JailCustodyLedger.Anchor("minecraft:overworld",1003,5,1000),"admin");ledger.transition(first.id(),first.revision(),JailCustodyLedger.State.RELEASED,"admin","Verified completion");assertFalse(ledger.outstanding(a));assertTrue(ledger.outstanding(b));assertThrows(IllegalArgumentException.class,()->ledger.bind(UUID.randomUUID(),a,"../outside","a/origins",new byte[32],ANCHOR,"admin"));assertThrows(IllegalArgumentException.class,()->ledger.transition(first.id(),ledger.get(first.id()).revision(),JailCustodyLedger.State.QUARANTINED,"admin\nforged","Bad audit"));}
    }
    @Test void damagedOpenLedgerCannotAuthorizeOrAppendARecovery()throws Exception {
        Path path=temp.resolve("ledger");JailCustodyLedger.provisionNew(path);
        try(var ledger=new JailCustodyLedger(path)){var b=ledger.bind(UUID.randomUUID(),UUID.randomUUID(),"a/items","a/origins",new byte[32],ANCHOR,"admin");byte[] bytes=Files.readAllBytes(path.resolve("custody.v1"));bytes[bytes.length-1]^=1;Files.write(path.resolve("custody.v1"),bytes);assertThrows(IOException.class,ledger::verifyDisk);assertFalse(ledger.healthy());assertThrows(IOException.class,()->ledger.transition(b.id(),b.revision(),JailCustodyLedger.State.RELEASED,"admin","Must not authorize"));assertEquals(JailCustodyLedger.State.QUARANTINED,ledger.get(b.id()).state());}
    }
}
