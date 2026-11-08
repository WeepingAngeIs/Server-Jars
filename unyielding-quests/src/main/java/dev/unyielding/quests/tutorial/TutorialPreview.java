package dev.unyielding.quests.tutorial;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Non-operator command checks without changing the real player's identity or permissions. */
public final class TutorialPreview {
    private static final GameProfile PROFILE = new GameProfile(UUID.randomUUID(), "UQGuidePreview");
    private static final Map<ServerLevel, NeutralPlayer> PLAYERS = new IdentityHashMap<>();

    private TutorialPreview() { }

    public static CommandSourceStack source(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(0);
        var players = player.getServer().getPlayerList();
        if (!players.isOp(player.getGameProfile()) && !player.hasPermissions(1)) return source;
        // LAN worlds can grant operator bypass to every identity. Do not loop trying random UUIDs,
        // and never let that global bypass turn the player guide into an operator command index.
        if (players.isOp(PROFILE)) {
            return new CommandSourceStack(CommandSource.NULL, source.getPosition(), source.getRotation(),
                    player.serverLevel(), 0, "UQGuidePreview", Component.literal("UQGuidePreview"),
                    player.getServer(), null);
        }
        NeutralPlayer preview = PLAYERS.computeIfAbsent(player.serverLevel(), level -> new NeutralPlayer(level, PROFILE));
        preview.setPos(player.position());
        return source.withEntity(preview);
    }

    /** Release level references when a server starts or stops. */
    public static void clear() {
        PLAYERS.clear();
    }

    private static final class NeutralPlayer extends FakePlayer {
        NeutralPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean hasPermissions(int level) {
            return level <= 0;
        }

        @Override
        public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos position) {
            // This entity never joins the world, so it must not scan or load spawn chunks.
            return position;
        }
    }
}
