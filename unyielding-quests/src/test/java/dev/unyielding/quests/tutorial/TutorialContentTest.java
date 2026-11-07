package dev.unyielding.quests.tutorial;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TutorialContentTest {
    private static LiteralArgumentBuilder<Integer> command(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    @Test
    void filtersRestrictedRootsAndNestedCommands() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.register(command("public").executes(c -> 1)
                .then(command("admin").requires(level -> level >= 2).executes(c -> 1)));
        dispatcher.register(command("op").requires(level -> level >= 2).executes(c -> 1));
        assertEquals(List.of("/public"), TutorialContent.catalog(dispatcher.getRoot(), 0).usages());
        assertTrue(TutorialContent.available(dispatcher.getRoot(), 0, "public"));
        assertFalse(TutorialContent.available(dispatcher.getRoot(), 0, "public admin"));
        assertFalse(TutorialContent.available(dispatcher.getRoot(), 0, "op"));
        assertFalse(TutorialContent.available(dispatcher.getRoot(), 0, "missing"));
    }

    @Test
    void argumentPathsAndFutureCommandsComeFromLiveRegistrations() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.register(command("tpa").then(RequiredArgumentBuilder.<Integer, String>argument(
                "player", StringArgumentType.word()).executes(c -> 1)));
        assertTrue(TutorialContent.available(dispatcher.getRoot(), 0, "tpa player"));
        assertEquals(List.of("/tpa <player>"), TutorialContent.catalog(dispatcher.getRoot(), 0).usages());
        dispatcher.register(command("newfeature").executes(c -> 1));
        assertTrue(TutorialContent.catalog(dispatcher.getRoot(), 0).usages().contains("/newfeature"));
    }

    @Test
    void followsAliasesWithoutDuplicatesAndPreservesTargetPermissions() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        CommandNode<Integer> target = dispatcher.register(command("message").executes(c -> 1));
        dispatcher.register(command("msg").redirect(target));
        CommandNode<Integer> admin = dispatcher.register(command("admin").requires(level -> level >= 2).executes(c -> 1));
        dispatcher.register(command("hiddenalias").redirect(admin));
        var usages = TutorialContent.catalog(dispatcher.getRoot(), 0).usages();
        assertEquals(List.of("/message", "/msg"), usages);
        assertEquals(usages.size(), new HashSet<>(usages).size());
        assertFalse(TutorialContent.available(dispatcher.getRoot(), 0, "hiddenalias"));
    }

    @Test
    void rootRedirectDoesNotRecurseForever() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.register(command("visible").executes(c -> 1));
        dispatcher.register(command("loop").redirect(dispatcher.getRoot()));
        TutorialContent.Catalog catalog = TutorialContent.catalog(dispatcher.getRoot(), 0);
        assertEquals(List.of("/visible"), catalog.usages());
        assertFalse(catalog.truncated());
    }

    @Test
    void oversizedTreesHaveAnExplicitBound() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        for (int i = 0; i < TutorialContent.MAX_USAGES + 20; i++) {
            dispatcher.register(command("feature" + i).executes(c -> 1));
        }
        TutorialContent.Catalog catalog = TutorialContent.catalog(dispatcher.getRoot(), 0);
        assertEquals(TutorialContent.MAX_USAGES, catalog.usages().size());
        assertTrue(catalog.truncated());
    }

    @Test
    void throwingThirdPartyRequirementFailsClosed() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.register(command("broken").requires(level -> { throw new IllegalStateException("Unavailable"); })
                .executes(c -> 1));
        assertTrue(TutorialContent.catalog(dispatcher.getRoot(), 0).usages().isEmpty());
        assertFalse(TutorialContent.available(dispatcher.getRoot(), 0, "broken"));
    }

    @Test
    void directoryDescriptionsUseTheMostSpecificRegisteredPath() {
        TutorialContent content = new TutorialContent(List.of(new TutorialContent.Page(
                "travel", "Travel", "Places to go", List.of(
                new TutorialContent.Entry("quests", "/quests", "Open the book."),
                new TutorialContent.Entry("quests stats", "/quests stats", "See your progress."),
                new TutorialContent.Entry("tpa player", "/tpa <player>", "Request a visit.")))));
        assertEquals("See your progress.", content.describe("/quests stats"));
        assertEquals("Request a visit.", content.describe("/tpa <player>"));
        assertTrue(content.describe("/future").startsWith("Available on this server."));
    }

    @Test
    void malformedOrInvalidJsonPreservesLastValidContent() {
        TutorialContent fallback = new TutorialContent(List.of());
        for (String json : List.of("{", "{\"pages\":null}", "{\"pages\":[{\"id\":\"same\"}]}")) {
            TutorialContent.LoadResult result = TutorialContent.parseOrFallback(json, fallback);
            assertSame(fallback, result.content());
            assertNotNull(result.error());
        }
    }

    @Test
    void packagedDefaultIsValidAndHasEditableCommandPaths() throws IOException {
        try (var input = getClass().getResourceAsStream("/data/unyieldingquests/tutorial.json")) {
            assertNotNull(input);
            TutorialContent content = TutorialContent.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(content.pages().size() >= 5);
            assertTrue(content.pages().stream().flatMap(page -> page.entries().stream())
                    .anyMatch(entry -> entry.command().equals("tutorial") && entry.usage().equals("/tutorial")));
        }
    }
}
