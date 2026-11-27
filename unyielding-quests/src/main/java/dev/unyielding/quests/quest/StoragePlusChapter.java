package dev.unyielding.quests.quest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Adds the bundled chapter to the selected book before it is sent to players. */
final class StoragePlusChapter {
    private StoragePlusChapter() {}

    static void append(QuestFile book, Path source, List<String> warnings) {
        QuestFile extra;
        try {
            extra = QuestFile.load(source, warnings);
        } catch (IOException | RuntimeException e) {
            warnings.add("Could not load the Anya Storage Plus chapter: " + e.getMessage());
            return;
        }
        if (extra.chapters.size() != 1) {
            warnings.add("Expected one Anya Storage Plus chapter in " + source);
            return;
        }
        QuestFile.Chapter chapter = extra.chapters.getFirst();
        // An operator's saved version owns its IDs, text and layout on subsequent loads.
        if (book.chapters.stream().anyMatch(c -> c.id == chapter.id || c.filename.equals(chapter.filename))) return;
        Set<Long> occupied = ids(book);
        if (ids(extra).stream().anyMatch(occupied::contains)) {
            warnings.add("Skipped Anya Storage Plus because a quest ID is already in use in this book.");
            return;
        }
        if (book.groups.stream().noneMatch(g -> g.id == chapter.group)) chapter.group = 0;
        book.chapters.add(chapter);
        book.reindex();
    }

    private static Set<Long> ids(QuestFile file) {
        Set<Long> ids = new HashSet<>(file.chapterMap.keySet());
        ids.addAll(file.quests.keySet());
        ids.addAll(file.tasks.keySet());
        ids.addAll(file.rewards.keySet());
        ids.addAll(file.spinQuests.keySet());
        ids.addAll(file.lootTables.keySet());
        file.groups.forEach(g -> ids.add(g.id));
        file.chapters.forEach(c -> c.images.forEach(i -> ids.add(QuestFile.parseId(i.id))));
        return ids;
    }
}
