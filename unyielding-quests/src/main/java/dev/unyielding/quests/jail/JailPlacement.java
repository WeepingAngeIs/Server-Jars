package dev.unyielding.quests.jail;

import com.mojang.brigadier.CommandDispatcher;
import dev.unyielding.quests.essentials.Loc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

/** Bounded placement with a durable before/after backup and explicit confirmation. */
public final class JailPlacement {
    private record Preview(MinecraftServer server, ResourceLocation dimension, BlockPos min, long expires, List<net.minecraft.world.level.block.state.BlockState> before, List<net.minecraft.world.level.block.state.BlockState> after) {}
    private record UndoPreview(MinecraftServer server, ResourceLocation dimension, long expires) {}
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private static final Map<UUID, UndoPreview> UNDO_PREVIEWS = new HashMap<>();
    private static final long PREVIEW_MILLIS = 600_000;
    private static final int WIDTH = 13, HEIGHT = 7, DEPTH = 7;
    // These blocks can be player-built too: never label them natural, and require
    // inspection of every affected coordinate plus explicit confirmation.
    private static final Set<net.minecraft.world.level.block.Block> TERRAIN = Set.of(
            Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.PODZOL,
            Blocks.MYCELIUM, Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE, Blocks.DIORITE,
            Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.NETHERRACK, Blocks.END_STONE);
    private static final Set<net.minecraft.world.level.block.Block> PLANTS = Set.of(
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.DEAD_BUSH);
    private JailPlacement() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jailbuild").requires(JailCommands::mayManage)
                .then(Commands.literal("preview").executes(c -> preview(c.getSource())))
                .then(Commands.literal("details").executes(c -> details(c.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> details(c.getSource(), IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("place").executes(c -> place(c.getSource())))
                .then(Commands.literal("cancel").executes(c -> cancel(c.getSource())))
                .then(Commands.literal("undo").executes(c -> undoPreview(c.getSource())))
                .then(Commands.literal("undo-confirm").executes(c -> undoConfirm(c.getSource()))));
    }

    private static int fail(CommandSourceStack source, String message) {
        dev.unyielding.quests.UnyieldingQuests.LOGGER.warn("Jail command refused for {}: {}", source.getTextName(), message);
        source.sendFailure(Component.literal(message));
        return 0;
    }
    private static int preview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        if (JailData.get(p.server).hasPrisoners()) return fail(source, "Release assigned players before moving the prison.");
        if (JailTerrainJournal.exists(p.server)) return fail(source, "A jail placement backup already exists. Use /jailbuild undo to inspect recovery before placing another jail.");
        BlockPos min = p.blockPosition().relative(p.getDirection(), 11).offset(-3, 0, -3).immutable();
        String problem = validateSite(p, p.serverLevel(), min);
        if (problem != null) { PREVIEWS.remove(p.getUUID()); return fail(source, "Cannot preview " + p.serverLevel().dimension().location() + " from " + min.toShortString() + " to " + min.offset(WIDTH-1, HEIGHT-1, DEPTH-1).toShortString() + ": " + problem); }
        List<net.minecraft.world.level.block.state.BlockState> before = capture(p.serverLevel(), min), after = desired();
        PREVIEWS.put(p.getUUID(), new Preview(p.server, p.serverLevel().dimension().location(), min, System.currentTimeMillis() + PREVIEW_MILLIS, before, after));
        for (int dx : new int[]{0, WIDTH-1}) for (int dy : new int[]{0, HEIGHT-1}) for (int dz : new int[]{0, DEPTH-1})
            p.serverLevel().sendParticles(ParticleTypes.END_ROD, min.getX()+dx+.5, min.getY()+dy+.5, min.getZ()+dz+.5, 1, 0, 0, 0, 0);
        Map<String, Integer> impact = new LinkedHashMap<>(); int changed = 0;
        for (int i=0; i<before.size(); i++) if (!before.get(i).equals(after.get(i))) {
            changed++; if (!before.get(i).isAir()) impact.merge(before.get(i).toString(), 1, Integer::sum);
        }
        int total = changed;
        source.sendSuccess(() -> Component.literal("Jail preview: " + p.serverLevel().dimension().location() + " from " + min.toShortString() + " through " + min.offset(WIDTH-1, HEIGHT-1, DEPTH-1).toShortString() + " (13×7×7). " + total + " blocks will change. Non-air removed: " + (impact.isEmpty() ? "none" : impact) + ". Stone and soil may be player-built: inspect /jailbuild details before confirming. Inventories, ores, fluids, block entities and unlisted blocks are refused. A recovery backup is saved before edits. Preview lasts 10 minutes; /jailbuild place or /jailbuild cancel."), false);
        return 1;
    }
    private static int details(CommandSourceStack source, int page) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException(); Preview preview = PREVIEWS.get(player.getUUID());
        if (preview == null || preview.server() != player.server || preview.expires() < System.currentTimeMillis()) return fail(source, "Preview expired; use /jailbuild preview again.");
        List<Integer> changed = new ArrayList<>();
        for (int i=0; i<preview.before().size(); i++) if (!preview.before().get(i).equals(preview.after().get(i))) changed.add(i);
        int pages = Math.max(1, (changed.size()+7)/8); if (page > pages) return fail(source, "Impact page must be between 1 and " + pages + ".");
        StringBuilder message = new StringBuilder("Jail impact page " + page + "/" + pages + ": ");
        for (int n=(page-1)*8; n<Math.min(page*8, changed.size()); n++) {
            int index = changed.get(n); message.append(at(preview.min(), index).toShortString()).append(" ").append(preview.before().get(index)).append(" → ").append(preview.after().get(index)).append("; ");
        }
        if (changed.isEmpty()) message.append("no blocks changed.");
        source.sendSuccess(() -> Component.literal(message.toString()), false); return 1;
    }
    private static int cancel(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PREVIEWS.remove(source.getPlayerOrException().getUUID());
        UNDO_PREVIEWS.remove(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> Component.literal("Prison preview cancelled; no blocks changed."), false);
        return 1;
    }
    private static int place(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = source.getPlayerOrException();
        Preview preview = PREVIEWS.get(p.getUUID());
        if (preview == null || preview.server != p.server || preview.expires < System.currentTimeMillis()) return fail(source, "Preview expired. Use /jailbuild preview first; no blocks were changed.");
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().equals(preview.dimension)) return fail(source, "Return to the preview dimension and preview again.");
        JailData data = JailData.get(p.server);
        if (data.hasPrisoners()) return fail(source, "Release assigned players before moving the prison.");
        BlockPos min = preview.min;
        if (JailTerrainJournal.exists(p.server)) return fail(source, "A recovery backup already exists. Use /jailbuild undo first.");
        String problem = validateSite(p, level, min);
        if (problem != null) return fail(source, problem);
        if (!capture(level, min).equals(preview.before())) return fail(source, "The previewed blocks changed. Preview again; nothing was placed.");
        var backup = new JailTerrainJournal.Backup(level.dimension().location(), min, preview.before(), preview.after(), data.location(), data.geometry(), p.getUUID());
        try {
            JailTerrainJournal.write(p.server, backup);
            dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} ({}) confirmed terrain edits at {} in {} with a recovery backup", p.getGameProfile().getName(), p.getUUID(), min, level.dimension().location());
        } catch (Exception error) {
            return fail(source, "Could not save and verify the recovery backup; no blocks changed. " + error.getMessage());
        }
        try {
            for (int i=0; i<preview.before().size(); i++) {
                BlockPos pos = at(min, i); var old = preview.before().get(i); var next = preview.after().get(i);
                if (!level.getBlockState(pos).equals(old) || level.getBlockEntity(pos) != null) throw new IllegalStateException("World changed at " + pos.toShortString());
                if (!old.equals(next) && !level.setBlock(pos, next, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS))
                    throw new IllegalStateException("Could not edit " + pos.toShortString());
            }
            data.setLocation(Loc.of(level, min.getX()+3.5, min.getY()+1, min.getZ()+3.5, 0, 0), JailData.INTERROGATION_CHAIR_GEOMETRY);
            PREVIEWS.remove(p.getUUID());
        } catch (RuntimeException error) {
            PREVIEWS.remove(p.getUUID());
            return fail(source, "Placement was interrupted: " + error.getMessage() + ". The backup is retained. Use /jailbuild undo to inspect recovery; do not edit the affected area.");
        }
        dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} placed a prison in {} at {}", source.getTextName(), level.dimension().location(), min.toShortString());
        source.sendSuccess(() -> Component.literal("Jail placed. A recovery backup is retained; /jailbuild undo previews restoration. The admin entrance is on the east wall."), true);
        return 1;
    }
    private static int undoPreview(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (JailData.get(player.server).hasPrisoners()) return fail(source, "Release assigned players before restoring the area.");
        try {
            var backup = JailTerrainJournal.read(player.server);
            String error = validateRestore(player, backup);
            if (error != null) return fail(source, error);
            UNDO_PREVIEWS.put(player.getUUID(), new UndoPreview(player.server, backup.dimension(), System.currentTimeMillis() + PREVIEW_MILLIS));
            int changed = 0; ServerLevel level = player.serverLevel();
            for (int i=0; i<JailTerrainJournal.VOLUME; i++) if (!level.getBlockState(at(backup.min(), i)).equals(backup.before().get(i))) changed++;
            int count = changed;
            source.sendSuccess(() -> Component.literal("Undo preview: restore up to " + count + " blocks in " + backup.dimension() + " from " + backup.min().toShortString() + " through " + backup.min().offset(WIDTH-1, HEIGHT-1, DEPTH-1).toShortString() + ". Changes by other players are refused. Confirm with /jailbuild undo-confirm within 10 minutes, or /jailbuild cancel. The world will be saved before the backup is removed."), false);
            return 1;
        } catch (Exception error) { return fail(source, "Cannot read the placement backup; it was retained: " + error.getMessage()); }
    }
    private static int undoConfirm(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException(); UndoPreview preview = UNDO_PREVIEWS.get(player.getUUID());
        if (preview == null || preview.server() != player.server || preview.expires() < System.currentTimeMillis())
            return fail(source, "Undo preview expired. Use /jailbuild undo again; no blocks changed.");
        if (JailData.get(player.server).hasPrisoners()) return fail(source, "Release assigned players before restoring the area.");
        try {
            var backup = JailTerrainJournal.read(player.server);
            if (!backup.dimension().equals(preview.dimension())) return fail(source, "The backup changed; preview the undo again.");
            String error = validateRestore(player, backup); if (error != null) return fail(source, error);
            ServerLevel level = player.serverLevel();
            for (int i=0; i<JailTerrainJournal.VOLUME; i++) {
                BlockPos pos = at(backup.min(), i); var current = level.getBlockState(pos); var before = backup.before().get(i); var after = backup.after().get(i);
                if (level.getBlockEntity(pos) != null || !(current.equals(before) || current.equals(after))) throw new IllegalStateException("World changed at " + pos.toShortString());
                if (!current.equals(before) && !level.setBlock(pos, before, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS))
                    throw new IllegalStateException("Could not restore " + pos.toShortString());
            }
            JailData.get(player.server).setLocation(backup.prior(), backup.priorGeometry());
            if (!player.server.saveEverything(true, true, true)) throw new IllegalStateException("World save did not complete");
            JailTerrainJournal.remove(player.server); UNDO_PREVIEWS.remove(player.getUUID());
            dev.unyielding.quests.UnyieldingQuests.LOGGER.info("Jail: {} restored the jail placement area in {} at {}", source.getTextName(), backup.dimension(), backup.min());
            source.sendSuccess(() -> Component.literal("Jail placement was undone and the original blocks restored."), true); return 1;
        } catch (Exception error) {
            return fail(source, "Undo was interrupted; the backup was retained. Preview recovery again: " + error.getMessage());
        }
    }
    private static String validateRestore(ServerPlayer player, JailTerrainJournal.Backup backup) {
        ServerLevel level = player.serverLevel(); BlockPos min = backup.min();
        if (!level.dimension().location().equals(backup.dimension())) return "Run undo in the backup's dimension.";
        if (min.getY() <= level.getMinBuildHeight() || min.getY()+HEIGHT-1 >= level.getMaxBuildHeight()) return "Backup is outside this dimension's height limits.";
        AABB bounds = new AABB(min.getX(), min.getY(), min.getZ(), min.getX()+WIDTH, min.getY()+HEIGHT, min.getZ()+DEPTH);
        if (!level.getWorldBorder().isWithinBounds(bounds)) return "Backup is outside the current world border.";
        for (int x=min.getX()>>4; x<=(min.getX()+WIDTH-1)>>4; x++) for (int z=min.getZ()>>4; z<=(min.getZ()+DEPTH-1)>>4; z++) {
            if (!level.hasChunk(x,z)) return "The backup area is not loaded; stand nearby first.";
            if (!claimAccess(player, level, x, z)) return "Open Parties claim access could not be verified for the backup area.";
        }
        if (!level.getEntities((Entity)null, bounds).isEmpty()) return "An entity or dropped item is inside the backup area. Move it out first.";
        Loc expected = Loc.of(level, min.getX()+3.5, min.getY()+1, min.getZ()+3.5, 0, 0), current = JailData.get(player.server).location();
        if (!java.util.Objects.equals(current, expected) && !java.util.Objects.equals(current, backup.prior())) return "The saved jail location changed since this backup; admin review is required.";
        try {
            Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object instance = api.getMethod("get", MinecraftServer.class).invoke(null, player.server);
            Object protection = api.getMethod("getChunkProtection").invoke(instance);
            Class<?> contract = Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI");
            var check = contract.getMethod("onEntityPlaceBlock", net.minecraft.world.level.block.state.BlockState.class, Entity.class, ServerLevel.class, BlockPos.class);
            for (int i=0; i<JailTerrainJournal.VOLUME; i++) {
                BlockPos pos = at(min, i); var state = level.getBlockState(pos); var before = backup.before().get(i); var after = backup.after().get(i);
                if (level.getBlockEntity(pos) != null || !(state.equals(before) || state.equals(after))) return "A later edit at " + pos.toShortString() + " conflicts with the backup. Nothing was restored.";
                if (!state.equals(before) && !Boolean.FALSE.equals(check.invoke(protection, before, player, level, pos))) return "Open Parties denied restoring " + pos.toShortString() + ".";
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return "Open Parties could not verify restoration permissions."; }
        return null;
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

    /** Version 3 adds a visible chair without reinterpreting existing v2 cages. */
    static boolean chairStructure(int x, int y, int z) {
        return newStructure(x,y,z) || x == 10 && y == 1 && z == 3;
    }
    static net.minecraft.world.level.block.state.BlockState chairTemplateState(int x, int y, int z) {
        if (x == 10 && y == 1 && z == 3)
            return Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.WEST);
        if (x == 6 && y >= 1 && y <= 4 && z >= 1 && z <= 5)
            return Blocks.IRON_BARS.defaultBlockState().setValue(net.minecraft.world.level.block.CrossCollisionBlock.NORTH, true)
                    .setValue(net.minecraft.world.level.block.CrossCollisionBlock.SOUTH, true);
        return newTemplateState(x,y,z);
    }
    private static BlockPos at(BlockPos min, int index) {
        return min.offset(index / (HEIGHT * DEPTH), index / DEPTH % HEIGHT, index % DEPTH);
    }
    private static List<net.minecraft.world.level.block.state.BlockState> capture(ServerLevel level, BlockPos min) {
        var states = new ArrayList<net.minecraft.world.level.block.state.BlockState>(JailTerrainJournal.VOLUME);
        for (int i=0; i<JailTerrainJournal.VOLUME; i++) states.add(level.getBlockState(at(min, i)));
        return List.copyOf(states);
    }
    private static List<net.minecraft.world.level.block.state.BlockState> desired() {
        var states = new ArrayList<net.minecraft.world.level.block.state.BlockState>(JailTerrainJournal.VOLUME);
        for (int x=0; x<WIDTH; x++) for (int y=0; y<HEIGHT; y++) for (int z=0; z<DEPTH; z++)
            states.add(chairStructure(x,y,z) ? chairTemplateState(x,y,z) : Blocks.AIR.defaultBlockState());
        return List.copyOf(states);
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
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(WIDTH-1,HEIGHT-1,DEPTH-1))) {
            var state = level.getBlockState(pos);
            if (state.hasBlockEntity() || level.getBlockEntity(pos) != null || !state.getFluidState().isEmpty())
                return "Block at " + pos.toShortString() + " contains a block entity or fluid. Nothing is changed.";
            if (!state.isAir() && !TERRAIN.contains(state.getBlock()) && !PLANTS.contains(state.getBlock()))
                return "Protected or unlisted block at " + pos.toShortString() + ": " + state + ". Ores, valuable blocks and inventories are never cleared.";
            if (state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) {
                var half = state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF);
                BlockPos other = half == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                if (other.getY() < min.getY() || other.getY() >= min.getY()+HEIGHT || !level.getBlockState(other).is(state.getBlock()))
                    return "Tall plant at " + pos.toShortString() + " extends outside the preview or is incomplete. Choose another area.";
            }
        }
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
        if (geometry != JailData.LEGACY_GEOMETRY && geometry != JailData.INTERROGATION_GEOMETRY && geometry != JailData.INTERROGATION_CHAIR_GEOMETRY) return false;
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
            } else if (geometry == JailData.INTERROGATION_CHAIR_GEOMETRY && chairStructure(x,y,z)) {
                if (!state.equals(chairTemplateState(x,y,z))) return false;
            } else if (geometry == JailData.INTERROGATION_GEOMETRY && newStructure(x,y,z)) {
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
            var targets = desired();
            for (int i=0; i<targets.size(); i++) {
                BlockPos pos = at(min, i); var next = targets.get(i);
                if (!level.getBlockState(pos).equals(next) && !Boolean.FALSE.equals(check.invoke(protection, next, player, level, pos))) return false;
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return false; }
    }
}
