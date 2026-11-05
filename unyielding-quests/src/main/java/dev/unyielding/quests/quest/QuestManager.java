package dev.unyielding.quests.quest;

import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import dev.unyielding.quests.quest.QuestFile.Quest;
import dev.unyielding.quests.quest.QuestFile.Reward;
import dev.unyielding.quests.quest.QuestFile.RewardType;
import dev.unyielding.quests.quest.QuestFile.Task;
import dev.unyielding.quests.spin.LuckySpin;
import dev.unyielding.quests.team.Team;
import dev.unyielding.quests.team.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Server-side quest engine: loading, detection, completion, rewards, syncing. */
public final class QuestManager {
    private static final int CHUNK = 180_000;

    private static MinecraftServer server;
    private static QuestData data;
    private static boolean cobblemonHooked;

    public static volatile QuestFile file = new QuestFile();
    private static byte[][] chunks = new byte[0][];
    private static final Set<UUID> dirtyTeams = new HashSet<>();

    private QuestManager() {
    }

    // =====================================================================
    // Lifecycle / loading
    // =====================================================================

    public static Path questDir() {
        return FMLPaths.CONFIGDIR.get().resolve("unyieldingquests").resolve("quests");
    }

    public static void onServerStart(MinecraftServer s) {
        server = s;
        QuestFile.REGS = s.registryAccess();
        data = s.overworld().getDataStorage().computeIfAbsent(QuestData.factory(), QuestData.NAME);
        List<String> warnings = reload(false);
        for (String w : warnings) UnyieldingQuests.LOGGER.warn("[quests] {}", w);
        if (!cobblemonHooked && ModList.get().isLoaded("cobblemon")) {
            try {
                CobblemonHooks.register();
                cobblemonHooked = true;
                UnyieldingQuests.LOGGER.info("[quests] Cobblemon tasks enabled");
            } catch (Throwable t) {
                UnyieldingQuests.LOGGER.error("[quests] Could not hook into Cobblemon", t);
            }
        }
    }

    public static void onServerStop() {
        saveIfDue(true);
        server = null;
        data = null;
        dirtyTeams.clear();
    }

