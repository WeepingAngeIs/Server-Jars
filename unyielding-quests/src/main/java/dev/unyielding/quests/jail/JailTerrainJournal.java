package dev.unyielding.quests.jail;

import dev.unyielding.quests.essentials.Loc;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/** One immutable before/after journal, forced before the first world write. */
final class JailTerrainJournal {
    static final int WIDTH = 13, HEIGHT = 7, DEPTH = 7, VOLUME = WIDTH * HEIGHT * DEPTH;
    private static final int MAGIC = 0x554A4231, MAX_BYTES = 8 * 1024 * 1024;
    record Backup(ResourceLocation dimension, BlockPos min, List<BlockState> before,
                  List<BlockState> after, Loc prior, int priorGeometry, UUID actor) { }
    private JailTerrainJournal() { }
    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("unyielding-jail-placement.before");
    }
    static boolean exists(MinecraftServer server) { return Files.exists(path(server), LinkOption.NOFOLLOW_LINKS); }
    static void write(MinecraftServer server, Backup backup) throws IOException {
        if (backup.before().size() != VOLUME || backup.after().size() != VOLUME) throw new IOException("Invalid backup volume");
        CompoundTag root = new CompoundTag();
        root.putInt("format", 1); root.putString("dimension", backup.dimension().toString());
        root.putIntArray("min", new int[]{backup.min().getX(), backup.min().getY(), backup.min().getZ()});
        root.putInt("priorGeometry", backup.priorGeometry());
        root.putUUID("actor", backup.actor());
        if (backup.prior() != null) root.put("prior", backup.prior().toTag());
        ListTag before = new ListTag(), after = new ListTag();
        for (BlockState state : backup.before()) before.add(NbtUtils.writeBlockState(state));
        for (BlockState state : backup.after()) after.add(NbtUtils.writeBlockState(state));
        root.put("before", before); root.put("after", after);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); NbtIo.writeCompressed(root, out);
        byte[] payload = out.toByteArray();
        if (payload.length == 0 || payload.length > MAX_BYTES) throw new IOException("Backup is too large");
        ByteBuffer bytes = ByteBuffer.allocate(8 + payload.length + 32).putInt(MAGIC).putInt(payload.length).put(payload).put(hash(payload)).flip();
        Path file = path(server); Files.createDirectories(file.getParent());
        // CREATE_NEW prevents replacing the only recovery record from an earlier operation.
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        // Force the directory entry where the platform supports opening directories.
        // Windows may reject this operation; process-crash recovery remains available,
        // but power-loss durability of directory metadata is platform-dependent.
        try (FileChannel directory = FileChannel.open(file.getParent(), StandardOpenOption.READ)) { directory.force(true); }
        catch (IOException | UnsupportedOperationException ignored) { }
        // Detect incomplete/corrupt backup before any world write.
        Backup verify = read(server);
        if (!verify.dimension().equals(backup.dimension()) || !verify.min().equals(backup.min())
                || !verify.before().equals(backup.before()) || !verify.after().equals(backup.after()))
            throw new IOException("Backup verification failed; no blocks were changed");
    }
    static Backup read(MinecraftServer server) throws IOException {
        Path file = path(server);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Placement backup is missing or invalid");
        long size = Files.size(file); if (size < 40 || size > MAX_BYTES + 40L) throw new IOException("Invalid backup size");
        ByteBuffer bytes = ByteBuffer.wrap(Files.readAllBytes(file));
        if (bytes.getInt() != MAGIC) throw new IOException("Invalid backup header");
        int length = bytes.getInt(); if (length < 1 || length > MAX_BYTES || bytes.remaining() != length + 32) throw new IOException("Incomplete backup");
        byte[] payload = new byte[length], digest = new byte[32]; bytes.get(payload); bytes.get(digest);
        if (!MessageDigest.isEqual(hash(payload), digest)) throw new IOException("Backup checksum mismatch");
        CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.create(16L * 1024 * 1024));
        if (root.getInt("format") != 1 || !root.contains("dimension", Tag.TAG_STRING) || !root.contains("min", Tag.TAG_INT_ARRAY)
                || !root.contains("before", Tag.TAG_LIST) || !root.contains("after", Tag.TAG_LIST) || !root.contains("priorGeometry", Tag.TAG_INT) || !root.hasUUID("actor"))
            throw new IOException("Invalid placement backup");
        ResourceLocation dimension = ResourceLocation.tryParse(root.getString("dimension")); int[] min = root.getIntArray("min");
        if (dimension == null || min.length != 3) throw new IOException("Invalid backup location");
        ListTag beforeTags = root.getList("before", Tag.TAG_COMPOUND), afterTags = root.getList("after", Tag.TAG_COMPOUND);
        if (beforeTags.size() != VOLUME || afterTags.size() != VOLUME) throw new IOException("Invalid backup volume");
        var registry = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<BlockState> before = new ArrayList<>(VOLUME), after = new ArrayList<>(VOLUME);
        try {
            for (int i = 0; i < VOLUME; i++) {
                CompoundTag a = beforeTags.getCompound(i), b = afterTags.getCompound(i);
                BlockState old = NbtUtils.readBlockState(registry, a), next = NbtUtils.readBlockState(registry, b);
                if (!NbtUtils.writeBlockState(old).equals(a) || !NbtUtils.writeBlockState(next).equals(b)) throw new IOException("Unknown backup block state");
                before.add(old); after.add(next);
            }
        } catch (RuntimeException invalid) { throw new IOException("Invalid backup block state", invalid); }
        Loc prior = root.contains("prior", Tag.TAG_COMPOUND) ? Loc.fromTag(root.getCompound("prior")) : null;
        return new Backup(dimension, new BlockPos(min[0], min[1], min[2]), List.copyOf(before), List.copyOf(after), prior, root.getInt("priorGeometry"), root.getUUID("actor"));
    }
    static void remove(MinecraftServer server) throws IOException { Files.delete(path(server)); }
    private static byte[] hash(byte[] payload) {
        try { return MessageDigest.getInstance("SHA-256").digest(payload); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
