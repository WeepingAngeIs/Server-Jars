package dev.unyielding.testquest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestData;
import dev.unyielding.quests.quest.QuestFile;
import dev.unyielding.quests.quest.QuestManager;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import net.neoforged.fml.ModList;

final class QuestChecks {
    static final Path OUT = Path.of(System.getProperty("quests.receipts"));
    static final long CHAPTER = 0x123AAE985B3F9476L;
    static final long OLD = 0x6C361539CA23140CL;
    private static final long OLD_INTRO_TASK = 0x0B7EFEC19FEE395EL;
    private static final long OLD_BARREL = 0x4675B6328328B627L;
    private static final long OLD_REWARD = 0x38969277EB7310A6L;
    private static final String MOD = "anyastorage:";
    private static final String TIERS = "unyieldingquests:anya_storage_plus/";
    static final String PHASE = System.getenv("QUEST_PHASE");

    static void check(boolean ok, String name) {
        if (!ok) throw new IllegalStateException(name);
    }

    static QuestData data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME);
    }

    static void record(String name, CompoundTag tag) throws Exception {
        NbtIo.writeCompressed(tag, OUT.resolve(name + ".nbt"));
    }

    static CompoundTag read(String name) throws Exception {
        return NbtIo.readCompressed(OUT.resolve(name + ".nbt"), NbtAccounter.unlimitedHeap());
    }

    static void foldersAndMods() {
        check(ModList.get().isLoaded("sophisticatedstorage") && ModList.get().isLoaded("sophisticatedbackpacks"), "original mods");
        check(ModList.get().isLoaded("anyastorage") != PHASE.equals("without"), "expected optional mod");
        check("config_folder".equals(Config.QUEST_BOOK_SOURCE.get()), "pack config folder");
        check(QuestManager.file.chapterMap.get(OLD).quests.size() == 29, "shipped Storage Solutions chapter");
    }

    // Run before the first player logs into the upgraded save.
    static void started(MinecraftServer server) throws Exception {
        foldersAndMods();
        if (PHASE.equals("after") || PHASE.equals("restart")) {
            String generation = PHASE.equals("after") ? "before" : "after";
            CompoundTag actual = data(server).save(new CompoundTag(), server.registryAccess());
            check(equalSavedRecords(read(generation + "-progress"), actual), "all saved progress and reward records after restart");
            CompoundTag book = QuestManager.file.toTag();
            check(book.getList("chapters", Tag.TAG_COMPOUND).removeIf(t -> ((CompoundTag)t).getLong("id") == CHAPTER), "new chapter");
            check(book.equals(read("before-book")), "all previous decoded chapters, tasks, rewards, texts and groups");
        }
    }

    // Long arrays in quest progress represent sets. Their serialized order can change after loading.
    private static boolean equalSavedRecords(CompoundTag a, CompoundTag b) {
        return normalized(a).equals(normalized(b));
    }

    private static String normalized(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var entries = new TreeMap<String, String>();
            for (String key : compound.getAllKeys()) entries.put(key, normalized(compound.get(key)));
            return entries.toString();
        }
        if (tag instanceof ListTag list) return list.stream().map(QuestChecks::normalized).sorted().toList().toString();
        if (tag instanceof LongArrayTag array) return Arrays.toString(Arrays.stream(array.getAsLongArray()).sorted().toArray());
        return tag.toString();
    }

    static void before(ServerPlayer player) throws Exception {
        check(!QuestManager.file.chapterMap.containsKey(CHAPTER), "old binary has no add-on chapter");
        QuestManager.onTaskAction(player, new Net.TaskAction(OLD_INTRO_TASK, 0));
        carry(player, item("sophisticatedstorage:barrel"));
        var progress = QuestManager.progressOf(player);
        check(progress.questDone(OLD_BARREL), "real Sophisticated Storage barrel quest completed");
        QuestManager.onClaim(player, new Net.Claim(OLD_REWARD, 0));
        QuestData saved = data(player.server);
        check(saved.claimedRewards.get(player.getUUID()).contains(OLD_REWARD), "original reward claim recorded");
        var partial = QuestManager.file.tasks.values().stream()
                .filter(t -> t.type == QuestFile.TaskType.KILL && t.maxProgress() > 3 && !progress.taskDone(t.id))
                .findFirst().orElseThrow();
        progress.taskProgress.put(partial.id, 3L);
        progress.keys.put("storage-quest-fixture", 47L);
        saved.setDirty();
        player.awardStat(Stats.JUMP, 9);
        var stats = new CompoundTag();
        stats.putUUID("player", player.getUUID());
        stats.putInt("jumps", player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)));
        stats.putInt("xp", player.totalExperience);
        record("before-stats", stats);
        record("before-progress", saved.save(new CompoundTag(), player.registryAccess()));
        record("before-book", QuestManager.file.toTag());
    }

    static ItemStack item(String id) {
        var entry = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElseThrow();
        return new ItemStack(entry);
    }

    static CraftingRecipe recipe(ServerPlayer player, String key) {
        var holder = player.server.getRecipeManager().byKey(ResourceLocation.parse(MOD + key)).orElseThrow();
        return (CraftingRecipe) holder.value();
    }

    static ItemStack ingredient(JsonObject data) {
        if (data.has("item")) return item(data.get("item").getAsString());
        check("minecraft:planks".equals(data.get("tag").getAsString()), "known material tag " + data);
        return new ItemStack(Items.OAK_PLANKS);
    }

    static ItemStack craft(ServerPlayer player, String key) throws Exception {
        var path = ResourceLocation.parse(MOD + "recipe/" + key + ".json");
        List<ItemStack> cells = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        try (Reader reader = player.server.getResourceManager().getResourceOrThrow(path).openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("ingredients")) {
                int i = 0;
                for (var e : json.getAsJsonArray("ingredients")) cells.set(i++, ingredient(e.getAsJsonObject()));
            } else {
                int y = 0;
                for (var line : json.getAsJsonArray("pattern")) {
                    String row = line.getAsString();
                    for (int x = 0; x < row.length(); x++) {
                        String symbol = row.substring(x, x+1);
                        if (!symbol.isBlank()) cells.set(3*y+x, ingredient(json.getAsJsonObject("key").getAsJsonObject(symbol)));
                    }
                    y++;
                }
            }
        }
        return assemble(player, key, cells);
    }

    static ItemStack assemble(ServerPlayer player, String key, List<ItemStack> cells) {
        var input = CraftingInput.of(3, 3, cells);
        var matching = player.server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, player.level()).orElseThrow();
        var recipe = recipe(player, key);
        check(recipe.matches(input, player.level()) && matching.value() == recipe, "crafting table selects " + key);
        return recipe.assemble(input, player.registryAccess());
    }

    static void carry(ServerPlayer p, ItemStack stack) {
        p.getInventory().setItem(8, stack);
        p.inventoryMenu.broadcastChanges();
        QuestManager.detect(p);
    }

    static QuestFile.Quest itemQuest(String key) {
        return QuestManager.file.chapterMap.get(CHAPTER).quests.stream()
                .filter(q -> q.tasks.getFirst().item != null && (MOD+key).equals(q.tasks.getFirst().item.id))
                .findFirst().orElseThrow();
    }

    static void play(ServerPlayer p) throws Exception {
        var progress = QuestManager.progressOf(p);
        var stats = read("before-stats");
        check(stats.getUUID("player").equals(p.getUUID()) && progress.questDone(OLD_BARREL), "returning Survival player");
        check(progress.keys.get("storage-quest-fixture") == 47L && !progress.taskProgress.isEmpty(), "counter and partial objective");
        check(stats.getInt("jumps") == p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)), "old vanilla statistic");
        int xp = p.totalExperience;
        QuestManager.onClaim(p, new Net.Claim(OLD_REWARD, 0));
        check(p.totalExperience == xp && xp == stats.getInt("xp"), "old reward cannot pay twice");
        var ch = QuestManager.file.chapterMap.get(CHAPTER);
        check(ch.quests.size() == 18 && ch.group == QuestManager.file.chapterMap.get(OLD).group, "18 quests in Base and Supplies");
        check("Anya Storage Plus".equals(ch.title) && ch.quests.stream().allMatch(q -> !q.title.isBlank() && !q.desc.isEmpty()), "translated quest text");
        var pickup = itemQuest("pickup_upgrade");
        carry(p, craft(p, "pickup_upgrade"));
        check(!progress.questDone(pickup.id) && !QuestManager.available(QuestManager.file, pickup, progress), "advanced path waits for backpack");
        var bag = craft(p, "backpack");
        carry(p, bag);
        check(progress.questDone(itemQuest("backpack").id), "starter backpack");
        var tierNames = List.of("iron", "gold", "diamond", "netherite");
        var materials = List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.NETHERITE_INGOT);
        for (int i=0; i<tierNames.size(); i++) {
            String tier = tierNames.get(i);
            var quest = ch.quests.stream().filter(q -> (TIERS+tier).equals(q.tasks.getFirst().target)).findFirst().orElseThrow();
            check(!progress.questDone(quest.id), "lower tier cannot satisfy " + tier);
            var slots = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
            slots.set(4, bag);
            int cost = i == 3 ? 4 : 8;
            for (int k=0; k<cost; k++) slots.set(k<4?k:k+1, new ItemStack(materials.get(i)));
            bag = assemble(p, "backpack_upgrade", slots);
            check(tier.equals(bag.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("Tier")), tier+" crafted");
            carry(p, bag);
            check(progress.questDone(quest.id), tier+" distinctive backpack condition");
        }
        for (var q : ch.quests) {
            var t = q.tasks.getFirst();
            if (t.item == null || t.item.id.equals(MOD+"backpack")) continue;
            String key = t.item.id.substring(MOD.length());
            var stack = craft(p, key);
            check(t.item.matches(stack), "recipe result " + key);
            carry(p, stack);
            check(progress.questDone(q.id) && !stack.isEmpty(), "non-consuming objective " + q.title);
        }
        check(ch.quests.stream().allMatch(q -> progress.questDone(q.id)), "all 18 quests through item detection");
        long reward = itemQuest("backpack").rewards.getFirst().id;
        QuestManager.onClaim(p, new Net.Claim(reward,0));
        int claimed = p.totalExperience;
        QuestManager.onClaim(p, new Net.Claim(reward,0));
        check(claimed == xp+20 && p.totalExperience == claimed, "new XP reward paid exactly once");
        record("after-progress", data(p.server).save(new CompoundTag(), p.registryAccess()));
    }

    static void otherBookMode() {
        Config.QUEST_BOOK_SOURCE.set("builtin");
        check(QuestManager.reload(false).stream().noneMatch(s -> s.contains("Could not load quests")), "built-in reload");
        check(QuestManager.file.chapters.stream().filter(c -> c.id == CHAPTER).count() == 1, "built-in chapter appears once");
        Config.QUEST_BOOK_SOURCE.set("config_folder");
        QuestManager.reload(false);
        check(QuestManager.file.chapters.stream().filter(c -> c.id == CHAPTER).count() == 1, "editable book reload");
    }
}
