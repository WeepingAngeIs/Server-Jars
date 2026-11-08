package dev.unyielding.quests.tutorial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.tree.CommandNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Validated editable help and a bounded, permission-aware command catalog. */
public record TutorialContent(List<Page> pages) {
    static final int MAX_JSON_BYTES = 128_000;
    static final int MAX_USAGES = 1_024;
    static final int MAX_NODES = 12_000;
    static final int MAX_DEPTH = 24;
    static final int MAX_DIRECTORY_BYTES = 240_000;

    public record Entry(String command, String usage, String description, List<String> requiredRanks) {
        public Entry(String command, String usage, String description) { this(command, usage, description, null); }
        public boolean visibleTo(Set<String> ranks) {
            return requiredRanks == null || requiredRanks.stream().anyMatch(ranks::contains);
        }
    }
    public record Page(String id, String title, String description, List<Entry> entries) { }
    public record LoadResult(TutorialContent content, String error) { }
    public record Catalog(List<String> usages, boolean truncated) { }

    private static final Set<String> RANK_COMMANDS = Set.of("crafting", "anvil", "smithing", "stonecutter",
            "enderchest", "trashcan", "near", "hat", "nick", "pc", "healpokemon", "checkspawn");

    /** Upgrade old generated help once, preserving custom pages, wording and explicit rank rules. */
    public static String migrateLegacy(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("schema_version") && root.get("schema_version").getAsInt() >= 2) return json;
        for (JsonElement value : array(root, "pages", 16)) {
            JsonObject page = value.getAsJsonObject();
            JsonArray entries = array(page, "entries", 32);
            for (int i = entries.size() - 1; i >= 0; i--) {
                JsonObject entry = entries.get(i).getAsJsonObject();
                String command = string(entry, "command", 160);
                if (command.equals("oparties about")) {
                    entries.remove(i);
                    continue;
                }
                if (command.equals("oparties member invite player") &&
                        "Invite a player to your claims party.".equals(string(entry, "description", 600))) {
                    entry.addProperty("description", "Invite a player to your claims party. You must be the party owner or a moderator.");
                }
                if (RANK_COMMANDS.contains(command.split(" ", 2)[0]) && !entry.has("required_ranks")) {
                    entry.add("required_ranks", new JsonArray());
                }
            }
            if ("claims".equals(string(page, "id", 40))) {
                boolean found = false;
                for (JsonElement entry : entries) {
                    if ("oparties member invite player".equals(entry.getAsJsonObject().get("command").getAsString())) found = true;
                }
                if (!found) {
                    JsonObject invite = new JsonObject();
                    invite.addProperty("command", "oparties member invite player");
                    invite.addProperty("usage", "/oparties member invite <player>");
                    invite.addProperty("description", "Invite a player to your claims party. You must be the party owner or a moderator.");
                    entries.add(invite);
                }
            }
            String description = string(page, "description", 600);
            page.addProperty("description", description.replace(
                    "J opens it, and the Premier Ball button in your inventory opens the same book.",
                    "Open your inventory and click the Poke Ball at the top-left to open it."));
        }
        root.addProperty("schema_version", 2);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    public String describe(String usage) {
        String normalized = usage.substring(1).replace("<", "").replace(">", "");
        Entry best = null;
        for (Page page : pages) {
            for (Entry entry : page.entries()) {
                boolean matches = normalized.equals(entry.command()) || normalized.startsWith(entry.command() + " ");
                if (matches && (best == null || entry.command().length() > best.command().length())) best = entry;
            }
        }
        return best == null ? "Available on this server. Use Tab in chat to fill in arguments." : best.description();
    }

