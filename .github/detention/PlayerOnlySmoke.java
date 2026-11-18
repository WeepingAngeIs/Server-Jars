package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.unyielding.jail.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Drives two real clients through the mod's commands and observes the server respawn. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class PlayerOnlySmoke {
    static final Map<String,Object> proof = new LinkedHashMap<>();
    static final Path results = Path.of(System.getenv("GITHUB_WORKSPACE"), "dist/packaged-smoke");
    static final Path signal = Path.of("/tmp/uq-player-only-result");
    static ServerPlayer corpse;
    static int firstDeaths, deadline, phase;
    static boolean started, done;

    static int cmd(String text) throws Exception { return CertifiedJailSmoke.command(text); }
    static void flag(String key) { proof.put(key, true); }
    static void require(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
    }
    static int deaths(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
    }
    static Path evidence(ServerPlayer p, String kind) {
        return CertifiedJailSmoke.world.resolve("center-" + p.getUUID() + "-" + kind);
    }
    static void focus(ServerPlayer p) throws IOException {
        CertifiedJailSmoke.target = p;
        CertifiedJailSmoke.original = Files.readAllBytes(evidence(p,"equipment"));
        CertifiedJailSmoke.last = UUID.fromString(Files.readString(evidence(p,"transaction")));
    }
    static void seed(ServerPlayer p, boolean wallet) throws Exception {
        CertifiedJailSmoke.target = p;
        CertifiedJailSmoke.seed();
        if (wallet) PackedWalletProof.seed(p);
        CertifiedJailSmoke.flush();
        CertifiedJailSmoke.saved();
        Files.write(evidence(p,"equipment"), CertifiedJailSmoke.items(UUID.randomUUID(),null).current().inventory());
    }
    static void remember(ServerPlayer p) throws Exception {
        UUID transaction = JailIntegrated.activeTransaction(p.server,p.getUUID());
        require(transaction != null, "No active transaction");
        Files.writeString(evidence(p,"transaction"), transaction.toString());
        focus(p);
    }
    static void cell(ServerPlayer p, int n) throws Exception {
        var data = JailData.get(p.server);
        var a = data.assignment(p.getUUID());
        require(a != null && a.geometry() == 4, "Lost numbered assignment");
        var first = data.location();
        var j = a.jail();
        require(j.dim().equals(first.dim()) && j.x() == first.x()+(n%2==0?12:0)
                && j.y() == first.y() && j.z() == first.z()+6*((n-1)/2), "Wrong destination for cell " + n);
        require(p.serverLevel().dimension().location().toString().equals(j.dim()) &&
                p.distanceToSqr(j.x(), j.y(), j.z()) < 0.0001, "Player outside assigned position");
        require(Files.readString(evidence(p,"transaction")).equals(JailIntegrated.activeTransaction(p.server,p.getUUID()).toString()), "Transaction changed");
        focus(p);
        CertifiedJailSmoke.held();
        if (p.getGameProfile().getName().equals("Visitor")) PackedWalletProof.held(p,CertifiedJailSmoke.last);
    }
    static void begin(ServerPlayer dev, ServerPlayer visitor) throws Exception {
        CenterBuildProof.place(dev,visitor);
        flag("preview_place_undo_and_six_intact_cells");
        dev.server.getPlayerList().deop(dev.getGameProfile());
        require(!dev.hasPermissions(2), "Second prisoner still has administrator permission");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get() && !JailConfig.JAIL_PACKED_WALLETS.get(), "Admission defaults changed");
        seed(dev,false);
        seed(visitor,true);
        require(cmd("jailcustody init")==1 && cmd("jailintegrated init")==1, "Cannot provision histories");
        require(cmd("jail Visitor 6")==0, "Disabled admission accepted");
        require(cmd("jailintegrated mode supported")==1, "Supported mode unavailable");
        JailConfig.JAIL_PACKED_WALLETS.set(false);
        require(cmd("jail Visitor 6")==0, "Packed wallet accepted without opt-in");
        JailConfig.JAIL_PACKED_WALLETS.set(true);
        JailConfig.JAIL_PACKED_WALLETS.save();
        require(cmd("jail Visitor 6")==1, "Explicit cell admission failed");
        remember(visitor);
        require(cmd("jail Dev 6")==0 && JailIntegrated.activeTransaction(dev.server,dev.getUUID())==null,
                "Occupied request accepted or created a transaction");
        CertifiedJailSmoke.target = dev;
        require(Arrays.equals(Files.readAllBytes(evidence(dev,"equipment")), CertifiedJailSmoke.items(UUID.randomUUID(),null).current().inventory()),
                "Rejected cell request changed second player's equipment");
        require(cmd("jail Dev")==1, "Automatic admission failed");
        remember(dev);
        require(cmd("jailcells")==1, "Roster refused");
        cell(dev,1);
        cell(visitor,6);
        flag("explicit_automatic_distinct_cells_and_no_clobber");
        require(!visitor.serverLevel().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN), "Global respawn gamerule changed");
        firstDeaths = deaths(visitor);
        corpse = visitor;
        visitor.setInvulnerable(false);
        CertifiedJailSmoke.cross();
        require(!visitor.isAlive() && deaths(visitor)==firstDeaths+1, "Movement packet escape failed to cause exactly one death");
        deadline = dev.server.getTickCount()+600;
    }
    static void afterRespawn(ServerPlayer dev, ServerPlayer visitor) throws Exception {
        if (!visitor.isAlive()) return;
        require(visitor != corpse && visitor.connection.player == visitor && deaths(visitor)==firstDeaths+1, "Invalid respawn replacement");
        require(ProductionObserver.automaticCalls==1 && ProductionObserver.automaticCompletions==1 &&
                ProductionObserver.clientRespawnRequests==0,"Respawn did not take the no-button server path");
        Path report = results.resolve("player-only-client-Visitor-1.json");
        if (!Files.isRegularFile(report)) return;
        // Client writes from another process. A partial read is retried on the next tick.
        com.google.gson.JsonObject view;
        try { view=JsonParser.parseString(Files.readString(report)).getAsJsonObject(); }
        catch (RuntimeException incomplete) { return; }
        if (view.getAsJsonArray("verified_respawns").isEmpty()) return;
        require(view.get("respawn_actions_sent").getAsInt()==0, "Client sent a respawn action");
        cell(dev,1);
        cell(visitor,6);
        flag("escape_native_respawn_in_cell_six_without_client_action");
        require(cmd("jailintegrated mode disabled")==1, "Could not freeze admissions for restart");
        pass();
    }
    static void release(ServerPlayer p) throws Exception {
        focus(p);
        require(cmd("unjail "+p.getGameProfile().getName())==1, "Release failed for "+p.getGameProfile().getName());
        CertifiedJailSmoke.returned();
        if (p.getGameProfile().getName().equals("Visitor")) PackedWalletProof.returned(p);
    }
    static void resumed(ServerPlayer dev, ServerPlayer visitor) throws Exception {
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(), "Admission flag changed at restart");
        require(cmd("jailcells")==1, "Restart roster unavailable");
        cell(dev,1);
        cell(visitor,6);
        release(dev);
        cell(visitor,6);
        release(visitor);
        flag("restart_preserved_assignments_and_both_exact_inventories_and_wallet");
        require(cmd("jailintegrated mode supported")==1 && cmd("jail Dev 6")==1, "Released cell not reusable by another owner");
        remember(dev);
        cell(dev,6);
        release(dev);
        require(cmd("jailcells")==1 && JailIntegrated.activeTransaction(dev.server,dev.getUUID())==null &&
                JailIntegrated.activeTransaction(visitor.server,visitor.getUUID())==null, "Stale reservation after release");
        flag("released_cell_reused_by_other_player_and_closed");
        pass();
    }
    static void pass() throws Exception {
        CertifiedJailSmoke.saved();
        proof.put("phase", phase);
        proof.put("commit", System.getenv("GITHUB_SHA"));
        proof.put("jail_sha256", System.getenv("UQ_JAIL_SHA256"));
        proof.put("companion_sha256", System.getenv("UQ_BUNDLE_SHA256"));
        Files.writeString(results.resolve("center-phase"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        Files.writeString(signal,"passed: detention center phase "+phase+"\n");
        done = true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event) {
        if (done || !ProductionObserver.enabled()) return;
        try {
            MinecraftServer server = event.getServer();
            ServerPlayer dev = server.getPlayerList().getPlayerByName("Dev"), visitor = server.getPlayerList().getPlayerByName("Visitor");
            if (dev == null || visitor == null) return;
            CertifiedJailSmoke.admin=dev;
            CertifiedJailSmoke.target=visitor;
            if (!started) {
                if (server.getTickCount()<120 || dev.tickCount<100 || visitor.tickCount<100) return;
                started=true;
                CertifiedJailSmoke.world=server.getWorldPath(LevelResource.ROOT);
                require(CertifiedJailSmoke.world.toRealPath().getFileName().toString().equals("jail_certified_smoke"), "Wrong world");
                ProductionObserver.identity();
                phase=Integer.getInteger("uq.playerOnlyPhase");
                if (phase==1) begin(dev,visitor); else if (phase==2) resumed(dev,visitor); else throw new IOException("Unknown phase");
            } else {
                require(server.getTickCount()<=deadline, "Client did not observe no-button respawn");
                afterRespawn(dev,visitor);
            }
        } catch (Throwable failure) {
            done=true;
            failure.printStackTrace();
            try { Files.writeString(signal,"failed: "+failure+"\n"); } catch (IOException ignored) {}
        }
    }
}
