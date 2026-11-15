package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.essentials.EssConfig;
import dev.unyielding.quests.team.TeamManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Development-only, disposable-world candidate test; excluded from release jars. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class MeetingTwoPlayerSmoke {
    private static boolean done;
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if (done || !Boolean.getBoolean("uq.meetingTwoPlayer")) return;
        var server = e.getServer();
        var owner = server.getPlayerList().getPlayerByName("Dev");
        var visitor = server.getPlayerList().getPlayerByName("Visitor");
        if (owner == null || visitor == null || server.getTickCount() < 100) return;
        done = true;
        try {
            if (!net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")) throw new IllegalStateException("OPAC not loaded");
            EssConfig.PARTY_MEET_ENABLED.set(true);
            EssConfig.PARTY_MEET_PUBLIC.set(true);
            require(TeamManager.createParty(owner, "Smoke Party") == null, "create party");
            require(TeamManager.invite(owner, "Visitor") == null, "invite");
            require(TeamManager.join(visitor, TeamManager.teamOf(owner.getUUID()).id.toString()) == null, "join");
            var level = owner.serverLevel();
            BlockPos base = owner.blockPosition();
            // Safe floor and clear headroom in the already loaded spawn chunk.
            BlockPos destination = new BlockPos((base.getX() >> 4) * 16 + 8, base.getY(), (base.getZ() >> 4) * 16 + 8);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                level.setBlockAndUpdate(destination.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(destination.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            owner.teleportTo(level, destination.getX() + .5, destination.getY(), destination.getZ() + .5, 0, 0);
            visitor.teleportTo(level, destination.getX() + 2.5, destination.getY(), destination.getZ() + .5, 0, 0);
            server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "partymeet set");
            var party = TeamManager.teamOf(owner.getUUID());
            var visitorId = visitor.getUUID();
            // All denials must leave the connected visitor exactly where they started.
            EssConfig.PARTY_MEET_PUBLIC.set(false);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "permission denied but visitor moved");
            EssConfig.PARTY_MEET_PUBLIC.set(true);
            party.members.remove(visitorId);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "non-member moved");
            party.members.put(visitorId, dev.unyielding.quests.team.Team.Rank.MEMBER);
            party.members.put(owner.getUUID(), dev.unyielding.quests.team.Team.Rank.MEMBER);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "former officer's point allowed movement");
            party.members.put(owner.getUUID(), dev.unyielding.quests.team.Team.Rank.OWNER);
            level.setBlockAndUpdate(destination, Blocks.STONE.defaultBlockState());
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "obstructed destination allowed movement");
            level.setBlockAndUpdate(destination, Blocks.AIR.defaultBlockState());
            var apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var api = apiClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            var manager = apiClass.getMethod("getServerClaimsManager").invoke(api);
            var managerApi = Class.forName("xaero.pac.common.server.claims.api.IServerClaimsManagerAPI");
            var dim = ResourceLocation.parse("minecraft:overworld");
            int cx = destination.getX() >> 4, cz = destination.getZ() >> 4;
            managerApi.getMethod("claim", ResourceLocation.class, java.util.UUID.class, int.class, int.class, int.class, boolean.class)
                    .invoke(manager, dim, owner.getUUID(), 0, cx, cz, false);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "visitor moved into denied claim");
            managerApi.getMethod("unclaim", ResourceLocation.class, int.class, int.class).invoke(manager, dim, cx, cz);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + .5, destination.getY(), destination.getZ() + .5) < .0001, "allowed visitor did not arrive");
            visitor.teleportTo(level, destination.getX() + 2.5, destination.getY(), destination.getZ() + .5, 0, 0);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "cooldown allowed travel");
            Files.writeString(Path.of("/tmp/uq-meeting-two-player-result"), "passed: connected visitor moved; cooldown denied second move\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Two-player meeting smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-meeting-two-player-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
