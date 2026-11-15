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

/** A bounded template built above existing terrain. A preview never changes world data. */
public final class JailPlacement {
    private record Preview(MinecraftServer server, ResourceLocation dimension, BlockPos min, long expires) {}
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private static final long PREVIEW_MILLIS = 600_000;
    private static final int WIDTH = 13, HEIGHT = 7, DEPTH = 7;
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
        BlockPos min = p.blockPosition().relative(p.getDirection(), 11).offset(-3, 0, -3).immutable();
        String problem = validateSite(p, p.serverLevel(), min);
        if (problem != null) { PREVIEWS.remove(p.getUUID()); return fail(source, "Cannot preview " + p.serverLevel().dimension().location() + " from " + min.toShortString() + " to " + min.offset(WIDTH-1, HEIGHT-1, DEPTH-1).toShortString() + ": " + problem); }
        PREVIEWS.put(p.getUUID(), new Preview(p.server, p.serverLevel().dimension().location(), min, System.currentTimeMillis() + PREVIEW_MILLIS));
        for (int dx : new int[]{0, WIDTH-1}) for (int dy : new int[]{0, HEIGHT-1}) for (int dz : new int[]{0, DEPTH-1})
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, min.getX()+dx+.5, min.getY()+dy+.5, min.getZ()+dz+.5, 1, 0, 0, 0, 0);
        source.sendSuccess(() -> Component.literal("Jail preview: " + p.serverLevel().dimension().location() + " from " + min.toShortString() + " to " + min.offset(WIDTH-1, HEIGHT-1, DEPTH-1).toShortString() + " (13×7×7). Builds a lit barred cell and an accessible admin room above existing ground. No terrain or inventories are removed. Preview lasts 10 minutes; /jailbuild place or /jailbuild cancel."), false);
        return 1;
    }
    private static int cancel(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PREVIEWS.remove(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.literal("Prison preview cancelled; no blocks changed."), false);
        return 1;
    }
    private static int place(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        Preview preview = PREVIEWS.get(p.getUUID());
        if (preview == null || preview.server != p.server || preview.expires < System.currentTimeMillis()) return fail(source, "Preview expired. Use /jailbuild preview first.");
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().equals(preview.dimension)) return fail(source, "Return to the preview dimension and preview again.");
        JailData data = JailData.get(p.server);
        if (data.hasPrisoners()) return fail(source, "Release assigned players before moving the prison.");
        BlockPos min = preview.min;
        String problem = validateSite(p, level, min);
        if (problem != null) return fail(source, problem);
        var placed = new ArrayList<BlockPos>();
        try {
            for (int x=0; x<WIDTH; x++) for (int y=0; y<HEIGHT; y++) for (int z=0; z<DEPTH; z++) {
                if (!newStructure(x,y,z)) continue;
                BlockPos pos = min.offset(x,y,z);
                if (!level.getBlockState(pos).isAir() || !level.setBlockAndUpdate(pos, newTemplateState(x,y,z)))
                    throw new IllegalStateException("Could not place every prison block");
                placed.add(pos.immutable());
            }
            data.setLocation(Loc.of(level, min.getX()+3.5, min.getY()+1, min.getZ()+3.5, 0, 0), JailData.INTERROGATION_GEOMETRY);
            PREVIEWS.remove(p.getUUID());
        } catch (RuntimeException error) {
            // Revert only blocks still matching our template; never replace another change.
            for (BlockPos pos : placed) {
                var expected = newTemplateState(pos.getX()-min.getX(), pos.getY()-min.getY(), pos.getZ()-min.getZ());
                if (level.getBlockState(pos).is(expected.getBlock())) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            return fail(source, "Placement failed; created blocks were rolled back where unchanged. Check the area before retrying.");
        }
        dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} placed a prison in {} at {}", source.getTextName(), level.dimension().location(), min.toShortString());
        source.sendSuccess(() -> Component.literal("Jail placed. The barred prisoner cell is separate from the admin room; the admin entrance is on the east wall."), true);
        return 1;
    }
    private static boolean shell(int x, int y, int z) { return x==0 || x==6 || y==0 || y==6 || z==0 || z==6; }
    private static net.minecraft.world.level.block.state.BlockState templateState(int x, int y, int z) {
        return (y == 0 && x == 3 && z == 3 ? Blocks.SEA_LANTERN : Blocks.STONE_BRICKS).defaultBlockState();
    }

    /** The east wall has a two-block doorway for staff; the middle wall is barred. */
    static boolean newStructure(int x, int y, int z) {
        if (x == WIDTH-1 && z == 3 && (y == 1 || y == 2)) return false;
        return x == 0 || x == WIDTH-1 || y == 0 || y == HEIGHT-1 || z == 0 || z == DEPTH-1 || x == 6;
    }
    static net.minecraft.world.level.block.state.BlockState newTemplateState(int x, int y, int z) {
        if (x == 6 && y >= 1 && y <= 4 && z >= 1 && z <= 5) return Blocks.IRON_BARS.defaultBlockState();
        if (y == 0 && (x == 3 || x == 9) && z == 3) return Blocks.SEA_LANTERN.defaultBlockState();
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    private static String validateSite(ServerPlayer player, ServerLevel level, BlockPos min) {
        if (min.getY() <= level.getMinBuildHeight() || min.getY()+HEIGHT-1 >= level.getMaxBuildHeight()) return "The preview crosses the dimension height limit.";
        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(), min.getX()+WIDTH, min.getY()+HEIGHT, min.getZ()+DEPTH);
        if (!level.getWorldBorder().isWithinBounds(bounds)) return "The preview crosses the world border.";
        for (int x=min.getX()>>4; x<=(min.getX()+WIDTH-1)>>4; x++) for (int z=min.getZ()>>4; z<=(min.getZ()+DEPTH-1)>>4; z++) {
            if (!level.hasChunk(x,z)) return "The preview includes an unloaded chunk. Stand nearby and preview again.";
            if (!claimAccess(player, level, x, z)) return "Open Parties claim access could not be verified for the entire preview.";
        }
        if (!level.getEntities((Entity)null, bounds).isEmpty()) return "The preview contains an entity or dropped item; clear it and preview again.";
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(WIDTH-1,HEIGHT-1,DEPTH-1)))
            if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null) return "Block at " + pos.toShortString() + " is " + level.getBlockState(pos).getBlock().getName().getString() + ". Choose a clear area; nothing is removed.";
        for (int x=0; x<WIDTH; x++) for (int z=0; z<DEPTH; z++) {
            BlockPos support = min.offset(x,-1,z);
            if (!level.getBlockState(support).isFaceSturdy(level, support, net.minecraft.core.Direction.UP)) return "The ground below " + support.above().toShortString() + " needs a solid top. Choose a level supported area; terrain is not changed.";
        }
        if (!placementAllowed(player, level, min)) return "Open Parties denied or could not verify block placement throughout the preview.";
        return null;
    }
    /** Never creates chunk tickets or searches for a replacement location. */
    public static boolean intact(MinecraftServer server, Loc loc) {
        JailData data = JailData.get(server);
        int version = loc != null && loc.equals(data.location()) ? data.geometry() : JailData.LEGACY_GEOMETRY;
        return intact(server, loc, version);
    }
    /** Unknown geometry versions are deliberately rejected instead of guessed. */
    public static boolean intact(MinecraftServer server, Loc loc, int geometry) {
        if (loc == null || !Double.isFinite(loc.x()) || !Double.isFinite(loc.y()) || !Double.isFinite(loc.z())) return false;
        if (geometry != JailData.LEGACY_GEOMETRY && geometry != JailData.INTERROGATION_GEOMETRY) return false;
        ServerLevel level = loc.level(server);
        if (level == null) return false;
        BlockPos min = BlockPos.containing(loc.x(), loc.y(), loc.z()).offset(-3,-1,-3);
        if (min.getY() < level.getMinBuildHeight() || min.getY()+6 >= level.getMaxBuildHeight()) return false;
        int width = geometry == JailData.LEGACY_GEOMETRY ? 7 : WIDTH;
        if (!level.getWorldBorder().isWithinBounds(new AABB(min.getX(), min.getY(), min.getZ(), min.getX()+width, min.getY()+7, min.getZ()+7))) return false;
        for (int x=min.getX()>>4; x<=(min.getX()+width-1)>>4; x++) for (int z=min.getZ()>>4; z<=(min.getZ()+6)>>4; z++) if (!level.hasChunk(x,z)) return false;
        for (int x=0; x<width; x++) for (int y=0; y<7; y++) for (int z=0; z<7; z++) {
            var state = level.getBlockState(min.offset(x,y,z));
            if (geometry == JailData.LEGACY_GEOMETRY) {
                if (shell(x,y,z) ? !state.equals(templateState(x,y,z)) : !state.isAir()) return false;
            } else if (newStructure(x,y,z)) {
                if (!state.is(newTemplateState(x,y,z).getBlock())) return false;
            } else if (x < 6 && !state.isAir()) return false;
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
            for (int x=0; x<WIDTH; x++) for (int y=0; y<HEIGHT; y++) for (int z=0; z<DEPTH; z++) {
                if (newStructure(x,y,z) && !Boolean.FALSE.equals(check.invoke(protection, newTemplateState(x,y,z), player, level, min.offset(x,y,z)))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return false; }
    }
}
