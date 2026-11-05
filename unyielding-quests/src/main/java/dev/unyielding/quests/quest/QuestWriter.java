package dev.unyielding.quests.quest;

import dev.unyielding.quests.quest.QuestFile.Chapter;
import dev.unyielding.quests.quest.QuestFile.ChapterImage;
import dev.unyielding.quests.quest.QuestFile.Group;
import dev.unyielding.quests.quest.QuestFile.ItemRef;
import dev.unyielding.quests.quest.QuestFile.LootEntry;
import dev.unyielding.quests.quest.QuestFile.LootTable;
import dev.unyielding.quests.quest.QuestFile.Quest;
import dev.unyielding.quests.quest.QuestFile.Reward;
import dev.unyielding.quests.quest.QuestFile.Task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Writes a quest book back to FTB Quests style SNBT files (what the in-game editor saves). */
public final class QuestWriter {
    private QuestWriter() {
    }

    /** A number written verbatim (keeps the FTB suffixes: 1.5d, 3L). */
    private record Raw(String text) {
    }

    private static Raw d(double v) {
        String s = v == Math.rint(v) ? String.valueOf((long) v) + ".0" : String.valueOf(v);
        return new Raw(s + "d");
    }

    private static Raw l(long v) {
        return new Raw(v + "L");
    }

    private static Raw i(long v) {
        return new Raw(String.valueOf(v));
    }

