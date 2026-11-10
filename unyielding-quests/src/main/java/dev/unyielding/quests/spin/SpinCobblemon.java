package dev.unyielding.quests.spin;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import dev.unyielding.quests.Config;
import dev.unyielding.quests.UnyieldingQuests;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Cobblemon side of the Lucky Spin (only loaded when Cobblemon is installed). */
final class SpinCobblemon {
    static boolean starterReady(ServerPlayer p) {
        return Cobblemon.INSTANCE.getPlayerDataManager().getGenericData(p).getStarterSelected();
    }

    /** What was handed over. */
    record Given(String name, int level, boolean shiny) {
    }

    private static final Object LOCK = new Object();
    private static List<? extends String> keyL, keyM, keyP, keyX, keyExtraL, keyExtraM, keyExtraP;
    private static List<String> poolL = List.of(), poolM = List.of(), poolP = List.of();
    private static int speciesCount = -1;

    private SpinCobblemon() {
    }

    /** Species ids (cobblemon:mewtwo) of a Pokemon tier. */
    static List<String> pool(LuckySpin.Tier t) {
        synchronized (LOCK) {
            List<? extends String> l = Config.SPIN_LEGENDARY_LABELS.get(), m = Config.SPIN_MYTHICAL_LABELS.get(), p = Config.SPIN_PARADOX_LABELS.get(), x = Config.SPIN_EXCLUDE.get();
            List<? extends String> extraL = Config.SPIN_EXTRA_LEGENDARY.get(), extraM = Config.SPIN_EXTRA_MYTHICAL.get(), extraP = Config.SPIN_EXTRA_PARADOX.get();
            int n = PokemonSpecies.getImplemented().size();
            if (l != keyL || m != keyM || p != keyP || x != keyX || extraL != keyExtraL || extraM != keyExtraM || extraP != keyExtraP || n != speciesCount) {
                keyL = l;
                keyM = m;
                keyP = p;
                keyX = x;
                keyExtraL = extraL;
                keyExtraM = extraM;
                keyExtraP = extraP;
                speciesCount = n;
                poolL = build(l, extraL, x, true);
                poolM = build(m, extraM, x, false);
                poolP = build(p, extraP, x, false);
                UnyieldingQuests.LOGGER.info("[spin] Pokemon prize pools: {} legendary, {} mythical, {} paradox", poolL.size(), poolM.size(), poolP.size());
            }
            return switch (t) {
                case LEGENDARY -> poolL;
                case MYTHICAL -> poolM;
                case PARADOX -> poolP;
                default -> List.of();
            };
        }
    }

    private static List<String> build(List<? extends String> labels, List<? extends String> extra, List<? extends String> exclude, boolean skipMythical) {
        Set<String> want = new HashSet<>();
        for (String s : labels) want.add(s.toLowerCase(Locale.ROOT).trim());
        Set<String> never = new HashSet<>();
        for (String s : exclude) never.add(norm(s));
        List<String> out = new ArrayList<>();
        for (Species sp : PokemonSpecies.getImplemented()) {
            String id = sp.getResourceIdentifier().toString();
            if (never.contains(id)) continue;
            boolean hit = false;
            for (String lab : sp.getLabels()) if (want.contains(lab.toLowerCase(Locale.ROOT))) hit = true;
            if (!hit) continue;
            if (skipMythical && sp.getLabels().contains("mythical")) continue;
            out.add(id);
        }
        for (String s : extra) {
            String id = norm(s);
            if (!id.isEmpty() && !never.contains(id) && !out.contains(id)) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl != null && PokemonSpecies.getByIdentifier(rl) != null) out.add(id);
            }
        }
        return out;
    }

    private static String norm(String s) {
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return s;
        return s.contains(":") ? s : "cobblemon:" + s;
    }

    static String pick(LuckySpin.Tier t, Random r) {
        List<String> p = pool(t);
        return p.isEmpty() ? "cobblemon:pikachu" : p.get(r.nextInt(p.size()));
    }

    /** Creates the Pokemon with random stats and puts it in the party (or the PC when the party is full). Null if it could not be created. */
    static Given give(ServerPlayer p, String speciesId, int level) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(speciesId);
            Species sp = rl == null ? null : PokemonSpecies.getByIdentifier(rl);
            if (sp == null) return null;
            PokemonProperties props = new PokemonProperties();
            props.setSpecies(speciesId);
            props.setLevel(level);
            Pokemon pk = props.create(p);
            boolean ok = Cobblemon.INSTANCE.getStorage().getParty(p).add(pk);
            if (!ok) ok = Cobblemon.INSTANCE.getStorage().getPC(p).add(pk);
            if (!ok) return null;
            return new Given(sp.getName(), pk.getLevel(), pk.getShiny());
        } catch (Throwable t) {
            UnyieldingQuests.LOGGER.error("[spin] Could not give {}", speciesId, t);
            return null;
        }
    }
}
