package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Independent harness only. Never packaged inside either production JAR. */
public final class SplitAssertions {
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static String hash(Path p) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));
    }
    public static void identity(MinecraftServer server) {
        try {
            require("true".equals(System.getenv("GITHUB_ACTIONS")) && "github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")), "Not a disposable cloud runner");
            Path companion = Path.of(System.getenv("UQ_BUNDLE_JAR")).toRealPath();
            Path jail = Path.of(System.getenv("UQ_JAIL_JAR")).toRealPath();
            require(!jail.equals(companion), "Expected two separate production JARs");
            require(hash(companion).equals(System.getenv("UQ_BUNDLE_SHA256")), "Companion changed unexpectedly");
            require(hash(jail).equals(System.getenv("UQ_JAIL_SHA256")), "Jail JAR changed unexpectedly");
            require(ModList.get().getModFileById("minecraftjail").getFile().getFilePath().toRealPath().equals(jail), "Jail did not load from the final standalone JAR");
            for (String id : List.of("unyieldingquests", "cusoultome", "curanks", "cugiant", "culeaderboard", "cuportal", "cubalance", "mawonna")) {
                require(ModList.get().isLoaded(id) && ModList.get().getModFileById(id).getFile().getFilePath().toRealPath().equals(companion), "Non-jail component missing or loaded from wrong artifact: " + id);
            }
            try { Class.forName("dev.unyielding.quests.jail.JailData"); throw new IllegalStateException("Old integrated jail code loaded"); }
            catch (ClassNotFoundException expected) { }
            String classpath = System.getProperty("java.class.path", "");
            require(!classpath.contains("unyielding-quests/build/classes") && !classpath.contains("minecraft-jail/build/classes"), "Production source classes leaked into runtime");
            var root = server.getCommands().getDispatcher().getRoot();
            for (String name : List.of("quests", "jail", "unjail", "jailbuild", "jailcustody", "jailintegrated")) require(root.getChild(name) != null, "Missing production command " + name);
            var advancement = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("unyieldingquests", "jail_escape"));
            require(advancement != null && advancement.value().rewards().equals(net.minecraft.advancements.AdvancementRewards.EMPTY)
                && !advancement.value().display().orElseThrow().shouldAnnounceChat(), "Escape achievement rewards or broadcast changed");
            String phase = System.getenv("UQ_PACKAGED_PHASE");
            if (phase == null) phase = System.getProperty("uq.retainedPhase", System.getProperty("uq.certifiedPhase", System.getProperty("uq.integratedPhase", System.getProperty("uq.jailTerrainPhase", "0"))));
            String world = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
            String probe = Boolean.getBoolean("uq.integratedCompanionProbe") ? "companion-refusal" : Boolean.getBoolean("uq.integratedDependencyProbe") ? "dependency-refusal" : "normal";
            var proof = new TreeMap<String, Object>();
            proof.put("commit", System.getenv("GITHUB_SHA")); proof.put("world", world); proof.put("phase", phase); proof.put("probe", probe);
            proof.put("jail_sha256", hash(jail)); proof.put("companion_sha256", hash(companion));
            proof.put("separate_jars_loaded", true); proof.put("non_jail_mods_loaded", true); proof.put("old_jail_classes_absent", true); proof.put("escape_reward_empty", true);
            Path evidence = Path.of(System.getenv("GITHUB_WORKSPACE"), "dist/packaged-smoke");
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("split-identity-" + world + "-" + phase + "-" + probe + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception failure) { throw new IllegalStateException("Split production artifact identity check failed", failure); }
    }
    public static void enforcement(ServerPlayer player) throws Exception {
        var server = player.server;
        var assignment = JailData.get(server).assignment(player.getUUID());
        require(assignment != null && JailCustodyService.quarantined(player), "Enforcement fixture needs a real admitted prisoner");
        var jail = assignment.jail();
        int[] executed = {0};
        server.getCommands().getDispatcher().register(Commands.literal("uqsplit_forbidden").executes(c -> ++executed[0]));
        var event = new CommandEvent(server.getCommands().getDispatcher().parse("uqsplit_forbidden", player.createCommandSourceStack()));
        NeoForge.EVENT_BUS.post(event); require(event.isCanceled(), "Jailed command event not cancelled");
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "uqsplit_forbidden");
        require(executed[0] == 0, "Jailed player command executed");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "uqsplit_forbidden");
        require(executed[0] == 1, "Command positive control failed");
        var teleport = new EntityTeleportEvent.TeleportCommand(player, jail.x()+50, jail.y(), jail.z());
        NeoForge.EVENT_BUS.post(teleport); require(teleport.isCanceled(), "Jailed teleport command not blocked");
        var dimension = new EntityTravelToDimensionEvent(player, net.minecraft.world.level.Level.NETHER);
        NeoForge.EVENT_BUS.post(dimension); require(dimension.isCanceled(), "Jailed dimension travel not blocked");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp " + player.getGameProfile().getName() + " " + (jail.x()+50) + " " + jail.y() + " " + jail.z());
        require(player.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "Actual teleport command escaped confinement");
        var prior = assignment.prior();
        player.teleportTo(prior.level(server), prior.x(), prior.y(), prior.z(), prior.yaw(), prior.pitch());
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        require(player.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001 && player.isAlive(), "Direct API teleport was not safely recontained");
        require(JailCustodyService.quarantined(player), "Enforcement test lost custody restrictions");
    }
}