    public static void write(QuestFile f, Path dir) throws IOException {
        Files.createDirectories(dir.resolve("chapters"));
        Files.createDirectories(dir.resolve("lang"));
        Files.createDirectories(dir.resolve("reward_tables"));
        Map<String, Object> lang = new TreeMap<>();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("default_quest_shape", f.defaultShape);
        data.put("detection_delay", i(f.detectionDelay));
        data.put("progression_mode", f.progressionMode);
        data.put("version", i(16));
        save(dir.resolve("data.snbt"), data);

        Map<String, Object> groups = new LinkedHashMap<>();
        List<Object> gl = new ArrayList<>();
        for (Group g : f.groups) {
            Map<String, Object> gm = new LinkedHashMap<>();
            gm.put("id", QuestFile.idToString(g.id));
            gl.add(gm);
            putText(lang, "chapter_group." + QuestFile.idToString(g.id) + ".title", g.title);
        }
        groups.put("chapter_groups", gl);
        save(dir.resolve("chapter_groups.snbt"), groups);

        Set<String> keep = new HashSet<>();
        for (Chapter c : f.chapters) {
            String cid = QuestFile.idToString(c.id);
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("default_hide_dependency_lines", false);
            cm.put("default_quest_shape", "");
            cm.put("filename", c.filename);
            if (c.group != 0) cm.put("group", QuestFile.idToString(c.group));
            if (c.icon != null) cm.put("icon", item(c.icon));
            cm.put("id", cid);
            List<Object> images = new ArrayList<>();
            for (int k = 0; k < c.images.size(); k++) {
                ChapterImage im = c.images.get(k);
                Map<String, Object> m = new LinkedHashMap<>();
                String iid = im.id.isEmpty() ? cid.substring(0, 8) + String.format(Locale.ROOT, "%08X", k) : im.id;
                m.put("height", d(im.height));
                m.put("id", iid);
                if (!im.image.isEmpty()) m.put("image", im.image);
                m.put("rotation", d(im.rotation));
                if (!im.text.isEmpty()) {
                    m.put("text_on_image", true);
                    putText(lang, "image." + iid + ".title", im.text);
                }
                m.put("width", d(im.width));
                m.put("x", d(im.x));
                m.put("y", d(im.y));
                images.add(m);
            }
            cm.put("images", images);
            cm.put("order_index", i(c.order));
            if (!c.progressionMode.isEmpty()) cm.put("progression_mode", c.progressionMode);
            if (c.autofocus != 0) cm.put("autofocus_id", QuestFile.idToString(c.autofocus));
            List<Object> quests = new ArrayList<>();
            for (Quest q : c.quests) quests.add(quest(q, lang));
            cm.put("quests", quests);
            putText(lang, "chapter." + cid + ".title", c.title);
            if (!c.subtitle.isEmpty()) lang.put("chapter." + cid + ".chapter_subtitle", new ArrayList<Object>(c.subtitle));
            String file = c.filename + ".snbt";
            keep.add(file);
            save(dir.resolve("chapters").resolve(file), cm);
        }
        // chapters that were deleted in game
        try (Stream<Path> s = Files.list(dir.resolve("chapters"))) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String n = p.getFileName().toString();
                if (n.endsWith(".snbt") && !keep.contains(n)) Files.deleteIfExists(p);
            }
        }

        for (LootTable lt : f.lootTables.values()) {
            String tid = QuestFile.idToString(lt.id);
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", tid);
            tm.put("loot_size", i(lt.lootSize));
            if (lt.icon != null) tm.put("icon", item(lt.icon));
            List<Object> rs = new ArrayList<>();
            for (LootEntry e : lt.entries) {
                Map<String, Object> em = new LinkedHashMap<>();
                em.put("item", item(e.item));
                if (e.count != 1) em.put("count", i(e.count));
                em.put("weight", d(e.weight));
                rs.add(em);
            }
            tm.put("rewards", rs);
            tm.put("title", lt.title);
            save(dir.resolve("reward_tables").resolve(tid.toLowerCase(Locale.ROOT) + ".snbt"), tm);
        }

        save(dir.resolve("lang").resolve("en_us.snbt"), lang);
    }

    private static void putText(Map<String, Object> lang, String key, String text) {
        if (text == null || text.isEmpty()) return;
        if (text.contains("\n")) lang.put(key, new ArrayList<Object>(List.of(text.split("\n", -1))));
        else lang.put(key, text);
    }

    private static Map<String, Object> quest(Quest q, Map<String, Object> lang) {
        String qid = QuestFile.idToString(q.id);
        Map<String, Object> m = new LinkedHashMap<>();
        if (q.deps.length > 0) {
            List<Object> deps = new ArrayList<>();
            for (long d : q.deps) deps.add(QuestFile.idToString(d));
            m.put("dependencies", deps);
        }
        if (q.hideDepLines) m.put("hide_dependency_lines", true);
        if (q.hideDependentLines) m.put("hide_dependent_lines", true);
        if (q.hideUntilDeps) m.put("hide_until_deps_complete", true);
        if (q.icon != null) m.put("icon", item(q.icon));
        if (q.iconScale != 1.0) m.put("icon_scale", d(q.iconScale));
        m.put("id", qid);
        if (q.optional) m.put("optional", true);
        List<Object> rewards = new ArrayList<>();
        for (Reward r : q.rewards) rewards.add(reward(r));
        if (!rewards.isEmpty()) m.put("rewards", rewards);
        if (!q.shape.isEmpty()) m.put("shape", q.shape);
        if (q.size != 1.0) m.put("size", d(q.size));
        List<Object> tasks = new ArrayList<>();
        for (Task k : q.tasks) tasks.add(task(k, lang));
        m.put("tasks", tasks);
        m.put("x", d(q.x));
        m.put("y", d(q.y));
        putText(lang, "quest." + qid + ".title", q.title);
        if (!q.subtitle.isEmpty()) lang.put("quest." + qid + ".quest_subtitle", q.subtitle.size() == 1 ? q.subtitle.get(0) : new ArrayList<Object>(q.subtitle));
        if (!q.desc.isEmpty()) lang.put("quest." + qid + ".quest_desc", new ArrayList<Object>(q.desc));
        return m;
    }

    private static Map<String, Object> task(Task k, Map<String, Object> lang) {
        String tid = QuestFile.idToString(k.id);
        Map<String, Object> m = new LinkedHashMap<>();
        if (k.icon != null) m.put("icon", item(k.icon));
        m.put("id", tid);
        switch (k.type) {
            case CHECKMARK -> m.put("type", "checkmark");
            case ITEM -> {
                if (k.consume) m.put("consume_items", true);
                if (k.amount != 1) m.put("count", l(k.amount));
                m.put("item", k.item == null ? "minecraft:air" : item(k.item));
                m.put("type", "item");
            }
            case ADVANCEMENT -> {
                m.put("advancement", k.target);
                m.put("criterion", k.extra);
                m.put("type", "advancement");
            }
            case STRUCTURE -> {
                m.put("structure", k.target);
                m.put("type", "structure");
            }
            case BIOME -> {
                m.put("biome", k.target);
                m.put("type", "biome");
            }
            case DIMENSION -> {
                m.put("dimension", k.target);
                m.put("type", "dimension");
            }
            case KILL -> {
                m.put("entity", k.target);
                m.put("type", "kill");
                m.put("value", l(k.amount));
            }
            case KEY_VALUE -> {
                m.put("key", k.target);
                m.put("type", "unyieldingquests:key_value");
                m.put("value", l(k.amount));
            }
            case COBBLEMON -> {
                m.put("action", k.target);
                m.put("amount", l(k.amount));
                if (!k.dexProgress.isEmpty()) m.put("dex_progress", k.dexProgress);
                if (!k.extra.isEmpty()) m.put("pokemon", k.extra);
                if (!k.pokemonType.isEmpty()) m.put("pokemon_type", k.pokemonType);
                if (k.shiny) m.put("shiny", true);
                m.put("type", "unyieldingquests:cobblemon");
            }
            default -> m.put("type", k.rawType.isEmpty() ? "checkmark" : k.rawType);
        }
        putText(lang, "task." + tid + ".title", k.title);
        return m;
    }

    private static Map<String, Object> reward(Reward r) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (r.excludeFromClaimAll) m.put("exclude_from_claim_all", true);
        if (r.icon != null) m.put("icon", item(r.icon));
        m.put("id", QuestFile.idToString(r.id));
        if (!r.title.isEmpty()) m.put("title", r.title);
        switch (r.type) {
            case ITEM -> {
                if (r.amount != 1) m.put("count", l(r.amount));
                m.put("item", r.item == null ? "minecraft:air" : item(r.item));
                m.put("type", "item");
            }
            case XP -> {
                m.put("type", "xp");
                m.put("xp", l(r.amount));
            }
            case XP_LEVELS -> {
                m.put("type", "xp_levels");
                m.put("xp_levels", l(r.amount));
            }
            case COMMAND -> {
                m.put("command", r.command);
                m.put("console", r.console);
                m.put("type", "command");
            }
            case LOOT -> {
                m.put("table_id", l(r.amount));
                m.put("type", "loot");
            }
            case KEY_VALUE -> {
                if (r.auto) m.put("auto", "enabled");
                m.put("key", r.key);
                m.put("type", "unyieldingquests:key_value");
                m.put("value", l(r.amount));
            }
            default -> m.put("type", r.rawType.isEmpty() ? "xp" : r.rawType);
        }
        return m;
    }

    private static Object item(ItemRef ref) {
        if (ref.isFilter()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", i(1));
            m.put("id", "ftbfiltersystem:smart_filter");
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("ftbfiltersystem:filter", ref.filter);
            m.put("components", c);
            return m;
        }
        if (ref.components == null && ref.count == 1) return ref.id;
        Map<String, Object> m = new LinkedHashMap<>();
        if (ref.components != null) m.put("components", new Raw(ref.components.toString()));
        m.put("count", i(Math.max(1, ref.count)));
        m.put("id", ref.id);
        return m;
    }

    // ---- SNBT output -----------------------------------------------------

    private static void save(Path p, Map<String, Object> m) throws IOException {
        StringBuilder sb = new StringBuilder();
        emit(sb, m, 0);
        sb.append('\n');
        Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
    }

    private static final java.util.regex.Pattern IDENT = java.util.regex.Pattern.compile("[A-Za-z0-9_.+-]+");

    private static void emit(StringBuilder sb, Object v, int ind) {
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append("{ }");
                return;
            }
            sb.append("{\n");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                tab(sb, ind + 1);
                String k = String.valueOf(e.getKey());
                sb.append(IDENT.matcher(k).matches() ? k : quote(k)).append(": ");
                emit(sb, e.getValue(), ind + 1);
                sb.append('\n');
            }
            tab(sb, ind);
            sb.append('}');
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append("[ ]");
                return;
            }
            sb.append("[\n");
            for (Object o : l) {
                tab(sb, ind + 1);
                emit(sb, o, ind + 1);
                sb.append('\n');
            }
            tab(sb, ind);
            sb.append(']');
        } else if (v instanceof Raw r) {
            sb.append(r.text());
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Number n) {
            sb.append(n);
        } else {
            sb.append(quote(String.valueOf(v)));
        }
    }

    private static void tab(StringBuilder sb, int n) {
        for (int k = 0; k < n; k++) sb.append('\t');
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"';
    }
}
