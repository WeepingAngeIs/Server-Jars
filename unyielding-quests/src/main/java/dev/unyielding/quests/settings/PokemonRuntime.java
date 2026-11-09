package dev.unyielding.quests.settings;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools;
import com.cobblemon.mod.common.api.spawning.SpawnSet;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import dev.unyielding.quests.UnyieldingQuests;
import kotlin.Unit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/** The Cobblemon 1.8.4 data boundary. Runtime instances keep their identities during edits. */
public final class PokemonRuntime {
    private static Map<String, JsonObject> currentSpecies = Map.of();
    private static Map<String, JsonObject> currentSpawns = Map.of();

    private PokemonRuntime() { }

    /** Source JSON, including additions, is roundtrippable; live Kotlin objects are not. */
    public static Map<String, JsonObject> captureSpecies(MinecraftServer server) {
        Map<String, JsonObject> files = readResources(server.getResourceManager(), "species");
        Map<String, JsonObject> result = new TreeMap<>();
        for (var entry : files.entrySet()) {
            ResourceLocation file = ResourceLocation.parse(entry.getKey());
            String path = file.getPath();
            String key = file.getNamespace() + ":" + path.substring(path.lastIndexOf('/') + 1);
            if (PokemonSpecies.getByIdentifier(ResourceLocation.parse(key)) != null)
                result.put(key, entry.getValue().deepCopy());
        }
        for (JsonObject addition : readResources(server.getResourceManager(), "species_additions").values()) {
            if (!addition.has("target")) continue;
            String target = canonical(addition.get("target").getAsString());
            JsonObject destination = result.get(target);
            if (destination == null) continue;
            for (var property : addition.entrySet()) {
                if (property.getKey().equals("target")) continue;
                if ((property.getKey().equals("forms") || property.getKey().equals("evolutions"))
                        && property.getValue().isJsonArray()) {
                    JsonArray merged = destination.has(property.getKey())
                            ? destination.getAsJsonArray(property.getKey()).deepCopy() : new JsonArray();
                    for (JsonElement value : property.getValue().getAsJsonArray()) merged.add(value.deepCopy());
                    destination.add(property.getKey(), merged);
                } else destination.add(property.getKey(), property.getValue().deepCopy());
            }
        }
        for (Species species : PokemonSpecies.getSpecies()) {
            String id = species.getResourceIdentifier().toString();
            if (!result.containsKey(id))
                throw new IllegalStateException("No editable data resource was found for loaded species " + id + ".");
        }
        currentSpecies = copy(result);
        return result;
    }

    public static Map<String, JsonObject> captureSpawns(MinecraftServer server) {
        Map<String, JsonObject> result = readResources(server.getResourceManager(), "spawn_pool_world");
        currentSpawns = copy(result);
        return result;
    }

    public static JsonObject speciesJson(String id) {
        JsonObject value = currentSpecies.get(id);
        return value == null ? null : value.deepCopy();
    }

