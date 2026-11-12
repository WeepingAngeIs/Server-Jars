package dev.unyielding.quests.settings;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Versioned, atomic server configuration. Only explicit overrides are stored. */
public final class PokemonEdits {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final int MAX_DOCUMENT = 900_000;
    public record State(long revision, Map<String, JsonObject> species, Map<String, JsonObject> spawns) {
        public State { species = copy(species); spawns = copy(spawns); }
    }
    public static Map<String, JsonObject> copy(Map<String, JsonObject> source) {
        Map<String, JsonObject> result = new TreeMap<>();
        source.forEach((key,value) -> result.put(key, value.deepCopy()));
        return result;
    }
    public static State empty() { return new State(0, Map.of(), Map.of()); }
    public static State read(Path path) throws IOException {
        if (!Files.exists(path)) return empty();
        if (Files.size(path) > 32_000_000) throw new IOException("Pokemon configuration exceeds 32 MB.");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported Pokemon configuration format.");
            long revision = root.get("revision").getAsLong();
            if (revision < 0) throw new IllegalArgumentException("Invalid configuration revision.");
            return new State(revision, map(root.getAsJsonObject("species")), map(root.getAsJsonObject("spawns")));
        } catch (RuntimeException failure) { throw new IOException("Cannot read Pokemon configuration; the existing file was preserved.", failure); }
    }
    private static Map<String, JsonObject> map(JsonObject object) {
        Map<String, JsonObject> values = new TreeMap<>();
        if (object == null) throw new IllegalArgumentException("Missing overrides object.");
        for (var entry : object.entrySet()) {
            identifier(entry.getKey());
            JsonObject value = entry.getValue().getAsJsonObject();
            validate(value);
            values.put(entry.getKey(), value);
        }
        return values;
    }
    public static void write(Path path, State state) throws IOException {
        JsonObject root = new JsonObject(); root.addProperty("format",1); root.addProperty("revision",state.revision());
        root.add("species", JSON.toJsonTree(state.species())); root.add("spawns", JSON.toJsonTree(state.spawns()));
        byte[] bytes = JSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 32_000_000) throw new IOException("Pokemon configuration exceeds 32 MB.");
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "pokemon-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException failure) { throw new IOException("This server filesystem cannot save Pokemon settings atomically.",failure); }
        } finally { Files.deleteIfExists(temporary); }
    }
    public static String identifier(String id) {
        if (id == null || id.length() > 200 || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                || id.contains("..") || id.endsWith("/") || id.contains("//"))
            throw new IllegalArgumentException("Use a valid namespaced identifier.");
        return id;
    }
    /** Native property strings permit an explicit species= token anywhere in the list. */
    public static String speciesToken(String properties) {
        String[] tokens=properties.trim().split("\\s+");
        for(String token:tokens)if(token.startsWith("species="))return token.substring(8).toLowerCase(Locale.ROOT);
        return tokens.length==0?"":tokens[0].toLowerCase(Locale.ROOT);
    }
    public static JsonElement parse(String text) {
        if (text == null || text.length() > MAX_DOCUMENT) throw new IllegalArgumentException("This document is too large.");
        JsonElement value = JsonParser.parseString(text);
        validate(value);
        return value;
    }
    public static void validate(JsonElement value) { walk(value,0,new int[]{0}); }
    private static void walk(JsonElement value, int depth, int[] count) {
        if (depth > 48 || ++count[0] > 100_000) throw new IllegalArgumentException("This document is too complex.");
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            if (entry.getKey().length() > 256) throw new IllegalArgumentException("A setting name is too long.");
            walk(entry.getValue(),depth+1,count);
        }
        else if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) walk(child,depth+1,count);
        else if (value.isJsonPrimitive()) {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isNumber() && !Double.isFinite(p.getAsDouble())) throw new IllegalArgumentException("Numbers must be finite.");
            if (p.isString() && p.getAsString().length() > 100_000) throw new IllegalArgumentException("A setting value is too long.");
        }
    }
}
