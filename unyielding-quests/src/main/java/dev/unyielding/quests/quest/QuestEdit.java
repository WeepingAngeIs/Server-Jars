package dev.unyielding.quests.quest;

import dev.unyielding.quests.quest.QuestFile.Chapter;
import dev.unyielding.quests.quest.QuestFile.ChapterImage;
import dev.unyielding.quests.quest.QuestFile.Quest;
import dev.unyielding.quests.quest.QuestFile.Reward;
import dev.unyielding.quests.quest.QuestFile.Task;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * In-game editing: one operation = one small NBT tag ("op" + fields). The same code applies an operation on the
 * server (which then saves the book) and on every client (so the screen updates without re-sending the whole book).
 */
public final class QuestEdit {
    private QuestEdit() {
    }

    private static final Random RNG = new Random();

    public static long newId() {
        long id;
        do id = RNG.nextLong(); while (id == 0);
        return id;
    }

    /** A deep copy of a quest (for editing drafts). */
    public static Quest copy(Quest q) {
        return Quest.fromTag(q.toTag(), q.chapter);
    }

    // ---- builders (client side) -------------------------------------------

    private static CompoundTag op(String name) {
        CompoundTag t = new CompoundTag();
        t.putString("op", name);
        return t;
    }

    public static CompoundTag move(long quest, double x, double y) {
        CompoundTag t = op("move");
        t.putLong("q", quest);
        t.putDouble("x", x);
        t.putDouble("y", y);
        return t;
    }

    public static CompoundTag questAdd(long chapter, long id, double x, double y, long taskId) {
        CompoundTag t = op("quest_add");
        t.putLong("ch", chapter);
        t.putLong("q", id);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putLong("task", taskId);
        return t;
    }

    public static CompoundTag questDuplicate(long quest, long newId, double x, double y) {
        CompoundTag t = op("quest_dup");
        t.putLong("q", quest);
        t.putLong("id", newId);
        t.putDouble("x", x);
        t.putDouble("y", y);
        return t;
    }

    public static CompoundTag questDelete(long quest) {
        CompoundTag t = op("quest_del");
        t.putLong("q", quest);
        return t;
    }

    /** Replaces every editable field of a quest (title, tasks, rewards, ...) with the given quest. */
    public static CompoundTag questSet(Quest q) {
        CompoundTag t = op("quest_set");
        t.putLong("q", q.id);
        t.put("data", q.toTag());
        return t;
    }

    public static CompoundTag depAdd(long quest, long dep) {
        CompoundTag t = op("dep_add");
        t.putLong("q", quest);
        t.putLong("d", dep);
        return t;
    }

    public static CompoundTag depRemove(long quest, long dep) {
        CompoundTag t = op("dep_del");
        t.putLong("q", quest);
        t.putLong("d", dep);
        return t;
    }

    public static CompoundTag depClear(long quest) {
        CompoundTag t = op("dep_clear");
        t.putLong("q", quest);
        return t;
    }

    public static CompoundTag chapterAdd(long id, String title, long group, String icon) {
        CompoundTag t = op("chapter_add");
        t.putLong("ch", id);
        t.putString("title", title);
        t.putLong("group", group);
        t.putString("icon", icon);
        return t;
    }

    public static CompoundTag chapterSet(long id, String title, List<String> subtitle, long group, String icon) {
        CompoundTag t = op("chapter_set");
        t.putLong("ch", id);
        t.putString("title", title);
        t.put("sub", QuestFile.strList(subtitle));
        t.putLong("group", group);
        t.putString("icon", icon);
        return t;
    }

    public static CompoundTag chapterDelete(long id) {
        CompoundTag t = op("chapter_del");
        t.putLong("ch", id);
        return t;
    }

    /** dir -1 = up, +1 = down (within the whole list; the chapter takes the group of its new neighbour). */
    public static CompoundTag chapterMove(long id, int dir) {
        CompoundTag t = op("chapter_move");
        t.putLong("ch", id);
        t.putInt("dir", dir);
        return t;
    }

    public static CompoundTag labelAdd(long chapter, String text, double x, double y) {
        CompoundTag t = op("label_add");
        t.putLong("ch", chapter);
        t.putString("text", text);
        t.putDouble("x", x);
        t.putDouble("y", y);
        t.putString("id", QuestFile.idToString(newId()));
        return t;
    }

    public static CompoundTag labelSet(long chapter, int index, String text, double x, double y) {
        CompoundTag t = op("label_set");
        t.putLong("ch", chapter);
        t.putInt("i", index);
        t.putString("text", text);
        t.putDouble("x", x);
        t.putDouble("y", y);
        return t;
    }

    public static CompoundTag labelDelete(long chapter, int index) {
        CompoundTag t = op("label_del");
        t.putLong("ch", chapter);
        t.putInt("i", index);
        return t;
    }

    // ---- applying --------------------------------------------------------

