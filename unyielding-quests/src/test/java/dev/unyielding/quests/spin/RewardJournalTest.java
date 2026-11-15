package dev.unyielding.quests.spin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class RewardJournalTest {
    @TempDir Path dir;
    @Test void failsBeforeCreationIfParentIsUnavailable() throws Exception {
        Path missing = dir.resolve("missing").resolve("journal");
        assertThrows(IOException.class, () -> new RewardJournal(missing));
        assertFalse(Files.exists(missing));
        try (RewardJournal j = new RewardJournal(dir)) { assertTrue(j.list().isEmpty()); }
    }
    @Test void recordsPrizeBeforeDeliveryAndSurvivesRestartWithoutReplay() throws Exception {
        UUID tx=UUID.randomUUID(), player=UUID.randomUUID(); byte[] prize={1,2,3,4};
        try (RewardJournal journal=new RewardJournal(dir)) {
            journal.begin(tx,player,2,prize);
            prize[0]=9;
            assertArrayEquals(new byte[]{1,2,3,4},journal.read(tx).payload());
        }
        try (RewardJournal journal=new RewardJournal(dir)) {
            assertEquals(RewardJournal.State.PREPARED,journal.read(tx).state());
            journal.transition(tx,RewardJournal.State.PREPARED,RewardJournal.State.DEBIT_APPLIED);
            journal.transition(tx,RewardJournal.State.DEBIT_APPLIED,RewardJournal.State.DELIVERING);
        }
        try (RewardJournal journal=new RewardJournal(dir)) {
            assertEquals(RewardJournal.State.DELIVERING,journal.read(tx).state());
            journal.transition(tx,RewardJournal.State.DELIVERING,RewardJournal.State.HELD);
            assertThrows(IOException.class,()->journal.transition(tx,RewardJournal.State.HELD,RewardJournal.State.DELIVERING));
            journal.transition(tx,RewardJournal.State.HELD,RewardJournal.State.COMPLETE);
            assertThrows(IOException.class,()->journal.transition(tx,RewardJournal.State.HELD,RewardJournal.State.COMPLETE));
        }
        try (RewardJournal journal=new RewardJournal(dir)) { assertEquals(RewardJournal.State.COMPLETE,journal.read(tx).state()); }
    }
    @Test void rejectsTruncatedAndDamagedRecordsRatherThanLosingACommittedDebit() throws Exception {
        UUID tx=UUID.randomUUID();
        try (RewardJournal j=new RewardJournal(dir)) { j.begin(tx,UUID.randomUUID(),3,new byte[]{7}); }
        Path file=dir.resolve("reward-journal.v1"); byte[] original=Files.readAllBytes(file);
        Files.write(file,java.util.Arrays.copyOf(original,original.length-1));
        assertThrows(IOException.class,()->new RewardJournal(dir));
        original[original.length-1]^=1; Files.write(file,original);
        assertThrows(IOException.class,()->new RewardJournal(dir));
    }
    @Test void rejectsDuplicatesAndIllegalTransitionsBeforeDelivery() throws Exception {
        UUID tx=UUID.randomUUID(), player=UUID.randomUUID();
        try (RewardJournal j=new RewardJournal(dir)) {
            assertThrows(IOException.class,()->j.begin(tx,player,1,new byte[]{1}));
            assertNull(j.read(tx));
            j.begin(tx,player,2,new byte[]{1});
            assertThrows(IOException.class,()->j.begin(tx,player,2,new byte[]{1}));
            assertThrows(IOException.class,()->j.transition(tx,RewardJournal.State.PREPARED,RewardJournal.State.DELIVERING));
            assertEquals(RewardJournal.State.PREPARED,j.read(tx).state());
        }
    }
    @Test void restartsAtEveryDurableBoundaryWithoutAutomaticDelivery() throws Exception {
        UUID beforeDebit=UUID.randomUUID(), afterDebit=UUID.randomUUID(), afterMutation=UUID.randomUUID(), player=UUID.randomUUID();
        try (RewardJournal j=new RewardJournal(dir)) {
            j.begin(beforeDebit,player,2,new byte[]{1});
            j.begin(afterDebit,player,3,new byte[]{2});
            j.transition(afterDebit,RewardJournal.State.PREPARED,RewardJournal.State.DEBIT_APPLIED);
            j.begin(afterMutation,player,2,new byte[]{3});
            j.transition(afterMutation,RewardJournal.State.PREPARED,RewardJournal.State.DEBIT_APPLIED);
            j.transition(afterMutation,RewardJournal.State.DEBIT_APPLIED,RewardJournal.State.DELIVERING);
        }
        try (RewardJournal j=new RewardJournal(dir)) {
            assertEquals(RewardJournal.State.PREPARED,j.read(beforeDebit).state());
            assertEquals(RewardJournal.State.DEBIT_APPLIED,j.read(afterDebit).state());
            assertEquals(RewardJournal.State.DELIVERING,j.read(afterMutation).state());
            assertEquals(3,j.list().size());
        }
    }
}