    public static LoadResult parseOrFallback(String json, TutorialContent fallback) {
        try {
            return new LoadResult(parse(json), null);
        } catch (RuntimeException e) {
            return new LoadResult(fallback, java.util.Objects.toString(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    public static TutorialContent parse(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Tutorial JSON exceeds 128 KB.");
        }
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray pageArray = array(root, "pages", 16);
        List<Page> pages = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement value : pageArray) {
            JsonObject page = value.getAsJsonObject();
            String id = string(page, "id", 40);
            if (!id.matches("[a-z0-9_]+") || !ids.add(id)) {
                throw new IllegalArgumentException("Tutorial page IDs must be unique lower-case words.");
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonElement item : array(page, "entries", 32)) {
                JsonObject entry = item.getAsJsonObject();
                String command = string(entry, "command", 160);
                String usage = string(entry, "usage", 180);
                if (!command.matches("[a-zA-Z0-9_:.=-]+( [a-zA-Z0-9_:.=-]+)*")) {
                    throw new IllegalArgumentException("Command paths contain registered node names separated by spaces.");
                }
                String commandRoot = command.split(" ", 2)[0];
                if (!usage.equals("/" + commandRoot) && !usage.startsWith("/" + commandRoot + " ")) {
                    throw new IllegalArgumentException("Command usage must start with its registered command root.");
                }
                List<String> ranks = null;
                if (entry.has("required_ranks")) {
                    ranks = new ArrayList<>();
                    for (JsonElement rank : array(entry, "required_ranks", 64)) {
                        if (!rank.isJsonPrimitive() || !rank.getAsJsonPrimitive().isString()
                                || !rank.getAsString().matches("[a-zA-Z0-9_.-]{1,64}")) {
                            throw new IllegalArgumentException("Required ranks must be configured rank IDs.");
                        }
                        ranks.add(rank.getAsString().toLowerCase(java.util.Locale.ROOT));
                    }
                    ranks = List.copyOf(ranks);
                }
                entries.add(new Entry(command, usage, string(entry, "description", 600), ranks));
            }
            pages.add(new Page(id, string(page, "title", 80),
                    string(page, "description", 600), List.copyOf(entries)));
        }
        return new TutorialContent(List.copyOf(pages));
    }

    private static JsonArray array(JsonObject object, String key, int maximum) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > maximum) {
            throw new IllegalArgumentException("Invalid or oversized " + key + " list.");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String key, int maximum) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing text field " + key + ".");
        }
        String result = value.getAsString().trim();
        if (result.isEmpty() || result.length() > maximum || result.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("Invalid text field " + key + ".");
        }
        return result;
    }

    /** Resolve actual literal/argument node names without executing a command. */
    public static <S> boolean available(CommandNode<S> root, S source, String path) {
        CommandNode<S> node = root;
        for (String segment : path.split(" ")) {
            node = node.getChild(segment);
            if (node == null || !permitted(node, source)) return false;
        }
        Set<CommandNode<S>> redirects = Collections.newSetFromMap(new IdentityHashMap<>());
        while (node.getRedirect() != null) {
            if (!redirects.add(node)) return false;
            node = node.getRedirect();
            if (!permitted(node, source)) return false;
        }
        return executable(node, source, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static <S> boolean executable(CommandNode<S> node, S source, Set<CommandNode<S>> seen, int depth) {
        if (depth > MAX_DEPTH || !seen.add(node) || !permitted(node, source)) return false;
        if (node.getCommand() != null) return true;
        if (node.getRedirect() != null && executable(node.getRedirect(), source, seen, depth + 1)) return true;
        for (CommandNode<S> child : node.getChildren()) {
            if (executable(child, source, seen, depth + 1)) return true;
        }
        return false;
    }

    /** A documented party-role prerequisite is explained in help, not treated as an operator permission. */
    public static <S> boolean registeredExecutable(CommandNode<S> root, String path) {
        CommandNode<S> node = root;
        for (String segment : path.split(" ")) {
            node = node.getChild(segment);
            if (node == null) return false;
        }
        return node.getCommand() != null;
    }

    private static <S> boolean permitted(CommandNode<S> node, S source) {
        try {
            return node.canUse(source);
        } catch (RuntimeException e) {
            // A third-party requirement must never make joining the server fail.
            return false;
        }
    }

    public static <S> Catalog catalog(CommandNode<S> root, S source) {
        CatalogWalker<S> walker = new CatalogWalker<>(source);
        walker.visit(root, "", 0);
        return new Catalog(List.copyOf(walker.usages), walker.truncated);
    }

    private static final class CatalogWalker<S> {
        private final S source;
        private final Set<CommandNode<S>> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<String> usages = new LinkedHashSet<>();
        private int visited;
        private int bytes;
        private boolean truncated;

        private CatalogWalker(S source) {
            this.source = source;
        }

        private void visit(CommandNode<S> node, String path, int depth) {
            if (depth > MAX_DEPTH || visited >= MAX_NODES || usages.size() >= MAX_USAGES) {
                truncated = true;
                return;
            }
            visited++;
            if (!permitted(node, source) || !ancestors.add(node)) return;
            try {
                if (node.getCommand() != null && !path.isEmpty()) add(path);
                CommandNode<S> redirect = node.getRedirect();
                if (redirect != null) {
                    // Do not execute redirect modifiers while building a help screen.
                    visit(redirect, path, depth + 1);
                }
                for (CommandNode<S> child : node.getChildren().stream()
                        .sorted(java.util.Comparator.comparing(CommandNode::getName)).toList()) {
                    String next = path.isEmpty() ? child.getUsageText() : path + " " + child.getUsageText();
                    visit(child, next, depth + 1);
                    if (visited >= MAX_NODES || usages.size() >= MAX_USAGES || bytes >= MAX_DIRECTORY_BYTES) {
                        truncated = true;
                        break;
                    }
                }
            } finally {
                ancestors.remove(node);
            }
        }

        private void add(String path) {
            String usage = "/" + path;
            int cost = usage.getBytes(StandardCharsets.UTF_8).length + 128;
            if (usage.length() > 384 || bytes + cost > MAX_DIRECTORY_BYTES) {
                truncated = true;
                return;
            }
            if (usages.add(usage)) bytes += cost;
        }
    }
}
