package dev.unyielding.quests.quest;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.pokedex.SpeciesDexRecord;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cobblemon integration for the "cobblemon" quest task. Only loaded when Cobblemon is installed.
 */
public final class CobblemonHooks {
    private static final Map<UUID, Set<UUID>> SCANNED = new HashMap<>();
    private static final Map<UUID, Cache> DEX_CACHE = new HashMap<>();

    private record Cache(long time, Map<String, Integer> caughtByType, Map<String, Integer> seenByType, Map<String, Integer> totalByType) {
    }

    private CobblemonHooks() {
    }

    private static <T> kotlin.jvm.functions.Function1<T, kotlin.Unit> h(java.util.function.Consumer<T> c) {
        return t -> {
            c.accept(t);
            return kotlin.Unit.INSTANCE;
        };
    }

    public static void register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, h(e -> fire(e.getPlayer(), "catch", e.getPokemon(), 1, false)));
        CobblemonEvents.FOSSIL_REVIVED.subscribe(Priority.NORMAL, h(e -> fire(e.getPlayer(), "revive_fossil", e.getPokemon(), 1, false)));
        CobblemonEvents.STARTER_CHOSEN.subscribe(Priority.NORMAL, h(e -> fire(e.getPlayer(), "select_starter", e.getPokemon(), 1, false)));
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL, h(e -> {
            ServerPlayer p = e.getPokemon().getOwnerPlayer();
            fire(p, "evolve", e.getPokemon(), 1, false);
            fire(p, "evolve_into", e.getPokemon(), 1, false);
        }));
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL, h(e ->
                fire(e.getPokemon().getOwnerPlayer(), "level_up_to", e.getPokemon(), e.getNewLevel(), true)));
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, h(e -> {
            if (e.getWasWildCapture()) return;
            for (BattleActor a : e.getWinners()) {
                if (a instanceof PlayerBattleActor pba) {
                    ServerPlayer p = pba.getEntity();
                    if (p != null) QuestManager.cobblemonEvent(p, "defeat", null, null, false, 1, false);
                }
            }
        }));
        CobblemonEvents.POKEMON_SCANNED.subscribe(Priority.NORMAL, h(e -> {
            ServerPlayer p = e.getPlayer();
            var le = e.getScannedEntity().resolveEntityScan();
            Set<UUID> seen = SCANNED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            if (le != null && !seen.add(le.getUUID())) return;
            if (seen.size() > 2000) seen.clear();
            QuestManager.cobblemonEvent(p, "scan", null, null, false, 1, false);
        }));
        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(Priority.NORMAL, h(e -> {
            Player owner = e.getBobber().getPlayerOwner();
            if (owner instanceof ServerPlayer sp && e.getPokemon() != null) fire(sp, "fishing", e.getPokemon().getPokemon(), 1, false);
        }));
    }

    private static void fire(ServerPlayer p, String action, Pokemon pk, long value, boolean absolute) {
        if (p == null || pk == null) return;
        Species s = pk.getSpecies();
        Set<String> types = new HashSet<>();
        types.add(s.getPrimaryType().getName().toLowerCase());
        if (s.getSecondaryType() != null) types.add(s.getSecondaryType().getName().toLowerCase());
        QuestManager.cobblemonEvent(p, action, s.getResourceIdentifier().toString(), types, pk.getShiny(), value, absolute);
    }

    /** Number of Pokedex entries (matching the type filter) the player has registered at the wanted level. */
    public static long registered(ServerPlayer p, QuestFile.Task t) {
        long now = System.currentTimeMillis();
        Cache c = DEX_CACHE.get(p.getUUID());
        if (c == null || now - c.time > 5000) {
            Map<String, Integer> caught = new HashMap<>();
            Map<String, Integer> seen = new HashMap<>();
            Map<String, Integer> total = new HashMap<>();
            for (Species s : PokemonSpecies.getImplemented()) {
                String a = s.getPrimaryType().getName().toLowerCase();
                String b = s.getSecondaryType() == null ? null : s.getSecondaryType().getName().toLowerCase();
                for (String key : new String[]{"", a, b}) if (key != null) total.merge(key, 1, Integer::sum);
            }
            PokedexManager dex = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(p);
            for (SpeciesDexRecord r : dex.getSpeciesRecords().values()) {
                boolean isCaught = r.hasAtLeast(PokedexEntryProgress.OWNED);
                boolean isSeen = r.hasAtLeast(PokedexEntryProgress.SEEN);
                if (!isSeen) continue;
                Species s = PokemonSpecies.INSTANCE.getByIdentifier(r.getId());
                if (s == null) continue;
                String a = s.getPrimaryType().getName().toLowerCase();
                String b = s.getSecondaryType() == null ? null : s.getSecondaryType().getName().toLowerCase();
                for (String key : new String[]{"", a, b}) {
                    if (key == null) continue;
                    seen.merge(key, 1, Integer::sum);
                    if (isCaught) caught.merge(key, 1, Integer::sum);
                }
            }
            c = new Cache(now, caught, seen, total);
            DEX_CACHE.put(p.getUUID(), c);
        }
        String key = t.pokemonType.toLowerCase();
        String mode = t.dexProgress.toLowerCase();
        Map<String, Integer> m = mode.startsWith("caught") ? c.caughtByType : c.seenByType;
        long n = m.getOrDefault(key, 0);
        if (mode.endsWith("percent")) {
            long tot = c.totalByType.getOrDefault(key, 0);
            return tot <= 0 ? 0 : n * 100 / tot;
        }
        return n;
    }
}
