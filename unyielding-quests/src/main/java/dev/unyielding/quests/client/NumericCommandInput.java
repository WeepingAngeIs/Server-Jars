package dev.unyielding.quests.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;

import java.util.Optional;

/** Recognizes numeric inputs in the command tree supplied to the current player. */
final class NumericCommandInput {
    private static final int MAX_LENGTH = 255;

    private NumericCommandInput() {}

    static <S> boolean supported(RootCommandNode<S> root, String prefix) {
        return argument(root, prefix) != null;
    }

    static <S> String rangeHint(RootCommandNode<S> root, String prefix) {
        return switch (argument(root, prefix)) {
            case IntegerArgumentType arg -> range("Whole number", arg.getMinimum(), arg.getMaximum(), Integer.MIN_VALUE, Integer.MAX_VALUE);
            case LongArgumentType arg -> range("Whole number", arg.getMinimum(), arg.getMaximum(), Long.MIN_VALUE, Long.MAX_VALUE);
            case FloatArgumentType arg -> range("Number", arg.getMinimum(), arg.getMaximum(), -Float.MAX_VALUE, Float.MAX_VALUE);
            case DoubleArgumentType arg -> range("Number", arg.getMinimum(), arg.getMaximum(), -Double.MAX_VALUE, Double.MAX_VALUE);
            case null, default -> "";
        };
    }

    private static String range(String label, Number min, Number max, Number unboundedMin, Number unboundedMax) {
        boolean lower = !min.equals(unboundedMin);
        boolean upper = !max.equals(unboundedMax);
        if (lower && upper) return label + " from " + min + " to " + max;
        if (lower) return label + " of " + min + " or more";
        if (upper) return label + " of " + max + " or less";
        return label;
    }

    static <S> Optional<String> prepare(RootCommandNode<S> root, String prefix, String value) {
        ArgumentType<?> type = argument(root, prefix);
        if (type == null || value == null || value.isEmpty() || value.length() > MAX_LENGTH - prefix.length() - 1) {
            return Optional.empty();
        }
        StringReader reader = new StringReader(value);
        try {
            Object parsed = type.parse(reader);
            if (reader.canRead() || !(parsed instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                return Optional.empty();
            }
            return Optional.of(prefix + " " + value);
        } catch (CommandSyntaxException invalidNumber) {
            return Optional.empty();
        }
    }

    private static <S> ArgumentType<?> argument(RootCommandNode<S> root, String prefix) {
        if (root == null || prefix == null || prefix.isEmpty() || prefix.length() > MAX_LENGTH - 2) return null;
        CommandNode<S> node = root;
        for (String token : prefix.split(" ", -1)) {
            if (token.isEmpty() || token.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c)
                    || c == '/' || c == '<' || c == '>')) return null;
            node = node.getChild(token);
            if (!(node instanceof LiteralCommandNode<?>) || node.getRedirect() != null) return null;
        }
        if (node.getChildren().size() != 1) return null;
        var child = node.getChildren().iterator().next();
        if (!(child instanceof ArgumentCommandNode<?, ?> number) || child.getCommand() == null
                || child.getRedirect() != null || !child.getChildren().isEmpty()) return null;
        ArgumentType<?> type = number.getType();
        return type instanceof IntegerArgumentType || type instanceof LongArgumentType
                || type instanceof FloatArgumentType || type instanceof DoubleArgumentType ? type : null;
    }
}
