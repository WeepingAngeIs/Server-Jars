package dev.unyielding.jail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

class JailMigrationIOTest {
 @TempDir Path root;
 private Path history()throws Exception {Path p=root.resolve("history");JailEscrowJournal.provisionNew(p);return p;}
 @Test void validHeldReplaysWithoutChangingBytes()throws Exception {Path p=history();UUID tx=UUID.randomUUID(),owner=UUID.randomUUID();try(var j=new JailEscrowJournal(p)){j.begin(tx,owner,new byte[]{1,2,3});j.transition(tx,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);j.transition(tx,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);}byte[] before=Files.readAllBytes(p.resolve("escrow.v1"));var e=JailMigrationIO.entries(before).getFirst();assertEquals(tx,e.id());assertEquals(owner,e.owner());assertEquals(JailEscrowJournal.State.HELD,e.state());assertArrayEquals(before,Files.readAllBytes(p.resolve("escrow.v1")));}
 @Test void readOnlyReplayDoesNotAcquireSecondWriterLock()throws Exception {Path p=history();try(var j=new JailEscrowJournal(p)){j.begin(UUID.randomUUID(),UUID.randomUUID(),new byte[]{1});assertEquals(1,JailMigrationIO.entries(p.resolve("escrow.v1")).size());}}
 @Test void truncationRefuses()throws Exception {Path p=history();try(var j=new JailEscrowJournal(p)){j.begin(UUID.randomUUID(),UUID.randomUUID(),new byte[]{1});}byte[] b=Files.readAllBytes(p.resolve("escrow.v1"));for(int n=0;n<b.length;n++){if(n==8)continue;final int size=n;assertThrows(IOException.class,()->JailMigrationIO.entries(Arrays.copyOf(b,size)));}}
 @Test void tamperedFrameRefuses()throws Exception {Path p=history();try(var j=new JailEscrowJournal(p)){j.begin(UUID.randomUUID(),UUID.randomUUID(),new byte[]{1});}byte[] b=Files.readAllBytes(p.resolve("escrow.v1"));b[b.length-1]^=1;assertThrows(IOException.class,()->JailMigrationIO.entries(b));}
 @Test void replayingInitialFrameRefuses()throws Exception {Path p=history();try(var j=new JailEscrowJournal(p)){j.begin(UUID.randomUUID(),UUID.randomUUID(),new byte[]{1});}byte[] b=Files.readAllBytes(p.resolve("escrow.v1"));var out=new ByteArrayOutputStream();out.write(b);out.write(b,8,b.length-8);assertThrows(IOException.class,()->JailMigrationIO.entries(out.toByteArray()));}
 @Test void unknownVersionRefuses()throws Exception {byte[] b={0x4a,0x45,0x53,0x31,0,0,0,2};assertThrows(IOException.class,()->JailMigrationIO.entries(b));}
 @Test void incompleteTransitionRemainsIncomplete()throws Exception {Path p=history();UUID id=UUID.randomUUID(),owner=UUID.randomUUID();try(var j=new JailEscrowJournal(p)){j.begin(id,owner,new byte[]{9});j.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);}assertEquals(JailEscrowJournal.State.REMOVAL_STARTED,JailMigrationIO.entries(p.resolve("escrow.v1")).getFirst().state());}
 @Test void terminalStateReplaysButCannotGrantAgain()throws Exception {Path p=history();UUID id=UUID.randomUUID(),owner=UUID.randomUUID();try(var j=new JailEscrowJournal(p)){j.begin(id,owner,new byte[]{9});j.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);}assertEquals(JailEscrowJournal.State.CANCELLED,JailMigrationIO.entries(p.resolve("escrow.v1")).getFirst().state());}
 @Test void backupCreationNeverOverwrites()throws Exception {Path p=root.resolve("copy");JailMigrationIO.create(p,new byte[]{1});assertThrows(FileAlreadyExistsException.class,()->JailMigrationIO.create(p,new byte[]{2}));assertArrayEquals(new byte[]{1},Files.readAllBytes(p));}
 @Test void safeFileAccepts()throws Exception {Files.write(root.resolve("data"),new byte[]{1});assertEquals(root.resolve("data").toRealPath(),JailMigrationIO.regular(root,"data"));}
 @Test void traversalRefuses()throws Exception {Files.write(root.resolve("data"),new byte[]{1});assertThrows(IOException.class,()->JailMigrationIO.regular(root,"../data"));assertThrows(IOException.class,()->JailMigrationIO.regular(root,"./data"));}
 @Test void symbolicFileRefuses()throws Exception {Files.write(root.resolve("data"),new byte[]{1});Files.createSymbolicLink(root.resolve("link"),root.resolve("data"));assertThrows(IOException.class,()->JailMigrationIO.regular(root,"link"));}
 @Test void linkedFileRefuses()throws Exception {Files.write(root.resolve("data"),new byte[]{1});Files.createLink(root.resolve("link"),root.resolve("data"));assertThrows(IOException.class,()->JailMigrationIO.regular(root,"data"));}
 @Test void exactArtifactPin() {assertEquals(128,JailRuntimeMigration.OLD_SHA512.length());assertTrue(JailRuntimeMigration.OLD_SHA512.startsWith("0c992e69"));}
 @Test void eitherReviewedPredecessorBindsToItsOwnArchive() throws IOException {
     String target = "5".repeat(128);
     for (String predecessor : List.of(JailRuntimeMigration.OLD_SHA512, JailRuntimeMigration.ATTACHED_SHA512)) {
         JailRuntimeMigration.verifyArtifactPair(predecessor, target, predecessor, target);
     }
 }
 @Test void anotherApprovedArchiveCannotStandInForTheRequestedOne() {
     String target = "5".repeat(128);
     assertThrows(IOException.class, () -> JailRuntimeMigration.verifyArtifactPair(
             JailRuntimeMigration.ATTACHED_SHA512, target, JailRuntimeMigration.OLD_SHA512, target));
 }
 @Test void alteredOrUnknownPredecessorCannotAuthorizeHeldCustody() {
     String unknown = "1" + JailRuntimeMigration.ATTACHED_SHA512.substring(1);
     String target = "5".repeat(128);
     assertThrows(IOException.class, () -> JailRuntimeMigration.verifyArtifactPair(unknown, target, unknown, target));
 }
 @Test void replayAgainstDifferentTargetOrSelfMigrationRefused() {
     String predecessor = JailRuntimeMigration.ATTACHED_SHA512;
     assertThrows(IOException.class, () -> JailRuntimeMigration.verifyArtifactPair(
             predecessor, "5".repeat(128), predecessor, "6".repeat(128)));
     assertThrows(IOException.class, () -> JailRuntimeMigration.verifyArtifactPair(
             predecessor, predecessor, predecessor, predecessor));
 }
 @Test void onlyNativeQuarantineAppendAccepted()throws Exception {
  Path dir=root.resolve("custody");JailCustodyLedger.provisionNew(dir);UUID tx=UUID.randomUUID(),owner=UUID.randomUUID();
  try(var ledger=new JailCustodyLedger(dir)){
   var first=ledger.bind(tx,owner,"a/items","a/origins",new byte[32],new JailCustodyLedger.Anchor("minecraft:overworld",1,2,3),"console");
   byte[] before=Files.readAllBytes(dir.resolve("custody.v1"));
   JailMigrationIO.quarantineExtension(before,before,tx);
   var next=ledger.transition(tx,first.revision(),JailCustodyLedger.State.QUARANTINED,"server","Interface block or chunk unavailable; escrow retained");
   byte[] after=Files.readAllBytes(dir.resolve("custody.v1"));JailMigrationIO.quarantineExtension(before,after,tx);
   byte[] bad=after.clone();bad[bad.length-1]^=1;assertThrows(IOException.class,()->JailMigrationIO.quarantineExtension(before,bad,tx));
   assertThrows(IOException.class,()->JailMigrationIO.quarantineExtension(before,after,UUID.randomUUID()));
   ledger.transition(tx,next.revision(),JailCustodyLedger.State.RELEASED,"server","Interface block or chunk unavailable; escrow retained");
   assertThrows(IOException.class,()->JailMigrationIO.quarantineExtension(before,Files.readAllBytes(dir.resolve("custody.v1")),tx));
  }
 }
 @Test void administratorAndRelocationAppendRefused()throws Exception {
  Path dir=root.resolve("custody");JailCustodyLedger.provisionNew(dir);UUID tx=UUID.randomUUID(),owner=UUID.randomUUID();
  try(var ledger=new JailCustodyLedger(dir)){
   var first=ledger.bind(tx,owner,"a/items","a/origins",new byte[32],new JailCustodyLedger.Anchor("minecraft:overworld",1,2,3),"console");
   byte[] before=Files.readAllBytes(dir.resolve("custody.v1"));
   var next=ledger.transition(tx,first.revision(),JailCustodyLedger.State.QUARANTINED,"console","Explicit administrator hold; review required");
   assertThrows(IOException.class,()->JailMigrationIO.quarantineExtension(before,Files.readAllBytes(dir.resolve("custody.v1")),tx));
   byte[] held=Files.readAllBytes(dir.resolve("custody.v1"));ledger.relocate(tx,next.revision(),new JailCustodyLedger.Anchor("minecraft:overworld",4,2,3),"server");
   assertThrows(IOException.class,()->JailMigrationIO.quarantineExtension(held,Files.readAllBytes(dir.resolve("custody.v1")),tx));
  }
 }
}
