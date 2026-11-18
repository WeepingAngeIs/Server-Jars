package dev.unyielding.jail;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A derived view. Reservations live in the same durable plan as the custody transaction. */
final class JailCellRoster {
    record Occupant(UUID player, boolean assigned) {}
    private final int count;
    private final Map<Integer, Occupant> cells = new HashMap<>();

    JailCellRoster(int count) { this.count = count; }
    Occupant occupant(int number) { return cells.get(number); }

    void record(int number, UUID player, boolean assigned) throws IOException {
        if (number < 1 || number > count)
            throw new IOException("A custody record points outside this detention center. Review before admission");
        var current = cells.get(number);
        if (current != null && !current.player().equals(player))
            throw new IOException("Conflicting custody records for cell " + number);
        for (var entry : cells.entrySet()) {
            if (entry.getKey() != number && entry.getValue().player().equals(player))
                throw new IOException("Conflicting cell assignments for " + player);
        }
        cells.put(number, new Occupant(player, assigned || current != null && current.assigned()));
    }

    int choose(int requested) throws IOException {
        if (requested < 0 || requested > count) throw new IOException("Choose a cell from 1 through " + count);
        if (requested > 0) {
            if (cells.containsKey(requested)) throw new IOException("Cell " + requested + " is occupied or reserved");
            return requested;
        }
        for (int number = 1; number <= count; number++) if (!cells.containsKey(number)) return number;
        throw new IOException("Every cell is occupied or reserved");
    }
}
