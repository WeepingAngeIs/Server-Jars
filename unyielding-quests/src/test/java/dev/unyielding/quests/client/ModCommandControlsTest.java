package dev.unyielding.quests.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModCommandControlsTest {
    private static LiteralArgumentBuilder<Object> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static LiteralArgumentBuilder<Object> action(String name) {
        return literal(name).executes(context -> { throw new AssertionError("Ran a command while browsing"); });
    }

    @Test void bindsOnlyDirectlyUsableActionsAndDoesNotAssociateShortModIdsByAccident() {
        var tree = new CommandDispatcher<Object>();
        tree.register(literal("raids").then(literal("ai").then(action("on")).then(action("off"))));
        tree.register(literal("ai:config").then(literal("flight").then(action("on")).then(action("off")))
                .then(literal("speed").then(RequiredArgumentBuilder.<Object, Integer>argument("value",
                                IntegerArgumentType.integer(1, 10)).executes(c -> 1))).then(action("start")));
        var preview = new ModCommandControls();
        var all = preview.find(tree.getRoot(), "ai", "", false);
        assertEquals(List.of("ai:config flight on", "ai:config flight off", "ai:config speed"),
                all.stream().map(row -> row.entry().path()).toList());
        assertSame(all, preview.find(tree.getRoot(), "ai", "", false));
        assertEquals("Set number", preview.find(tree.getRoot(), "ai", "speed", false).getFirst().label());
        assertTrue(preview.find(new CommandDispatcher<>().getRoot(), "ai", "speed", false).isEmpty());
    }

    @Test void hidesDuplicateExcavationActionsOnlyWhileTheDedicatedSwitchIsVisible() {
        var tree = new CommandDispatcher<Object>();
        tree.register(literal("uwu").then(literal("launcher").then(literal("excavation").then(action("on")).then(action("off")))));
        var preview = new ModCommandControls();
        tree.register(literal("uwucurrency").then(literal("rewards").then(action("on")).then(action("off"))));
        assertTrue(preview.find(tree.getRoot(), "uwudimension", "rewards", false).isEmpty());
        assertEquals(2, preview.find(tree.getRoot(), "uwudimension", "excavation", false).size());
        assertTrue(preview.find(tree.getRoot(), "uwudimension", "excavation", true).isEmpty());
    }
}
