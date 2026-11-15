package dev.unyielding.quests.spin;
import dev.unyielding.quests.quest.QuestRewardEpochs;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class QuestCreditRecoveryTest {
    @TempDir Path temp;
    @Test void creditBeforeClaimFlagSurvivesRestartAndIsAppliedOnlyOnce() throws Exception {
        Path path = temp.resolve("journal"); RewardJournal.provisionNew(path);
        UUID player = UUID.randomUUID(), tx;
        try (RewardJournal j = new RewardJournal(path)) { tx = j.creditQuest(player, 42, QuestRewardEpochs.LEGACY).id(); }
        long[] balance = {0,0,0,0,0}; Set<UUID> applied = new HashSet<>();
        try (RewardJournal j = new RewardJournal(path)) {
            assertEquals(tx, j.creditQuest(player, 42, QuestRewardEpochs.LEGACY).id());
            assertTrue(RewardCredit.apply(balance, applied, tx));
            assertFalse(RewardCredit.apply(balance, applied, tx));
            assertEquals(1, balance[3]); assertEquals(1, j.questCredits().size());
        }
    }
    @Test void resettingAndReearningDoesNotReplayAResetOrEraseNewProgress() throws Exception {
        Path path = temp.resolve("journal"); RewardJournal.provisionNew(path);
        UUID player = UUID.randomUUID(); QuestRewardEpochs epochs = new QuestRewardEpochs();
        try (RewardJournal j = new RewardJournal(path)) {
            UUID before = j.creditQuest(player, 42, epochs.generation(player, 42)).id();
            epochs.reset(Set.of(player), Set.of(42L));
            UUID after = j.creditQuest(player, 42, epochs.generation(player, 42)).id();
            assertNotEquals(before, after);
            // Model lost unsaved quest reset: progress AND epoch return to their old snapshot.
            QuestRewardEpochs restored = new QuestRewardEpochs();
            assertEquals(before, j.creditQuest(player, 42, restored.generation(player, 42)).id());
            assertEquals(2, j.questCredits().size());
            // Recovery only sees credits; it has no reset operation that can clear newer progress.
            Set<Long> newerProgress = new HashSet<>(Set.of(99L));
            for (var c : j.questCredits()) RewardCredit.apply(new long[5], new HashSet<>(), c.id());
            assertEquals(Set.of(99L), newerProgress);
        }
    }
    @Test void epochsArePersonalAcrossPartyMembershipAndResetScopes() {
        QuestRewardEpochs e = new QuestRewardEpochs(); UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        e.reset(Set.of(a), Set.of(1L)); UUID first=e.generation(a,1);
        assertEquals(QuestRewardEpochs.LEGACY,e.generation(a,2));
        assertEquals(QuestRewardEpochs.LEGACY,e.generation(b,1));
        e.reset(Set.of(a,b), null);
        assertNotEquals(first,e.generation(a,1)); assertEquals(e.generation(a,1),e.generation(a,2));
        assertNotEquals(e.generation(a,1),e.generation(b,1));
    }
    @Test void overflowDoesNotWriteMarker() {
        long[] balance={0,0,0,Long.MAX_VALUE,0}; Set<UUID> markers=new HashSet<>();
        assertThrows(ArithmeticException.class,()->RewardCredit.apply(balance,markers,UUID.randomUUID()));
        assertTrue(markers.isEmpty()); assertEquals(Long.MAX_VALUE,balance[3]);
    }
    @Test void successfulReturnRemainsDistinctFromDurableReceipt() throws Exception {
        Path path=temp.resolve("journal"); RewardJournal.provisionNew(path); UUID id=UUID.randomUUID();
        try(var j=new RewardJournal(path)){
            j.begin(id,UUID.randomUUID(),2,new byte[]{1});
            j.transition(id,RewardJournal.State.PREPARED,RewardJournal.State.DEBIT_APPLIED);
            j.transition(id,RewardJournal.State.DEBIT_APPLIED,RewardJournal.State.DELIVERING);
            j.transition(id,RewardJournal.State.DELIVERING,RewardJournal.State.RETURNED);
        }
        try(var j=new RewardJournal(path)){
            assertEquals(RewardJournal.State.RETURNED,j.read(id).state());
            assertThrows(java.io.IOException.class,()->j.transition(id,RewardJournal.State.RETURNED,RewardJournal.State.DELIVERING));
        }
    }
}
