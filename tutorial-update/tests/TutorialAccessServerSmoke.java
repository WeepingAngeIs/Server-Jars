package dev.unyielding.bundlesmoke;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import dev.unyielding.quests.tutorial.TutorialManager;
import dev.unyielding.quests.tutorial.TutorialPreview;
import dev.unyielding.quests.tutorial.TutorialRanks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.unyielding.bundlesmoke.BundleSmoke.require;

/** Real-player guide filtering against the final JAR; this helper is never distributed. */
@EventBusSubscriber(modid = BundleSmoke.MOD_ID)
public final class TutorialAccessServerSmoke {
    private static final String RANK = "uq_guide_fixture";
    private static final Path SUCCESS = Path.of("/tmp/uq-tutorial-access-passed.json");
    private static boolean checked;

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (checked || !Files.exists(Path.of("/tmp/uq-admin-features-passed.json"))) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayerByName("Dev");
        if (player == null) return;
        checked = true;
        try {
            Map<String, Object> report = BundleSmoke.verifyProductionJar();
            exercise(player, report);
            report.put("scope", "Final-JAR real connected player, curated command fixtures and live bundled CuRanks data; not the complete server permission stack");
            BundleSmoke.write(SUCCESS.toString(), report);
        } catch (Throwable failure) {
            BundleSmoke.fail("/tmp/uq-tutorial-access-failed.txt", failure);
        }
    }

    private static void exercise(ServerPlayer player, Map<String, Object> report) throws Exception {
        var server = player.getServer();
        var players = server.getPlayerList();
        var profile = player.getGameProfile();
        boolean originalOperator = players.isOp(profile);
        Path config = FMLPaths.CONFIGDIR.get().resolve("unyieldingquests/tutorial.json");
        byte[] originalConfig = Files.readAllBytes(config);
        var dispatcher = server.getCommands().getDispatcher();
        var root = dispatcher.getRoot();
        List<String> addedRoots = new ArrayList<>();
        try (RankFixture ranks = new RankFixture(player)) {
            try {
                players.op(profile);
                require(player.hasPermissions(2), "Operator test player did not receive operator permission");
                CompoundTag defaults = TutorialManager.payload(player);
                Set<String> defaultUsages = allUsages(defaults);
                for (String command : List.of("op", "give", "healpokemon", "pc", "crafting", "anvil", "smithing", "stonecutter")) {
                    require(defaultUsages.stream().noneMatch(usage -> usage.equals("/" + command) || usage.startsWith("/" + command + " ")),
                            "Default operator guide exposes unavailable player command: " + command);
                }
                report.put("default_operator_guide_omits_admin_and_unconfigured_rank_commands", "passed");

                for (String command : List.of("uqguide_public", "uqguide_rank", "uqguide_hidden", "uqguide_unknown")) {
                    require(root.getChild(command) == null, "Guide fixture command already exists");
                    dispatcher.register(Commands.literal(command).executes(context -> 0));
                    addedRoots.add(command);
                }
                dispatcher.register(Commands.literal("uqguide_operator").requires(source -> source.hasPermission(2)).executes(context -> 0));
                addedRoots.add("uqguide_operator");
                dispatcher.register(Commands.literal("uqguide_entity_operator").requires(source -> source.getPlayer() != null
                        && source.getPlayer().hasPermissions(2)).executes(context -> 0));
                addedRoots.add("uqguide_entity_operator");
                dispatcher.register(Commands.literal("uqguide_profile_operator").requires(source -> source.getPlayer() != null
                        && source.getServer().getPlayerList().isOp(source.getPlayer().getGameProfile())).executes(context -> 0));
                addedRoots.add("uqguide_profile_operator");
                if (root.getChild("oparties") == null) {
                    dispatcher.register(Commands.literal("oparties").then(Commands.literal("member")
                            .then(Commands.literal("invite").then(Commands.argument("player", StringArgumentType.word()).executes(context -> 0)))));
                    addedRoots.add("oparties");
                }

                JsonObject document = fixtureDocument();
                Files.writeString(config, document.toString());
                require(TutorialManager.reload(), "Guide fixture failed to reload");
                CompoundTag operatorGuide = TutorialManager.payload(player);
                require(allUsages(operatorGuide).equals(Set.of("/uqguide_public")), "Operator privileges bypass guide policy or unknown-root filtering");
                assertPagesMatchDirectory(operatorGuide);
                GameProfile neutralProfile = (GameProfile) field(TutorialPreview.class, "PROFILE").get(null);
                var originalNeutralOp = players.getOps().get(neutralProfile);
                try {
                    // Force the same branch used when a LAN world treats every profile as an operator.
                    players.op(neutralProfile);
                    require(players.isOp(neutralProfile), "Neutral profile did not enter the global-privilege fallback fixture");
                    CommandSourceStack fallback = TutorialPreview.source(player);
                    require(fallback.getEntity() == null && fallback.hasPermission(0) && !fallback.hasPermission(1),
                            "Globally privileged preview did not fall back to an entity-free permission-zero source");
                    require(allUsages(TutorialManager.payload(player)).equals(Set.of("/uqguide_public")),
                            "Global operator bypass leaked entity/profile-only commands into the guide");
                    require(players.isOp(profile) && player.hasPermissions(2), "Guide preview changed the real player's privileges");
                } finally {
                    players.deop(neutralProfile);
                    if (originalNeutralOp != null) players.getOps().add(originalNeutralOp);
                    TutorialPreview.clear();
                }
                report.put("global_operator_fallback_has_no_entity_or_privilege_leak", "passed");
                players.deop(profile);
                require(!player.hasPermissions(2), "Nonoperator test player still has operator permission");
                CompoundTag unrankedGuide = TutorialManager.payload(player);
                require(allUsages(unrankedGuide).equals(Set.of("/uqguide_public")), "Unranked guide exposes locked or unknown commands");
                assertPagesMatchDirectory(unrankedGuide);

                ranks.grant(0L);
                require(TutorialRanks.activeRanks(player).contains(RANK), "Live bundled rank grant was not observed");
                CompoundTag rankedGuide = TutorialManager.payload(player);
                require(allUsages(rankedGuide).equals(Set.of("/uqguide_public", "/uqguide_rank")), "Live rank grant did not reveal exactly its curated command");
                assertPagesMatchDirectory(rankedGuide);
                players.op(profile);
                require(allUsages(TutorialManager.payload(player)).equals(Set.of("/uqguide_public", "/uqguide_rank")),
                        "Operator preview lost the real player's rank grant or exposed operator-only entries");
                players.deop(profile);
                ranks.grant(System.currentTimeMillis() - 60_000L);
                require(!TutorialRanks.activeRanks(player).contains(RANK), "Expired rank remains active");
                require(allUsages(TutorialManager.payload(player)).equals(Set.of("/uqguide_public")), "Expired rank command remains visible");
                ranks.grant(0L);
                ranks.clear();
                require(!TutorialRanks.activeRanks(player).contains(RANK), "Revoked rank remains active");
                require(allUsages(TutorialManager.payload(player)).equals(Set.of("/uqguide_public")), "Revoked rank command remains visible");
                report.put("curated_directory_matches_pages_and_ignores_unlisted_roots", "passed");
                report.put("nonoperator_and_operator_do_not_bypass_rank_rules", "passed");
                report.put("live_rank_grant_expiry_and_revocation", "passed");

                JsonObject legacy = JsonParser.parseString("""
                        {"custom_note":"keep this administrator note","pages":[
                          {"id":"claims","title":"Custom claims title","description":"Custom wording stays.","entries":[
                            {"command":"oparties about","usage":"/oparties about","description":"Old party information"}]},
                          {"id":"book","title":"My quest book","description":"J opens it, and the Premier Ball button in your inventory opens the same book.","entries":[
                            {"command":"uqguide_public","usage":"/uqguide_public","description":"Custom player help"}]},
                          {"id":"tools","title":"Tools","description":"Keep this description","entries":[
                            {"command":"crafting","usage":"/crafting","description":"Old unrestricted tool"}]}]}
                        """).getAsJsonObject();
                Files.writeString(config, legacy.toString());
                require(TutorialManager.reload(), "Legacy guide migration failed");
                JsonObject migrated = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
                require(migrated.get("schema_version").getAsInt() == 2, "Legacy schema version was not advanced");
                require(migrated.get("custom_note").getAsString().equals("keep this administrator note"), "Migration lost custom fields");
                require(migrated.getAsJsonArray("pages").get(0).getAsJsonObject().get("title").getAsString().equals("Custom claims title"), "Migration lost custom title");
                Set<String> migratedUsages = allUsages(TutorialManager.payload(player));
                require(migratedUsages.contains("/oparties member invite <player>") && !migratedUsages.contains("/oparties about"), "Migrated guide did not replace party information with invitation command");
                require(!migratedUsages.contains("/crafting"), "Migration left a rank-only legacy tool visible");
                String bookDescription = migrated.getAsJsonArray("pages").get(1).getAsJsonObject().get("description").getAsString();
                require(bookDescription.contains("inventory") && bookDescription.contains("top-left") && !bookDescription.contains("J opens"), "Migration retained obsolete quest-book opening instructions");
                String afterFirstReload = Files.readString(config);
                require(TutorialManager.reload() && Files.readString(config).equals(afterFirstReload), "Migration repeats or changes the edited document on subsequent reload");
                report.put("legacy_guide_migrates_invite_inventory_hint_and_rank_policy_once", "passed");
                report.put("custom_guide_wording_and_fields_survive_migration", "passed");
            } finally {
                Files.write(config, originalConfig);
                require(TutorialManager.reload(), "Original guide could not be restored");
                if (originalOperator) players.op(profile); else players.deop(profile);
                for (String name : addedRoots) removeRoot(root, name);
            }
        }
    }

    private static JsonObject fixtureDocument() {
        JsonObject document = new JsonObject();
        document.addProperty("schema_version", 2);
        JsonArray pages = new JsonArray();
        JsonObject page = new JsonObject();
        page.addProperty("id", "access_fixture");
        page.addProperty("title", "Guide access fixture");
        page.addProperty("description", "Test only");
        JsonArray entries = new JsonArray();
        entries.add(entry("uqguide_public", null));
        entries.add(entry("uqguide_rank", List.of(RANK)));
        entries.add(entry("uqguide_hidden", List.of()));
        entries.add(entry("uqguide_operator", null));
        entries.add(entry("uqguide_entity_operator", null));
        entries.add(entry("uqguide_profile_operator", null));
        page.add("entries", entries);
        pages.add(page);
        document.add("pages", pages);
        return document;
    }

    private static JsonObject entry(String name, List<String> ranks) {
        JsonObject entry = new JsonObject();
        entry.addProperty("command", name);
        entry.addProperty("usage", "/" + name);
        entry.addProperty("description", "Test only");
        if (ranks != null) {
            JsonArray values = new JsonArray();
            ranks.forEach(values::add);
            entry.add("required_ranks", values);
        }
        return entry;
    }

    private static Set<String> directory(CompoundTag payload) {
        Set<String> result = new LinkedHashSet<>();
        for (Tag entry : payload.getList("directory", Tag.TAG_COMPOUND)) result.add(((CompoundTag) entry).getString("usage"));
        return result;
    }

    private static Set<String> allUsages(CompoundTag payload) {
        Set<String> result = directory(payload);
        for (Tag page : payload.getList("pages", Tag.TAG_COMPOUND)) {
            for (Tag entry : ((CompoundTag) page).getList("entries", Tag.TAG_COMPOUND)) result.add(((CompoundTag) entry).getString("usage"));
        }
        return result;
    }

    private static void assertPagesMatchDirectory(CompoundTag payload) {
        require(directory(payload).equals(allUsages(payload)), "Tutorial pages and searchable directory disagree");
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void removeRoot(CommandNode<CommandSourceStack> root, String name) throws ReflectiveOperationException {
        for (String map : List.of("children", "literals", "arguments")) ((Map<?, ?>) field(CommandNode.class, map).get(root)).remove(name);
    }

    private static final class RankFixture implements AutoCloseable {
        private final Map<String, Object> players;
        private final String key;
        private final Object originalEntry;
        private final Object config;
        private final Field configRanks;
        private final Object originalRanks;
        private final Object entry;

        @SuppressWarnings("unchecked")
        RankFixture(ServerPlayer player) throws ReflectiveOperationException {
            Class<?> ranksType = Class.forName("dev.unyielding.ranks.CuRanks");
            config = field(ranksType, "cfg").get(null);
            Object store = field(ranksType, "store").get(null);
            require(config != null && store != null, "Bundled rank module is not initialized");
            players = (Map<String, Object>) field(store.getClass(), "players").get(store);
            key = player.getUUID().toString();
            originalEntry = players.get(key);
            configRanks = field(config.getClass(), "ranks");
            originalRanks = configRanks.get(config);
            Object rank = Class.forName("dev.unyielding.ranks.RanksConfig$Rank").getConstructor().newInstance();
            field(rank.getClass(), "id").set(rank, RANK);
            List<Object> replacement = new ArrayList<>((List<Object>) originalRanks);
            replacement.add(rank);
            entry = Class.forName("dev.unyielding.ranks.PlayerStore$Entry").getConstructor().newInstance();
            configRanks.set(config, replacement);
            players.put(key, entry);
        }

        void grant(long expiresAt) throws ReflectiveOperationException {
            Object grant = Class.forName("dev.unyielding.ranks.PlayerStore$Grant").getConstructor().newInstance();
            field(grant.getClass(), "rank").set(grant, RANK);
            field(grant.getClass(), "expiresAt").setLong(grant, expiresAt);
            field(entry.getClass(), "ranks").set(entry, new ArrayList<>(List.of(grant)));
        }

        void clear() throws ReflectiveOperationException {
            field(entry.getClass(), "ranks").set(entry, new ArrayList<>());
        }

        @Override
        public void close() throws ReflectiveOperationException {
            configRanks.set(config, originalRanks);
            if (originalEntry == null) players.remove(key); else players.put(key, originalEntry);
        }
    }
}
