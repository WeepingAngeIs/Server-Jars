package dev.unyielding.quests.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NumericCommandInputTest {
    private static LiteralArgumentBuilder<Object> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<Object, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static RootCommandNode<Object> bounded(ArgumentType<?> type) {
        var dispatcher = new CommandDispatcher<>();
        dispatcher.register(literal("example:rules").then(literal("speed")
                .executes(ctx -> { throw new AssertionError("Must never execute"); })
                .then(argument("value", type).executes(ctx -> { throw new AssertionError("Must never execute"); }))));
        return dispatcher.getRoot();
    }

    @Test
    void honorsAllFourNumericTypesAndBrigadiersAdvertisedBounds() {
        String prefix = "example:rules speed";
        for (var type : List.of(IntegerArgumentType.integer(-2, 5), LongArgumentType.longArg(-2L, 5L),
                FloatArgumentType.floatArg(-2, 5), DoubleArgumentType.doubleArg(-2, 5))) {
            var root = bounded(type);
            assertTrue(NumericCommandInput.supported(root, prefix));
            assertEquals(Optional.of(prefix + " -2"), NumericCommandInput.prepare(root, prefix, "-2"));
            assertEquals(Optional.of(prefix + " 5"), NumericCommandInput.prepare(root, prefix, "5"));
            assertTrue(NumericCommandInput.prepare(root, prefix, "6").isEmpty());
        }
        assertTrue(NumericCommandInput.prepare(bounded(IntegerArgumentType.integer()), prefix, "2.5").isEmpty());
        assertTrue(NumericCommandInput.prepare(bounded(LongArgumentType.longArg()), prefix, "9223372036854775808").isEmpty());
        assertTrue(NumericCommandInput.prepare(bounded(DoubleArgumentType.doubleArg()), prefix, "-0.25").isPresent());
        assertEquals("Whole number from 1 to 100", NumericCommandInput.rangeHint(bounded(IntegerArgumentType.integer(1, 100)), prefix));
        assertEquals("Number", NumericCommandInput.rangeHint(bounded(DoubleArgumentType.doubleArg()), prefix));
        assertEquals("Whole number of 2 or more", NumericCommandInput.rangeHint(bounded(LongArgumentType.longArg(2)), prefix));
    }

    @Test
    void excludesAmbiguousRedirectedNonterminalAndTextArguments() {
        var dispatcher = new CommandDispatcher<>();
        var root = dispatcher.getRoot();
        var terminal = dispatcher.register(literal("canonical").then(argument("n", IntegerArgumentType.integer()).executes(c -> 1)));
        dispatcher.register(literal("alias").redirect(terminal));
        dispatcher.register(literal("numberAlias").then(argument("n", IntegerArgumentType.integer()).redirect(terminal)));
        dispatcher.register(literal("unfinished").then(argument("n", IntegerArgumentType.integer())));
        dispatcher.register(literal("optional").then(argument("n", IntegerArgumentType.integer()).executes(c -> 1)
                .then(literal("force").executes(c -> 1))));
        dispatcher.register(literal("ambiguous").then(literal("1").executes(c -> 1))
                .then(argument("n", IntegerArgumentType.integer()).executes(c -> 1)));
        dispatcher.register(literal("selector").then(argument("target", StringArgumentType.word())
                .then(argument("n", IntegerArgumentType.integer()).executes(c -> 1))));
        for (String path : List.of("alias", "numberAlias", "unfinished", "optional", "ambiguous", "selector",
                "selector target", "unadvertised", "/canonical", "canonical ", " canonical", "canonical\n")) {
            assertFalse(NumericCommandInput.supported(root, path), path);
            assertTrue(NumericCommandInput.prepare(root, path, "1").isEmpty());
        }
    }

    @Test
    void requiresASingleFiniteValueWithinTheMinecraftCommandLengthLimit() {
        var root = bounded(DoubleArgumentType.doubleArg());
        for (String invalid : List.of("NaN", "Infinity", "+Infinity", "1e500", " 2", "2 ", "2 3", "2\n",
                "2 /other", "/reload", "1;another", "1".repeat(255))) {
            assertTrue(NumericCommandInput.prepare(root, "example:rules speed", invalid).isEmpty(), invalid);
        }
        assertTrue(NumericCommandInput.prepare(null, "example:rules speed", "2").isEmpty());
        assertFalse(NumericCommandInput.supported(root, null));
        assertFalse(NumericCommandInput.supported(root, "x".repeat(254)));
    }
}
