package dev.unyielding.quests.jail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical physical slot identities; compatibility aliases never add a second item source. */
public final class AccessoryCustodyPolicy {
    private AccessoryCustodyPolicy() {}
    public record Size(int normal, int cosmetic) {}
    public record Slot(String name, boolean cosmetic, int index) {}
    public static List<Slot> slots(Map<String, Object> canonical, Map<String, Object> aliases, Map<String, Size> sizes) throws IOException {
        if (canonical.size() > 256 || !AccessoryBackingCoverage.covers(canonical, aliases) || !canonical.keySet().equals(sizes.keySet()))
            throw new IOException("Unknown accessory backing or layout");
        List<Slot> result = new ArrayList<>();
        for (var entry : new TreeMap<>(sizes).entrySet()) {
            String name = entry.getKey(); Size size = entry.getValue();
            if (name.isBlank() || name.length() > 128 || size == null || size.normal() < 0 || size.normal() != size.cosmetic()
                    || result.size() + 2L * size.normal() > 1024) throw new IOException("Unsupported accessory slot sizes");
            for (int index = 0; index < size.normal(); index++) {
                result.add(new Slot(name, false, index)); result.add(new Slot(name, true, index));
            }
        }
        return List.copyOf(result);
    }
}
