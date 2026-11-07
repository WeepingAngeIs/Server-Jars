package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Runs against the actual final release JAR in a separate, excluded test mod. */
@Mod(BundleSmoke.MOD_ID)
public final class BundleSmoke {
    public static final String MOD_ID = "uq_bundle_smoke";
    private static final List<String> PRODUCTION_MODS = List.of(
            "cusoultome", "curanks", "cugiant", "culeaderboard", "cuportal", "cubalance", "mawonna", "unyieldingquests");

    public BundleSmoke() {
        require("true".equals(System.getenv("GITHUB_ACTIONS"))
                && "github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")), "Cloud-only test harness");
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    public static Map<String, Object> verifyProductionJar() throws Exception {
        Path expected = Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();
        String expectedHash = System.getenv("UQ_BUNDLE_SHA256");
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(expected)));
        require(actual.equals(expectedHash), "Release JAR changed after assembly");
        Map<String, String> loaded = new LinkedHashMap<>();
        for (String modId : PRODUCTION_MODS) {
            require(ModList.get().isLoaded(modId), "Production mod is missing: " + modId);
            Path modFile = ModList.get().getModFileById(modId).getFile().getFilePath().toRealPath();
            require(modFile.equals(expected), "Production code did not load from final release JAR: " + modId + " -> " + modFile);
            loaded.put(modId, ModList.get().getModContainerById(modId).orElseThrow().getModInfo().getVersion().toString());
        }
        return new LinkedHashMap<>(Map.of("status", "passed", "production_jar", expected.toString(),
                "production_sha256", actual, "production_mods", loaded));
    }

    private void onServerStarted(ServerStartedEvent event) {
        try {
            Map<String, Object> report = verifyProductionJar();
            var server = event.getServer();
            var root = server.getCommands().getDispatcher().getRoot();
            var daily = root.getChild("daily");
            require(daily != null && daily.getCommand() != null, "Rank daily command is missing");
            String dailyOwner = daily.getCommand().getClass().getName();
            require(dailyOwner.startsWith("dev.unyielding.ranks."), "Quest command replaced the rank daily kit: " + dailyOwner);
            require(daily.getChildren().isEmpty(), "Quest subcommands leaked into the standalone rank daily command");
            var kit = root.getChild("kit");
            require(kit != null && kit.getCommand() != null, "Rank /kit root alias is missing");
            require(kit.getCommand().getClass() == daily.getCommand().getClass(), "Rank /kit root alias no longer uses the rank daily executor");
            require(kit != null && kit.getChild("list") != null && kit.getChild("list").getCommand() != null,
                    "Essentials /kit list route is missing");
            String kitOwner = kit.getChild("list").getCommand().getClass().getName();
            require(kitOwner.startsWith("dev.unyielding.quests.essentials."), "Essentials kit command has the wrong owner: " + kitOwner);
            var quests = root.getChild("quests");
            require(quests != null, "Quest root command is missing");
            var questDaily = quests.getChild("daily");
            require(questDaily != null && questDaily.getCommand() != null, "Namespaced quest daily status is missing");
            require(questDaily.getCommand().getClass().getName().startsWith("dev.unyielding.quests."), "Quest daily command has the wrong owner");
            CommandSourceStack publicSource = server.createCommandSourceStack().withPermission(0);
            CommandSourceStack adminSource = server.createCommandSourceStack().withPermission(2);
            require(daily.canUse(publicSource) && kit.canUse(publicSource), "Players cannot use the rank /daily and /kit aliases");
            require(questDaily.canUse(publicSource), "Players cannot use /quests daily");
            var give = questDaily.getChild("give");
            require(give != null && !give.canUse(publicSource) && give.canUse(adminSource), "Quest daily give is not restricted to administrators");
            report.put("rank_daily_executor", dailyOwner);
            report.put("rank_kit_root_executor", kit.getCommand().getClass().getName());
            report.put("essentials_kit_executor", kitOwner);
            report.put("daily_routes_and_permissions", "passed");

            // This invokes the shipped companion's battle/event, meal-count, marriage,
            // evolution-threshold and persistence fixtures. Food-mod availability is
            // intentionally outside this minimal compatibility smoke environment.
            var companion = Class.forName("dev.mawonna.MawonnaCompanion");
            var selfTest = companion.getDeclaredMethod("selfTest", RegistryAccess.class);
            selfTest.setAccessible(true);
            selfTest.invoke(null, server.registryAccess());
            report.put("mawonna_runtime_self_test", "passed");
            report.put("scope", "Final release JAR, Cobblemon 1.8.4 and pinned minimum runtime dependencies; not the full modpack");
            write("/tmp/uq-bundle-server-passed.json", report);
        } catch (Throwable failure) {
            fail("/tmp/uq-bundle-server-failed.txt", failure);
        }
    }

    public static void write(String file, Object value) throws Exception {
        Files.writeString(Path.of(file), new GsonBuilder().setPrettyPrinting().create().toJson(value));
    }

    public static void fail(String file, Throwable failure) {
        failure.printStackTrace();
        try { Files.writeString(Path.of(file), failure.toString()); }
        catch (Exception ignored) { }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
