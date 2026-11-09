package dev.unyielding.quests.settings;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.requirements.PokemonPropertiesRequirement;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Compatibility for the original, persistent custom evolution progress features. */
public final class PokemonCounterRules {
    public static final int MAX_PROGRESS = 2_000_000_000;
    public static final String DEFEATS = "mawonna_verified_dark_defeats";
    public static final String DEFEATS_1000 = "mawonna_verified_dark_defeats_1000";
    public static final String MEALS = "mawonna_distinct_meals";
    public static final String MARRIED = "mawonna_married";
    private static final String MEAL_IDS = "mawonna_verified_dishes_v1";
    private static final Set<String> THRESHOLDS = Set.of(DEFEATS, DEFEATS_1000, MEALS);

    private PokemonCounterRules() {}

    public static boolean hasThreshold(PokemonProperties target) {
        return target.getCustomProperties().stream().anyMatch(property ->
                property instanceof IntSpeciesFeature number && THRESHOLDS.contains(number.getName()));
    }

    /** Only progress thresholds use >=; every unrelated Pokemon property keeps its native test. */
    public static boolean propertiesMatch(PokemonProperties target, Pokemon pokemon, boolean pendingMarriage) {
        var remaining = new ArrayList<>(target.getCustomProperties());
        for (var property : target.getCustomProperties()) {
            if (!(property instanceof IntSpeciesFeature number)) continue;
            if (THRESHOLDS.contains(number.getName())) {
                if (value(pokemon, number.getName()) < number.getValue()) return false;
                remaining.remove(property);
            } else if (pendingMarriage && MARRIED.equals(number.getName()) && number.getValue() == 1) {
                remaining.remove(property);
            }
        }
        PokemonProperties ordinary = target.copy();
        ordinary.setCustomProperties(remaining);
        return ordinary.matches(pokemon);
    }

    public static int value(Pokemon pokemon, String key) {
        var feature = pokemon.getFeature(key);
        return feature instanceof IntSpeciesFeature number ? number.getValue() : 0;
    }

    public static int creditDefeat(Pokemon pokemon) {
        String species = pokemon.getSpecies().getName().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        String key = switch (species) {
            case "mawile" -> DEFEATS;
            case "corvisquire", "sliggoo", "sylveon" -> DEFEATS_1000;
            default -> throw new IllegalArgumentException("No verified defeat feature for this species");
        };
        int next = (int)Math.min(MAX_PROGRESS, Math.max(0L, (long)value(pokemon, key)) + 1L);
        new IntSpeciesFeature(key, next).apply(pokemon);
        return next;
    }

    public static boolean rememberMeal(Pokemon pokemon, String canonical) {
        Set<String> ids = new LinkedHashSet<>();
        for (Tag tag : pokemon.getPersistentData().getList(MEAL_IDS, Tag.TAG_STRING)) ids.add(tag.getAsString());
        if (!ids.add(canonical)) return false;
        ListTag saved = new ListTag();
        ids.forEach(id -> saved.add(StringTag.valueOf(id)));
        pokemon.getPersistentData().put(MEAL_IDS, saved);
        new IntSpeciesFeature(MEALS, ids.size()).apply(pokemon);
        return true;
    }

    /** A ring fulfills an explicit marriage gate after the evolution's other conditions pass. */
    public static boolean readyToMarry(Pokemon pokemon) {
        if (!pokemon.getSpecies().getName().equalsIgnoreCase("mawile") || value(pokemon, MARRIED) != 0
                || pokemon.getPersistentData().hasUUID("mawonna_spouse")) return false;
        for (Evolution evolution : pokemon.getForm().getEvolutions()) {
            boolean marriageRequired = false;
            boolean otherConditionsPass = true;
            for (var requirement : evolution.getRequirements()) {
                if (requirement instanceof PokemonPropertiesRequirement properties) {
                    boolean containsMarriage = properties.getTarget().getCustomProperties().stream().anyMatch(property ->
                            property instanceof IntSpeciesFeature number && MARRIED.equals(number.getName()) && number.getValue() == 1);
                    marriageRequired |= containsMarriage;
                    otherConditionsPass &= propertiesMatch(properties.getTarget(), pokemon, containsMarriage);
                } else {
                    otherConditionsPass &= requirement.check(pokemon);
                }
            }
            if (marriageRequired && otherConditionsPass) return true;
        }
        return false;
    }
}
