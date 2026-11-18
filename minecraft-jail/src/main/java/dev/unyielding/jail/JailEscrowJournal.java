package dev.unyielding.jail;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Standalone journal primitive; it does not remove, grant, or reconcile any items. */
public final class JailEscrowJournal implements AutoCloseable {
    private static final int HEADER = 0x4a455331, FRAME = 0x4a454631, VERSION = 1;
    private static final int MAX_SNAPSHOT = 4 * 1024 * 1024;
    public enum State { PREPARED, REMOVAL_STARTED, HELD, RESTORE_STARTED, COMPLETE, CANCELLED }
    public record Entry(UUID id, UUID owner, State state, byte[] snapshot) {
        public Entry {
            Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(state);
            snapshot = Objects.requireNonNull(snapshot).clone();
        }
        @Override public byte[] snapshot() { return snapshot.clone(); }
    }
    private final FileChannel channel;
    private final FileLock lock;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean poisoned;

    /** Explicit initialization only. Never invoke when prior history might exist. */
    public static void provisionNew(Path directory) throws IOException {
        Path dir = directory.toAbsolutePath().normalize();
        Files.createDirectory(dir);
        try (FileChannel file = FileChannel.open(dir.resolve("escrow.v1"), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(8).putInt(HEADER).putInt(VERSION).flip();
            while (header.hasRemaining()) file.write(header);
            file.force(true);
        }
        // The new file and directory names must survive rename/creation metadata
        // loss as well. Unsupported platforms fail initialization, never claim
        // durability from a SavedData dirty flag or a file-only flush.
        try (var metadata = FileChannel.open(dir, StandardOpenOption.READ)) { metadata.force(true); }
        try (var metadata = FileChannel.open(dir.getParent(), StandardOpenOption.READ)) { metadata.force(true); }
    }

    public JailEscrowJournal(Path directory) throws IOException {
        Path dir = directory.toAbsolutePath().normalize();
        Path file = dir.resolve("escrow.v1");
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Escrow history missing; administrator review required");
        FileChannel opened = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = opened.tryLock();
            if (acquired == null) throw new IOException("Escrow history is already open");
            channel = opened; lock = acquired; replay();
        } catch (Exception failure) {
            if (acquired != null) acquired.release();
            opened.close();
            if (failure instanceof IOException io) throw io;
            throw new IOException("Cannot open escrow history", failure);
        }
    }

    public synchronized Entry begin(UUID id, UUID owner, byte[] snapshot) throws IOException {
        check();
        if (id == null || owner == null || snapshot == null || snapshot.length == 0 || snapshot.length > MAX_SNAPSHOT)
            throw new IOException("Invalid escrow intent");
        if (entries.containsKey(id) || entries.values().stream().anyMatch(e -> e.owner().equals(owner) && !terminal(e.state())))
            throw new IOException("Duplicate or unresolved escrow intent");
        Entry entry = new Entry(id, owner, State.PREPARED, snapshot);
        append(entry); return entry;
    }

    /**
     * Appends a verified state transition. The caller must establish whether an
     * external mutation completed before calling this; the journal cannot do so.
     * After an interrupted removal or restore, leave the record unresolved for
     * explicit review. Never infer completion or repeat a transfer from it.
     */
    public synchronized Entry transition(UUID id, State expected, State next) throws IOException {
        check(); Entry previous = entries.get(id);
        if (previous == null || previous.state() != expected || !allowed(expected, next))
            throw new IOException("Invalid escrow transition");
        Entry updated = new Entry(id, previous.owner(), next, previous.snapshot());
        append(updated); return updated;
    }
    public synchronized Entry read(UUID id) throws IOException { check(); return entries.get(id); }
    public synchronized List<Entry> list() throws IOException { check(); return List.copyOf(entries.values()); }
    static boolean terminal(State state) { return state == State.COMPLETE || state == State.CANCELLED; }
    private static boolean allowed(State from, State to) {
        return switch (from) {
            case PREPARED -> to == State.REMOVAL_STARTED || to == State.CANCELLED;
            case REMOVAL_STARTED -> to == State.HELD;
            case HELD -> to == State.RESTORE_STARTED;
            case RESTORE_STARTED -> to == State.COMPLETE;
            case COMPLETE, CANCELLED -> false;
        };
    }
    private void check() throws IOException {
        if (poisoned || !channel.isOpen()) throw new IOException("Escrow journal unavailable; administrator review required");
    }
    private void append(Entry entry) throws IOException {
        byte[] payload = entry.snapshot();
        ByteBuffer body = ByteBuffer.allocate(16 + 16 + 4 + 4 + payload.length)
                .putLong(entry.id().getMostSignificantBits()).putLong(entry.id().getLeastSignificantBits())
                .putLong(entry.owner().getMostSignificantBits()).putLong(entry.owner().getLeastSignificantBits())
                .putInt(entry.state().ordinal()).putInt(payload.length).put(payload);
        byte[] bytes = body.array();
        ByteBuffer frame = ByteBuffer.allocate(8 + bytes.length + 32).putInt(FRAME).putInt(bytes.length).put(bytes).put(hash(bytes)).flip();
        try {
            channel.position(channel.size());
            while (frame.hasRemaining()) channel.write(frame);
            channel.force(true);
            entries.put(entry.id(), entry);
        } catch (IOException failure) { poisoned = true; throw failure; }
    }
    private void replay() throws IOException {
        channel.position(0);
        ByteBuffer header = read(8);
        if (header.getInt() != HEADER || header.getInt() != VERSION) throw new IOException("Invalid escrow header");
        while (channel.position() < channel.size()) {
            ByteBuffer frame = read(8);
            int magic = frame.getInt(), size = frame.getInt();
            if (magic != FRAME || size < 40 || size > MAX_SNAPSHOT + 40) throw new IOException("Invalid escrow frame");
            byte[] body = new byte[size]; read(size).get(body);
            byte[] digest = new byte[32]; read(32).get(digest);
            if (!MessageDigest.isEqual(hash(body), digest)) throw new IOException("Corrupt escrow frame");
            ByteBuffer data = ByteBuffer.wrap(body);
            UUID id = new UUID(data.getLong(), data.getLong()), owner = new UUID(data.getLong(), data.getLong());
            int ordinal = data.getInt(), length = data.getInt();
            if (ordinal < 0 || ordinal >= State.values().length || length <= 0 || length != data.remaining()) throw new IOException("Invalid escrow record");
            byte[] snapshot = new byte[length]; data.get(snapshot);
            Entry current = new Entry(id, owner, State.values()[ordinal], snapshot), old = entries.get(id);
            if (old == null) {
                if (current.state() != State.PREPARED || entries.values().stream().anyMatch(e -> e.owner().equals(owner) && !terminal(e.state()))) throw new IOException("Invalid initial escrow record");
            } else if (!old.owner().equals(owner) || !Arrays.equals(old.snapshot(), snapshot) || !allowed(old.state(), current.state()))
                throw new IOException("Invalid escrow history");
            entries.put(id, current);
        }
    }
    private ByteBuffer read(int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new EOFException("Incomplete escrow history");
        return buffer.flip();
    }
    private static byte[] hash(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    @Override public synchronized void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
