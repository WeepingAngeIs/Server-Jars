package dev.unyielding.quests.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Operator-only forced respawn. Uses the normal server respawn path so death drops and mod hooks remain intact. */
public final class ForceReviveCommand {
    private ForceReviveCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("forcerevive").requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> revive(c.getSource(), EntityArgument.getPlayer(c, "player"), "death"))
                        .then(Commands.literal("death").executes(c -> revive(c.getSource(), EntityArgument.getPlayer(c, "player"), "death")))
                        .then(Commands.literal("bed").executes(c -> revive(c.getSource(), EntityArgument.getPlayer(c, "player"), "bed")))
                        .then(Commands.literal("here").executes(c -> revive(c.getSource(), EntityArgument.getPlayer(c, "player"), "here")))));
    }

    private static int revive(CommandSourceStack source, ServerPlayer target, String mode) throws CommandSyntaxException {
        if (target.getHealth() > 0 || !target.isDeadOrDying()) {
            source.sendFailure(Component.literal("That player is not waiting to respawn."));
            return 0;
        }
        ServerLevel level = target.serverLevel();
        double x = target.getX(), y = target.getY(), z = target.getZ();
        float yaw = target.getYRot(), pitch = target.getXRot();
        if (mode.equals("bed") && (target.getRespawnPosition() == null || source.getServer().getLevel(target.getRespawnDimension()) == null)) {
            source.sendFailure(Component.literal("That player has no available bed or respawn anchor set."));
            return 0;
        }
        if (mode.equals("here")) {
            ServerPlayer admin = source.getPlayerOrException();
            if (admin == target || admin.isDeadOrDying()) {
                source.sendFailure(Component.literal("The reviving admin must be alive."));
                return 0;
            }
            level = admin.serverLevel();
            x = admin.getX() + 1.0; y = admin.getY(); z = admin.getZ();
            yaw = admin.getYRot(); pitch = admin.getXRot();
        }
        // Vanilla's respawn path consumes a respawn-anchor charge before we can
        // teleport. Temporarily clear the spawn for the other destinations and
        // restore it immediately on the new player, preserving their bed/anchor.
        var spawnDimension = target.getRespawnDimension();
        var spawnPosition = target.getRespawnPosition();
        float spawnAngle = target.getRespawnAngle();
        boolean spawnForced = target.isRespawnForced();
        boolean preserveSpawn = !mode.equals("bed");
        if (preserveSpawn) target.setRespawnPosition(Level.OVERWORLD, null, 0.0f, false, false);
        var connection = target.connection;
        ServerPlayer revived;
        try {
            revived = source.getServer().getPlayerList().respawn(target, false, Entity.RemovalReason.KILLED);
        } catch (RuntimeException ex) {
            if (preserveSpawn) target.setRespawnPosition(spawnDimension, spawnPosition, spawnAngle, spawnForced, false);
            throw ex;
        }
        connection.player = revived;
        if (preserveSpawn) {
            revived.setRespawnPosition(spawnDimension, spawnPosition, spawnAngle, spawnForced, false);
            revived.teleportTo(level, x, y, z, yaw, pitch);
        }
        source.sendSuccess(() -> Component.literal("Revived " + revived.getGameProfile().getName() + " (" + mode + ")."), true);
        return 1;
    }
}
