package dev.unyielding.quests.spin;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Durable append-only payout intent log. Damaged or incomplete records fail closed. */
public final class RewardJournal implements AutoCloseable {
    private static final int MAGIC = 0x55515231, MAX_PAYLOAD = 1024 * 1024, MAX_FRAME = MAX_PAYLOAD + 128;
    public enum State { PREPARED, DEBIT_APPLIED, DELIVERING, HELD, COMPLETE }
    public record Entry(UUID id, UUID player, int slot, byte[] payload, State state) {
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(player); Objects.requireNonNull(payload); Objects.requireNonNull(state); payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    private final FileChannel channel;
    private final FileLock lock;
    private final Map<UUID,Entry> records = new LinkedHashMap<>();
    private boolean poisoned;

    public RewardJournal(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        boolean existed = Files.isDirectory(absolute);
        if (!existed) Files.createDirectory(absolute); // Parent must already exist and be durable.
        if (!existed && absolute.getParent() != null) {
            try (FileChannel parent = FileChannel.open(absolute.getParent(), StandardOpenOption.READ)) { parent.force(true); }
        }
        directory = absolute;
        // Fail closed on filesystems where directory metadata cannot be forced.
        try (FileChannel dir = FileChannel.open(directory, StandardOpenOption.READ)) { dir.force(true); }
        Path file = directory.resolve("reward-journal.v1");
        FileChannel opened = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = opened.tryLock();
            if (acquired == null) throw new IOException("Reward journal already in use");
            try (FileChannel dir = FileChannel.open(directory, StandardOpenOption.READ)) { dir.force(true); }
            channel = opened; lock = acquired;
            replay();
        } catch (Exception e) {
            if (acquired != null) acquired.release();
            opened.close();
            if (e instanceof IOException io) throw io;
            throw new IOException("Cannot open reward journal", e);
        }
    }

    public synchronized Entry begin(UUID id, UUID player, int slot, byte[] payload) throws IOException {
        check();
        if (records.containsKey(id)) throw new IOException("Transaction already exists");
        if ((slot != 2 && slot != 3) || payload.length == 0 || payload.length > MAX_PAYLOAD) throw new IOException("Invalid transaction");
        Entry entry = new Entry(id, player, slot, payload, State.PREPARED);
        append(entry); return entry;
    }
    public synchronized Entry transition(UUID id, State expected, State next) throws IOException {
        check(); Entry old = records.get(id);
        if (old == null || old.state() != expected || !allowed(expected, next)) throw new IOException("Invalid transaction transition");
        Entry entry = new Entry(old.id(), old.player(), old.slot(), old.payload(), next);
        append(entry); return entry;
    }
    public synchronized Entry read(UUID id) throws IOException { check(); return records.get(id); }
    public synchronized List<Entry> list() throws IOException { check(); return List.copyOf(records.values()); }
    private static boolean allowed(State from, State to) {
        return switch (from) {
            case PREPARED -> to == State.DEBIT_APPLIED || to == State.HELD;
            case DEBIT_APPLIED -> to == State.DELIVERING || to == State.HELD;
            case DELIVERING -> to == State.HELD || to == State.COMPLETE;
            case HELD -> to == State.COMPLETE;
            case COMPLETE -> false;
        };
    }
    private void check() throws IOException { if (poisoned || !channel.isOpen()) throw new IOException("Reward journal unavailable; admin review required"); }
    private void append(Entry entry) throws IOException {
        byte[] body = encode(entry), digest = hash(body);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length + digest.length).putInt(MAGIC).putInt(body.length).put(body).put(digest);
        frame.flip();
        try { channel.position(channel.size()); while (frame.hasRemaining()) channel.write(frame); channel.force(true); }
        catch (IOException e) { poisoned = true; throw e; }
        records.put(entry.id(), entry);
    }
    private void replay() throws IOException {
        channel.position(0);
        while (channel.position() < channel.size()) {
            ByteBuffer header = ByteBuffer.allocate(8); readFully(header); header.flip();
            if (header.getInt() != MAGIC) throw new IOException("Corrupt reward journal header");
            int size = header.getInt(); if (size < 41 || size > MAX_FRAME) throw new IOException("Invalid reward journal frame");
            ByteBuffer data = ByteBuffer.allocate(size + 32); readFully(data); data.flip();
            byte[] body = new byte[size], digest = new byte[32]; data.get(body); data.get(digest);
            if (!MessageDigest.isEqual(digest, hash(body))) throw new IOException("Corrupt reward journal checksum");
            Entry entry = decode(body); Entry old = records.get(entry.id());
            if (old == null) { if (entry.state() != State.PREPARED) throw new IOException("Missing transaction start"); }
            else if (!old.player().equals(entry.player()) || old.slot() != entry.slot() || !Arrays.equals(old.payload(), entry.payload()) || !allowed(old.state(), entry.state())) throw new IOException("Invalid transaction history");
            records.put(entry.id(), entry);
        }
    }
    private void readFully(ByteBuffer buf) throws IOException { while (buf.hasRemaining()) if (channel.read(buf) < 0) throw new EOFException("Incomplete reward journal; admin review required"); }
    private static byte[] encode(Entry e) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(e.id().getMostSignificantBits()); out.writeLong(e.id().getLeastSignificantBits());
        out.writeLong(e.player().getMostSignificantBits()); out.writeLong(e.player().getLeastSignificantBits());
        out.writeInt(e.slot()); out.writeByte(e.state().ordinal()); out.writeInt(e.payload().length); out.write(e.payload()); out.flush(); return bytes.toByteArray();
    }
    private static Entry decode(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        UUID id = new UUID(in.readLong(), in.readLong()), player = new UUID(in.readLong(), in.readLong());
        int slot = in.readInt(), state = in.readUnsignedByte(), length = in.readInt();
        if ((slot != 2 && slot != 3) || state >= State.values().length || length < 1 || length > MAX_PAYLOAD || length != in.available()) throw new IOException("Invalid reward journal record");
        return new Entry(id, player, slot, in.readNBytes(length), State.values()[state]);
    }
    private static byte[] hash(byte[] body) { try { return MessageDigest.getInstance("SHA-256").digest(body); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    @Override public synchronized void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
}