    /** (Re)loads the quest book from the config folder, creating it from FTB Quests data or the bundled defaults when missing. */
    public static List<String> reload(boolean forceImportFtb) {
        List<String> warnings = new ArrayList<>();
        Path dir = questDir();
        Path ftb = FMLPaths.CONFIGDIR.get().resolve("ftbquests").resolve("quests");
        try {
            boolean builtin = "builtin".equals(dev.unyielding.quests.Config.QUEST_BOOK_SOURCE.get());
            boolean have = Files.exists(dir.resolve("data.snbt")) || Files.isDirectory(dir.resolve("chapters"));
            if (builtin && !forceImportFtb) {
                dir = builtinDir();
                UnyieldingQuests.LOGGER.info("[quests] Using the built-in quest book (quest_book_source = builtin)");
            } else if (forceImportFtb && Files.isDirectory(ftb.resolve("chapters"))) {
                deleteDir(dir);
                copyDir(ftb, dir);
                UnyieldingQuests.LOGGER.info("[quests] Imported FTB Quests data from {}", ftb);
            } else if (!have) {
                if (Files.isDirectory(ftb.resolve("chapters"))) {
                    copyDir(ftb, dir);
                    UnyieldingQuests.LOGGER.info("[quests] Imported FTB Quests data from {}", ftb);
                } else {
                    extractDefaults(dir);
                    UnyieldingQuests.LOGGER.info("[quests] Installed the default quest book into {}", dir);
                }
            }
            QuestFile f = QuestFile.load(dir, warnings);
            file = f;
            ItemFilter.clearCache();
            CompoundTag tag = f.toTag();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            byte[] all = out.toByteArray();
            int n = Math.max(1, (all.length + CHUNK - 1) / CHUNK);
            chunks = new byte[n][];
            for (int i = 0; i < n; i++) chunks[i] = Arrays.copyOfRange(all, i * CHUNK, Math.min(all.length, (i + 1) * CHUNK));
            int tasks = f.tasks.size();
            UnyieldingQuests.LOGGER.info("[quests] Loaded {} chapters, {} quests, {} tasks, {} rewards ({} KB compressed)",
                    f.chapters.size(), f.quests.size(), tasks, f.rewards.size(), all.length / 1024);
            if (server != null) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    sendFile(p);
                    sendProgress(p);
                }
            }
        } catch (IOException | RuntimeException e) {
            warnings.add("Could not load quests: " + e);
            UnyieldingQuests.LOGGER.error("[quests] load failed", e);
        }
        return warnings;
    }

    private static Path builtinDir() {
        var info = ModList.get().getModFileById(UnyieldingQuests.MODID);
        return info.getFile().findResource("defaultquests");
    }

    private static void extractDefaults(Path dir) throws IOException {
        copyDir(builtinDir(), dir);
    }

    private static void copyDir(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String rel = from.relativize(p).toString();
                Path dest = to.resolve(rel.replace('\\', '/'));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) s.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(p);
        }
    }

    // =====================================================================
    // Access
    // =====================================================================

    public static TeamProgress progress(UUID teamId) {
        return data.get(teamId);
    }

    public static TeamProgress progressOf(ServerPlayer p) {
        return data.get(TeamManager.teamIdOf(p.getUUID()));
    }

    private static void markDirty(UUID teamId) {
        dirtyTeams.add(teamId);
        data.setDirty();
    }

    // =====================================================================
    // Sync
    // =====================================================================

    public static void onLogin(ServerPlayer p) {
        if (data == null) return;
        sendFile(p);
        sendSpinInfo(p);
        sendProgress(p);
        autoClaim(p);
        detect(p);
    }

    public static void sendSpinInfo(ServerPlayer p) {
        Net.sendTo(p, new Net.SpinInfo(LuckySpin.enabled(), LuckySpin.weights()));
    }

    static void sendFile(ServerPlayer p) {
        if (chunksDirty) rebuildChunks();
        for (int i = 0; i < chunks.length; i++) Net.sendTo(p, new Net.QuestFileChunk(i, chunks.length, chunks[i]));
    }

    // =====================================================================
    // In-game editing
    // =====================================================================

    private static boolean chunksDirty;
    private static long saveAt;

    private static void rebuildChunks() {
        try {
            CompoundTag tag = file.toTag();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            byte[] all = out.toByteArray();
            int n = Math.max(1, (all.length + CHUNK - 1) / CHUNK);
            chunks = new byte[n][];
            for (int i = 0; i < n; i++) chunks[i] = Arrays.copyOfRange(all, i * CHUNK, Math.min(all.length, (i + 1) * CHUNK));
            chunksDirty = false;
        } catch (IOException e) {
            UnyieldingQuests.LOGGER.error("[quests] could not pack the quest book", e);
        }
    }

    public static boolean canEdit(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    public static void onEdit(ServerPlayer p, Net.EditRequest req) {
        if (!canEdit(p)) {
            Net.sendTo(p, new Net.Notify(1, 0, "Only operators can edit the quest book."));
            return;
        }
        try {
            // the first edit moves the book out of the jar into the config folder, so edits survive updates
            if ("builtin".equals(Config.QUEST_BOOK_SOURCE.get())) {
                Path dir = questDir();
                deleteDir(dir);
                extractDefaults(dir);
                Config.QUEST_BOOK_SOURCE.set("config_folder");
                Config.SPEC.save();
                UnyieldingQuests.LOGGER.info("[quests] Editing started: quest book copied to {} and quest_book_source set to config_folder", dir);
            }
        } catch (IOException e) {
            UnyieldingQuests.LOGGER.error("[quests] could not copy the quest book to the config folder", e);
            Net.sendTo(p, new Net.Notify(1, 0, "Could not copy the quest book to the config folder (see log)."));
            return;
        }
        String err = QuestEdit.apply(file, req.tag());
        if (err != null) {
            Net.sendTo(p, new Net.Notify(1, 0, err));
            return;
        }
        ItemFilter.clearCache();
        chunksDirty = true;
        saveAt = server.getTickCount() + 40;
        for (ServerPlayer o : server.getPlayerList().getPlayers()) Net.sendTo(o, new Net.EditApply(req.tag()));
    }

    private static void saveIfDue(boolean force) {
        if (saveAt == 0) return;
        if (!force && server != null && server.getTickCount() < saveAt) return;
        saveAt = 0;
        try {
            QuestWriter.write(file, questDir());
            UnyieldingQuests.LOGGER.info("[quests] Quest book saved to {}", questDir());
        } catch (IOException e) {
            UnyieldingQuests.LOGGER.error("[quests] could not save the quest book", e);
        }
    }

    public static void sendProgress(ServerPlayer p) {
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        CompoundTag t = new CompoundTag();
        t.putUUID("team", tid);
        t.put("p", data.get(tid).toTag(p.getUUID()));
        Net.sendTo(p, new Net.ProgressSync(t));
    }

    private static void flushDirty() {
        if (dirtyTeams.isEmpty()) return;
        List<UUID> ids = new ArrayList<>(dirtyTeams);
        dirtyTeams.clear();
        for (UUID tid : ids) {
            Team t = TeamManager.get(tid);
            if (t == null) continue;
            for (UUID u : t.members.keySet()) {
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) sendProgress(p);
            }
        }
    }

    // =====================================================================
    // Team changes
    // =====================================================================

    public static void onTeamChanged(UUID player, Team from, Team to) {
        if (data == null) return;
        if (to != null && to.type != Team.Type.PLAYER) {
            TeamProgress mine = data.get(player);
            TeamProgress target = data.get(to.id);
            if (from == null || from.type == Team.Type.PLAYER) target.mergeFrom(mine);
        }
        if (from != null) markDirty(from.id);
        if (to != null) markDirty(to.id);
        markDirty(player);
    }

    public static void onTeamDeleted(UUID teamId) {
        if (data == null) return;
        data.teams.remove(teamId);
        data.setDirty();
    }

    // =====================================================================
    // Availability
    // =====================================================================

    public static boolean depsDone(QuestFile f, Quest q, TeamProgress tp) {
        for (long dep : q.deps) {
            if (tp.questDone(dep)) continue;
            Quest d = f.quests.get(dep);
            if (d == null || d.optional) continue;
            return false;
        }
        return true;
    }

    public static boolean available(QuestFile f, Quest q, TeamProgress tp) {
        return f.isFlexible(q) || depsDone(f, q, tp);
    }

    // =====================================================================
    // Ticking / detection
    // =====================================================================

    public static void onServerTick() {
        if (server == null || data == null) return;
        QuestFile f = file;
        int delay = Config.DETECTION_DELAY_OVERRIDE.get() > 0 ? Config.DETECTION_DELAY_OVERRIDE.get() : f.detectionDelay;
        long now = server.getTickCount();
        int idx = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((now + idx) % delay == 0 && p.isAlive()) detect(p);
            idx++;
        }
        flushDirty();
        saveIfDue(false);
    }

    public static void detect(ServerPlayer p) {
        QuestFile f = file;
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        TeamProgress tp = data.get(tid);
        List<ItemStack> stacks = null;
        for (Quest q : f.quests.values()) {
            if (tp.questDone(q.id) || !available(f, q, tp)) continue;
            boolean changed = false;
            for (Task t : q.tasks) {
                if (tp.taskDone(t.id)) continue;
                switch (t.type) {
                    case ITEM -> {
                        if (t.consume) break;
                        if (stacks == null) stacks = inventoryStacks(p);
                        long n = 0;
                        for (ItemStack s : stacks) if (t.item.matches(s)) n += s.getCount();
                        if (n >= t.amount) changed |= completeTask(tp, t);
                    }
                    case ADVANCEMENT -> {
                        if (advancementDone(p, t)) changed |= completeTask(tp, t);
                    }
                    case STRUCTURE -> {
                        if (inStructure(p, t.target)) changed |= completeTask(tp, t);
                    }
                    case BIOME -> {
                        if (inBiome(p, t.target)) changed |= completeTask(tp, t);
                    }
                    case DIMENSION -> {
                        if (p.level().dimension().location().toString().equals(t.target)) changed |= completeTask(tp, t);
                    }
                    case KEY_VALUE -> {
                        if (tp.key(t.target) >= t.amount) changed |= completeTask(tp, t);
                    }
                    case COBBLEMON -> {
                        if (cobblemonHooked && t.target.equals("have_registered")) {
                            long v = CobblemonHooks.registered(p, t);
                            if (v >= t.amount) {
                                changed |= completeTask(tp, t);
                            } else {
                                Long old = tp.taskProgress.get(t.id);
                                if (old == null || old != v) {
                                    tp.taskProgress.put(t.id, v);
                                    tp.dirty = true;
                                    markDirty(tid);
                                }
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            if (changed) checkQuest(f, q, tid, tp);
        }
    }

    private static List<ItemStack> inventoryStacks(ServerPlayer p) {
        List<ItemStack> l = new ArrayList<>();
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    private static boolean advancementDone(ServerPlayer p, Task t) {
        ResourceLocation rl = ResourceLocation.tryParse(t.target);
        if (rl == null) return false;
        AdvancementHolder h = server.getAdvancements().get(rl);
        if (h == null) return false;
        AdvancementProgress ap = p.getAdvancements().getOrStartProgress(h);
        if (t.extra.isEmpty()) return ap.isDone();
        var c = ap.getCriterion(t.extra);
        return c != null && c.isDone();
    }

    private static boolean inStructure(ServerPlayer p, String target) {
        if (target.isEmpty()) return false;
        ServerLevel level = p.serverLevel();
        try {
            if (target.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(target.substring(1));
                if (rl == null) return false;
                return level.structureManager().getStructureWithPieceAt(p.blockPosition(), TagKey.create(Registries.STRUCTURE, rl)).isValid();
            }
            ResourceLocation rl = ResourceLocation.tryParse(target);
            if (rl == null) return false;
            Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure s = reg.get(rl);
            if (s == null) return false;
            return level.structureManager().getStructureWithPieceAt(p.blockPosition(), s).isValid();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean inBiome(ServerPlayer p, String target) {
        if (target.isEmpty()) return false;
        Holder<Biome> b = p.serverLevel().getBiome(p.blockPosition());
        if (target.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(target.substring(1));
            return rl != null && b.is(TagKey.create(Registries.BIOME, rl));
        }
        ResourceLocation rl = ResourceLocation.tryParse(target);
        return rl != null && b.is(ResourceKey.create(Registries.BIOME, rl));
    }

    // =====================================================================
    // Completion
    // =====================================================================

    /** @return true if the task state changed */
    private static boolean completeTask(TeamProgress tp, Task t) {
        if (!tp.completedTasks.add(t.id)) return false;
        tp.taskProgress.remove(t.id);
        tp.dirty = true;
        return true;
    }

    private static void checkQuest(QuestFile f, Quest q, UUID tid, TeamProgress tp) {
        markDirty(tid);
        if (tp.questDone(q.id)) return;
        for (Task t : q.tasks) if (!tp.taskDone(t.id)) return;
        tp.completedQuests.add(q.id);
        Team team = TeamManager.get(tid);
        if (team == null) return;
        String title = q.displayTitle().replace('&', '§');
        for (UUID u : team.members.keySet()) {
            ServerPlayer m = server.getPlayerList().getPlayer(u);
            if (m == null) continue;
            Net.sendTo(m, new Net.Notify(0, q.id, ""));
            if (Config.ANNOUNCE_COMPLETION.get()) {
                m.sendSystemMessage(Component.literal("Quest completed: ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(title)));
            }
            for (Reward r : q.rewards) {
                if (r.auto && !tp.isClaimed(u, r.id)) claimReward(m, tp, tid, r, true);
            }
        }
    }

    /** Called on any progress change of a team for a given quest (adds progress to a counted task). */
    private static void addProgress(ServerPlayer p, TeamProgress tp, UUID tid, Quest q, Task t, long delta, boolean absolute) {
        long cur = tp.progress(t.id);
        long next = absolute ? Math.max(cur, delta) : cur + delta;
        long max = t.maxProgress();
        if (next >= max) {
            if (completeTask(tp, t)) checkQuest(file, q, tid, tp);
        } else if (next != cur) {
            tp.taskProgress.put(t.id, next);
            tp.dirty = true;
            markDirty(tid);
        }
    }

    // =====================================================================
    // Event driven tasks
    // =====================================================================

    public static void onKill(ServerPlayer p, Entity victim) {
        if (data == null) return;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        TeamProgress tp = data.get(tid);
        QuestFile f = file;
        for (Quest q : f.quests.values()) {
            if (tp.questDone(q.id) || !available(f, q, tp)) continue;
            for (Task t : q.tasks) {
                if (t.type != QuestFile.TaskType.KILL || tp.taskDone(t.id)) continue;
                if (entityMatches(t.target, victim.getType(), id)) addProgress(p, tp, tid, q, t, 1, false);
            }
        }
    }

    private static boolean entityMatches(String target, EntityType<?> type, ResourceLocation id) {
        if (target.isEmpty()) return true;
        if (target.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(target.substring(1));
            return rl != null && type.is(TagKey.create(Registries.ENTITY_TYPE, rl));
        }
        return id != null && id.toString().equals(target);
    }

    /** Generic Cobblemon style event: action name + species/type/shiny filters. */
    public static void cobblemonEvent(ServerPlayer p, String action, String species, Set<String> types, boolean shiny, long value, boolean absolute) {
        if (data == null || p == null) return;
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        TeamProgress tp = data.get(tid);
        QuestFile f = file;
        for (Quest q : f.quests.values()) {
            if (tp.questDone(q.id) || !available(f, q, tp)) continue;
            for (Task t : q.tasks) {
                if (t.type != QuestFile.TaskType.COBBLEMON || tp.taskDone(t.id) || !t.target.equals(action)) continue;
                if (!t.extra.isEmpty()) {
                    String want = t.extra.contains(":") ? t.extra : "cobblemon:" + t.extra;
                    if (species == null || !species.equalsIgnoreCase(want)) continue;
                }
                if (!t.pokemonType.isEmpty() && (types == null || !types.contains(t.pokemonType.toLowerCase()))) continue;
                if (t.shiny && !shiny) continue;
                addProgress(p, tp, tid, q, t, value, absolute);
            }
        }
    }

    // =====================================================================
    // Player actions
    // =====================================================================

    public static void onTaskAction(ServerPlayer p, Net.TaskAction a) {
        if (data == null) return;
        QuestFile f = file;
        Task t = f.tasks.get(a.taskId());
        if (t == null) return;
        Quest q = f.quests.get(t.quest);
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        TeamProgress tp = data.get(tid);
        if (q == null || tp.questDone(q.id) || tp.taskDone(t.id) || !available(f, q, tp)) return;
        if (a.action() == 0 && t.type == QuestFile.TaskType.CHECKMARK) {
            if (completeTask(tp, t)) checkQuest(f, q, tid, tp);
        } else if (a.action() == 1 && t.type == QuestFile.TaskType.ITEM && t.consume) {
            long need = t.amount - tp.progress(t.id);
            long taken = 0;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize() && taken < need; i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && t.item.matches(s)) {
                    int n = (int) Math.min(s.getCount(), need - taken);
                    s.shrink(n);
                    taken += n;
                }
            }
            if (taken > 0) addProgress(p, tp, tid, q, t, taken, false);
        }
        flushDirty();
    }

    public static void onClaim(ServerPlayer p, Net.Claim c) {
        if (data == null) return;
        QuestFile f = file;
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        TeamProgress tp = data.get(tid);
        List<LuckySpin.Spin> spins = new ArrayList<>();
        switch (c.mode()) {
            case 0 -> {
                Reward r = f.rewards.get(c.id());
                if (r != null) {
                    claimReward(p, tp, tid, r, false);
                } else {
                    Quest q = f.spinQuests.get(c.id());
                    if (q != null) spin(p, tp, tid, q, spins);
                }
            }
            case 1 -> {
                Quest q = f.quests.get(c.id());
                if (q != null) {
                    for (Reward r : q.rewards) claimReward(p, tp, tid, r, false);
                    spin(p, tp, tid, q, spins);
                }
            }
            default -> {
                for (Quest q : f.quests.values()) {
                    if (!tp.questDone(q.id)) continue;
                    for (Reward r : q.rewards) if (!r.excludeFromClaimAll) claimReward(p, tp, tid, r, false);
                    spin(p, tp, tid, q, spins);
                }
            }
        }
        for (int i = 0; i < spins.size(); i += 16) Net.sendTo(p, new Net.SpinResult(new ArrayList<>(spins.subList(i, Math.min(spins.size(), i + 16)))));
        detect(p);
        flushDirty();
    }

    /** Rolls the quest's Lucky Spin for this player if it is still unused. */
    private static void spin(ServerPlayer p, TeamProgress tp, UUID tid, Quest q, List<LuckySpin.Spin> out) {
        if (!LuckySpin.enabled() || !tp.questDone(q.id)) return;
        long sid = LuckySpin.spinId(q.id);
        if (tp.isClaimed(p.getUUID(), sid)) return;
        Set<Long> mine = tp.claimed.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        mine.add(sid);
        tp.dirty = true;
        markDirty(tid);
        try {
            out.add(LuckySpin.roll(p, q.displayTitle().replaceAll("[&§][0-9a-fk-orA-FK-OR]", "")));
        } catch (RuntimeException e) {
            mine.remove(sid);
            UnyieldingQuests.LOGGER.error("[spin] roll failed", e);
            p.sendSystemMessage(Component.literal("The Lucky Spin failed; please try again.").withStyle(ChatFormatting.RED));
        }
    }

    public static void autoClaim(ServerPlayer p) {
        UUID tid = TeamManager.teamIdOf(p.getUUID());
        TeamProgress tp = data.get(tid);
        for (long qid : new ArrayList<>(tp.completedQuests)) {
            Quest q = file.quests.get(qid);
            if (q == null) continue;
            for (Reward r : q.rewards) if (r.auto) claimReward(p, tp, tid, r, true);
        }
    }

    private static boolean claimReward(ServerPlayer p, TeamProgress tp, UUID tid, Reward r, boolean silent) {
        Quest q = file.quests.get(r.quest);
        if (q == null || !tp.questDone(q.id) || tp.isClaimed(p.getUUID(), r.id)) return false;
        switch (r.type) {
            case ITEM -> {
                if (r.item == null || !r.item.exists() || r.item.isFilter()) {
                    if (!silent) p.sendSystemMessage(Component.literal("That reward item is not available in this modpack.").withStyle(ChatFormatting.RED));
                    return false;
                }
                ItemStack base = r.item.stack();
                long left = r.amount;
                int max = Math.max(1, base.getMaxStackSize());
                while (left > 0) {
                    int n = (int) Math.min(left, max);
                    ItemStack give = base.copyWithCount(n);
                    ItemHandlerHelper.giveItemToPlayer(p, give);
                    left -= n;
                }
            }
            case XP -> p.giveExperiencePoints((int) Math.min(Integer.MAX_VALUE, r.amount));
            case XP_LEVELS -> p.giveExperienceLevels((int) Math.min(Integer.MAX_VALUE, r.amount));
            case KEY_VALUE -> tp.keys.merge(r.key, r.amount, Long::sum);
            case COMMAND -> {
                String cmd = r.command.replace("{p}", p.getGameProfile().getName()).replace("{team}", TeamManager.get(tid) == null ? "" : TeamManager.get(tid).name);
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                MinecraftServer srv = p.getServer();
                if (srv == null) return false;
                net.minecraft.commands.CommandSourceStack src = r.console
                        ? srv.createCommandSourceStack().withPosition(p.position()).withLevel(p.serverLevel()).withSuppressedOutput()
                        : p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
                try {
                    srv.getCommands().performPrefixedCommand(src, cmd);
                } catch (RuntimeException e) {
                    UnyieldingQuests.LOGGER.error("[quests] command reward failed: {}", cmd, e);
                }
            }
            case LOOT -> {
                QuestFile.LootTable lt = file.lootTables.get(r.amount);
                if (lt == null || lt.entries.isEmpty()) {
                    if (!silent) p.sendSystemMessage(Component.literal("That reward table is missing.").withStyle(ChatFormatting.RED));
                    return false;
                }
                java.util.Random rng = new java.util.Random();
                for (int n = 0; n < lt.lootSize; n++) {
                    float total = 0;
                    for (QuestFile.LootEntry e : lt.entries) total += Math.max(0, e.weight);
                    float roll = rng.nextFloat() * total;
                    QuestFile.LootEntry pick = lt.entries.get(lt.entries.size() - 1);
                    for (QuestFile.LootEntry e : lt.entries) {
                        roll -= Math.max(0, e.weight);
                        if (roll < 0) {
                            pick = e;
                            break;
                        }
                    }
                    if (pick.item != null && pick.item.exists()) {
                        ItemStack give = pick.item.stack().copyWithCount(pick.count);
                        ItemHandlerHelper.giveItemToPlayer(p, give);
                        p.sendSystemMessage(Component.literal("You received: " + pick.count + " × " + give.getHoverName().getString()).withStyle(ChatFormatting.GREEN));
                    }
                }
            }
            default -> {
                return false;
            }
        }
        tp.claimed.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(r.id);
        tp.dirty = true;
        markDirty(tid);
        return true;
    }

    // =====================================================================
    // Admin helpers (used by commands)
    // =====================================================================

    public static String adminComplete(ServerPlayer target, long questId) {
        Quest q = file.quests.get(questId);
        if (q == null) return "Unknown quest id.";
        UUID tid = TeamManager.teamIdOf(target.getUUID());
        TeamProgress tp = data.get(tid);
        for (Task t : q.tasks) completeTask(tp, t);
        checkQuest(file, q, tid, tp);
        flushDirty();
        return null;
    }

    public static String adminReset(ServerPlayer target, long questId) {
        UUID tid = TeamManager.teamIdOf(target.getUUID());
        TeamProgress tp = data.get(tid);
        if (questId == 0) {
            tp.completedQuests.clear();
            tp.completedTasks.clear();
            tp.taskProgress.clear();
            tp.keys.clear();
            tp.claimed.clear();
        } else {
            Quest q = file.quests.get(questId);
            if (q == null) return "Unknown quest id.";
            tp.completedQuests.remove(q.id);
            for (Task t : q.tasks) {
                tp.completedTasks.remove(t.id);
                tp.taskProgress.remove(t.id);
            }
            for (Reward r : q.rewards) for (Set<Long> s : tp.claimed.values()) s.remove(r.id);
        }
        markDirty(tid);
        flushDirty();
        return null;
    }

    public static int completedCount(ServerPlayer p) {
        return progressOf(p).completedQuests.size();
    }
}
