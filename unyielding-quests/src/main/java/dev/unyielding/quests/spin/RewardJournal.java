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
    private static final int CREDIT_MAGIC = 0x55514332;
    public record QuestCredit(UUID id, UUID player, long spinId, UUID generation) {}
    private final Map<UUID, QuestCredit> credits = new LinkedHashMap<>();
    public enum State { PREPARED, DEBIT_APPLIED, DELIVERING, HELD, COMPLETE, RETURNED }
    public record Entry(UUID id, UUID player, int slot, byte[] payload, State state) {
        public Entry { Objects.requireNonNull(id); Objects.requireNonNull(player); Objects.requireNonNull(payload); Objects.requireNonNull(state); payload = payload.clone(); }
        @Override public byte[] payload() { return payload.clone(); }
    }
    private final FileChannel channel;
    private final FileLock lock;
    private final Map<UUID,Entry> records = new LinkedHashMap<>();
    private final Set<UUID> deliveryAttempts = new HashSet<>();
    private boolean poisoned;

    public RewardJournal(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        // Normal startup must never replace missing history with an empty journal.
        if (!Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Recovery directory missing; restore history or explicitly provision a new installation");
        Path file = absolute.resolve("reward-journal.v1");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Recovery journal missing; restore history, do not recreate it");
        FileChannel opened = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = opened.tryLock();
            if (acquired == null) throw new IOException("Reward journal already in use");
            channel = opened; lock = acquired;
            replay();
        } catch (Exception e) {
            if (acquired != null) acquired.release();
            opened.close();
            if (e instanceof IOException io) throw io;
            throw new IOException("Cannot open reward journal", e);
        }
    }

    /** Explicit NEW-installation operation. Never call from startup or recovery retry.
     * A partial initialization is intentionally retained and requires operator inspection.
     */
    public static void provisionNew(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Files.createDirectory(absolute); // Refuse even an existing empty directory.
        Path file = absolute.resolve("reward-journal.v1");
        try (FileChannel out = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer header = ByteBuffer.allocate(8).putInt(0x55514831).putInt(1);
            header.flip();
            while (header.hasRemaining()) out.write(header);
            out.force(true);
        }
        // No portable directory-fsync contract on Windows. Missing publication on a later
        // boot fails closed above. This is not a physical-power-loss guarantee.
    }

    /** One credit per quest, player and reset generation; retries never add another. */
    public synchronized QuestCredit creditQuest(UUID player, long spinId, UUID generation) throws IOException {
        check();
        ByteBuffer identity = ByteBuffer.allocate(40).putLong(player.getMostSignificantBits()).putLong(player.getLeastSignificantBits())
                .putLong(spinId).putLong(generation.getMostSignificantBits()).putLong(generation.getLeastSignificantBits());
        UUID id = UUID.nameUUIDFromBytes(identity.array());
        QuestCredit credit = new QuestCredit(id, player, spinId, generation);
        QuestCredit old = credits.get(id);
        if (old != null) {
            if (!old.equals(credit)) throw new IOException("Conflicting quest credit identity");
            return old;
        }
        ByteBuffer body = ByteBuffer.allocate(56).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).put(identity.array());
        writeFrame(CREDIT_MAGIC, body.array());
        credits.put(id, credit);
        return credit;
    }
    public synchronized List<QuestCredit> questCredits() throws IOException { check(); return List.copyOf(credits.values()); }

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
    /** A durable attempt marker is evidence of an attempt, not proof of receipt. */
    public synchronized boolean deliveryAttempted(UUID id) throws IOException {
        check();
        if (!records.containsKey(id)) throw new IOException("Unknown reward transaction");
        return deliveryAttempts.contains(id);
    }
    private static boolean allowed(State from, State to) {
        return switch (from) {
            case PREPARED -> to == State.DEBIT_APPLIED || to == State.HELD;
            case DEBIT_APPLIED -> to == State.DELIVERING || to == State.HELD;
            case DELIVERING -> to == State.HELD || to == State.COMPLETE || to == State.RETURNED;
            case RETURNED -> to == State.COMPLETE;
            case HELD -> to == State.COMPLETE;
            case COMPLETE -> false;
        };
    }
    private void check() throws IOException { if (poisoned || !channel.isOpen()) throw new IOException("Reward journal unavailable; admin review required"); }
    private void append(Entry entry) throws IOException {
        writeFrame(MAGIC, encode(entry));
        records.put(entry.id(), entry);
        if (entry.state() == State.DELIVERING) deliveryAttempts.add(entry.id());
    }
    private void writeFrame(int magic, byte[] body) throws IOException {
        byte[] digest = hash(body);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length + digest.length).putInt(magic).putInt(body.length).put(body).put(digest);
        frame.flip();
        try { channel.position(channel.size()); while (frame.hasRemaining()) channel.write(frame); channel.force(true); }
        catch (IOException e) { poisoned = true; throw e; }
    }
    private void replay() throws IOException {
        channel.position(0);
        if (channel.size() < 8) throw new IOException("Empty or incomplete journal; preserve it for review");
        ByteBuffer initial = ByteBuffer.allocate(8); readFully(initial); initial.flip();
        int first = initial.getInt();
        if (first == 0x55514831) {
            if (initial.getInt() != 1) throw new IOException("Unsupported journal header version");
        } else {
            // Legacy non-empty v1 record streams are read in place, never rewritten.
            if (first != MAGIC) throw new IOException("Corrupt journal header");
            channel.position(0);
        }
        while (channel.position() < channel.size()) {
            ByteBuffer header = ByteBuffer.allocate(8); readFully(header); header.flip();
            int magic = header.getInt();
            if (magic != MAGIC && magic != CREDIT_MAGIC) throw new IOException("Corrupt reward journal header");
            int size = header.getInt(); if (size < 41 || size > MAX_FRAME) throw new IOException("Invalid reward journal frame");
            ByteBuffer data = ByteBuffer.allocate(size + 32); readFully(data); data.flip();
            byte[] body = new byte[size], digest = new byte[32]; data.get(body); data.get(digest);
            if (!MessageDigest.isEqual(digest, hash(body))) throw new IOException("Corrupt reward journal checksum");
            if (magic == CREDIT_MAGIC) {
                if (body.length != 56) throw new IOException("Invalid quest credit frame");
                ByteBuffer b = ByteBuffer.wrap(body);
                UUID id = new UUID(b.getLong(), b.getLong()), player = new UUID(b.getLong(), b.getLong());
                long spinId = b.getLong(); UUID generation = new UUID(b.getLong(), b.getLong());
                if (!id.equals(UUID.nameUUIDFromBytes(Arrays.copyOfRange(body, 16, 56))) || credits.containsKey(id))
                    throw new IOException("Invalid or duplicate quest credit identity");
                credits.put(id, new QuestCredit(id, player, spinId, generation));
                continue;
            }
            Entry entry = decode(body); Entry old = records.get(entry.id());
            if (old == null) { if (entry.state() != State.PREPARED) throw new IOException("Missing transaction start"); }
            else if (!old.player().equals(entry.player()) || old.slot() != entry.slot() || !Arrays.equals(old.payload(), entry.payload()) || !allowed(old.state(), entry.state())) throw new IOException("Invalid transaction history");
            records.put(entry.id(), entry);
            if (entry.state() == State.DELIVERING) deliveryAttempts.add(entry.id());
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
