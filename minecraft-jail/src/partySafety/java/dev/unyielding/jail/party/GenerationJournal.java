package dev.unyielding.jail.party;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

/**
 * Experimental, unregistered local-filesystem authority. Not a native Cobblemon save receipt.
 * One locked writer; immutable chained generations; no backup fallback or automatic replay.
 * A receipt exists only after file forces, atomic moves, directory forces and readback succeed.
 * The caller must provide a trusted, private directory on a filesystem with these semantics.
 */
public final class GenerationJournal implements AutoCloseable {
    public static final int MAX_STATE = 16 * 1024 * 1024;
    private static final int MAGIC = 0x4a504731, MAX_GENERATION = 4096;
    public enum Step { INTENT_FORCED, DATA_FORCED, GENERATION_MOVED, HEAD_FORCED, HEAD_MOVED, CLEAN_FORCED }
    @FunctionalInterface public interface Fault { void after(Step step) throws IOException; }
    private static final Fault NONE = step -> {};
    public record Receipt(UUID journal, String binding, long generation, String recordHash, String stateHash) {}
    private final Path root;
    private final String binding;
    private final UUID identity;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private Receipt current;
    private byte[] state;
    private boolean poisoned, closed;

    public static GenerationJournal create(Path root, String binding) throws IOException {
        Objects.requireNonNull(binding);
        if (!binding.matches("[a-zA-Z0-9:._=-]{1,1024}")) throw new IOException("Invalid runtime/provider binding");
        Path absolute = checkedPath(root);
        Files.createDirectory(absolute); // Never reset, reuse, or silently initialize an existing directory.
        forceDirectory(absolute.getParent());
        UUID id = UUID.randomUUID();
        writeNew(absolute.resolve("IDENTITY"), (id + "\n" + binding + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] initial = record(id, binding, 0, "0".repeat(64), new byte[0]);
        writeNew(absolute.resolve(generationName(0)), initial);
        writeNew(absolute.resolve("CURRENT"), head(0, hash(initial)));
        forceDirectory(absolute);
        return open(absolute, binding);
    }

    public static GenerationJournal open(Path root, String binding) throws IOException {
        return new GenerationJournal(checkedPath(root), binding);
    }

    private GenerationJournal(Path root, String binding) throws IOException {
        this.root = root; this.binding = binding;
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) throw new IOException("Missing journal directory");
        String[] identityLines = new String(read(root.resolve("IDENTITY"), 2048), java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
        if (identityLines.length != 3 || !identityLines[1].equals(binding) || !identityLines[2].isEmpty())
            throw new IOException("Runtime/provider binding mismatch");
        try { identity = UUID.fromString(identityLines[0]); }
        catch (RuntimeException e) { throw new IOException("Malformed journal identity", e); }
        lockChannel = FileChannel.open(root.resolve("LOCK"), CREATE, WRITE, NOFOLLOW_LINKS);
        FileLock acquired = null;
        try {
            acquired = lockChannel.tryLock();
            if (acquired == null) throw new IOException("Journal already owned");
            lock = acquired;
            refresh();
        } catch (Throwable failure) {
            if (acquired != null) acquired.close();
            lockChannel.close();
            if (failure instanceof IOException e) throw e;
            throw new IOException("Journal cannot be opened exclusively", failure);
        }
    }

    /** Checks disk every time. An uncertain write poisons this instance; it cannot supply old receipts. */
    public synchronized Receipt receipt() throws IOException { ready(); refresh(); return current; }
    public synchronized byte[] state() throws IOException { ready(); refresh(); return state.clone(); }
    public synchronized void verify(Receipt receipt) throws IOException {
        if (!receipt().equals(receipt)) throw new IOException("Stale, foreign, or fabricated generation receipt");
    }
    public synchronized Receipt commit(Receipt expected, byte[] next) throws IOException { return commit(expected, next, NONE); }

    /** Fault points are dependency-injected for tests; this class is not packaged in the production JAR. */
    public synchronized Receipt commit(Receipt expected, byte[] next, Fault fault) throws IOException {
        Objects.requireNonNull(next); Objects.requireNonNull(fault);
        ready(); refresh();
        if (!current.equals(expected)) throw new IOException("Stale generation refused before mutation");
        if (next.length > MAX_STATE || current.generation() >= MAX_GENERATION) throw new IOException("Journal bound reached");
        byte[] snapshot = next.clone();
        long generation = current.generation() + 1;
        byte[] data = record(identity, binding, generation, current.recordHash(), snapshot);
        String digest = hash(data);
        // Any uncertain IO from here onward closes the gate, including failures after CURRENT changed.
        try {
            writeNew(root.resolve("PENDING"), head(generation, digest)); forceDirectory(root); fault.after(Step.INTENT_FORCED);
            writeNew(root.resolve("DATA.tmp"), data); fault.after(Step.DATA_FORCED);
            Files.move(root.resolve("DATA.tmp"), root.resolve(generationName(generation)), StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(root); fault.after(Step.GENERATION_MOVED);
            writeNew(root.resolve("HEAD.tmp"), head(generation, digest)); fault.after(Step.HEAD_FORCED);
            Files.move(root.resolve("HEAD.tmp"), root.resolve("CURRENT"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            forceDirectory(root); fault.after(Step.HEAD_MOVED);
            Files.delete(root.resolve("PENDING")); forceDirectory(root); fault.after(Step.CLEAN_FORCED);
            refresh();
            if (current.generation() != generation || !current.recordHash().equals(digest) || !Arrays.equals(state, snapshot))
                throw new IOException("Commit readback differs");
            return current;
        } catch (Throwable failure) {
            poisoned = true;
            if (failure instanceof Error e) throw e;
            throw new IOException("Commit uncertain; quarantine required, no receipt issued", failure);
        }
    }

    private void ready() throws IOException {
        if (closed || poisoned || !lock.isValid()) throw new IOException("Closed or quarantined journal");
    }
    private void refresh() throws IOException {
        try {
            String identityText = new String(read(root.resolve("IDENTITY"),2048),java.nio.charset.StandardCharsets.UTF_8);
            if (!identityText.equals(identity + "\n" + binding + "\n")) throw new IOException("Journal identity changed");
            String pointer = new String(read(root.resolve("CURRENT"), 256), java.nio.charset.StandardCharsets.US_ASCII);
            String[] parts = pointer.split("\n", -1);
            if (parts.length != 3 || !parts[2].isEmpty() || !parts[1].matches("[0-9a-f]{64}")) throw new IOException("Corrupt current pointer");
            long generation = Long.parseLong(parts[0]);
            if (generation < 0 || generation > MAX_GENERATION || !parts[0].equals(Long.toString(generation))) throw new IOException("Invalid generation");
            Set<String> allowed = new HashSet<>(List.of("IDENTITY", "CURRENT", "LOCK"));
            String previous = "0".repeat(64); byte[] payload = null;
            for (long g = 0; g <= generation; g++) {
                String name = generationName(g); allowed.add(name);
                byte[] data = read(root.resolve(name), MAX_STATE + 2048);
                try (var in = new DataInputStream(new ByteArrayInputStream(data))) {
                    if (in.readInt() != MAGIC || !new UUID(in.readLong(), in.readLong()).equals(identity)
                            || !in.readUTF().equals(binding) || in.readLong() != g || !in.readUTF().equals(previous))
                        throw new IOException("Broken generation chain");
                    int size = in.readInt();
                    if (size < 0 || size > MAX_STATE || size != in.available()) throw new IOException("Malformed generation payload");
                    payload = in.readNBytes(size);
                }
                previous = hash(data);
            }
            if (!previous.equals(parts[1])) throw new IOException("Current generation hash mismatch");
            try (var paths = Files.list(root)) {
                for (Path path : paths.toList()) if (!allowed.contains(path.getFileName().toString()) || !Files.isRegularFile(path, NOFOLLOW_LINKS))
                    throw new IOException("Incomplete, foreign, or orphan journal state: " + path.getFileName());
            }
            // Never silently roll a still-open authority back even to a valid old chain.
            if (current != null && generation < current.generation()) throw new IOException("Generation rollback refused");
            state = payload;
            current = new Receipt(identity, binding, generation, previous, hash(payload));
        } catch (Throwable failure) {
            poisoned = true;
            if (failure instanceof IOException e) throw e;
            throw new IOException("Journal verification failed", failure);
        }
    }
    private static byte[] record(UUID id, String binding, long generation, String previous, byte[] state) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC); out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
            out.writeUTF(binding); out.writeLong(generation); out.writeUTF(previous); out.writeInt(state.length); out.write(state);
        }
        return bytes.toByteArray();
    }
    private static byte[] head(long generation, String digest) { return (generation + "\n" + digest + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII); }
    private static String generationName(long generation) { return String.format(java.util.Locale.ROOT, "g-%020d.bin", generation); }
    private static Path checkedPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path p = absolute; p != null; p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Symbolic journal path refused");
        return absolute;
    }
    private static byte[] read(Path path, int bound) throws IOException {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw new IOException("Missing or nonregular journal file: " + path.getFileName());
        try (FileChannel channel = FileChannel.open(path, READ, NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 0 || size > bound) throw new IOException("Journal file exceeds bound");
            ByteBuffer buffer = ByteBuffer.allocate((int) size);
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new EOFException("Short journal file");
            if (channel.size() != size) throw new IOException("File changed during verification");
            return buffer.array();
        }
    }
    private static void writeNew(Path path, byte[] data) throws IOException {
        try (FileChannel channel = FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(data); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, READ, NOFOLLOW_LINKS)) { channel.force(true); }
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    @Override public synchronized void close() throws IOException {
        if (!closed) { closed = true; try { lock.release(); } finally { lockChannel.close(); } }
    }
}
