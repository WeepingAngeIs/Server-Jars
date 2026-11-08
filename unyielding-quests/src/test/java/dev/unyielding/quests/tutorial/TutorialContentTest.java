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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TutorialContentTest {
    @Test
    void rankVisibilityRequiresTheSpecificConfiguredRank() {
        var publicEntry = new TutorialContent.Entry("quests", "/quests", "Open book.");
        var unconfigured = new TutorialContent.Entry("pc", "/pc", "PC.", List.of());
        var vip = new TutorialContent.Entry("crafting", "/crafting", "Craft.", List.of("vip"));
        assertTrue(publicEntry.visibleTo(Set.of()));
        assertFalse(unconfigured.visibleTo(Set.of("vip", "admin")));
        assertFalse(vip.visibleTo(Set.of()));
        assertFalse(vip.visibleTo(Set.of("vip_plus")));
        assertTrue(vip.visibleTo(Set.of("vip")));
        assertFalse(vip.visibleTo(Set.of())); // A later payload does not retain a removed rank.
    }

    @Test
    void oldGuideMigrationPreservesCustomTextAndExplicitRankRules() {
        String original = """
                {"custom_note":"keep me","pages":[
                  {"id":"claims","title":"My claims","description":"Custom description","entries":[
                    {"command":"oparties about","usage":"/oparties about","description":"Old help"}]},
                  {"id":"tools","title":"Tools","description":"My tools","entries":[
                    {"command":"crafting","usage":"/crafting","description":"Custom crafting text"},
                    {"command":"pc","usage":"/pc","description":"Custom PC text","required_ranks":["vip_plus"]}]}
                ]}
                """;
        String upgraded = TutorialContent.migrateLegacy(original);
        assertTrue(upgraded.contains("keep me"));
        TutorialContent guide = TutorialContent.parse(upgraded);
        assertEquals("My claims", guide.pages().getFirst().title());
        assertEquals("Custom description", guide.pages().getFirst().description());
        assertEquals("oparties member invite player", guide.pages().getFirst().entries().getFirst().command());
        assertEquals("Custom crafting text", guide.pages().get(1).entries().getFirst().description());
        assertEquals(List.of(), guide.pages().get(1).entries().getFirst().requiredRanks());
        assertEquals(List.of("vip_plus"), guide.pages().get(1).entries().get(1).requiredRanks());
        assertEquals(upgraded, TutorialContent.migrateLegacy(upgraded));
    }

    @Test
    void parentWithoutAnyAllowedExecutableRouteIsNotAvailable() {
        CommandDispatcher<Integer> dispatcher = new CommandDispatcher<>();
        dispatcher.register(command("container")
                .then(command("admin").requires(level -> level >= 2).executes(c -> 1)));
        assertFalse(TutorialContent.available(dispatcher.getRoot(), 0, "container"));
        assertTrue(TutorialContent.available(dispatcher.getRoot(), 2, "container"));
    }

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
            var entries = content.pages().stream().flatMap(page -> page.entries().stream()).toList();
            assertFalse(entries.stream().anyMatch(entry -> entry.command().equals("oparties about")));
            assertTrue(entries.stream().anyMatch(entry -> entry.command().equals("oparties member invite player")));
            assertTrue(entries.stream().filter(entry -> Set.of("crafting", "pc", "healpokemon")
                    .contains(entry.command())).allMatch(entry -> !entry.visibleTo(Set.of())));
            assertTrue(content.pages().getFirst().description().contains("inventory"));
            assertFalse(content.pages().getFirst().description().contains("J opens"));
        }
    }
}
