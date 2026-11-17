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
}
