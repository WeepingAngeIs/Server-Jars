package dev.unyielding.quests.quest;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProgressSharingTest {
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID NEWCOMER = new UUID(0, 3);
    private static final UUID PARTY = new UUID(0, 100);
    private static final UUID OTHER_PARTY = new UUID(0, 101);

    private static final class State {
        final Set<Long> complete = new HashSet<>();
        final Map<Long, Long> counts = new HashMap<>();

        void merge(State source) {
            complete.addAll(source.complete);
            source.counts.forEach((task, count) -> counts.merge(task, count, Math::max));
        }
    }

    @Test
    void joiningAndLeavingCompletedPartiesCannotChangePersonalProgressWhenSharingIsOff() {
        Map<UUID, State> records = new HashMap<>();
        State personal = new State();
        personal.counts.put(10L, 4L);
        records.put(NEWCOMER, personal);
        State shared = new State();
        shared.complete.addAll(Set.of(10L, 20L, 30L));
        records.put(PARTY, shared);
        records.put(OTHER_PARTY, shared);

        for (UUID membership : new UUID[]{PARTY, NEWCOMER, OTHER_PARTY, NEWCOMER}) {
            State active = records.get(ProgressSharing.owner(NEWCOMER, membership, false));
            assertSame(personal, active);
            assertTrue(active.complete.isEmpty());
            assertEquals(4L, active.counts.get(10L));
        }
    }

    @Test
    void disablingSharingPreservesCurrentOfflineMembersButDoesNotSeedFutureMembers() {
        Map<UUID, State> records = new HashMap<>();
        State party = new State();
        party.complete.add(42L);
        party.counts.put(43L, 6L);
        records.put(PARTY, party);
        Map<UUID, UUID> membership = new HashMap<>(Map.of(ALICE, PARTY, BOB, PARTY));

        ProgressSharing.preservePersonal(membership, records, State::new, State::merge);
        membership.put(NEWCOMER, PARTY);
        State newcomer = records.computeIfAbsent(ProgressSharing.owner(NEWCOMER, PARTY, false), ignored -> new State());

        assertEquals(Set.of(42L), records.get(ALICE).complete);
        assertEquals(Set.of(42L), records.get(BOB).complete);
        assertTrue(newcomer.complete.isEmpty());
        assertTrue(newcomer.counts.isEmpty());
        assertNotSame(party, records.get(ALICE));
        records.get(ALICE).complete.add(99L);
        assertFalse(records.get(BOB).complete.contains(99L));
        assertFalse(party.complete.contains(99L));
    }

    @Test
    void enablingSharingMergesPersonalAndExistingPartyProgressWithoutDoubleCounting() {
        Map<UUID, State> records = new HashMap<>();
        State alice = new State();
        alice.complete.add(10L);
        alice.counts.put(20L, 3L);
        State bob = new State();
        bob.complete.add(11L);
        bob.counts.put(20L, 7L);
        State party = new State();
        party.complete.add(12L);
        records.put(ALICE, alice);
        records.put(BOB, bob);
        records.put(PARTY, party);
        Map<UUID, UUID> membership = Map.of(ALICE, PARTY, BOB, PARTY);

        ProgressSharing.enableShared(membership, records, State::new, State::merge);
        ProgressSharing.enableShared(membership, records, State::new, State::merge);

        assertEquals(PARTY, ProgressSharing.owner(ALICE, PARTY, true));
        assertEquals(Set.of(10L, 11L, 12L), party.complete);
        assertEquals(7L, party.counts.get(20L));
        assertEquals(Set.of(10L), alice.complete);
        assertEquals(Set.of(11L), bob.complete);
    }

    @Test
    void repeatedTransitionsPreserveProgressWithoutSharingMutableRecords() {
        Map<UUID, State> records = new HashMap<>();
        records.put(ALICE, new State());
        records.get(ALICE).counts.put(10L, 5L);
        Map<UUID, UUID> membership = Map.of(ALICE, PARTY, BOB, PARTY);

        ProgressSharing.enableShared(membership, records, State::new, State::merge);
        records.get(PARTY).complete.add(10L);
        ProgressSharing.preservePersonal(membership, records, State::new, State::merge);
        records.get(BOB).counts.put(20L, 8L);
        ProgressSharing.enableShared(membership, records, State::new, State::merge);
        ProgressSharing.preservePersonal(membership, records, State::new, State::merge);

        assertEquals(Set.of(10L), records.get(ALICE).complete);
        assertEquals(Set.of(10L), records.get(BOB).complete);
        assertEquals(8L, records.get(ALICE).counts.get(20L));
        assertNotSame(records.get(ALICE), records.get(BOB));
        assertNotSame(records.get(ALICE), records.get(PARTY));
    }

    @Test
    void soloPlayersAndMissingLegacyPartyRecordsRemainUntouched() {
        Map<UUID, State> records = new HashMap<>();
        State solo = new State();
        solo.complete.add(1L);
        records.put(ALICE, solo);
        ProgressSharing.preservePersonal(Map.of(ALICE, ALICE, BOB, PARTY), records, State::new, State::merge);
        assertSame(solo, records.get(ALICE));
        assertFalse(records.containsKey(BOB));
        assertEquals(Set.of(1L), solo.complete);
    }
}
