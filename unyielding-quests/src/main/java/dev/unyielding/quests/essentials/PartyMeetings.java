package dev.unyielding.quests.essentials;

import com.mojang.brigadier.CommandDispatcher;
import dev.unyielding.quests.team.Team;
import dev.unyielding.quests.team.TeamManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/** Explicit party destination, not an alias or visibility change to private homes. */
public final class PartyMeetings {
    public static final PermissionNode<Boolean> USE = new PermissionNode<>("unyieldingquests", "partymeet", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && (player.hasPermissions(2) || EssConfig.PARTY_MEET_PUBLIC.get()));
    private PartyMeetings() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Essentials.reg(dispatcher, "partymeet", Commands.literal("partymeet")
                .executes(c -> run(c.getSource(), "help"))
                .then(Commands.literal("set").executes(c -> run(c.getSource(), "set")))
                .then(Commands.literal("go").executes(c -> run(c.getSource(), "go")))
                .then(Commands.literal("clear").executes(c -> run(c.getSource(), "clear"))));
    }
    private static int run(CommandSourceStack source, String action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Team party = TeamManager.teamOf(player.getUUID());
        boolean permission = hasPermission(player);
        if (!PartyMeetingPolicy.permitted(EssConfig.PARTY_MEET_ENABLED.get(), permission,
                party != null && party.isParty(), party != null && party.isMember(player.getUUID())))
            return Essentials.fail(source, "Party meetings require an enabled server setting, permission and current party membership.");
        if (action.equals("help")) {
            Essentials.ok(source, "Party officers: /partymeet set or clear. Members: /partymeet go. Private homes remain private."); return 1;
        }
        var data = PartyMeetingData.get(player.server);
        if (action.equals("set") || action.equals("clear")) {
            if (!party.rankOf(player.getUUID()).atLeast(Team.Rank.OFFICER))
                return Essentials.fail(source, "Only a current party officer or owner can change the meeting point.");
            if (action.equals("clear")) {
                data.points.remove(party.id); data.setDirty(); Essentials.ok(source, "Party meeting point cleared."); return 1;
            }
            // Centre over a full block, never preserve fractional positions inside hazards.
            Loc location = new Loc(player.level().dimension().location().toString(),
                    player.blockPosition().getX() + 0.5, player.blockPosition().getY(), player.blockPosition().getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            String error = validate(player, location, player.getUUID());
            if (error != null) return Essentials.fail(source, error);
            data.points.put(party.id, new PartyMeetingData.Meeting(player.getUUID(), location)); data.setDirty();
            Essentials.ok(source, "Party meeting point saved. Party members can use /partymeet go when permitted."); return 1;
        }
        // A full OPAC claim-access check is not proof that every teleport restriction
        // permits travel. Never allow this feature to bypass an unverified provider.
        if (!PartyMeetingPolicy.travelProtectionVerified())
            return Essentials.fail(source, "Meeting travel is unavailable: teleport protections have not been verified. Your saved meeting point is unchanged.");
        var point = data.points.get(party.id);
        if (point == null) return Essentials.fail(source, "Your party has no meeting point yet.");
        Team.Rank setterRank = party.rankOf(point.setter());
        if (setterRank == null || !setterRank.atLeast(Team.Rank.OFFICER))
            return Essentials.fail(source, "The meeting's setter is no longer a party officer. Ask a current officer to set it again.");
        if (!player.isAlive() || player.isSleeping() || player.isPassenger() || player.isVehicle())
            return Essentials.fail(source, "Stand safely on your own before travelling.");
        String error = validate(player, point.location(), point.setter());
        if (error != null) return Essentials.fail(source, error);
        long remaining = Essentials.cooldownRemaining(player, "partymeet");
        if (remaining > 0) return Essentials.fail(source, "Wait " + remaining + "s before another meeting teleport.");
        // Re-read all mutable authorization state on the server thread immediately before
        // movement. The built-in teleport commands have no delayed warmup.
        Team current = TeamManager.teamOf(player.getUUID());
        ServerPlayer setter = player.server.getPlayerList().getPlayer(point.setter());
        Team setterTeam = TeamManager.teamOf(point.setter());
        Team.Rank currentSetterRank = current == null ? null : current.rankOf(point.setter());
        if (!PartyMeetingPolicy.executionAuthorized(
                EssConfig.PARTY_MEET_ENABLED.get(), hasPermission(player),
                current != null && current.isParty() && current.id.equals(party.id)
                        && current.isMember(player.getUUID()),
                setter != null && hasPermission(setter),
                setterTeam != null && setterTeam.id.equals(party.id)
                        && currentSetterRank != null && currentSetterRank.atLeast(Team.Rank.OFFICER),
                data.points.get(party.id) == point))
            return Essentials.fail(source, "Meeting access could not be verified. Ask a current officer to check the meeting point.");
        error = validate(player, point.location(), point.setter());
        if (error != null) return Essentials.fail(source, error);
        remaining = Essentials.cooldownRemaining(player, "partymeet");
        if (remaining > 0) return Essentials.fail(source, "Wait " + remaining + "s before another meeting teleport.");
        // Direct safe destination only; no search, chunk ticket, forced load or private /back entry.
        if (!point.location().teleport(player)
                || !player.level().dimension().location().toString().equals(point.location().dim())
                || player.distanceToSqr(point.location().x(), point.location().y(), point.location().z()) > 0.0001)
            return Essentials.fail(source, "Meeting travel did not reach the destination.");
        player.fallDistance = 0;
        Essentials.cooldown(player, "partymeet", EssConfig.PARTY_MEET_COOLDOWN.get());
        Essentials.ok(source, "Arrived at your party meeting point."); return 1;
    }
    private static boolean hasPermission(ServerPlayer player) {
        try { return player != null && Boolean.TRUE.equals(PermissionAPI.getPermission(player, USE)); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    private static String validate(ServerPlayer player, Loc loc, java.util.UUID setter) {
        if (!PartyMeetingPolicy.validCoordinates(loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch())
                || !PartyMeetingPolicy.dimensionAllowed(player.level().dimension().location().toString(), loc.dim(), EssConfig.PARTY_MEET_DIMENSIONS.get()))
            return "Meeting travel is allowed only within an approved dimension.";
        ServerLevel level = loc.level(player.server);
        if (level == null) return "The destination dimension is unavailable.";
        if (player.getPose() != net.minecraft.world.entity.Pose.STANDING) return "Stand upright before setting or visiting a meeting point.";
        var box = player.getBoundingBox().move(loc.x()-player.getX(), loc.y()-player.getY(), loc.z()-player.getZ());
        // Oversized entities are not supported by the bounded hazard validator.
        if (box.getXsize() > 1 || box.getZsize() > 1 || box.getYsize() > 2) return "Your current size is not supported by meeting travel.";
        BlockPos feet = BlockPos.containing(loc.x(), loc.y(), loc.z());
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 >= level.getMaxBuildHeight()) return "Unsafe destination height.";
        // Include neighbouring chunks before collision checks to avoid implicit collision loads at boundaries.
        for (int x = net.minecraft.util.Mth.floor(box.minX-1)>>4; x <= net.minecraft.util.Mth.floor(box.maxX+1)>>4; x++)
            for (int z = net.minecraft.util.Mth.floor(box.minZ-1)>>4; z <= net.minecraft.util.Mth.floor(box.maxZ+1)>>4; z++) {
                if (!level.hasChunk(x,z)) return "The destination is not loaded. Someone must be nearby first.";
                if (!MeetingClaims.hasAccess(player.server, player.getUUID(), level.dimension().location(), x,z)
                        || !MeetingClaims.hasAccess(player.server, setter, level.dimension().location(), x,z))
                    return "Claim access could not be verified for this meeting point.";
            }
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(player, box)) return "The meeting point is obstructed or outside the world border.";
        BlockPos hazardMin = BlockPos.containing(box.minX, box.minY - 1, box.minZ);
        BlockPos hazardMax = BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ));
        for (BlockPos p : BlockPos.betweenClosed(hazardMin, hazardMax)) {
            var state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.FIRE)
                    || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.END_PORTAL) || state.is(Blocks.NETHER_PORTAL)) return "The meeting point contains a hazard.";
        }
        if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) return "The meeting point needs a solid floor.";
        return null;
    }
}
