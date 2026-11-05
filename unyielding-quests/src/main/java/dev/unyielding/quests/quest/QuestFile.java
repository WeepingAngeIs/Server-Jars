package dev.unyielding.quests.quest;

import dev.unyielding.quests.snbt.Snbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The whole quest book: groups, chapters, quests, tasks, rewards and their texts. */
public final class QuestFile {
    public String defaultShape = "circle";
    public String progressionMode = "linear";
    public int detectionDelay = 20;

    public final List<Group> groups = new ArrayList<>();
    public final List<Chapter> chapters = new ArrayList<>();
    public final Map<Long, Chapter> chapterMap = new HashMap<>();
    public final Map<Long, Quest> quests = new LinkedHashMap<>();
    public final Map<Long, LootTable> lootTables = new LinkedHashMap<>();
    public final Map<Long, Task> tasks = new HashMap<>();
    public final Map<Long, Reward> rewards = new HashMap<>();
    /** Lucky Spin id -> quest (see {@link dev.unyielding.quests.spin.LuckySpin#spinId}). */
    public final Map<Long, Quest> spinQuests = new HashMap<>();

    // =====================================================================
    // Model
    // =====================================================================

    public static final class Group {
        public long id;
        public String title = "";
    }

    /** Registries used to decode item components (set by the server on start and by the client on join). */
    public static volatile HolderLookup.Provider REGS;

    public static final class ItemRef {
        public String id = "minecraft:air";
        public int count = 1;
        public String filter = "";
        /** Extra item data components (e.g. which Pokémon a cobblemon:pokemon_model shows). May be null. */
        public CompoundTag components;
        private DataComponentPatch patch;
        private boolean patchTried;

        public boolean isFilter() {
            return !filter.isEmpty();
        }

        public static ItemRef of(String id) {
            ItemRef r = new ItemRef();
            r.id = id;
            return r;
        }

