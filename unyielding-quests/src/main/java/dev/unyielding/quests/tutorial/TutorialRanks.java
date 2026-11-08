package dev.unyielding.quests.tutorial;

import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Optional read-only adapter to the rank module bundled with Unyielding. */
public final class TutorialRanks {
    private static Access access;
    private static boolean warned;

    private TutorialRanks() { }

    /** Exact active rank IDs, never operator status or an assumed "any paid rank" grant. */
    public static Set<String> activeRanks(ServerPlayer player) {
        if (player == null) return Set.of();
        try {
            if (!ModList.get().isLoaded("curanks")) return Set.of();
            if (access == null) access = Access.resolve();
            // Read the current objects every time: /rank reload can replace them, and disk can lag live grants.
            Object config = access.config.get(null);
            Object store = access.store.get(null);
            if (config == null || store == null) return Set.of();
            Object entry = access.getPlayer.invoke(store, player.getUUID());
            if (entry == null) return Set.of();
            Object result = access.activeRanks.invoke(null, config, entry);
            if (!(result instanceof List<?> ranks)) throw new IllegalStateException("Unsupported active rank result");
            Set<String> ids = new LinkedHashSet<>();
            for (Object rank : ranks) {
                Object value = access.rankId.get(rank);
                if (!(value instanceof String id) || id.isBlank()) {
                    throw new IllegalStateException("Unsupported active rank ID");
                }
                ids.add(id.toLowerCase(Locale.ROOT));
            }
            // CuRanks.activeRanks already removes expired grants, deleted rank definitions and duplicates.
            return Set.copyOf(ids);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (!warned) {
                warned = true;
                UnyieldingQuests.LOGGER.warn("Tutorial rank access is unavailable; rank-only guide entries stay hidden.", failure);
            }
            return Set.of();
        }
    }

    private record Access(Field config, Field store, Method getPlayer, Method activeRanks, Field rankId) {
        static Access resolve() throws ReflectiveOperationException {
            Class<?> ranks = Class.forName("dev.unyielding.ranks.CuRanks");
            Class<?> configType = Class.forName("dev.unyielding.ranks.RanksConfig");
            Class<?> storeType = Class.forName("dev.unyielding.ranks.PlayerStore");
            Class<?> entryType = Class.forName("dev.unyielding.ranks.PlayerStore$Entry");
            Class<?> rankType = Class.forName("dev.unyielding.ranks.RanksConfig$Rank");
            Field config = ranks.getDeclaredField("cfg");
            Field store = ranks.getDeclaredField("store");
            Method getPlayer = storeType.getDeclaredMethod("get", UUID.class);
            Method activeRanks = ranks.getDeclaredMethod("activeRanks", configType, entryType);
            Field rankId = rankType.getDeclaredField("id");
            config.setAccessible(true);
            store.setAccessible(true);
            getPlayer.setAccessible(true);
            activeRanks.setAccessible(true);
            rankId.setAccessible(true);
            return new Access(config, store, getPlayer, activeRanks, rankId);
        }
    }
}
