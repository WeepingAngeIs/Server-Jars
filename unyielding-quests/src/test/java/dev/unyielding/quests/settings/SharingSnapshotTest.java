package dev.unyielding.quests.settings;

import dev.unyielding.quests.quest.ProgressSharing;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SharingSnapshotTest {
    @Test void capturedValueWinsDuringInterleavingAndNextObservationRepairsIt() {
        UUID player=UUID.randomUUID(), team=UUID.randomUUID();
        Map<UUID,UUID> members=Map.of(player,team);
        Map<UUID,Set<Integer>> records=new HashMap<>();
        records.put(player,new HashSet<>(Set.of(1))); records.put(team,new HashSet<>(Set.of(2)));
        Set<Integer> personalClaims=new HashSet<>(Set.of(77));
        AtomicBoolean config=new AtomicBoolean(true), applied=new AtomicBoolean(false);
        boolean captured=config.get();
        config.set(false); // config reload races after observation
        SharingSnapshot.apply(captured, desired -> {
            if (desired != applied.get()) {
                if (desired) ProgressSharing.enableShared(members,records,HashSet::new,Set::addAll);
                else ProgressSharing.preservePersonal(members,records,HashSet::new,Set::addAll);
                applied.set(desired);
            }
        });
        assertTrue(applied.get()); assertEquals(Set.of(1,2),records.get(team));
        SharingSnapshot.apply(config.get(), desired -> {
            if (desired != applied.get()) {
                if (desired) ProgressSharing.enableShared(members,records,HashSet::new,Set::addAll);
                else ProgressSharing.preservePersonal(members,records,HashSet::new,Set::addAll);
                applied.set(desired);
            }
        });
        assertFalse(applied.get()); assertEquals(Set.of(1,2),records.get(player));
        assertEquals(Set.of(77),personalClaims);
    }
    @Test void rapidTogglesAndEqualObservedValueReconcileAppliedState() {
        AtomicBoolean applied=new AtomicBoolean(false);
        boolean[] captured={true,false,true,true};
        List<Boolean> transitions=new ArrayList<>();
        for (boolean desired:captured) SharingSnapshot.apply(desired, value -> {
            if (value != applied.get()) { applied.set(value); transitions.add(value); }
        });
        assertEquals(List.of(true,false,true),transitions);
        assertTrue(applied.get());
        applied.set(false); // observed configuration is still true but application had lagged
        List<Boolean> observed=List.of(true), snapshot=List.of(true);
        assertTrue(SharingSnapshot.needsPublish(observed, snapshot, 1.0, 1.0, applied.get() == snapshot.get(0)));
        SharingSnapshot.apply(snapshot.get(0), applied::set);
        assertTrue(applied.get());
        assertFalse(SharingSnapshot.needsPublish(observed, snapshot, 1.0, 1.0, applied.get() == snapshot.get(0)));
    }
}
