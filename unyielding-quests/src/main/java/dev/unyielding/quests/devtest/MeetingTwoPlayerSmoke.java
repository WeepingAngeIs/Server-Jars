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
            server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "partymeet clear");
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "cleared point allowed movement");
            server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "partymeet set");
            var meetingDataClass = Class.forName("dev.unyielding.quests.essentials.PartyMeetingData");
            var getData = meetingDataClass.getDeclaredMethod("get", MinecraftServer.class);
            getData.setAccessible(true);
            var meetingData = getData.invoke(null, server);
            var pointsField = meetingDataClass.getDeclaredField("points");
            pointsField.setAccessible(true);
            @SuppressWarnings("unchecked") var points = (java.util.Map<java.util.UUID, Object>) pointsField.get(meetingData);
            Object originalPoint = points.get(party.id);
            require(originalPoint != null, "meeting point not stored");
            var pointClass = Class.forName("dev.unyielding.quests.essentials.PartyMeetingData$Meeting");
            var constructor = pointClass.getConstructor(java.util.UUID.class, dev.unyielding.quests.essentials.Loc.class);
            var wrongDimension = new dev.unyielding.quests.essentials.Loc("minecraft:the_nether", destination.getX() + .5, destination.getY(), destination.getZ() + .5, 0, 0);
            points.put(party.id, constructor.newInstance(owner.getUUID(), wrongDimension));
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "wrong dimension allowed movement");
            int farX = destination.getX() + 160000, farZ = destination.getZ() + 160000;
            require(!level.hasChunk(farX >> 4, farZ >> 4), "test destination unexpectedly loaded");
            var unloaded = new dev.unyielding.quests.essentials.Loc("minecraft:overworld", farX + .5, destination.getY(), farZ + .5, 0, 0);
            points.put(party.id, constructor.newInstance(owner.getUUID(), unloaded));
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "unloaded point allowed movement");
            require(!level.hasChunk(farX >> 4, farZ >> 4), "meeting check loaded a chunk");
            points.put(party.id, originalPoint);
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
            var opacParties = apiClass.getMethod("getPartyManager").invoke(api);
            var opacPartiesApi = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI");
            var opacParty = opacPartiesApi.getMethod("createPartyForOwner", net.minecraft.world.entity.player.Player.class).invoke(opacParties, owner);
            var opacPartyApi = Class.forName("xaero.pac.common.server.parties.party.api.IServerPartyAPI");
            var rankClass = Class.forName("xaero.pac.common.parties.party.member.PartyMemberRank");
            var memberRank = rankClass.getField("MEMBER").get(null);
            opacPartyApi.getMethod("addMember", java.util.UUID.class, rankClass, String.class).invoke(opacParty, visitorId, memberRank, "Visitor");
            var claimsClass = Class.forName("dev.unyielding.quests.essentials.MeetingClaims");
            var access = claimsClass.getDeclaredMethod("hasAccess", MinecraftServer.class, java.util.UUID.class, ResourceLocation.class, int.class, int.class);
            access.setAccessible(true);
            require(Boolean.TRUE.equals(access.invoke(null, server, visitorId, dim, cx, cz)), "OPAC party member did not receive claim access");
            opacPartyApi.getMethod("removeMember", java.util.UUID.class).invoke(opacParty, visitorId);
            require(Boolean.FALSE.equals(access.invoke(null, server, visitorId, dim, cx, cz)), "OPAC claim access was not revoked");
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "visitor moved into denied claim");
            managerApi.getMethod("unclaim", ResourceLocation.class, int.class, int.class).invoke(manager, dim, cx, cz);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + .5, destination.getY(), destination.getZ() + .5) < .0001, "allowed visitor did not arrive");
            visitor.teleportTo(level, destination.getX() + 2.5, destination.getY(), destination.getZ() + .5, 0, 0);
            server.getCommands().performPrefixedCommand(visitor.createCommandSourceStack(), "partymeet go");
            require(visitor.distanceToSqr(destination.getX() + 2.5, destination.getY(), destination.getZ() + .5) < .0001, "cooldown allowed travel");
            Files.writeString(Path.of("/tmp/uq-meeting-two-player-result"), "passed: permission, membership, officer, cleared point, dimension, unloaded chunk, obstruction, claim revocation, movement, cooldown\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Two-player meeting smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-meeting-two-player-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
