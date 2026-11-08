package dev.unyielding.quests.tutorial;

import com.mojang.brigadier.tree.CommandNode;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.net.Net;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/** Owns server configuration, per-login delivery and live command discovery. */
public final class TutorialManager {
    private static final String DEFAULT_RESOURCE = "/data/unyieldingquests/tutorial.json";
    private static final Path CONFIG = FMLPaths.CONFIGDIR.get().resolve("unyieldingquests/tutorial.json");
    private static final Map<UUID, Boolean> PENDING = new LinkedHashMap<>();
    private static TutorialContent content = new TutorialContent(List.of());
    private static int ticks;
    private static Method originData;
    private static Method originsComplete;
    private static boolean originReflectionFailed;

    private TutorialManager() { }

    public static void onServerStart() {
        PENDING.clear();
        TutorialPreview.clear();
        ticks = 0;
        originData = null;
        originsComplete = null;
        originReflectionFailed = false;
        content = new TutorialContent(List.of());
        reload();
    }

    public static void onServerStop() {
        PENDING.clear();
        TutorialPreview.clear();
        content = new TutorialContent(List.of());
    }

    /** Keep the last valid document on reload errors and preserve the editable file. */
    public static boolean reload() {
        try (InputStream input = TutorialManager.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input == null) throw new IOException("Packaged tutorial content is missing.");
            String defaults = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            TutorialContent packaged = TutorialContent.parse(defaults);
            if (content.pages().isEmpty()) content = packaged;
            Files.createDirectories(CONFIG.getParent());
            if (!Files.exists(CONFIG)) {
                try {
                    Files.writeString(CONFIG, defaults, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Another server action created the file before this one.
                }
            }
            if (Files.size(CONFIG) > TutorialContent.MAX_JSON_BYTES) {
                throw new IOException("Tutorial JSON exceeds 128 KB.");
            }
            String saved = Files.readString(CONFIG, StandardCharsets.UTF_8);
            String upgraded = TutorialContent.migrateLegacy(saved);
            TutorialContent.LoadResult result = TutorialContent.parseOrFallback(upgraded, content);
            content = result.content();
            if (result.error() != null) throw new IOException(result.error());
            if (!saved.equals(upgraded)) {
                Path backup = CONFIG.resolveSibling("tutorial.json.before-1.1.2");
                if (!Files.exists(backup)) Files.copy(CONFIG, backup);
                Path temporary = Files.createTempFile(CONFIG.getParent(), "tutorial-upgrade-", ".json");
                try {
                    Files.writeString(temporary, upgraded, StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary, CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, CONFIG, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            UnyieldingQuests.LOGGER.warn("Could not reload tutorial from {}. Keeping valid content. {}",
                    CONFIG, e.getMessage());
            return false;
        }
    }

    public static void onLogin(ServerPlayer player) {
        if (Config.TUTORIAL_ON_JOIN.get()) PENDING.put(player.getUUID(), true);
    }

    public static void onAutomaticChanged() {
        if (!Config.TUTORIAL_ON_JOIN.get()) PENDING.entrySet().removeIf(Map.Entry::getValue);
    }

    public static void onLogout(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    public static void open(ServerPlayer player) {
        if (originsReady(player)) {
            PENDING.remove(player.getUUID());
            send(player, false);
        } else {
            PENDING.put(player.getUUID(), false);
            player.sendSystemMessage(Component.literal("Your tutorial will open after you finish choosing your origins."));
        }
    }

    public static void onTick(MinecraftServer server) {
        if (++ticks % 20 != 0) return;
        Iterator<Map.Entry<UUID, Boolean>> waiting = PENDING.entrySet().iterator();
        while (waiting.hasNext()) {
            Map.Entry<UUID, Boolean> entry = waiting.next();
            if (entry.getValue() && !Config.TUTORIAL_ON_JOIN.get()) {
                waiting.remove();
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                waiting.remove();
                continue;
            }
            if (!originsReady(player)) continue;
            boolean automatic = entry.getValue();
            waiting.remove();
            send(player, automatic);
        }
    }

    /** NeoOrigins is optional; use its current server attachment instead of stale client state. */
    private static boolean originsReady(ServerPlayer player) {
        if (!ModList.get().isLoaded("neoorigins")) return true;
        if (originReflectionFailed) return false;
        try {
            if (originData == null) {
                originData = Class.forName("com.cyberday1.neoorigins.attachment.OriginAttachments")
                        .getMethod("originData");
                originsComplete = Class.forName("com.cyberday1.neoorigins.attachment.PlayerOriginData")
                        .getMethod("isHadAllOrigins");
            }
            AttachmentType<?> type = (AttachmentType<?>) originData.invoke(null);
            return player.hasData(type) && Boolean.TRUE.equals(originsComplete.invoke(player.getData(type)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            originReflectionFailed = true;
            UnyieldingQuests.LOGGER.warn("Tutorial is waiting because the NeoOrigins completion check is unavailable.", e);
            return false;
        }
    }

    private static void send(ServerPlayer player, boolean automatic) {
        try {
            Net.sendTo(player, new Net.TutorialSync(payload(player), automatic));
        } catch (RuntimeException e) {
            UnyieldingQuests.LOGGER.warn("Could not open tutorial for {}.", player.getGameProfile().getName(), e);
            player.sendSystemMessage(Component.literal("The tutorial could not open. Try /tutorial again."));
        }
    }

    public static CompoundTag payload(ServerPlayer player) {
        CommandSourceStack source = TutorialPreview.source(player);
        CommandNode<CommandSourceStack> root = player.getServer().getCommands().getDispatcher().getRoot();
        Set<String> ranks = TutorialRanks.activeRanks(player);
        Map<String, String> approved = new LinkedHashMap<>();
        CompoundTag result = new CompoundTag();
        ListTag pages = new ListTag();
        for (TutorialContent.Page page : content.pages()) {
            ListTag entries = new ListTag();
            for (TutorialContent.Entry entry : page.entries()) {
                boolean partyInvite = "oparties member invite player".equals(entry.command());
                boolean accessible = partyInvite
                        ? TutorialContent.registeredExecutable(root, entry.command())
                        : TutorialContent.available(root, source, entry.command());
                if (entry.visibleTo(ranks) && accessible) {
                    String description = commandDescription(entry.usage(), entry.description());
                    entries.add(entryTag(entry.usage(), description));
                    approved.putIfAbsent(entry.usage(), description);
                }
            }
            if (entries.isEmpty()) continue;
            CompoundTag item = new CompoundTag();
            item.putString("id", page.id());
            item.putString("title", page.title());
            String description = page.description();
            if ("friends".equals(page.id()) && !Config.SHARE_QUEST_PROGRESS.get()) {
                description = "Send a teleport request before visiting a friend. Parties support chat and invitations; quest progress stays personal on this server.";
            }
            item.putString("description", description);
            item.put("entries", entries);
            pages.add(item);
        }
        result.put("pages", pages);

        ListTag directory = new ListTag();
        int directoryBytes = 0;
        boolean truncated = false;
        // Only documented player routes belong here. Discovering every registered mod command
        // leaks operator-only and internally gated commands whose Brigadier roots look public.
        for (Map.Entry<String, String> entry : approved.entrySet()) {
            String usage = entry.getKey();
            String description = entry.getValue();
            int cost = usage.getBytes(StandardCharsets.UTF_8).length
                    + description.getBytes(StandardCharsets.UTF_8).length + 64;
            if (directoryBytes + cost > 320_000) {
                truncated = true;
                break;
            }
            directory.add(entryTag(usage, description));
            directoryBytes += cost;
        }
        result.put("directory", directory);
        result.putBoolean("directory_truncated", truncated);
        return result;
    }

    private static String commandDescription(String usage, String description) {
        if ("/quests daily".equals(usage)) return "Check your daily login reward status.";
        if (("/daily".equals(usage) || "/kit".equals(usage)) && ModList.get().isLoaded("curanks")) {
            return "Claim the daily kit available to your rank.";
        }
        return "/quests stats".equals(usage) ? "See your completed quest count." : description;
    }

    private static CompoundTag entryTag(String usage, String description) {
        CompoundTag entry = new CompoundTag();
        entry.putString("usage", usage);
        entry.putString("description", description);
        return entry;
    }
}
