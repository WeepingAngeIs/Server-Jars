package dev.unyielding.quests.jail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.unyielding.quests.essentials.Essentials;
import dev.unyielding.quests.essentials.Loc;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/** Admin-operated jail. Console is always available for emergency recovery. */
public final class JailCommands {
    public static final PermissionNode<Boolean> MANAGE = new PermissionNode<>("unyieldingquests", "jail.manage", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(2));
    private JailCommands() {}
    // Do not admit new players until durable item and origin recovery is verified.
    // Existing assignments and recovery commands remain available.
    private static boolean admissionReady() { return false; }
    public static boolean mayManage(CommandSourceStack source) {
        if (source.getEntity() == null) return source.hasPermission(2);
        return source.getEntity() instanceof ServerPlayer player && canManage(player);
    }
    private static boolean canManage(ServerPlayer player) {
        try { return Boolean.TRUE.equals(PermissionAPI.getPermission(player, MANAGE)); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    private static boolean protectedFromJail(ServerPlayer player) {
        if (player.hasPermissions(2)) return true;
        try { return Boolean.TRUE.equals(PermissionAPI.getPermission(player, MANAGE)); }
        catch (RuntimeException | LinkageError unavailable) { return true; }
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jail").requires(JailCommands::mayManage)
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> jail(c.getSource(), EntityArgument.getPlayer(c, "player")))));
        dispatcher.register(Commands.literal("unjail").requires(JailCommands::mayManage)
                .then(Commands.argument("player", EntityArgument.player()).executes(c -> release(c.getSource(), EntityArgument.getPlayer(c, "player"), false)))
                .then(Commands.literal("pardon").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> release(c.getSource(), EntityArgument.getPlayer(c, "player"), true))))
                .then(Commands.literal("pardonuuid").requires(s -> s.getEntity() == null && s.hasPermission(2))
                        .then(Commands.argument("uuid", net.minecraft.commands.arguments.UuidArgument.uuid())
                                .executes(c -> pardonUuid(c.getSource(), net.minecraft.commands.arguments.UuidArgument.getUuid(c, "uuid"))))));
    }
    private static int pardonUuid(CommandSourceStack source, java.util.UUID player) {
        var data = JailData.get(source.getServer());
        if (data.assignment(player) == null) return Essentials.fail(source, "That UUID has no jail assignment.");
        data.remove(player);
        dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} pardoned UUID {} in place", source.getTextName(), player);
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Removed jail assignment for " + player + " without moving the player."), true);
        return 1;
    }
    private static int jail(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException {
        if (target == source.getEntity() || protectedFromJail(target))
            return Essentials.fail(source, "Administrators cannot be jailed; console remains available for recovery.");
        if (!admissionReady())
            return Essentials.fail(source, "New jail admissions are unavailable until inventory and origin recovery are verified. Existing jailed players can still be released.");
        var data = JailData.get(target.server);
        if (data.assignment(target.getUUID()) != null) return Essentials.fail(source, "That player is already jailed.");
        Loc dest = data.location();
        if (dest == null || !JailPlacement.intact(target.server, dest, data.geometry()) || !safe(target, dest))
            return Essentials.fail(source, "The jail is unavailable, unsafe, not loaded, or claim access is denied.");
        if (source.getEntity() instanceof ServerPlayer actor && !claimAccess(actor, dest))
            return Essentials.fail(source, "Your claim access at the jail could not be verified.");
        Loc prior = Loc.of(target);
        if (!JailEnforcement.teleportAuthorized(target, dest)) return Essentials.fail(source, "Jail transfer failed.");
        data.assign(target.getUUID(), new JailData.Assignment(dest, prior, data.geometry()));
        dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} jailed {} ({})", source.getTextName(), target.getGameProfile().getName(), target.getUUID());
        target.sendSystemMessage(net.minecraft.network.chat.Component.literal("An administrator has jailed you."));
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Jailed " + target.getGameProfile().getName() + "."), true);
        return 1;
    }
    private static int release(CommandSourceStack source, ServerPlayer target, boolean pardon) {
        var data = JailData.get(target.server);
        var assignment = data.assignment(target.getUUID());
        if (assignment == null) return Essentials.fail(source, "That player is not jailed.");
        if (!pardon && (!safe(target, assignment.prior()) || source.getEntity() instanceof ServerPlayer actor && !claimAccess(actor, assignment.prior())))
            return Essentials.fail(source, "The return location is unavailable or unsafe. Load and secure it, or use /unjail pardon <player> to remove the restriction in place.");
        if (!pardon && !JailEnforcement.teleportAuthorized(target, assignment.prior())) {
            return Essentials.fail(source, "Release transfer failed; the jail assignment is preserved.");
        }
        data.remove(target.getUUID());
        dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} released {} ({}) inPlace={}", source.getTextName(), target.getGameProfile().getName(), target.getUUID(), pardon);
        target.sendSystemMessage(net.minecraft.network.chat.Component.literal("An administrator has released you."));
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal("Released " + target.getGameProfile().getName() + (pardon ? " in place." : ".")), true);
        return 1;
    }
    /** Checks an already-loaded destination without creating chunk tickets. */
    public static boolean safe(ServerPlayer player, Loc loc) {
        if (loc == null || !Double.isFinite(loc.x()) || !Double.isFinite(loc.y()) || !Double.isFinite(loc.z())) return false;
        var level = loc.level(player.server);
        if (level == null) return false;
        var box = player.getBoundingBox().move(loc.x() - player.getX(), loc.y() - player.getY(), loc.z() - player.getZ());
        if (box.getXsize() > 1 || box.getZsize() > 1 || box.getYsize() > 2) return false;
        BlockPos feet = BlockPos.containing(loc.x(), loc.y(), loc.z());
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 >= level.getMaxBuildHeight()) return false;
        for (int x = net.minecraft.util.Mth.floor(box.minX - 1) >> 4; x <= net.minecraft.util.Mth.floor(box.maxX + 1) >> 4; x++)
            for (int z = net.minecraft.util.Mth.floor(box.minZ - 1) >> 4; z <= net.minecraft.util.Mth.floor(box.maxZ + 1) >> 4; z++)
                if (!level.hasChunk(x, z) || !claimAccess(player, loc, x, z)) return false;
        if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(player, box)) return false;
        BlockPos low = BlockPos.containing(box.minX, box.minY - 1, box.minZ);
        BlockPos high = BlockPos.containing(Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ));
        for (BlockPos p : BlockPos.betweenClosed(low, high)) {
            var s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty() || s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE) || s.is(Blocks.MAGMA_BLOCK)
                    || s.is(Blocks.CACTUS) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.SOUL_CAMPFIRE)
                    || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.WITHER_ROSE) || s.is(Blocks.SWEET_BERRY_BUSH)
                    || s.is(Blocks.NETHER_PORTAL) || s.is(Blocks.END_PORTAL)) return false;
        }
        return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }
    private static boolean claimAccess(ServerPlayer player, Loc loc) {
        var box = player.getBoundingBox().move(loc.x() - player.getX(), loc.y() - player.getY(), loc.z() - player.getZ());
        for (int x = net.minecraft.util.Mth.floor(box.minX - 1) >> 4; x <= net.minecraft.util.Mth.floor(box.maxX + 1) >> 4; x++)
            for (int z = net.minecraft.util.Mth.floor(box.minZ - 1) >> 4; z <= net.minecraft.util.Mth.floor(box.maxZ + 1) >> 4; z++)
                if (!claimAccess(player, loc, x, z)) return false;
        return true;
    }
    private static boolean claimAccess(ServerPlayer player, Loc loc, int x, int z) {
        if (!ModList.get().isLoaded("openpartiesandclaims")) return false;
        try {
            var level = loc.level(player.server);
            if (level == null) return false;
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object instance = api.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, player.server);
            Object protection = api.getMethod("getChunkProtection").invoke(instance);
            Class<?> contract = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            return Boolean.TRUE.equals(contract.getMethod("hasChunkAccess", net.minecraft.world.entity.Entity.class, net.minecraft.resources.ResourceLocation.class, int.class, int.class)
                    .invoke(protection, player, level.dimension().location(), x, z));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return false; }
    }
}
