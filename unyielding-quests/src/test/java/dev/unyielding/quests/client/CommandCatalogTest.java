package dev.unyielding.quests.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandCatalogTest {
    private static LiteralArgumentBuilder<Object> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static LiteralArgumentBuilder<Object> action(String name) {
        return literal(name).executes(context -> { throw new AssertionError("Command ran during search"); });
    }

    private static List<String> paths(List<CommandCatalog.Entry> entries) {
        return entries.stream().map(CommandCatalog.Entry::path).toList();
    }

    @Test
    void exposesOnlyTheSynchronizedTreeAndSearchesNestedToggleAlternatives() {
        var dispatcher = new CommandDispatcher<Object>();
        dispatcher.register(literal("uwu:admin").then(literal("heart_launcher").then(literal("excavation")
                .then(action("on")).then(action("off")).then(action("status"))
                .then(action("enable")).then(action("disable")))));
        String prefix = "uwu:admin heart_launcher excavation ";
        assertEquals(List.of(prefix + "on", prefix + "off", prefix + "status", prefix + "enable", prefix + "disable"),
                paths(CommandCatalog.search(dispatcher.getRoot(), "/HEART-launcher", 100)));
        assertEquals(List.of(prefix + "on"), paths(CommandCatalog.search(dispatcher.getRoot(), "excavation on", 100)));
        assertTrue(CommandCatalog.search(dispatcher.getRoot(), "opac", 100).isEmpty());
    }

    @Test
    void rendersRequiredAndOptionalArgumentsWithoutInventingValuesOrFollowingArgumentBranches() {
        var dispatcher = new CommandDispatcher<Object>();
        var target = RequiredArgumentBuilder.<Object, String>argument("player", StringArgumentType.word())
                .suggests((context, builder) -> { throw new AssertionError("Suggestions called during search"); })
                .then(action("kick"));
        dispatcher.register(literal("revive").then(target));
        dispatcher.register(action("guide").then(RequiredArgumentBuilder.argument("topic", StringArgumentType.word())));
        var required = CommandCatalog.search(dispatcher.getRoot(), "player", 20).getFirst();
        assertEquals(new CommandCatalog.Entry("revive", "revive <player>", true), required);
        assertEquals(new CommandCatalog.Entry("guide", "guide [<topic>]", false),
                CommandCatalog.search(dispatcher.getRoot(), "topic", 20).getFirst());
        assertTrue(CommandCatalog.search(dispatcher.getRoot(), "kick", 20).isEmpty());
    }

    @Test
    void redirectsKeepTheirAliasAndDoNotExpandRootOrAncestorCycles() {
        var dispatcher = new CommandDispatcher<Object>();
        var real = dispatcher.register(literal("uwu").then(action("status")));
        real.addChild(literal("again").redirect(real).build());
        dispatcher.register(literal("hearts").redirect(real));
        dispatcher.register(literal("run").redirect(dispatcher.getRoot()));
        assertEquals(List.of("uwu status", "hearts status"), paths(CommandCatalog.search(dispatcher.getRoot(), "", 20)));
        assertEquals(List.of("hearts status"), paths(CommandCatalog.search(dispatcher.getRoot(), "hearts", 20)));
    }

    @Test
    void neverCallsLocalPermissionOrExecutionCallbacksAndRejectsUnsafeLiteralPaths() {
        var dispatcher = new CommandDispatcher<Object>();
        dispatcher.register(action("display").requires(source -> { throw new AssertionError("Predicate called"); }));
        dispatcher.register(action("unsafe\ntext"));
        dispatcher.register(action("two words"));
        dispatcher.register(action("/second"));
        assertEquals(List.of("display"), paths(CommandCatalog.search(dispatcher.getRoot(), "", 20)));
        assertEquals(new CommandCatalog.Entry("display", "display", false),
                CommandCatalog.search(dispatcher.getRoot(), "display", 20).getFirst());
    }

    @Test
    void limitsResultsAndDepth() {
        var dispatcher = new CommandDispatcher<Object>();
        for (int i = 0; i < 400; i++) dispatcher.register(action("mod" + i));
        assertEquals(3, CommandCatalog.search(dispatcher.getRoot(), "mod", 3).size());
        assertEquals(256, CommandCatalog.search(dispatcher.getRoot(), null, Integer.MAX_VALUE).size());
        assertTrue(CommandCatalog.search(dispatcher.getRoot(), "mod", 0).isEmpty());
        var deep = action("target");
        for (int i = 0; i < 30; i++) deep = literal("branch" + i).then(deep);
        dispatcher.register(deep);
        assertTrue(CommandCatalog.search(dispatcher.getRoot(), "target", 20).isEmpty());
    }

    @Test
    void onlyExactExecutableOppositeLiteralPairsAreBooleanActions() {
        var dispatcher = new CommandDispatcher<Object>();
        var toggles = dispatcher.register(literal("mod").then(literal("switch")
                .then(action("enable")).then(action("disable")).then(action("on")).then(action("off"))
                .then(action("true")).then(action("false")).then(action("status"))));
        dispatcher.register(literal("alias").redirect(toggles));
        dispatcher.register(literal("other").then(action("on")));
        dispatcher.register(literal("nested").then(action("on").then(action("please"))).then(action("off")));
        dispatcher.register(literal("redirect").then(action("on").redirect(toggles)).then(action("off")));
        var root = dispatcher.getRoot();
        for (String option : List.of("on", "off", "enable", "disable", "true", "false"))
            assertTrue(CommandCatalog.isBooleanAction(root, "mod switch " + option));
        for (String path : List.of("mod switch status", "alias switch on", "other on", "nested off", "redirect off",
                "mod switch On", "/mod switch on", "mod switch on ", "mod  switch on", "mod switch on 1"))
            assertFalse(CommandCatalog.isBooleanAction(root, path), path);
    }

    private static RequiredArgumentBuilder<Object, Boolean> enabled() {
        return RequiredArgumentBuilder.<Object, Boolean>argument("enabled", BoolArgumentType.bool())
                .executes(context -> { throw new AssertionError("Boolean command ran"); });
    }

    @Test
    void listsTypedBooleanValuesOnlyWhenTheyUnambiguouslyFinishARealCommand() {
        var dispatcher = new CommandDispatcher<Object>();
        var rule = dispatcher.register(literal("rule").then(literal("lights").then(enabled())));
        dispatcher.register(literal("shortcut").redirect(rule));
        dispatcher.register(literal("ambiguous").then(enabled())
                .then(RequiredArgumentBuilder.argument("text", StringArgumentType.word())));
        dispatcher.register(literal("shadow").then(enabled()).then(action("true")));
        dispatcher.register(literal("more").then(enabled().then(action("extra"))));
        dispatcher.register(literal("missing").then(RequiredArgumentBuilder.argument("enabled", BoolArgumentType.bool())));
        dispatcher.register(literal("after").then(RequiredArgumentBuilder.<Object, String>argument("target", StringArgumentType.word())
                .then(literal("rule").then(enabled()))));
        var root = dispatcher.getRoot();
        assertEquals(List.of("rule lights", "rule lights true", "rule lights false"),
                paths(CommandCatalog.search(root, "rule lights", 10)));
        for (String word : List.of("true", "false")) {
            assertTrue(CommandCatalog.isBooleanAction(root, "rule lights " + word));
            for (String prefix : List.of("shortcut lights", "ambiguous", "shadow", "more", "missing", "after player rule"))
                assertFalse(CommandCatalog.isBooleanAction(root, prefix + " " + word), prefix);
        }
        assertFalse(CommandCatalog.isBooleanAction(root, "rule lights on"));
        assertTrue(CommandCatalog.search(root, "shortcut lights true", 10).isEmpty());
        assertTrue(CommandCatalog.search(root, "ambiguous true", 10).isEmpty());
    }
}
