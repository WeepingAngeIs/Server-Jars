package dev.unyielding.quests.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reads the command tree already filtered and sent to this player by the server. */
final class CommandCatalog {
    record Entry(String path, String usage, boolean needsArguments) {}

    private static final int MAX_RESULTS = 256;
    private static final int MAX_NODES = 12_000;
    private static final int MAX_EDGES = MAX_NODES * 5;
    private static final int MAX_DEPTH = 24;
    private static final int MAX_ARGUMENT_HINTS = 6;

    private CommandCatalog() {}

    static <S> List<Entry> search(RootCommandNode<S> root, String query, int limit) {
        if (root == null || limit <= 0) return List.of();
        String normalized = normalize(query == null ? "" : query);
        Search<S> search = new Search<>(normalized, Math.min(limit, MAX_RESULTS));
        // Visit matching command roots first so a large unrelated mod cannot crowd them out.
        if (!normalized.isEmpty()) search.roots(root, true);
        search.roots(root, false);
        return List.copyOf(search.entries);
    }

    /** Rechecks the current player tree before offering to submit one of its concrete boolean actions. */
    static <S> boolean isBooleanAction(RootCommandNode<S> root, String path) {
        if (root == null || path == null || path.length() > (MAX_DEPTH + 1) * 129) return false;
        String[] words = path.split(" ", -1);
        if (words.length > MAX_DEPTH + 1) return false;
        CommandNode<S> parent = root;
        for (int i = 0; i < words.length - 1; i++) {
            if (!safeToken(words[i])) return false;
            parent = parent.getChild(words[i]);
            if (!(parent instanceof LiteralCommandNode<?>) || parent.getRedirect() != null) return false;
        }
        String last = words[words.length - 1];
        String opposite = switch (last) {
            case "on" -> "off";
            case "off" -> "on";
            case "enable" -> "disable";
            case "disable" -> "enable";
            case "true" -> "false";
            case "false" -> "true";
            default -> "";
        };
        if (opposite.isEmpty()) return false;
        CommandNode<S> leaf = parent.getChild(last);
        if (leaf instanceof LiteralCommandNode<?>) {
            CommandNode<S> peer = parent.getChild(opposite);
            return terminal(leaf) && peer instanceof LiteralCommandNode<?> && terminal(peer);
        }
        return (last.equals("true") || last.equals("false")) && terminalBoolean(parent);
    }

    private static boolean terminal(CommandNode<?> node) {
        return node.getCommand() != null && node.getRedirect() == null && node.getChildren().isEmpty();
    }

    private static boolean terminalBoolean(CommandNode<?> parent) {
        if (parent.getRedirect() != null || parent.getChild("true") instanceof LiteralCommandNode<?>
                || parent.getChild("false") instanceof LiteralCommandNode<?>) return false;
        boolean found = false;
        int inspected = 0;
        for (CommandNode<?> child : parent.getChildren()) {
            if (++inspected > MAX_NODES) return false;
            if (child instanceof ArgumentCommandNode<?, ?> argument) {
                if (found || !(argument.getType() instanceof BoolArgumentType) || !terminal(argument)) return false;
                found = true;
            }
        }
        return found;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('/', ' ').replace('_', ' ').replace('-', ' ')
                .replace(':', ' ').strip();
    }

    private static boolean safeToken(String value) {
        return !value.isEmpty() && value.length() <= 128 && value.chars().noneMatch(c ->
                Character.isWhitespace(c) || Character.isISOControl(c) || c == '/' || c == '<' || c == '>');
    }

    private static final class Search<S> {
        private final List<Entry> entries = new ArrayList<>();
        private final Set<String> paths = new HashSet<>();
        private final Set<CommandNode<S>> active = Collections.newSetFromMap(new IdentityHashMap<>());
        private final String[] terms;
        private final int limit;
        private int visited;
        private int inspectedEdges;

        Search(String normalized, int limit) {
            this.terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
            this.limit = limit;
        }

        void roots(RootCommandNode<S> root, boolean preferred) {
            int inspected = 0;
            for (CommandNode<S> child : root.getChildren()) {
                if (++inspected > MAX_NODES || full()) break;
                if (child instanceof LiteralCommandNode<S> literal && safeToken(literal.getLiteral())) {
                    String name = normalize(literal.getLiteral());
                    boolean matchesTerm = false;
                    for (String term : terms) {
                        if (name.contains(term)) {
                            matchesTerm = true;
                            break;
                        }
                    }
                    if (matchesTerm == preferred) visit(literal, literal.getLiteral(), 1, false);
                }
            }
        }

        boolean full() {
            return entries.size() >= limit || visited >= MAX_NODES || inspectedEdges >= MAX_EDGES;
        }

        void visit(LiteralCommandNode<S> node, String path, int depth, boolean redirected) {
            visited++;
            if (!active.add(node)) return;
            CommandNode<S> children = node.getRedirect() == null ? node : node.getRedirect();
            // A redirect back to root or an ancestor can repeat the whole dispatcher under itself.
            boolean descend = !(children instanceof RootCommandNode<?>) &&
                    (children == node ? node.getRedirect() == null : !active.contains(children));
            Set<String> arguments = new LinkedHashSet<>();
            boolean hasBooleanArgument = false;
            if (descend) {
                for (CommandNode<S> child : children.getChildren()) {
                    if (++inspectedEdges >= MAX_EDGES || arguments.size() >= MAX_ARGUMENT_HINTS) break;
                    if (child instanceof ArgumentCommandNode<?, ?> argument && safeToken(child.getName())) {
                        arguments.add("<" + child.getName() + ">");
                        hasBooleanArgument |= argument.getType() instanceof BoolArgumentType;
                    }
                }
            }
            boolean executable = node.getCommand() != null;
            if (executable || !arguments.isEmpty()) {
                String hints = String.join(" | ", arguments);
                String usage = hints.isEmpty() ? path : path + " " + (executable ? "[" + hints + "]" : hints);
                add(path, usage, !executable);
                if (!redirected && hasBooleanArgument && arguments.size() == 1 && terminalBoolean(node)) {
                    add(path + " true", path + " true", false);
                    add(path + " false", path + " false", false);
                }
            }
            if (descend && depth < MAX_DEPTH && !full()) {
                if (children != node) active.add(children);
                for (CommandNode<S> child : children.getChildren()) {
                    if (++inspectedEdges >= MAX_EDGES || full()) break;
                    if (child instanceof LiteralCommandNode<S> literal && safeToken(literal.getLiteral()))
                        visit(literal, path + " " + literal.getLiteral(), depth + 1, redirected || children != node);
                }
                if (children != node) active.remove(children);
            }
            active.remove(node);
        }

        boolean matches(String usage) {
            String normalized = normalize(usage);
            int from = 0;
            for (String term : terms) {
                int match = normalized.indexOf(term, from);
                if (match < 0) return false;
                from = match + term.length();
            }
            return true;
        }

        void add(String path, String usage, boolean needsArguments) {
            if (entries.size() < limit && matches(usage) && paths.add(path))
                entries.add(new Entry(path, usage, needsArguments));
        }
    }
}