    private static Map<String, JsonObject> readResources(ResourceManager manager, String folder) {
        Map<String, JsonObject> result = new TreeMap<>();
        for (var entry : manager.listResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
            if (entry.getKey().getNamespace().equals("pixelmon")) continue;
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":" + path.substring(folder.length() + 1, path.length() - 5);
            try (var reader = entry.getValue().openAsReader()) {
                result.put(id, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("Cannot read active " + folder + " data " + id + ".", e);
            }
        }
        return result;
    }

    public static String canonical(String id) {
        return (id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("cobblemon", id)).toString();
    }

    public static void validateSpecies(String id, JsonObject json) {
        Species before = PokemonSpecies.getByIdentifier(ResourceLocation.parse(id));
        if (before == null) throw new IllegalArgumentException("Unknown Pokemon species.");
        if (!json.has("name") || !json.get("name").getAsString().equals(before.getName()))
            throw new IllegalArgumentException("The species identity/name cannot be changed.");
        if (!json.has("nationalPokedexNumber") || json.get("nationalPokedexNumber").getAsInt() != before.getNationalPokedexNumber())
            throw new IllegalArgumentException("The Pokedex identity cannot be changed.");
        JsonObject original = currentSpecies.get(id);
        Set<String> known = fieldNames(Species.class);
        for (String key : json.keySet()) if (!known.contains(key) && (original == null || !original.has(key)))
            throw new IllegalArgumentException("Unknown species setting: " + key);
        validateData(json, false);
        if (!json.has("baseStats") || !json.has("primaryType") || !json.has("abilities"))
            throw new IllegalArgumentException("Species need baseStats, primaryType and abilities.");
        Species parsed = parseSpecies(id, json);
        parsed.initialize();
        // Resolve lazy evolution sets now, before an edit can reach live data.
        parsed.getEvolutions().size();
        for (FormData form : parsed.getForms()) form.getEvolutions().size();
        Set<String> oldForms = new HashSet<>();
        for (FormData form : before.getForms()) if (form != before.getStandardForm()) oldForms.add(form.getName());
        Set<String> newForms = new HashSet<>();
        for (FormData form : parsed.getForms()) if (form != parsed.getStandardForm()) {
            if (!newForms.add(form.getName())) throw new IllegalArgumentException("Duplicate form name.");
        }
        if (!newForms.containsAll(oldForms))
            throw new IllegalArgumentException("Existing forms may be edited, but cannot be removed or renamed while the server is running.");
    }

    private static Species parseSpecies(String id, JsonObject json) {
        Species result = PokemonSpecies.INSTANCE.getGson().fromJson(json, Species.class);
        result.setResourceIdentifier(ResourceLocation.parse(id));
        return result;
    }

    /** Validate registered names explicitly: some Cobblemon adapters silently discard unknown moves. */
    private static void validateData(JsonElement element, boolean form) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) validateData(child, form);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("baseStats")) {
            JsonObject stats = object.getAsJsonObject("baseStats");
            Set<String> names = Set.of("hp", "attack", "defence", "special_attack", "special_defence", "speed");
            if (!form && !stats.keySet().containsAll(names)) throw new IllegalArgumentException("All six base stats are required.");
            for (var stat : stats.entrySet()) {
                if (!names.contains(stat.getKey())) throw new IllegalArgumentException("Unknown base stat: " + stat.getKey());
                double value = stat.getValue().getAsDouble();
                if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > 10000)
                    throw new IllegalArgumentException("Base stats must be whole numbers from 1 to 10000.");
            }
        }
        numeric(object, "catchRate", 0, 255, true);
        numeric(object, "baseFriendship", 0, 255, true);
        numeric(object, "baseExperienceYield", 0, 1000000, true);
        numeric(object, "eggCycles", 0, 1000000, true);
        numeric(object, "maleRatio", -1, 1, false);
        for (String name : List.of("baseScale", "height", "weight")) numeric(object, name, 0.0001, 10000, false);
        for (String type : List.of("primaryType", "secondaryType")) if (object.has(type) && !object.get(type).isJsonNull()) {
            String name = object.get(type).getAsString();
            if (ElementalTypes.get(name) == null) throw new IllegalArgumentException("Unknown elemental type: " + name);
        }
        if (object.has("abilities")) {
            JsonArray array = object.getAsJsonArray("abilities");
            if (array.isEmpty()) throw new IllegalArgumentException("At least one ability is required.");
            for (JsonElement ability : array) if (ability.isJsonPrimitive()) {
                String name = ability.getAsString();
                if (name.startsWith("h:")) name = name.substring(2);
                if (Abilities.get(name) == null) throw new IllegalArgumentException("Unknown ability: " + name);
            }
        }
        if (object.has("moves")) for (JsonElement move : object.getAsJsonArray("moves")) {
            if (!move.isJsonPrimitive()) throw new IllegalArgumentException("Use registered learnset entries such as 1:tackle.");
            String entry = move.getAsString();
            int colon = entry.indexOf(':');
            if (colon < 1 || Moves.getByName(entry.substring(colon + 1)) == null)
                throw new IllegalArgumentException("Unknown move entry: " + entry);
            String prefix = entry.substring(0, colon);
            if (!Set.of("tm", "egg", "tutor", "legacy", "special", "form_change").contains(prefix)) {
                try { if (Integer.parseInt(prefix) < 0) throw new NumberFormatException(); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Unknown learnset category: " + prefix); }
            }
        }
        if (object.has("evolutions")) {
            Set<String> ids = new HashSet<>();
            for (JsonElement value : object.getAsJsonArray("evolutions")) {
                JsonObject evolution = value.getAsJsonObject();
                if (!evolution.has("id") || !ids.add(evolution.get("id").getAsString()))
                    throw new IllegalArgumentException("Every evolution needs a unique id.");
                if (!evolution.has("variant")) throw new IllegalArgumentException("Every evolution needs a variant.");
                if (evolution.has("result")) validatePokemonProperties(evolution.get("result").getAsString());
                if (evolution.has("learnableMoves")) for (JsonElement move : evolution.getAsJsonArray("learnableMoves"))
                    if (Moves.getByName(move.getAsString()) == null) throw new IllegalArgumentException("Unknown evolution move: " + move.getAsString());
            }
        }
        for (var child : object.entrySet()) if (!child.getKey().equals("moves") && !child.getKey().equals("abilities"))
            validateData(child.getValue(), form || child.getKey().equals("forms"));
    }

    private static void numeric(JsonObject object, String name, double min, double max, boolean integer) {
        if (!object.has(name) || object.get(name).isJsonNull()) return;
        double value = object.get(name).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max || integer && value != Math.rint(value))
            throw new IllegalArgumentException(name + " is outside its allowed range.");
    }

    private static Set<String> fieldNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Class<?> owner = type; owner != Object.class && owner != null; owner = owner.getSuperclass())
            for (Field field : owner.getDeclaredFields()) if (!Modifier.isStatic(field.getModifiers())
                    && !Modifier.isTransient(field.getModifiers()) && !field.getName().contains("$")) {
                SerializedName alias = field.getAnnotation(SerializedName.class);
                names.add(alias == null ? field.getName() : alias.value());
                if (alias != null) names.addAll(Arrays.asList(alias.alternate()));
            }
        return names;
    }

    private static void validatePokemonProperties(String value) {
        String species = value.trim().split("\\s+")[0];
        if (species.startsWith("species=")) species = species.substring(8);
        if (!species.equals("random") && PokemonSpecies.getByIdentifier(ResourceLocation.parse(canonical(species))) == null)
            throw new IllegalArgumentException("Unknown Pokemon in properties: " + species);
    }

    public static void validateSpawns(Map<String, JsonObject> files) {
        parseSpawns(files);
    }

    private static Map<ResourceLocation, SpawnSet> parseSpawns(Map<String, JsonObject> files) {
        Map<ResourceLocation, SpawnSet> result = new LinkedHashMap<>();
        var pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        Set<String> ids = new HashSet<>();
        for (var entry : files.entrySet()) {
            JsonObject json = entry.getValue();
            boolean edited = !json.equals(currentSpawns.get(entry.getKey()));
            if (!json.has("spawns") || !json.get("spawns").isJsonArray())
                throw new IllegalArgumentException("A spawn file needs a spawns array.");
            for (JsonElement value : json.getAsJsonArray("spawns")) {
                JsonObject spawn = value.getAsJsonObject();
                if (!spawn.has("id") || spawn.get("id").getAsString().isBlank())
                    throw new IllegalArgumentException("Every spawn needs an id.");
                if (!ids.add(spawn.get("id").getAsString()) && edited)
                    throw new IllegalArgumentException("Duplicate spawn id: " + spawn.get("id").getAsString());
                if (edited && spawn.has("pokemon") && spawn.get("pokemon").isJsonPrimitive())
                    validatePokemonProperties(spawn.get("pokemon").getAsString());
                if (spawn.has("weight") && (!Double.isFinite(spawn.get("weight").getAsDouble()) || spawn.get("weight").getAsDouble() < 0))
                    throw new IllegalArgumentException("Spawn weights must be finite and nonnegative.");
                if (spawn.has("percentage") && (spawn.get("percentage").getAsDouble() < 0 || spawn.get("percentage").getAsDouble() > 100))
                    throw new IllegalArgumentException("Spawn percentages must be between 0 and 100.");
            }
            SpawnSet set = pool.getGson().fromJson(json, SpawnSet.class);
            if (set.getSpawns().size() != json.getAsJsonArray("spawns").size())
                throw new IllegalArgumentException("A spawn could not be parsed.");
            for (int index = 0; index < set.getSpawns().size(); index++) {
                SpawnDetail detail = set.getSpawns().get(index);
                detail.getSpawnablePositionType();
                if (detail.getBucket().isBlank()) throw new IllegalArgumentException("Every spawn needs a bucket.");
                if (edited && detail.isModDependencySatisfied() && !detail.isValid() && set.isEnabled())
                    throw new IllegalArgumentException("Invalid enabled spawn: " + detail.getId());
                if (edited) validateConditionFields(json.getAsJsonArray("spawns").get(index).getAsJsonObject(), detail,
                        originalSpawn(entry.getKey(), detail.getId()));
            }
            result.put(ResourceLocation.parse(entry.getKey()), set);
        }
        return result;
    }

    private static JsonObject originalSpawn(String file, String id) {
        JsonObject old = currentSpawns.get(file);
        if (old == null || !old.has("spawns")) return null;
        for (JsonElement value : old.getAsJsonArray("spawns")) {
            JsonObject spawn = value.getAsJsonObject();
            if (spawn.has("id") && spawn.get("id").getAsString().equals(id)) return spawn;
        }
        return null;
    }

    private static void validateConditionFields(JsonObject spawn, SpawnDetail detail, JsonObject original) {
        Set<String> known = new HashSet<>();
        known.add("type");
        for (var condition : detail.getConditions()) known.addAll(fieldNames(condition.getClass()));
        for (var condition : detail.getAnticonditions()) known.addAll(fieldNames(condition.getClass()));
        for (String key : List.of("condition", "conditions", "anticondition", "anticonditions")) if (spawn.has(key)) {
            JsonElement value = spawn.get(key);
            if (original != null && value.equals(original.get(key))) continue;
            JsonArray list = value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
            if (!value.isJsonArray()) list.add(value);
            for (JsonElement item : list) {
                JsonObject condition = item.getAsJsonObject();
                for (String name : condition.keySet()) if (!known.contains(name))
                    throw new IllegalArgumentException("Unknown spawn condition setting: " + name);
                for (String axis : List.of("X", "Y", "Z", "Light", "SkyLight")) {
                    String min = "min" + axis, max = "max" + axis;
                    if (condition.has(min) && condition.has(max)
                            && condition.get(min).getAsDouble() > condition.get(max).getAsDouble())
                        throw new IllegalArgumentException(min + " must not exceed " + max + ".");
                }
                for (String light : List.of("minLight", "maxLight", "minSkyLight", "maxSkyLight"))
                    numeric(condition, light, 0, 15, true);
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (condition.has("dimensions")) for (JsonElement dimension : condition.getAsJsonArray("dimensions")) {
                    ResourceLocation id = ResourceLocation.parse(dimension.getAsString());
                    if (server != null && !server.levelKeys().contains(ResourceKey.create(Registries.DIMENSION, id)))
                        throw new IllegalArgumentException("Unknown spawn dimension: " + id);
                }
                if (condition.has("biomes")) for (JsonElement biome : condition.getAsJsonArray("biomes")) {
                    String name = biome.getAsString();
                    ResourceLocation id = ResourceLocation.parse(name.startsWith("#") ? name.substring(1) : name);
                    if (!name.startsWith("#") && server != null
                            && !server.registryAccess().registryOrThrow(Registries.BIOME).containsKey(id))
                        throw new IllegalArgumentException("Unknown spawn biome: " + id);
                }
            }
        }
    }

    public static boolean hasActiveBattles() {
        try {
            Field field = BattleRegistry.class.getDeclaredField("battleMap");
            field.setAccessible(true);
            return !((Map<?, ?>)field.get(null)).isEmpty();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot verify battle safety.", e); }
    }

    public static void apply(MinecraftServer server, Map<String, JsonObject> species, Map<String, JsonObject> spawnFiles) {
        if (hasActiveBattles()) throw new IllegalStateException("Finish active Pokemon battles before applying Pokemon edits.");
        Map<String, Species> replacements = new LinkedHashMap<>();
        for (var entry : species.entrySet()) if (!entry.getValue().equals(currentSpecies.get(entry.getKey()))) {
            validateSpecies(entry.getKey(), entry.getValue());
            replacements.put(entry.getKey(), parseSpecies(entry.getKey(), entry.getValue()));
        }
        Map<ResourceLocation, SpawnSet> spawns = parseSpawns(spawnFiles);
        var pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        List<SpawnDetail> oldSpawns = new ArrayList<>(pool.getDetails());
        Map<Set<MoveTemplate>, Set<MoveTemplate>> evolutionMoves = new IdentityHashMap<>();
        for (Species value : PokemonSpecies.getSpecies()) {
            saveEvolutionMoves(value.getMoves().getEvolutionMoves(), evolutionMoves);
            for (FormData form : value.getForms()) saveEvolutionMoves(form.getMoves().getEvolutionMoves(), evolutionMoves);
        }
        List<Restore> rollback = new ArrayList<>();
        try {
            for (var entry : replacements.entrySet()) {
                Species target = PokemonSpecies.getByIdentifier(ResourceLocation.parse(entry.getKey()));
                replaceSpecies(target, entry.getValue(), rollback);
            }
            // Rebuild derived evolution learnsets, including targets of a removed evolution.
            for (Species value : PokemonSpecies.getSpecies()) {
                value.getMoves().getEvolutionMoves().clear();
                for (FormData form : value.getForms()) form.getMoves().getEvolutionMoves().clear();
            }
            // Evolution move resolution must see all of the new species definitions together.
            for (Species value : PokemonSpecies.getSpecies()) value.resolveEvolutionMoves$common();
            CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().reload(spawns);
            CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().onServerLoad(server);
            publishSpecies(server);
            currentSpecies = copy(species);
            currentSpawns = copy(spawnFiles);
        } catch (RuntimeException | ReflectiveOperationException failure) {
            Collections.reverse(rollback);
            for (Restore restore : rollback) try { restore.field().set(restore.owner(), restore.value()); }
            catch (ReflectiveOperationException e) { failure.addSuppressed(e); }
            evolutionMoves.forEach((set, before) -> { set.clear(); set.addAll(before); });
            try {
                pool.getDetails().clear();
                pool.getDetails().addAll(oldSpawns);
                pool.onServerLoad(server);
                publishSpecies(server);
            } catch (RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw new IllegalStateException("Pokemon edit could not be applied; previous values restored. " + Objects.toString(failure.getMessage(), ""), failure);
        }
    }

    private static void saveEvolutionMoves(Set<MoveTemplate> moves, Map<Set<MoveTemplate>, Set<MoveTemplate>> saved) {
        saved.putIfAbsent(moves, new HashSet<>(moves));
    }

    private record Restore(Object owner, Field field, Object value) { }

    private static void replaceSpecies(Species target, Species source, List<Restore> rollback) throws ReflectiveOperationException {
        Map<String, FormData> oldForms = new HashMap<>();
        for (FormData form : target.getForms()) oldForms.put(form.getName(), form);
        FormData standard = target.getStandardForm();
        source.initialize();
        List<FormData> forms = new ArrayList<>();
        for (FormData candidate : source.getForms()) {
            FormData existing = candidate == source.getStandardForm() ? standard : oldForms.get(candidate.getName());
            if (existing == null) existing = candidate;
            else copyFields(existing, candidate, FormData.class, Set.of(), rollback);
            existing.setSpecies(target);
            forms.add(existing);
        }
        // No alternate forms means the standard form is absent from Species.forms, but extant Pokemon use it.
        if (source.getForms().isEmpty()) {
            copyFields(standard, source.getStandardForm(), FormData.class, Set.of(), rollback);
            standard.setSpecies(target);
        }
        copyFields(target, source, Species.class, Set.of("forms"), rollback);
        Field formsField = Species.class.getDeclaredField("forms");
        formsField.setAccessible(true);
        rollback.add(new Restore(target, formsField, formsField.get(target)));
        formsField.set(target, forms);
        target.initialize();
        standard.initialize(target);
    }

    private static void copyFields(Object destination, Object source, Class<?> type, Set<String> skip,
                                   List<Restore> rollback) throws ReflectiveOperationException {
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()
                    || field.getName().contains("$") || skip.contains(field.getName())) continue;
            field.setAccessible(true);
            rollback.add(new Restore(destination, field, field.get(destination)));
            field.set(destination, field.get(source));
        }
    }

    private static void publishSpecies(MinecraftServer server) {
        Cobblemon.INSTANCE.getShowdownThread().queue(service -> {
            service.resetRegistryData("species");
            service.sendRegistryData(PokemonSpecies.INSTANCE.allShowdownSpecies$common(), "species");
            service.indicateSpeciesInitialized();
            return Unit.INSTANCE;
        });
        for (var player : server.getPlayerList().getPlayers()) PokemonSpecies.INSTANCE.sync(player);
    }

    private static Map<String, JsonObject> copy(Map<String, JsonObject> source) {
        Map<String, JsonObject> result = new TreeMap<>();
        source.forEach((key, value) -> result.put(key, value.deepCopy()));
        return result;
    }
}