        @SuppressWarnings("unchecked")
        static ItemRef parse(Object o) {
            ItemRef r = new ItemRef();
            if (o instanceof String s) {
                r.id = s;
            } else if (o instanceof Map<?, ?> mm) {
                Map<String, Object> m = (Map<String, Object>) mm;
                r.id = Snbt.str(m, "id", "minecraft:air");
                r.count = (int) Snbt.lng(m, "count", 1);
                Map<String, Object> comps = Snbt.map(m, "components");
                if (comps != null) {
                    Object missing = comps.get("ftbquests:missing_item");
                    if (missing instanceof String ms) {
                        r.id = ms.contains(":") ? ms : "minecraft:" + ms;
                    }
                    Object f = comps.get("ftbfiltersystem:filter");
                    if (f instanceof String fs) r.filter = fs;
                    CompoundTag ct = new CompoundTag();
                    for (Map.Entry<String, Object> e : comps.entrySet()) {
                        if (e.getKey().equals("ftbquests:missing_item") || e.getKey().equals("ftbfiltersystem:filter")) continue;
                        Tag v = toNbt(e.getValue());
                        if (v != null) ct.put(e.getKey(), v);
                    }
                    if (!ct.isEmpty()) r.components = ct;
                }
            }
            if (r.id.equals("ftbquests:book")) r.id = "minecraft:book";
            if (r.id.equals("ftbfiltersystem:smart_filter") && r.filter.isEmpty()) r.id = "minecraft:paper";
            if (r.count < 1) r.count = 1;
            return r;
        }

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id);
            if (count != 1) t.putInt("n", count);
            if (!filter.isEmpty()) t.putString("f", filter);
            if (components != null) t.put("c", components);
            return t;
        }

        static ItemRef fromTag(CompoundTag t) {
            ItemRef r = new ItemRef();
            r.id = t.getString("id");
            r.count = t.contains("n") ? t.getInt("n") : 1;
            r.filter = t.getString("f");
            if (t.contains("c")) r.components = t.getCompound("c");
            return r;
        }

        /** Converts a parsed SNBT value into an NBT tag (null when it cannot be represented). */
        @SuppressWarnings("unchecked")
        static Tag toNbt(Object v) {
            if (v instanceof String s) return StringTag.valueOf(s);
            if (v instanceof Boolean b) return ByteTag.valueOf(b);
            if (v instanceof Long l) return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? IntTag.valueOf((int) (long) l) : LongTag.valueOf(l);
            if (v instanceof Double d) return DoubleTag.valueOf(d);
            if (v instanceof Map<?, ?> m) {
                CompoundTag c = new CompoundTag();
                for (Map.Entry<String, Object> e : ((Map<String, Object>) m).entrySet()) {
                    Tag t = toNbt(e.getValue());
                    if (t != null) c.put(e.getKey(), t);
                }
                return c;
            }
            if (v instanceof List<?> l) {
                ListTag lt = new ListTag();
                for (Object o : l) {
                    Tag t = toNbt(o);
                    if (t != null && !lt.addTag(lt.size(), t)) return null;
                }
                return lt;
            }
            return null;
        }

        private DataComponentPatch patch() {
            if (components == null) return null;
            if (!patchTried) {
                HolderLookup.Provider regs = REGS;
                if (regs == null) return null;
                patchTried = true;
                patch = DataComponentPatch.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, regs), components).result().orElse(null);
            }
            return patch;
        }

        public Item item() {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return null;
            return BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        }

        /** Stack to draw; filters cycle through their items. */
        public ItemStack stack() {
            if (isFilter()) {
                List<Item> items = ItemFilter.get(filter).displayItems();
                if (!items.isEmpty()) {
                    int idx = (int) ((System.currentTimeMillis() / 1000L) % items.size());
                    return new ItemStack(items.get(idx), 1);
                }
                return new ItemStack(Items.PAPER);
            }
            Item it = item();
            if (it == null || it == Items.AIR) return new ItemStack(Items.BARRIER);
            ItemStack s = new ItemStack(it, Math.max(1, count));
            DataComponentPatch p = patch();
            if (p != null) s.applyComponents(p);
            return s;
        }

        public boolean exists() {
            if (isFilter()) return !ItemFilter.get(filter).displayItems().isEmpty();
            Item it = item();
            return it != null && it != Items.AIR;
        }

        public boolean matches(ItemStack s) {
            if (s.isEmpty()) return false;
            if (isFilter()) return ItemFilter.get(filter).matches(s);
            Item it = item();
            return it != null && s.is(it);
        }
    }

    public enum TaskType {
        CHECKMARK, ITEM, ADVANCEMENT, STRUCTURE, BIOME, DIMENSION, KILL, KEY_VALUE, COBBLEMON, UNKNOWN;

        static TaskType of(String raw) {
            return switch (raw) {
                case "checkmark" -> CHECKMARK;
                case "item" -> ITEM;
                case "advancement" -> ADVANCEMENT;
                case "structure" -> STRUCTURE;
                case "biome" -> BIOME;
                case "dimension" -> DIMENSION;
                case "kill" -> KILL;
                case "extraquests:key_value", "unyieldingquests:key_value", "key_value" -> KEY_VALUE;
                case "cobblemon_tasks:cobblemon_task", "unyieldingquests:cobblemon" -> COBBLEMON;
                default -> UNKNOWN;
            };
        }
    }

    public static final class Task {
        public long id;
        public long quest;
        public TaskType type = TaskType.UNKNOWN;
        public String rawType = "";
        public String title = "";
        public ItemRef icon;
        /** ITEM: the wanted item. */
        public ItemRef item;
        /** advancement / structure / biome / dimension / entity / key / cobblemon action. */
        public String target = "";
        /** advancement criterion / cobblemon species. */
        public String extra = "";
        /** cobblemon type filter. */
        public String pokemonType = "";
        /** cobblemon dex progress (caught/seen). */
        public String dexProgress = "";
        /** item count / kill value / key value / cobblemon amount. */
        public long amount = 1;
        public boolean consume;
        public boolean shiny;

        public long maxProgress() {
            return switch (type) {
                case ITEM, KILL, KEY_VALUE, COBBLEMON -> Math.max(1, amount);
                default -> 1;
            };
        }

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putString("type", type.name());
            t.putString("raw", rawType);
            if (!title.isEmpty()) t.putString("title", title);
            if (icon != null) t.put("icon", icon.toTag());
            if (item != null) t.put("item", item.toTag());
            if (!target.isEmpty()) t.putString("target", target);
            if (!extra.isEmpty()) t.putString("extra", extra);
            if (!pokemonType.isEmpty()) t.putString("ptype", pokemonType);
            if (!dexProgress.isEmpty()) t.putString("dex", dexProgress);
            t.putLong("amount", amount);
            if (consume) t.putBoolean("consume", true);
            if (shiny) t.putBoolean("shiny", true);
            return t;
        }

        static Task fromTag(CompoundTag t, long questId) {
            Task k = new Task();
            k.id = t.getLong("id");
            k.quest = questId;
            try {
                k.type = TaskType.valueOf(t.getString("type"));
            } catch (IllegalArgumentException e) {
                k.type = TaskType.UNKNOWN;
            }
            k.rawType = t.getString("raw");
            k.title = t.getString("title");
            if (t.contains("icon")) k.icon = ItemRef.fromTag(t.getCompound("icon"));
            if (t.contains("item")) k.item = ItemRef.fromTag(t.getCompound("item"));
            k.target = t.getString("target");
            k.extra = t.getString("extra");
            k.pokemonType = t.getString("ptype");
            k.dexProgress = t.getString("dex");
            k.amount = t.getLong("amount");
            k.consume = t.getBoolean("consume");
            k.shiny = t.getBoolean("shiny");
            return k;
        }
    }

    public enum RewardType {
        ITEM, XP, KEY_VALUE, COMMAND, LOOT, XP_LEVELS, UNKNOWN
    }

    /** One line of an FTB-style reward table. */
    public static final class LootEntry {
        public ItemRef item;
        public int count = 1;
        public float weight = 1f;
    }

    /** A reward table (reward_tables/*.snbt): a random pick of its entries. */
    public static final class LootTable {
        public long id;
        public String title = "";
        public ItemRef icon;
        public int lootSize = 1;
        public final List<LootEntry> entries = new ArrayList<>();

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putString("title", title);
            if (icon != null) t.put("icon", icon.toTag());
            t.putInt("size", lootSize);
            ListTag l = new ListTag();
            for (LootEntry e : entries) {
                CompoundTag c = new CompoundTag();
                c.put("item", e.item.toTag());
                c.putInt("count", e.count);
                c.putFloat("weight", e.weight);
                l.add(c);
            }
            t.put("entries", l);
            return t;
        }

        static LootTable fromTag(CompoundTag t) {
            LootTable lt = new LootTable();
            lt.id = t.getLong("id");
            lt.title = t.getString("title");
            if (t.contains("icon")) lt.icon = ItemRef.fromTag(t.getCompound("icon"));
            lt.lootSize = Math.max(1, t.getInt("size"));
            for (Tag e : t.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                LootEntry le = new LootEntry();
                le.item = ItemRef.fromTag(c.getCompound("item"));
                le.count = Math.max(1, c.getInt("count"));
                le.weight = c.getFloat("weight");
                lt.entries.add(le);
            }
            return lt;
        }
    }

    public static final class Reward {
        public long id;
        public long quest;
        public RewardType type = RewardType.UNKNOWN;
        public String rawType = "";
        public ItemRef item;
        public ItemRef icon;
        /** item count / xp points / key amount. */
        public long amount = 1;
        public String key = "";
        public boolean excludeFromClaimAll;
        public boolean auto;
        /** COMMAND: the command ({p} = player name); LOOT: unused. */
        public String command = "";
        /** COMMAND: run as the server console instead of the player. */
        public boolean console = true;
        /** Shown instead of the generated label when set (FTB "title"). */
        public String title = "";

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putString("type", type.name());
            t.putString("raw", rawType);
            if (item != null) t.put("item", item.toTag());
            if (icon != null) t.put("icon", icon.toTag());
            t.putLong("amount", amount);
            if (!key.isEmpty()) t.putString("key", key);
            if (excludeFromClaimAll) t.putBoolean("xca", true);
            if (auto) t.putBoolean("auto", true);
            if (!command.isEmpty()) t.putString("cmd", command);
            if (!console) t.putBoolean("asPlayer", true);
            if (!title.isEmpty()) t.putString("title", title);
            return t;
        }

        static Reward fromTag(CompoundTag t, long questId) {
            Reward r = new Reward();
            r.id = t.getLong("id");
            r.quest = questId;
            try {
                r.type = RewardType.valueOf(t.getString("type"));
            } catch (IllegalArgumentException e) {
                r.type = RewardType.UNKNOWN;
            }
            r.rawType = t.getString("raw");
            if (t.contains("item")) r.item = ItemRef.fromTag(t.getCompound("item"));
            if (t.contains("icon")) r.icon = ItemRef.fromTag(t.getCompound("icon"));
            r.amount = t.getLong("amount");
            r.key = t.getString("key");
            r.excludeFromClaimAll = t.getBoolean("xca");
            r.auto = t.getBoolean("auto");
            r.command = t.getString("cmd");
            r.console = !t.getBoolean("asPlayer");
            r.title = t.getString("title");
            return r;
        }
    }

    public static final class Quest {
        public long id;
        public long chapter;
        public double x, y, size = 1.0, iconScale = 1.0;
        public String shape = "circle";
        public ItemRef icon;
        public String title = "";
        public List<String> subtitle = new ArrayList<>();
        public List<String> desc = new ArrayList<>();
        public long[] deps = new long[0];
        public boolean optional, hideDepLines, hideDependentLines, hideUntilDeps;
        public final List<Task> tasks = new ArrayList<>();
        public final List<Reward> rewards = new ArrayList<>();

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putDouble("x", x);
            t.putDouble("y", y);
            if (size != 1.0) t.putDouble("size", size);
            if (iconScale != 1.0) t.putDouble("is", iconScale);
            t.putString("shape", shape);
            if (icon != null) t.put("icon", icon.toTag());
            if (!title.isEmpty()) t.putString("title", title);
            if (!subtitle.isEmpty()) t.put("sub", strList(subtitle));
            if (!desc.isEmpty()) t.put("desc", strList(desc));
            if (deps.length > 0) t.putLongArray("deps", deps);
            if (optional) t.putBoolean("opt", true);
            if (hideDepLines) t.putBoolean("hdl", true);
            if (hideDependentLines) t.putBoolean("hdd", true);
            if (hideUntilDeps) t.putBoolean("hud", true);
            ListTag ts = new ListTag();
            for (Task k : tasks) ts.add(k.toTag());
            t.put("tasks", ts);
            ListTag rs = new ListTag();
            for (Reward r : rewards) rs.add(r.toTag());
            t.put("rewards", rs);
            return t;
        }

        static Quest fromTag(CompoundTag t, long chapterId) {
            Quest q = new Quest();
            q.id = t.getLong("id");
            q.chapter = chapterId;
            q.x = t.getDouble("x");
            q.y = t.getDouble("y");
            q.size = t.contains("size") ? t.getDouble("size") : 1.0;
            q.iconScale = t.contains("is") ? t.getDouble("is") : 1.0;
            q.shape = t.getString("shape");
            if (t.contains("icon")) q.icon = ItemRef.fromTag(t.getCompound("icon"));
            q.title = t.getString("title");
            q.subtitle = readStrList(t.getList("sub", Tag.TAG_STRING));
            q.desc = readStrList(t.getList("desc", Tag.TAG_STRING));
            q.deps = t.getLongArray("deps");
            q.optional = t.getBoolean("opt");
            q.hideDepLines = t.getBoolean("hdl");
            q.hideDependentLines = t.getBoolean("hdd");
            q.hideUntilDeps = t.getBoolean("hud");
            for (Tag e : t.getList("tasks", Tag.TAG_COMPOUND)) q.tasks.add(Task.fromTag((CompoundTag) e, q.id));
            for (Tag e : t.getList("rewards", Tag.TAG_COMPOUND)) q.rewards.add(Reward.fromTag((CompoundTag) e, q.id));
            return q;
        }

        /** Title shown when the lang file has none. */
        public String displayTitle() {
            if (!title.isEmpty()) return title;
            if (!tasks.isEmpty()) {
                Task k = tasks.get(0);
                if (!k.title.isEmpty()) return k.title;
            }
            return "Quest";
        }
    }

    /** A picture placed on a chapter page (FTB-style "images"), in quest-grid units. */
    public static final class ChapterImage {
        /** FTB image id (lang key for text labels). */
        public String id = "";
        public String image = "";
        /** Text label (FTB "text_on_image" images with no picture): drawn as a heading on the page. */
        public String text = "";
        public double x, y, width = 1, height = 1, rotation;

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putString("img", image);
            if (!id.isEmpty()) t.putString("iid", id);
            if (!text.isEmpty()) t.putString("txt", text);
            t.putDouble("x", x);
            t.putDouble("y", y);
            t.putDouble("w", width);
            t.putDouble("h", height);
            if (rotation != 0) t.putDouble("r", rotation);
            return t;
        }

        static ChapterImage fromTag(CompoundTag t) {
            ChapterImage i = new ChapterImage();
            i.image = t.getString("img");
            i.id = t.getString("iid");
            i.text = t.getString("txt");
            i.x = t.getDouble("x");
            i.y = t.getDouble("y");
            i.width = t.getDouble("w");
            i.height = t.getDouble("h");
            i.rotation = t.getDouble("r");
            return i;
        }
    }

    public static final class Chapter {
        public final List<ChapterImage> images = new ArrayList<>();
        public long id;
        public String filename = "";
        public long group;
        public int order;
        public String title = "";
        public List<String> subtitle = new ArrayList<>();
        public ItemRef icon;
        public String progressionMode = "";
        public long autofocus;
        public final List<Quest> quests = new ArrayList<>();

        CompoundTag toTag() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putString("file", filename);
            t.putLong("group", group);
            t.putInt("order", order);
            t.putString("title", title);
            if (!subtitle.isEmpty()) t.put("sub", strList(subtitle));
            if (icon != null) t.put("icon", icon.toTag());
            if (!progressionMode.isEmpty()) t.putString("mode", progressionMode);
            t.putLong("focus", autofocus);
            ListTag qs = new ListTag();
            for (Quest q : quests) qs.add(q.toTag());
            t.put("quests", qs);
            if (!images.isEmpty()) {
                ListTag is = new ListTag();
                for (ChapterImage i : images) is.add(i.toTag());
                t.put("images", is);
            }
            return t;
        }

        static Chapter fromTag(CompoundTag t) {
            Chapter c = new Chapter();
            c.id = t.getLong("id");
            c.filename = t.getString("file");
            c.group = t.getLong("group");
            c.order = t.getInt("order");
            c.title = t.getString("title");
            c.subtitle = readStrList(t.getList("sub", Tag.TAG_STRING));
            if (t.contains("icon")) c.icon = ItemRef.fromTag(t.getCompound("icon"));
            c.progressionMode = t.getString("mode");
            c.autofocus = t.getLong("focus");
            for (Tag e : t.getList("quests", Tag.TAG_COMPOUND)) c.quests.add(Quest.fromTag((CompoundTag) e, c.id));
            for (Tag e : t.getList("images", Tag.TAG_COMPOUND)) c.images.add(ChapterImage.fromTag((CompoundTag) e));
            return c;
        }
    }

    // =====================================================================
    // Queries
    // =====================================================================

    public Quest quest(long id) {
        return quests.get(id);
    }

    public Chapter chapterOf(Quest q) {
        return chapterMap.get(q.chapter);
    }

    public boolean isFlexible(Quest q) {
        Chapter c = chapterOf(q);
        String mode = c != null && !c.progressionMode.isEmpty() ? c.progressionMode : progressionMode;
        return mode.equalsIgnoreCase("flexible");
    }

    /** Rebuilds the id lookups after the book changed (editing). */
    public void reindex() {
        index();
    }

    private void index() {
        chapterMap.clear();
        quests.clear();
        tasks.clear();
        rewards.clear();
        spinQuests.clear();
        chapters.sort(Comparator.<Chapter>comparingInt(c -> groupIndex(c.group)).thenComparingInt(c -> c.order));
        for (Chapter c : chapters) {
            chapterMap.put(c.id, c);
            for (Quest q : c.quests) {
                quests.put(q.id, q);
                for (Task k : q.tasks) tasks.put(k.id, k);
                for (Reward r : q.rewards) rewards.put(r.id, r);
                spinQuests.put(dev.unyielding.quests.spin.LuckySpin.spinId(q.id), q);
            }
        }
    }

    private int groupIndex(long group) {
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).id == group) return i;
        return groups.size() + 1;
    }

    // =====================================================================
    // NBT
    // =====================================================================

    static ListTag strList(List<String> l) {
        ListTag t = new ListTag();
        for (String s : l) t.add(StringTag.valueOf(s));
        return t;
    }

    static List<String> readStrList(ListTag t) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) l.add(t.getString(i));
        return l;
    }

    public CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putString("shape", defaultShape);
        t.putString("mode", progressionMode);
        t.putInt("delay", detectionDelay);
        ListTag gs = new ListTag();
        for (Group g : groups) {
            CompoundTag gt = new CompoundTag();
            gt.putLong("id", g.id);
            gt.putString("title", g.title);
            gs.add(gt);
        }
        t.put("groups", gs);
        ListTag cs = new ListTag();
        for (Chapter c : chapters) cs.add(c.toTag());
        t.put("chapters", cs);
        ListTag ls = new ListTag();
        for (LootTable lt : lootTables.values()) ls.add(lt.toTag());
        t.put("tables", ls);
        return t;
    }

    public static QuestFile fromTag(CompoundTag t) {
        QuestFile f = new QuestFile();
        f.defaultShape = t.getString("shape");
        f.progressionMode = t.getString("mode");
        f.detectionDelay = t.getInt("delay");
        for (Tag e : t.getList("groups", Tag.TAG_COMPOUND)) {
            CompoundTag gt = (CompoundTag) e;
            Group g = new Group();
            g.id = gt.getLong("id");
            g.title = gt.getString("title");
            f.groups.add(g);
        }
        for (Tag e : t.getList("chapters", Tag.TAG_COMPOUND)) f.chapters.add(Chapter.fromTag((CompoundTag) e));
        for (Tag e : t.getList("tables", Tag.TAG_COMPOUND)) {
            LootTable lt = LootTable.fromTag((CompoundTag) e);
            f.lootTables.put(lt.id, lt);
        }
        f.index();
        return f;
    }

    // =====================================================================
    // Loading from the quest directory
    // =====================================================================

    public static long parseId(Object o) {
        if (o instanceof Long l) return l;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return 0;
        try {
            return Long.parseUnsignedLong(s, 16);
        } catch (NumberFormatException e) {
            return s.hashCode() & 0xffffffffL;
        }
    }

    public static String idToString(long id) {
        return String.format("%016X", id);
    }

    public static QuestFile load(Path dir, List<String> warnings) throws IOException {
        QuestFile f = new QuestFile();
        Map<String, Object> data = readMap(dir.resolve("data.snbt"), warnings);
        if (data != null) {
            f.defaultShape = Snbt.str(data, "default_quest_shape", "circle");
            f.progressionMode = Snbt.str(data, "progression_mode", "linear");
            f.detectionDelay = (int) Math.max(1, Snbt.lng(data, "detection_delay", 20));
        }
        Map<String, Object> lang = readMap(dir.resolve("lang").resolve("en_us.snbt"), warnings);
        if (lang == null) lang = new HashMap<>();

        Map<String, Object> groupsFile = readMap(dir.resolve("chapter_groups.snbt"), warnings);
        if (groupsFile != null) {
            for (Object o : Snbt.list(groupsFile, "chapter_groups")) {
                if (o instanceof Map<?, ?> gm) {
                    @SuppressWarnings("unchecked") Map<String, Object> g = (Map<String, Object>) gm;
                    Group gr = new Group();
                    gr.id = parseId(g.get("id"));
                    gr.title = text(lang.get("chapter_group." + Snbt.str(g, "id", "") + ".title"));
                    f.groups.add(gr);
                }
            }
        }

        Path tableDir = dir.resolve("reward_tables");
        if (Files.isDirectory(tableDir)) {
            List<Path> files;
            try (Stream<Path> st = Files.list(tableDir)) {
                files = st.filter(p -> p.getFileName().toString().endsWith(".snbt")).sorted().toList();
            }
            for (Path p : files) {
                Map<String, Object> tm = readMap(p, warnings);
                if (tm == null) continue;
                LootTable lt = new LootTable();
                lt.id = parseId(Snbt.str(tm, "id", "0"));
                lt.title = Snbt.str(tm, "title", "");
                if (lt.title.isEmpty()) lt.title = text(lang.get("reward_table." + Snbt.str(tm, "id", "") + ".title"));
                if (tm.get("icon") != null) lt.icon = ItemRef.parse(tm.get("icon"));
                lt.lootSize = (int) Math.max(1, Snbt.lng(tm, "loot_size", 1));
                for (Object o : Snbt.list(tm, "rewards")) {
                    if (o instanceof Map<?, ?> em) {
                        @SuppressWarnings("unchecked") Map<String, Object> e = (Map<String, Object>) em;
                        if (e.get("item") == null) continue;
                        LootEntry le = new LootEntry();
                        le.item = ItemRef.parse(e.get("item"));
                        le.count = (int) Math.max(1, Snbt.lng(e, "count", 1)) * Math.max(1, le.item.count);
                        le.item.count = 1;
                        le.weight = (float) Snbt.dbl(e, "weight", 1.0);
                        lt.entries.add(le);
                    }
                }
                f.lootTables.put(lt.id, lt);
            }
        }

        Path chapterDir = dir.resolve("chapters");
        if (Files.isDirectory(chapterDir)) {
            List<Path> files;
            try (Stream<Path> s = Files.list(chapterDir)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".snbt")).sorted().toList();
            }
            for (Path p : files) {
                Map<String, Object> cm = readMap(p, warnings);
                if (cm == null) continue;
                try {
                    f.chapters.add(parseChapter(cm, lang, f, p.getFileName().toString(), warnings));
                } catch (RuntimeException e) {
                    warnings.add("Chapter " + p.getFileName() + ": " + e);
                }
            }
        }
        f.index();
        // fix quest ownership of tasks / rewards after index
        return f;
    }

    private static Chapter parseChapter(Map<String, Object> cm, Map<String, Object> lang, QuestFile f, String fileName, List<String> warnings) {
        Chapter c = new Chapter();
        String idStr = Snbt.str(cm, "id", "");
        c.id = parseId(idStr);
        c.filename = Snbt.str(cm, "filename", fileName.replace(".snbt", ""));
        c.group = Snbt.str(cm, "group", "").isEmpty() ? 0 : parseId(cm.get("group"));
        c.order = (int) Snbt.lng(cm, "order_index", 0);
        c.title = text(lang.get("chapter." + idStr + ".title"));
        if (c.title.isEmpty()) c.title = c.filename;
        c.subtitle = lines(lang.get("chapter." + idStr + ".chapter_subtitle"));
        c.icon = cm.get("icon") != null ? ItemRef.parse(cm.get("icon")) : ItemRef.of("minecraft:book");
        c.progressionMode = Snbt.str(cm, "progression_mode", "");
        c.autofocus = Snbt.str(cm, "autofocus_id", "").isEmpty() ? 0 : parseId(cm.get("autofocus_id"));
        String chapterShape = Snbt.str(cm, "default_quest_shape", "");
        boolean chapterHide = Snbt.bool(cm, "default_hide_dependency_lines", false);
        for (Object io : Snbt.list(cm, "images")) {
            if (!(io instanceof Map<?, ?> imm)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> im = (Map<String, Object>) imm;
            ChapterImage ci = new ChapterImage();
            ci.id = Snbt.str(im, "id", "");
            ci.image = Snbt.str(im, "image", "");
            ci.x = Snbt.dbl(im, "x", 0);
            ci.y = Snbt.dbl(im, "y", 0);
            ci.width = Snbt.dbl(im, "width", 1);
            ci.height = Snbt.dbl(im, "height", 1);
            ci.rotation = Snbt.dbl(im, "rotation", 0);
            if (Snbt.bool(im, "text_on_image", false)) ci.text = text(lang.get("image." + Snbt.str(im, "id", "") + ".title"));
            if (!ci.image.isEmpty() || !ci.text.isEmpty()) c.images.add(ci);
        }

        for (Object qo : Snbt.list(cm, "quests")) {
            if (!(qo instanceof Map<?, ?> qmm)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> qm = (Map<String, Object>) qmm;
            Quest q = new Quest();
            String qid = Snbt.str(qm, "id", "");
            q.id = parseId(qid);
            q.chapter = c.id;
            q.x = Snbt.dbl(qm, "x", 0);
            q.y = Snbt.dbl(qm, "y", 0);
            q.size = Snbt.dbl(qm, "size", 1.0);
            q.iconScale = Snbt.dbl(qm, "icon_scale", 1.0);
            String shape = Snbt.str(qm, "shape", "");
            if (shape.isEmpty()) shape = chapterShape;
            if (shape.isEmpty()) shape = f.defaultShape;
            q.shape = shape;
            if (qm.get("icon") != null) q.icon = ItemRef.parse(qm.get("icon"));
            q.title = text(lang.get("quest." + qid + ".title"));
            q.subtitle = lines(lang.get("quest." + qid + ".quest_subtitle"));
            q.desc = lines(lang.get("quest." + qid + ".quest_desc"));
            List<Object> deps = Snbt.list(qm, "dependencies");
            q.deps = new long[deps.size()];
            for (int i = 0; i < deps.size(); i++) q.deps[i] = parseId(deps.get(i));
            q.optional = Snbt.bool(qm, "optional", false);
            q.hideDepLines = Snbt.bool(qm, "hide_dependency_lines", chapterHide);
            q.hideDependentLines = Snbt.bool(qm, "hide_dependent_lines", false);
            q.hideUntilDeps = Snbt.bool(qm, "hide_until_deps_complete", false);
            for (Object to : Snbt.list(qm, "tasks")) {
                if (to instanceof Map<?, ?> tmm) {
                    @SuppressWarnings("unchecked") Map<String, Object> tm = (Map<String, Object>) tmm;
                    q.tasks.add(parseTask(tm, q, lang));
                }
            }
            for (Object ro : Snbt.list(qm, "rewards")) {
                if (ro instanceof Map<?, ?> rmm) {
                    @SuppressWarnings("unchecked") Map<String, Object> rm = (Map<String, Object>) rmm;
                    q.rewards.add(parseReward(rm, q));
                }
            }
            c.quests.add(q);
        }
        return c;
    }

    private static Task parseTask(Map<String, Object> tm, Quest q, Map<String, Object> lang) {
        Task k = new Task();
        String idStr = Snbt.str(tm, "id", "");
        k.id = parseId(idStr);
        k.quest = q.id;
        k.rawType = Snbt.str(tm, "type", "");
        k.type = TaskType.of(k.rawType);
        k.title = text(lang.get("task." + idStr + ".title"));
        if (tm.get("icon") != null) k.icon = ItemRef.parse(tm.get("icon"));
        switch (k.type) {
            case ITEM -> {
                k.item = ItemRef.parse(tm.get("item"));
                k.amount = Math.max(1, Snbt.lng(tm, "count", 1));
                k.consume = Snbt.bool(tm, "consume_items", false);
            }
            case ADVANCEMENT -> {
                k.target = Snbt.str(tm, "advancement", "");
                k.extra = Snbt.str(tm, "criterion", "");
            }
            case STRUCTURE -> k.target = Snbt.str(tm, "structure", "");
            case BIOME -> k.target = Snbt.str(tm, "biome", "");
            case DIMENSION -> k.target = Snbt.str(tm, "dimension", "");
            case KILL -> {
                k.target = Snbt.str(tm, "entity", "");
                k.amount = Math.max(1, Snbt.lng(tm, "value", 1));
            }
            case KEY_VALUE -> {
                k.target = Snbt.str(tm, "key", "");
                k.amount = Math.max(1, Snbt.lng(tm, "value", 1));
            }
            case COBBLEMON -> {
                k.target = Snbt.str(tm, "action", "catch");
                k.extra = Snbt.str(tm, "pokemon", "");
                k.pokemonType = Snbt.str(tm, "pokemon_type", "");
                k.dexProgress = Snbt.str(tm, "dex_progress", "");
                k.amount = Math.max(1, Snbt.lng(tm, "amount", 1));
                k.shiny = Snbt.bool(tm, "shiny", false);
            }
            default -> {
            }
        }
        return k;
    }

    private static Reward parseReward(Map<String, Object> rm, Quest q) {
        Reward r = new Reward();
        r.id = parseId(Snbt.str(rm, "id", ""));
        r.quest = q.id;
        r.rawType = Snbt.str(rm, "type", "");
        r.excludeFromClaimAll = Snbt.bool(rm, "exclude_from_claim_all", false);
        if (rm.get("icon") != null) r.icon = ItemRef.parse(rm.get("icon"));
        r.title = Snbt.str(rm, "title", "");
        switch (r.rawType) {
            case "item" -> {
                r.type = RewardType.ITEM;
                r.item = ItemRef.parse(rm.get("item"));
                r.amount = Math.max(1, Snbt.lng(rm, "count", 1)) * Math.max(1, r.item.count);
                r.item.count = 1;
            }
            case "xp" -> {
                r.type = RewardType.XP;
                r.amount = Snbt.lng(rm, "xp", 0);
            }
            case "xp_levels" -> {
                r.type = RewardType.XP_LEVELS;
                r.amount = Snbt.lng(rm, "xp_levels", 1);
            }
            case "command" -> {
                r.type = RewardType.COMMAND;
                r.command = Snbt.str(rm, "command", "");
                r.console = Snbt.bool(rm, "console", false);
                if (r.command.isEmpty()) r.type = RewardType.UNKNOWN;
            }
            case "loot" -> {
                r.type = RewardType.LOOT;
                r.amount = Snbt.lng(rm, "table_id", 0);
                if (r.amount == 0) r.amount = parseId(Snbt.str(rm, "table", "0"));
            }
            case "extraquests:key_value", "unyieldingquests:key_value", "key_value" -> {
                r.type = RewardType.KEY_VALUE;
                r.key = Snbt.str(rm, "key", "");
                r.amount = Snbt.lng(rm, "value", 1);
                r.auto = Snbt.str(rm, "auto", "disabled").equalsIgnoreCase("enabled");
            }
            default -> r.type = RewardType.UNKNOWN;
        }
        return r;
    }

    // ---- file helpers ------------------------------------------------------

    private static Map<String, Object> readMap(Path p, List<String> warnings) {
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            return Snbt.parseMap(Files.readString(p, StandardCharsets.UTF_8));
        } catch (Exception e) {
            warnings.add(p.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static String text(Object o) {
        if (o == null) return "";
        if (o instanceof List<?> l) {
            StringBuilder sb = new StringBuilder();
            for (Object e : l) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(e);
            }
            return sb.toString();
        }
        return String.valueOf(o);
    }

    private static List<String> lines(Object o) {
        List<String> out = new ArrayList<>();
        if (o == null) return out;
        if (o instanceof List<?> l) {
            for (Object e : l) out.add(String.valueOf(e));
        } else {
            out.add(String.valueOf(o));
        }
        return out;
    }
}
