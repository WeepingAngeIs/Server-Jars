package dev.unyielding.quests.devtest;

import dev.unyielding.quests.UnyieldingQuests;
import dev.unyielding.quests.jail.JailData;
import dev.unyielding.quests.jail.JailEnforcement;
import dev.unyielding.quests.jail.JailPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;

/** Disposable two-client integration smoke, excluded from the released overlay. */
@EventBusSubscriber(modid = UnyieldingQuests.MODID)
public final class JailPlayerSmoke {
    private static boolean finished;
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("uq.jailPlayers")) return;
        var server = event.getServer();
        var admin = server.getPlayerList().getPlayerByName("Dev");
        var prisoner = server.getPlayerList().getPlayerByName("Visitor");
        if (admin == null || prisoner == null || server.getTickCount() < 100) return;
        finished = true;
        try {
            require(net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"), "OPAC missing");
            server.getPlayerList().op(admin.getGameProfile());
            var commands = server.getCommands();
            require(commands.performPrefixedCommand(prisoner.createCommandSourceStack(), "jailbuild preview") == 0, "non-admin could preview jail");
            var level = admin.serverLevel();
            admin.teleportTo(level, admin.getX(), admin.getY() + 25, admin.getZ(), 0, 0);
            BlockPos min = admin.blockPosition().relative(admin.getDirection(), 8).offset(-3, 0, -3);
            level.setBlockAndUpdate(min, Blocks.CHEST.defaultBlockState());
            require(commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild preview") == 0, "preview permitted a chest");
            require(level.getBlockState(min).is(Blocks.CHEST), "preview changed existing chest");
            level.setBlockAndUpdate(min, Blocks.AIR.defaultBlockState());
            require(commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild preview") > 0, "preview failed");
            require(commands.performPrefixedCommand(admin.createCommandSourceStack(), "jailbuild place") > 0, "place failed");
            var data = JailData.get(server);
            var jail = data.location();
            require(jail != null && JailPlacement.intact(server, jail), "jail template is not intact");
            require(commands.performPrefixedCommand(admin.createCommandSourceStack(), "jail Dev") == 0, "admin self-jail allowed");
            require(commands.performPrefixedCommand(admin.createCommandSourceStack(), "jail Visitor") > 0, "jail command failed");
            require(data.assignment(prisoner.getUUID()) != null, "assignment missing");
            require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "prisoner did not reach jail");
            require(commands.performPrefixedCommand(prisoner.createCommandSourceStack(), "say escape") == 0, "jailed command executed");
            var teleport = new EntityTeleportEvent.TeleportCommand(prisoner, jail.x()+50, jail.y(), jail.z());
            NeoForge.EVENT_BUS.post(teleport);
            require(teleport.isCanceled(), "teleport command event not cancelled");
            var dimension = new EntityTravelToDimensionEvent(prisoner, Level.NETHER);
            NeoForge.EVENT_BUS.post(dimension);
            require(dimension.isCanceled(), "dimension event not cancelled");
            var breaking = new BlockEvent.BreakEvent(level, min, level.getBlockState(min), prisoner);
            NeoForge.EVENT_BUS.post(breaking);
            require(breaking.isCanceled(), "jailed block break not cancelled");
            // Simulate arrival following respawn using the server-only trusted helper.
            var origin = data.assignment(prisoner.getUUID()).prior();
            require(JailEnforcement.teleportAuthorized(prisoner, origin), "test setup could not relocate player");
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(prisoner, false));
            require(prisoner.distanceToSqr(jail.x(), jail.y(), jail.z()) < 0.0001, "respawn event did not restore confinement");
            require(commands.performPrefixedCommand(server.createCommandSourceStack(), "unjail Visitor") > 0, "console release failed");
            require(data.assignment(prisoner.getUUID()) == null, "assignment not cleared");
            Files.writeString(Path.of("/tmp/uq-jail-player-result"), "passed: permission, preview collision, placement, self-jail, assignment, command/teleport/dimension/break events, respawn event, console release\n");
        } catch (Throwable failure) {
            UnyieldingQuests.LOGGER.error("Jail player smoke failed", failure);
            try { Files.writeString(Path.of("/tmp/uq-jail-player-result"), "failed: " + failure + "\n"); } catch (Exception ignored) { }
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
}
