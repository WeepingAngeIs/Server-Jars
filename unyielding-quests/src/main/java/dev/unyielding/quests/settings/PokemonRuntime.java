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
        validateData(json, false, original);
        if (!json.has("baseStats") || !json.has("primaryType") || !json.has("abilities"))
            throw new IllegalArgumentException("Species need baseStats, primaryType and abilities.");
        Species parsed = parseSpecies(id, json);
        parsed.initialize();
        // Resolve lazy evolution sets now, before an edit can reach live data.
        parsed.getEvolutions().size();
        for (FormData form : parsed.getForms()) form.getEvolutions().size();
        Map<String,Integer> oldForms=formCounts(before),newForms=formCounts(parsed);
        for(var form:newForms.entrySet())if(form.getValue()>Math.max(1,oldForms.getOrDefault(form.getKey(),0)))
            throw new IllegalArgumentException("An additional duplicate form name was introduced: "+form.getKey());
        for(var form:oldForms.entrySet())if(newForms.getOrDefault(form.getKey(),0)<form.getValue())
            throw new IllegalArgumentException("Existing forms may be edited, but cannot be removed or renamed while the server is running.");
    }

    private static boolean standardForm(FormData form,Species species) {
        FormData standard=species.getStandardForm();
        // Decoded packets contain a distinct instance of the serialized standard form.
        return form==standard||form.getName().equals(standard.getName())&&form.getAspects().isEmpty();
    }

    private static Map<String,Integer> formCounts(Species species) {
        Map<String,Integer> counts=new HashMap<>();
        for(FormData form:species.getForms())if(!standardForm(form,species))counts.merge(form.getName(),1,Integer::sum);
        return counts;
    }

    private static Species parseSpecies(String id, JsonObject json) {
        Species result = PokemonSpecies.INSTANCE.getGson().fromJson(json, Species.class);
        result.setResourceIdentifier(ResourceLocation.parse(id));
        return result;
    }

    /** Validate registered names explicitly: some Cobblemon adapters silently discard unknown moves. */
    private static void validateData(JsonElement element, boolean form, JsonElement baseline) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            JsonArray old = baseline != null && baseline.isJsonArray() ? baseline.getAsJsonArray() : null;
            for (int i = 0; i < array.size(); i++) validateData(array.get(i), form, old != null && i < old.size() ? old.get(i) : null);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        JsonObject original = baseline != null && baseline.isJsonObject() ? baseline.getAsJsonObject() : null;
        if (object.has("baseStats")) {
            JsonObject stats = object.getAsJsonObject("baseStats");
            Set<String> names = Set.of("hp", "attack", "defence", "special_attack", "special_defence", "speed");
            if (!form && !stats.keySet().containsAll(names)) throw new IllegalArgumentException("All six base stats are required.");
            for (var stat : stats.entrySet()) {
                if (!names.contains(stat.getKey())) throw new IllegalArgumentException("Unknown base stat: " + stat.getKey());
                if (original != null && original.has("baseStats") && original.get("baseStats").isJsonObject()
                        && stat.getValue().equals(original.getAsJsonObject("baseStats").get(stat.getKey()))) continue;
                double value = stat.getValue().getAsDouble();
                if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > 10000)
                    throw new IllegalArgumentException("Base stats must be whole numbers from 1 to 10000.");
            }
        }
        changedNumeric(object, original, "catchRate", 0, 255, true);
        changedNumeric(object, original, "baseFriendship", 0, 255, true);
        changedNumeric(object, original, "baseExperienceYield", 0, 1000000, true);
        changedNumeric(object, original, "eggCycles", 0, 1000000, true);
        changedNumeric(object, original, "maleRatio", -1, 1, false);
        for (String name : List.of("baseScale", "height")) changedNumeric(object, original, name, 0.0001, 10000, false);
        // Gigantamax species intentionally use zero for their unspecified weight.
        changedNumeric(object, original, "weight", 0, 10000, false);
        for (String type : List.of("primaryType", "secondaryType")) if (object.has(type) && !object.get(type).isJsonNull() && !unchanged(object, original, type)) {
            String name = object.get(type).getAsString();
            if (ElementalTypes.get(name) == null) throw new IllegalArgumentException("Unknown elemental type: " + name);
        }
        if (object.has("abilities")) {
            JsonArray array = object.getAsJsonArray("abilities");
            if (array.isEmpty()) throw new IllegalArgumentException("At least one ability is required.");
            for (JsonElement ability : array) if (ability.isJsonPrimitive()) {
                if (contains(original, "abilities", ability)) continue;
                String name = ability.getAsString();
                if (name.startsWith("h:")) name = name.substring(2);
                if (Abilities.get(name) == null) throw new IllegalArgumentException("Unknown ability: " + name);
            }
        }
        if (object.has("moves")) for (JsonElement move : object.getAsJsonArray("moves")) {
            if (contains(original, "moves", move)) continue;
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
                JsonObject old = namedEntry(original, "evolutions", "id", evolution.get("id").getAsString());
                if (evolution.has("result") && !unchanged(evolution, old, "result"))
                    validatePokemonProperties(evolution.get("result").getAsString());
                if (evolution.has("learnableMoves")) for (JsonElement move : evolution.getAsJsonArray("learnableMoves"))
                    if (!contains(old, "learnableMoves", move) && Moves.getByName(move.getAsString()) == null)
                        throw new IllegalArgumentException("Unknown evolution move: " + move.getAsString());
            }
        }
        for (var child : object.entrySet()) if (!child.getKey().equals("moves") && !child.getKey().equals("abilities"))
            validateData(child.getValue(), form || child.getKey().equals("forms"), original == null ? null : original.get(child.getKey()));
    }

    private static boolean unchanged(JsonObject object, JsonObject baseline, String key) {
        return baseline != null && object.get(key) != null && object.get(key).equals(baseline.get(key));
    }

    private static boolean contains(JsonObject object, String key, JsonElement value) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) return false;
        for (JsonElement before : object.getAsJsonArray(key)) if (before.equals(value)) return true;
        return false;
    }

    private static JsonObject namedEntry(JsonObject object, String array, String key, String name) {
        if (object == null || !object.has(array) || !object.get(array).isJsonArray()) return null;
        for (JsonElement value : object.getAsJsonArray(array)) if (value.isJsonObject()) {
            JsonObject candidate = value.getAsJsonObject();
            if (candidate.has(key) && candidate.get(key).isJsonPrimitive() && candidate.get(key).getAsString().equals(name)) return candidate;
        }
        return null;
    }

    private static void changedNumeric(JsonObject object, JsonObject baseline, String name, double min, double max, boolean integer) {
        if (!unchanged(object, baseline, name)) numeric(object, name, min, max, integer);
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
        String species = PokemonEdits.speciesToken(value);
        if (!species.equals("random") && PokemonSpecies.getByIdentifier(ResourceLocation.parse(canonical(species))) == null)
            throw new IllegalArgumentException("Unknown Pokemon in properties: " + species);
    }

    public static void validateSpawns(Map<String, JsonObject> files) {
        parseSpawns(files);
    }

    private static Map<ResourceLocation, SpawnSet> parseSpawns(Map<String, JsonObject> files) {
        Map<ResourceLocation, SpawnSet> result = new LinkedHashMap<>();
        var pool = CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL();
        Map<String,Integer> counts=new HashMap<>(),previousCounts=new HashMap<>();
        Set<String> changedPokemonIds=new HashSet<>();
        for(JsonObject file:currentSpawns.values())if(file.has("spawns"))for(JsonElement value:file.getAsJsonArray("spawns")) {
            JsonObject spawn=value.getAsJsonObject();
            if(spawn.has("id")&&!spawn.get("id").getAsString().isBlank())previousCounts.merge(spawn.get("id").getAsString(),1,Integer::sum);
        }
        for (var entry : files.entrySet()) {
            JsonObject json = entry.getValue();
            boolean edited = !json.equals(currentSpawns.get(entry.getKey()));
            if (!json.has("spawns") || !json.get("spawns").isJsonArray())
                throw new IllegalArgumentException("A spawn file needs a spawns array.");
            for (JsonElement value : json.getAsJsonArray("spawns")) {
                JsonObject spawn = value.getAsJsonObject();
                String id=spawn.has("id")?spawn.get("id").getAsString():"";
                if(!id.isBlank())counts.merge(id,1,Integer::sum);
                boolean changed=edited&&!originalSpawnUnchanged(entry.getKey(),spawn);
                boolean pokemon=spawn.has("pokemon")&&spawn.get("pokemon").isJsonPrimitive()
                        &&(!spawn.has("type")||!spawn.get("type").getAsString().equals("empty"));
                if(!changed)continue;
                if(pokemon) {
                    if(id.isBlank())throw new IllegalArgumentException("Every Pokemon spawn needs an id.");
                    changedPokemonIds.add(id);validatePokemonProperties(spawn.get("pokemon").getAsString());
                }
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
                if (edited&&detail.getBucket().isBlank()) throw new IllegalArgumentException("Every spawn needs a bucket.");
                if (edited && detail.isModDependencySatisfied() && !detail.isValid() && set.isEnabled())
                    throw new IllegalArgumentException("Invalid enabled spawn: " + detail.getId());
                if (edited) validateConditionFields(json.getAsJsonArray("spawns").get(index).getAsJsonObject(), detail,
                        originalSpawn(entry.getKey(), detail.getId()));
            }
            result.put(ResourceLocation.parse(entry.getKey()), set);
        }
        for(String id:changedPokemonIds)if(counts.getOrDefault(id,0)>Math.max(1,previousCounts.getOrDefault(id,0)))
            throw new IllegalArgumentException("Duplicate Pokemon spawn id: "+id);
        return result;
    }

    private static boolean originalSpawnUnchanged(String file,JsonObject spawn) {
        JsonObject original=currentSpawns.get(file);
        if(original==null||!original.has("spawns"))return false;
        for(JsonElement value:original.getAsJsonArray("spawns"))if(spawn.equals(value))return true;
        return false;
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
        Map<String, Species> replacements = new LinkedHashMap<>();
        for (var entry : species.entrySet()) if (!entry.getValue().equals(currentSpecies.get(entry.getKey()))) {
            validateSpecies(entry.getKey(), entry.getValue());
            replacements.put(entry.getKey(), parseSpecies(entry.getKey(), entry.getValue()));
        }
        if (!replacements.isEmpty() && hasActiveBattles())
            throw new IllegalStateException("Finish active Pokemon battles before applying species edits. Spawn-only edits remain available.");
        boolean spawnChanged=!spawnFiles.equals(currentSpawns);
        Map<ResourceLocation, SpawnSet> spawns = spawnChanged?parseSpawns(spawnFiles):Map.of();
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
            if (!replacements.isEmpty()) {
                for (Species value : PokemonSpecies.getSpecies()) {
                    value.getMoves().getEvolutionMoves().clear();
                    for (FormData form : value.getForms()) form.getMoves().getEvolutionMoves().clear();
                }
                // Evolution move resolution must see all of the new species definitions together.
                for (Species value : PokemonSpecies.getSpecies()) value.resolveEvolutionMoves$common();
            }
            if(spawnChanged) {
                CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().reload(spawns);
                CobblemonSpawnPools.INSTANCE.getWORLD_SPAWN_POOL().onServerLoad(server);
            }
            if (!replacements.isEmpty()) publishSpecies(server);
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
                if (!replacements.isEmpty()) publishSpecies(server);
            } catch (RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw new IllegalStateException("Pokemon edit could not be applied; previous values restored. " + Objects.toString(failure.getMessage(), ""), failure);
        }
    }

    private static void saveEvolutionMoves(Set<MoveTemplate> moves, Map<Set<MoveTemplate>, Set<MoveTemplate>> saved) {
        saved.putIfAbsent(moves, new HashSet<>(moves));
    }

    private record Restore(Object owner, Field field, Object value) { }

    /** Native species packets replace registry entries; retain objects used by existing client Pokemon. */
    public static Collection<Species> preserveClientReferences(Collection<Species> incoming) {
        List<Restore> rollback = new ArrayList<>();
        List<Species> entries = new ArrayList<>(incoming.size());
        try {
            for (Species source : incoming) {
                Species existing = PokemonSpecies.getByIdentifier(source.getResourceIdentifier());
                if (existing != null && existing != source) {
                    replaceSpecies(existing, source, rollback);
                    entries.add(existing);
                } else entries.add(source);
            }
            return entries;
        } catch (RuntimeException | ReflectiveOperationException failure) {
            Collections.reverse(rollback);
            for (Restore restore : rollback) try { restore.field().set(restore.owner(), restore.value()); }
            catch (ReflectiveOperationException e) { failure.addSuppressed(e); }
            throw new IllegalStateException("Could not synchronize existing Pokemon species.", failure);
        }
    }

    private static void replaceSpecies(Species target, Species source, List<Restore> rollback) throws ReflectiveOperationException {
        Map<String,Deque<FormData>> oldForms = new HashMap<>();
        for(FormData form:target.getForms())if(!standardForm(form,target))oldForms.computeIfAbsent(form.getName(),key->new ArrayDeque<>()).add(form);
        FormData standard = target.getStandardForm();
        source.initialize();
        copyFields(standard,source.getStandardForm(),FormData.class,Set.of(),rollback);
        standard.setSpecies(target);
        List<FormData> forms = new ArrayList<>();
        for (FormData candidate : source.getForms()) {
            FormData existing=standardForm(candidate,source)?standard:takeForm(oldForms.get(candidate.getName()),candidate);
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

    private static FormData takeForm(Deque<FormData> candidates,FormData desired) {
        if(candidates==null||candidates.isEmpty())return null;
        for(Iterator<FormData> iterator=candidates.iterator();iterator.hasNext();) {
            FormData form=iterator.next();
            if(new HashSet<>(form.getAspects()).equals(new HashSet<>(desired.getAspects()))){iterator.remove();return form;}
        }
        return candidates.removeFirst();
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