    /** Applies the operation. Returns null when it worked, otherwise a short reason. */
    public static String apply(QuestFile f, CompoundTag t) {
        String op = t.getString("op");
        switch (op) {
            case "move" -> {
                Quest q = f.quests.get(t.getLong("q"));
                if (q == null) return "That quest no longer exists.";
                q.x = t.getDouble("x");
                q.y = t.getDouble("y");
                return null;
            }
            case "quest_add" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                if (c == null) return "That chapter no longer exists.";
                long id = t.getLong("q");
                if (id == 0 || f.quests.containsKey(id)) return "Id clash, try again.";
                Quest q = new Quest();
                q.id = id;
                q.chapter = c.id;
                q.x = t.getDouble("x");
                q.y = t.getDouble("y");
                q.shape = f.defaultShape;
                q.title = "New quest";
                Task k = new Task();
                k.id = t.getLong("task");
                k.quest = q.id;
                k.type = QuestFile.TaskType.CHECKMARK;
                k.rawType = "checkmark";
                q.tasks.add(k);
                c.quests.add(q);
                f.reindex();
                return null;
            }
            case "quest_dup" -> {
                Quest src = f.quests.get(t.getLong("q"));
                if (src == null) return "That quest no longer exists.";
                long id = t.getLong("id");
                if (id == 0 || f.quests.containsKey(id)) return "Id clash, try again.";
                Chapter c = f.chapterMap.get(src.chapter);
                Quest q = Quest.fromTag(src.toTag(), src.chapter);
                q.id = id;
                q.x = t.getDouble("x");
                q.y = t.getDouble("y");
                q.deps = new long[0];
                // tasks and rewards need their own ids (progress is stored per id)
                long seed = id;
                for (Task k : q.tasks) {
                    k.quest = id;
                    k.id = mix(seed, k.id);
                }
                for (Reward r : q.rewards) {
                    r.quest = id;
                    r.id = mix(seed, r.id);
                }
                c.quests.add(q);
                f.reindex();
                return null;
            }
            case "quest_del" -> {
                Quest q = f.quests.get(t.getLong("q"));
                if (q == null) return "That quest no longer exists.";
                Chapter c = f.chapterMap.get(q.chapter);
                if (c != null) c.quests.remove(q);
                stripDep(f, q.id);
                f.reindex();
                return null;
            }
            case "quest_set" -> {
                Quest q = f.quests.get(t.getLong("q"));
                if (q == null) return "That quest no longer exists.";
                Quest n = Quest.fromTag(t.getCompound("data"), q.chapter);
                q.x = n.x;
                q.y = n.y;
                q.size = n.size;
                q.iconScale = n.iconScale;
                q.shape = n.shape;
                q.icon = n.icon;
                q.title = n.title;
                q.subtitle = n.subtitle;
                q.desc = n.desc;
                q.deps = n.deps;
                q.optional = n.optional;
                q.hideDepLines = n.hideDepLines;
                q.hideDependentLines = n.hideDependentLines;
                q.hideUntilDeps = n.hideUntilDeps;
                q.tasks.clear();
                for (Task k : n.tasks) {
                    k.quest = q.id;
                    if (k.id == 0) k.id = newId();
                    q.tasks.add(k);
                }
                q.rewards.clear();
                for (Reward r : n.rewards) {
                    r.quest = q.id;
                    if (r.id == 0) r.id = newId();
                    q.rewards.add(r);
                }
                f.reindex();
                return null;
            }
            case "dep_add" -> {
                Quest q = f.quests.get(t.getLong("q"));
                Quest d = f.quests.get(t.getLong("d"));
                if (q == null || d == null) return "That quest no longer exists.";
                if (q == d) return "A quest cannot require itself.";
                if (requires(f, d, q.id, 0)) return "That would make a loop (the other quest already needs this one).";
                for (long x : q.deps) if (x == d.id) return null;
                long[] nd = new long[q.deps.length + 1];
                System.arraycopy(q.deps, 0, nd, 0, q.deps.length);
                nd[q.deps.length] = d.id;
                q.deps = nd;
                return null;
            }
            case "dep_del" -> {
                Quest q = f.quests.get(t.getLong("q"));
                if (q == null) return "That quest no longer exists.";
                long d = t.getLong("d");
                List<Long> keep = new ArrayList<>();
                for (long x : q.deps) if (x != d) keep.add(x);
                q.deps = keep.stream().mapToLong(Long::longValue).toArray();
                return null;
            }
            case "dep_clear" -> {
                Quest q = f.quests.get(t.getLong("q"));
                if (q == null) return "That quest no longer exists.";
                q.deps = new long[0];
                return null;
            }
            case "chapter_add" -> {
                long id = t.getLong("ch");
                if (id == 0 || f.chapterMap.containsKey(id)) return "Id clash, try again.";
                Chapter c = new Chapter();
                c.id = id;
                c.title = t.getString("title");
                c.group = t.getLong("group");
                c.filename = fileName(f, c.title);
                String icon = t.getString("icon");
                c.icon = QuestFile.ItemRef.of(icon.isEmpty() ? "minecraft:book" : icon);
                int order = 0;
                for (Chapter o : f.chapters) if (o.group == c.group) order = Math.max(order, o.order + 1);
                c.order = order;
                f.chapters.add(c);
                f.reindex();
                return null;
            }
            case "chapter_set" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                if (c == null) return "That chapter no longer exists.";
                c.title = t.getString("title");
                c.subtitle = QuestFile.readStrList(t.getList("sub", net.minecraft.nbt.Tag.TAG_STRING));
                long g = t.getLong("group");
                if (g != c.group) {
                    c.group = g;
                    int order = 0;
                    for (Chapter o : f.chapters) if (o.group == g && o != c) order = Math.max(order, o.order + 1);
                    c.order = order;
                }
                String icon = t.getString("icon");
                if (!icon.isEmpty()) c.icon = QuestFile.ItemRef.of(icon);
                f.reindex();
                return null;
            }
            case "chapter_del" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                if (c == null) return "That chapter no longer exists.";
                if (f.chapters.size() <= 1) return "The book needs at least one chapter.";
                for (Quest q : c.quests) stripDep(f, q.id);
                f.chapters.remove(c);
                f.reindex();
                return null;
            }
            case "chapter_move" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                if (c == null) return "That chapter no longer exists.";
                int i = f.chapters.indexOf(c);
                int j = i + t.getInt("dir");
                if (j < 0 || j >= f.chapters.size()) return null;
                Chapter o = f.chapters.get(j);
                if (o.group == c.group) {
                    int tmp = c.order;
                    c.order = o.order;
                    o.order = tmp;
                    if (c.order == o.order) o.order += t.getInt("dir") > 0 ? -1 : 1;
                } else {
                    // hop into the neighbouring group, at its near edge
                    c.group = o.group;
                    c.order = o.order + (t.getInt("dir") > 0 ? -1 : 1);
                }
                // renumber so orders stay unique within each group
                f.reindex();
                long lastGroup = Long.MIN_VALUE;
                int n = 0;
                for (Chapter x : f.chapters) {
                    if (x.group != lastGroup) {
                        lastGroup = x.group;
                        n = 0;
                    }
                    x.order = n++;
                }
                return null;
            }
            case "label_add" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                if (c == null) return "That chapter no longer exists.";
                ChapterImage im = new ChapterImage();
                im.id = t.getString("id");
                im.text = t.getString("text");
                im.x = t.getDouble("x");
                im.y = t.getDouble("y");
                im.width = Math.max(3, im.text.length() * 0.42 + 1);
                im.height = 0.4;
                c.images.add(im);
                return null;
            }
            case "label_set" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                int i = t.getInt("i");
                if (c == null || i < 0 || i >= c.images.size()) return "That heading no longer exists.";
                ChapterImage im = c.images.get(i);
                im.text = t.getString("text");
                im.x = t.getDouble("x");
                im.y = t.getDouble("y");
                im.width = Math.max(3, im.text.length() * 0.42 + 1);
                return null;
            }
            case "label_del" -> {
                Chapter c = f.chapterMap.get(t.getLong("ch"));
                int i = t.getInt("i");
                if (c == null || i < 0 || i >= c.images.size()) return "That heading no longer exists.";
                c.images.remove(i);
                return null;
            }
            default -> {
                return "Unknown edit: " + op;
            }
        }
    }

    private static long mix(long a, long b) {
        long h = a * 0x9E3779B97F4A7C15L ^ (b + 0x632BE59BD9B4E019L);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h == 0 ? 1 : h;
    }

    private static void stripDep(QuestFile f, long id) {
        for (Quest o : f.quests.values()) {
            boolean has = false;
            for (long d : o.deps) if (d == id) has = true;
            if (!has) continue;
            List<Long> keep = new ArrayList<>();
            for (long d : o.deps) if (d != id) keep.add(d);
            o.deps = keep.stream().mapToLong(Long::longValue).toArray();
        }
    }

    /** True when quest q (transitively) requires the quest with the given id. */
    private static boolean requires(QuestFile f, Quest q, long id, int depth) {
        if (depth > 64) return false;
        for (long d : q.deps) {
            if (d == id) return true;
            Quest dq = f.quests.get(d);
            if (dq != null && requires(f, dq, id, depth + 1)) return true;
        }
        return false;
    }

    private static String fileName(QuestFile f, String title) {
        String base = title.toLowerCase().replaceAll("&[0-9a-fk-or]", "").replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) base = "chapter";
        String name = base;
        int n = 2;
        outer:
        while (true) {
            for (Chapter c : f.chapters) if (c.filename.equals(name)) {
                name = base + "_" + n++;
                continue outer;
            }
            return name;
        }
    }
}
