package dev.unyielding.quests.jail;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Pure identity-coverage check for translated views of an accessory store. */
final class AccessoryBackingCoverage {
    private AccessoryBackingCoverage() {}
    static boolean covers(Map<String, Object> sources, Map<String, Object> aliases) {
        if (sources == null || aliases == null) return false;
        Set<Object> known = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : sources.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !known.add(entry.getValue())) return false;
        }
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : aliases.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !known.contains(entry.getValue())) return false;
            seen.add(entry.getValue());
        }
        return seen.size() == known.size();
    }
}
