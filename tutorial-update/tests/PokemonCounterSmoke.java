package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution;
import com.cobblemon.mod.common.api.pokemon.feature.IntSpeciesFeature;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.requirements.PokemonPropertiesRequirement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Runs against the real companion class after the production mixins transform it. */
final class PokemonCounterSmoke {
    private static final String DEFEATS = "mawonna_verified_dark_defeats";
    private static final String DEFEATS_1000 = "mawonna_verified_dark_defeats_1000";
    private static final String MEALS = "mawonna_distinct_meals";
    private static final String MARRIED = "mawonna_married";

    static void exercise(ServerPlayer player, Map<String, Object> report) throws Exception {
        Class<?> companion = Class.forName("dev.mawonna.MawonnaCompanion");
        Method credit = method(companion, "creditDefeat", Pokemon.class);
        Method meal = method(companion, "rememberMeal", Pokemon.class, String.class);
        Method ready = method(companion, "readyToMarry", Pokemon.class);
        Pokemon first = pokemon("mawile");
        Pokemon second = pokemon("sliggoo");
        set(first, DEFEATS, 666);
        set(second, DEFEATS_1000, 1000);
        require((Integer)credit.invoke(null, first) == 667, "Transformed counter retains its old cap");
        require((Integer)credit.invoke(null, second) == 1001, "Transformed secondary counter retains its old cap");
        set(first, DEFEATS, 2_000_000_000);
        require((Integer)credit.invoke(null, first) == 2_000_000_000, "Progress upper bound overflowed");
        for (int i = 0; i < 51; i++) require((Boolean)meal.invoke(null, first, "cloud-test:dish_" + i), "Distinct meal ignored");
        require(!(Boolean)meal.invoke(null, first, "cloud-test:dish_0"), "Repeated meal was credited twice");
        require(value(first, MEALS) == 51, "Distinct meals retain their old cap");
        Pokemon restored = new Pokemon().loadFromNBT(player.registryAccess(), first.saveToNBT(player.registryAccess(), new CompoundTag()));
        require(value(restored, MEALS) == 51 && value(restored, DEFEATS) == 2_000_000_000,
                "Expanded progress did not survive native persistence");

        Evolution evolution = first.getForm().getEvolutions().stream()
                .filter(candidate -> candidate.getId().equals("mawile_mawonna")).findFirst().orElseThrow();
        PokemonProperties target = evolution.getRequirements().stream()
                .filter(PokemonPropertiesRequirement.class::isInstance).map(PokemonPropertiesRequirement.class::cast)
                .map(PokemonPropertiesRequirement::getTarget)
                .filter(properties -> properties.getCustomProperties().stream().anyMatch(property ->
                        property instanceof IntSpeciesFeature feature && MARRIED.equals(feature.getName())))
                .findFirst().orElseThrow();
        var originalProperties = new ArrayList<>(target.getCustomProperties());
        Map<IntSpeciesFeature, Integer> originals = new IdentityHashMap<>();
        for (var property : originalProperties) if (property instanceof IntSpeciesFeature feature)
            originals.put(feature, feature.getValue());
        Gender originalGender = target.getGender();
        try {
            // These objects came from the bundled JSON through Cobblemon's actual adapters.
            threshold(target, DEFEATS, 666); threshold(target, MEALS, 49);
            set(first, DEFEATS, 665); set(first, MEALS, 49); set(first, MARRIED, 0);
            require(!(Boolean)ready.invoke(null, first), "Ring accepted insufficient defeats");
            set(first, DEFEATS, 666); set(first, MEALS, 48);
            require(!(Boolean)ready.invoke(null, first), "Ring accepted insufficient distinct meals");
            set(first, MEALS, 49);
            require((Boolean)ready.invoke(null, first), "Default conditions no longer allow the ring");
            require(!evolution.test(first), "An unchanged marriage requirement was bypassed");

            threshold(target, DEFEATS, 700); threshold(target, MEALS, 50);
            set(first, DEFEATS, 701); set(first, MEALS, 51);
            require((Boolean)ready.invoke(null, first), "Edited higher thresholds were not used by the ring");
            require(!evolution.test(first), "Higher thresholds removed the marriage gate");
            set(first, MARRIED, 1);
            require(evolution.test(first), "Overshot progress failed the native evolution requirement");
            require(!(Boolean)ready.invoke(null, first), "A completed vow can be repeated");
            set(first, MARRIED, 0);

            threshold(target, DEFEATS, 10); threshold(target, MEALS, 2);
            set(first, DEFEATS, 11); set(first, MEALS, 3);
            require((Boolean)ready.invoke(null, first), "Edited lower thresholds were not used by the ring");
            target.setGender(Gender.FEMALE); first.setGender(Gender.MALE);
            require(!(Boolean)ready.invoke(null, first), "An ordinary edited condition was bypassed");
            first.setGender(Gender.FEMALE);
            require((Boolean)ready.invoke(null, first), "An ordinary edited condition was ignored");
            first.getPersistentData().putUUID("mawonna_spouse", UUID.randomUUID());
            require(!(Boolean)ready.invoke(null, first), "Persisted spouse allowed another vow");
            first.getPersistentData().remove("mawonna_spouse");

            var withoutMarriage = new ArrayList<>(target.getCustomProperties());
            withoutMarriage.removeIf(property -> property instanceof IntSpeciesFeature feature && MARRIED.equals(feature.getName()));
            target.setCustomProperties(withoutMarriage);
            require(evolution.test(first), "Explicit removal of the marriage condition was not respected");
            require(!(Boolean)ready.invoke(null, first), "A removed marriage gate still consumes a ring");
        } finally {
            target.setCustomProperties(originalProperties);
            originals.forEach(IntSpeciesFeature::setValue);
            target.setGender(originalGender);
        }
        report.put("custom_progress_compatibility", "passed");
        report.put("custom_progress_native_persistence", "passed");
        report.put("custom_evolution_thresholds_and_vow_gates", "passed");
    }

    private static Method method(Class<?> owner, String name, Class<?>... types) throws Exception {
        Method method = owner.getDeclaredMethod(name, types); method.setAccessible(true); return method;
    }
    private static Pokemon pokemon(String name) {
        Pokemon pokemon = new Pokemon(); pokemon.setSpecies(Objects.requireNonNull(PokemonSpecies.getByName(name))); return pokemon;
    }
    private static void threshold(PokemonProperties properties, String key, int value) {
        IntSpeciesFeature feature = properties.getCustomProperties().stream().filter(IntSpeciesFeature.class::isInstance)
                .map(IntSpeciesFeature.class::cast).filter(candidate -> candidate.getName().equals(key)).findFirst().orElseThrow();
        feature.setValue(value);
    }
    private static void set(Pokemon pokemon, String key, int value) { new IntSpeciesFeature(key, value).apply(pokemon); }
    private static int value(Pokemon pokemon, String key) {
        return pokemon.getFeature(key) instanceof IntSpeciesFeature feature ? feature.getValue() : 0;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
