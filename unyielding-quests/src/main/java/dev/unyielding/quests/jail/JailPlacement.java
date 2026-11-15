package dev.unyielding.quests.jail;

import com.mojang.brigadier.CommandDispatcher;
import dev.unyielding.quests.essentials.Loc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

/** A bounded, empty-space-only template. A preview never changes world data. */
public final class JailPlacement {
    private record Preview(MinecraftServer server, ResourceLocation dimension, BlockPos min, long expires) {}
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private static final long PREVIEW_MILLIS = 120_000;
    private JailPlacement() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jailbuild").requires(JailCommands::mayManage)
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("place").executes(c -> place(c.getSource())))
                .then(Commands.literal("cancel").executes(c -> cancel(c.getSource()))));
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
    private static int preview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        if (JailData.get(p.server).hasPrisoners()) return fail(source, "Release assigned players before moving the prison.");
        BlockPos min = p.blockPosition().relative(p.getDirection(), 8).offset(-3, 0, -3).immutable();
        String problem = validateEmpty(p, p.serverLevel(), min);
        if (problem != null) { PREVIEWS.remove(p.getUUID()); return fail(source, problem); }
        PREVIEWS.put(p.getUUID(), new Preview(p.server, p.serverLevel().dimension().location(), min, System.currentTimeMillis() + PREVIEW_MILLIS));
        for (int dx : new int[]{0, 6}) for (int dy : new int[]{0, 6}) for (int dz : new int[]{0, 6})
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, min.getX()+dx+.5, min.getY()+dy+.5, min.getZ()+dz+.5, 1, 0, 0, 0, 0);
        source.sendSuccess(() -> Component.literal("Prison preview: " + p.serverLevel().dimension().location() + " from " + min.toShortString() + " through " + min.offset(6, 6, 6).toShortString() + ". Empty 7×7×7 space only. Use /jailbuild place within two minutes, or /jailbuild cancel."), false);
        return 1;
    }
    private static int cancel(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PREVIEWS.remove(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.literal("Prison preview cancelled; no blocks changed."), false);
        return 1;
    }
    private static int place(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        Preview preview = PREVIEWS.remove(p.getUUID());
        if (preview == null || preview.server != p.server || preview.expires < System.currentTimeMillis()) return fail(source, "Preview expired. Use /jailbuild preview first.");
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().equals(preview.dimension)) return fail(source, "Return to the preview dimension and preview again.");
        JailData data = JailData.get(p.server);
        if (data.hasPrisoners()) return fail(source, "Release assigned players before moving the prison.");
        BlockPos min = preview.min;
        String problem = validateEmpty(p, level, min);
        if (problem != null) return fail(source, problem);
        var placed = new ArrayList<BlockPos>();
        try {
            for (int x=0; x<7; x++) for (int y=0; y<7; y++) for (int z=0; z<7; z++) {
                if (!shell(x,y,z)) continue;
                BlockPos pos = min.offset(x,y,z);
                if (!level.getBlockState(pos).isAir() || !level.setBlockAndUpdate(pos, templateState(x,y,z)))
                    throw new IllegalStateException("Could not place every prison block");
                placed.add(pos.immutable());
            }
            data.setLocation(Loc.of(level, min.getX()+3.5, min.getY()+1, min.getZ()+3.5, 0, 0));
        } catch (RuntimeException error) {
            // Revert only blocks still matching our template; never replace another change.
            for (BlockPos pos : placed) {
                var expected = templateState(pos.getX()-min.getX(), pos.getY()-min.getY(), pos.getZ()-min.getZ());
                if (level.getBlockState(pos).equals(expected)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            return fail(source, "Placement failed; created blocks were rolled back where unchanged. Check the area before retrying.");
        }
        dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} placed a prison in {} at {}", source.getTextName(), level.dimension().location(), min.toShortString());
        source.sendSuccess(() -> Component.literal("Prison placed in the previewed empty space."), true);
        return 1;
    }
    private static boolean shell(int x, int y, int z) { return x==0 || x==6 || y==0 || y==6 || z==0 || z==6; }
    private static net.minecraft.world.level.block.state.BlockState templateState(int x, int y, int z) {
        return (y == 0 && x == 3 && z == 3 ? Blocks.SEA_LANTERN : Blocks.STONE_BRICKS).defaultBlockState();
    }

    private static String validateEmpty(ServerPlayer player, ServerLevel level, BlockPos min) {
        if (min.getY() < level.getMinBuildHeight() || min.getY()+6 >= level.getMaxBuildHeight()) return "The preview crosses the dimension height limit.";
        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(), min.getX()+7, min.getY()+7, min.getZ()+7);
        if (!level.getWorldBorder().isWithinBounds(bounds)) return "The preview crosses the world border.";
        for (int x=min.getX()>>4; x<=(min.getX()+6)>>4; x++) for (int z=min.getZ()>>4; z<=(min.getZ()+6)>>4; z++) {
            if (!level.hasChunk(x,z)) return "The preview includes an unloaded chunk. Stand nearby and preview again.";
            if (!claimAccess(player, level, x, z)) return "Open Parties claim access could not be verified for the entire preview.";
        }
        if (!level.getEntities((Entity)null, bounds).isEmpty()) return "The preview contains an entity or dropped item; clear it and preview again.";
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(6,6,6)))
            if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) return "The entire preview must be empty air; no blocks or inventories are replaced.";
        if (!placementAllowed(player, level, min)) return "Open Parties denied or could not verify block placement throughout the preview.";
        return null;
    }
    /** Never creates chunk tickets or searches for a replacement location. */
    public static boolean intact(MinecraftServer server, Loc loc) {
        if (loc == null || !Double.isFinite(loc.x()) || !Double.isFinite(loc.y()) || !Double.isFinite(loc.z())) return false;
        ServerLevel level = loc.level(server);
        if (level == null) return false;
        BlockPos min = BlockPos.containing(loc.x(), loc.y(), loc.z()).offset(-3,-1,-3);
        if (min.getY() < level.getMinBuildHeight() || min.getY()+6 >= level.getMaxBuildHeight()) return false;
        if (!level.getWorldBorder().isWithinBounds(new AABB(min.getX(), min.getY(), min.getZ(), min.getX()+7, min.getY()+7, min.getZ()+7))) return false;
        for (int x=min.getX()>>4; x<=(min.getX()+6)>>4; x++) for (int z=min.getZ()>>4; z<=(min.getZ()+6)>>4; z++) if (!level.hasChunk(x,z)) return false;
        for (int x=0; x<7; x++) for (int y=0; y<7; y++) for (int z=0; z<7; z++) {
            var state = level.getBlockState(min.offset(x,y,z));
            if (shell(x,y,z) ? !state.equals(templateState(x,y,z)) : !state.isAir()) return false;
        }
        return true;
    }
    private static boolean claimAccess(ServerPlayer player, ServerLevel level, int x, int z) {
        if (!ModList.get().isLoaded("openpartiesandclaims")) return false;
        try {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object instance = api.getMethod("get", MinecraftServer.class).invoke(null, player.server);
            Object protection = api.getMethod("getChunkProtection").invoke(instance);
            Class<?> contract = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            return Boolean.TRUE.equals(contract.getMethod("hasChunkAccess", Entity.class, ResourceLocation.class, int.class, int.class)
                    .invoke(protection, player, level.dimension().location(), x, z));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return false; }
    }
    /** OPAC returns true for a denied placement. Its public API pauses full-pass overrides. */
    private static boolean placementAllowed(ServerPlayer player, ServerLevel level, BlockPos min) {
        try {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object instance = api.getMethod("get", MinecraftServer.class).invoke(null, player.server);
            Object protection = api.getMethod("getChunkProtection").invoke(instance);
            Class<?> contract = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            var check = contract.getMethod("onEntityPlaceBlock", net.minecraft.world.level.block.state.BlockState.class, Entity.class, ServerLevel.class, BlockPos.class);
            for (int x=0; x<7; x++) for (int y=0; y<7; y++) for (int z=0; z<7; z++) {
                if (shell(x,y,z) && !Boolean.FALSE.equals(check.invoke(protection, templateState(x,y,z), player, level, min.offset(x,y,z)))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return false; }
    }
}
