package dev.unyielding.jail.party;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

/**
 * Bounded experimental authority for complete party+PC snapshots, NOT an admission adapter.
 * Reservations and active store bytes share one generation commit. All reads use this gate.
 * Native stores, backups and provider results are untrusted candidates, never fallback authority.
 * Terminal reservations are retained. Reusing an owner/identity requires a future reviewed protocol.
 */
public final class PartyCustodyGuard implements AutoCloseable {
    public enum Phase { RESERVED, SEALED, RESTORED }
    public record Snapshot(UUID owner, String provider, byte[] party, Set<UUID> partyIds, byte[] pc, Set<UUID> pcIds) {
        public Snapshot {
            Objects.requireNonNull(owner); Objects.requireNonNull(provider);
            party = party.clone(); pc = pc.clone(); partyIds = Set.copyOf(partyIds); pcIds = Set.copyOf(pcIds);
            if (!provider.matches("[a-zA-Z0-9:._=-]{1,1024}") || party.length == 0 || pc.length == 0
                    || party.length > 2*1024*1024 || pc.length > 2*1024*1024 || partyIds.size() > 6 || pcIds.size() > 30)
                throw new IllegalArgumentException("Unsupported snapshot bounds or provider");
            if (!Collections.disjoint(partyIds, pcIds)) throw new IllegalArgumentException("Duplicate Pokemon identity across party/PC");
        }
        @Override public byte[] party() { return party.clone(); }
        @Override public byte[] pc() { return pc.clone(); }
        public boolean same(Snapshot other) {
            return owner.equals(other.owner) && provider.equals(other.provider) && partyIds.equals(other.partyIds)
                    && pcIds.equals(other.pcIds) && Arrays.equals(party, other.party) && Arrays.equals(pc, other.pc);
        }
        Set<UUID> identities() { Set<UUID> result = new HashSet<>(partyIds); result.addAll(pcIds); return result; }
    }
    private record Entry(UUID transaction, Phase phase, Snapshot original, Snapshot active) {}
    private final GenerationJournal journal;
    private static final int MAGIC = 0x4a504732, MAX_ENTRIES = 16;

    public static PartyCustodyGuard create(Path root, String binding) throws IOException { return new PartyCustodyGuard(GenerationJournal.create(root, binding)); }
    public static PartyCustodyGuard open(Path root, String binding) throws IOException { return new PartyCustodyGuard(GenerationJournal.open(root, binding)); }
    private PartyCustodyGuard(GenerationJournal journal) throws IOException {
        this.journal = journal;
        try { entries(); } catch (Throwable failure) { journal.close(); throw failure; }
    }
    public synchronized GenerationJournal.Receipt receipt() throws IOException { entries(); return journal.receipt(); }
    public synchronized void verify(GenerationJournal.Receipt receipt) throws IOException { entries(); journal.verify(receipt); }
    public synchronized Phase phase(UUID transaction) throws IOException { return entry(entries(), transaction).phase; }

    /** Complete source bytes and all party/PC UUID reservations become durable before any removal. */
    public synchronized GenerationJournal.Receipt reserve(UUID transaction, Snapshot source, GenerationJournal.Receipt expected) throws IOException {
        return reserve(transaction, source, expected, step -> {});
    }
    public synchronized GenerationJournal.Receipt reserve(UUID transaction, Snapshot source, GenerationJournal.Receipt expected, GenerationJournal.Fault fault) throws IOException {
        Objects.requireNonNull(transaction); Objects.requireNonNull(source);
        var entries = entries(); journal.verify(expected);
        if (entries.size() >= MAX_ENTRIES || entries.containsKey(transaction) || source.partyIds.isEmpty()) throw new IOException("Duplicate transaction or unsupported empty/bounded reservation");
        for (Entry old : entries.values()) {
            if (old.original.owner.equals(source.owner) || !Collections.disjoint(old.original.identities(), source.identities()))
                throw new IOException("Owner or Pokemon identity already reserved, including terminal tombstones");
        }
        entries.put(transaction, new Entry(transaction, Phase.RESERVED, source, source));
        return journal.commit(expected, encode(entries), fault);
    }

    /** Commits an owned empty-party generation. It does not certify native source-file removal. */
    public synchronized GenerationJournal.Receipt sealEmpty(UUID transaction, Snapshot empty, GenerationJournal.Receipt expected) throws IOException {
        return sealEmpty(transaction, empty, expected, step -> {});
    }
    public synchronized GenerationJournal.Receipt sealEmpty(UUID transaction, Snapshot empty, GenerationJournal.Receipt expected, GenerationJournal.Fault fault) throws IOException {
        var entries = entries(); journal.verify(expected); Entry old = entry(entries, transaction);
        if (old.phase != Phase.RESERVED || !compatibleEmpty(old.original, empty)) throw new IOException("Invalid source-removal generation");
        entries.put(transaction, new Entry(transaction, Phase.SEALED, old.original, empty));
        return journal.commit(expected, encode(entries), fault);
    }

    /** No merge/overwrite/grant. Caller supplies exact observed destination; changed state is refused. */
    public synchronized GenerationJournal.Receipt restore(UUID transaction, Snapshot destination, GenerationJournal.Receipt expected) throws IOException {
        return restore(transaction, destination, expected, step -> {});
    }
    public synchronized GenerationJournal.Receipt restore(UUID transaction, Snapshot destination, GenerationJournal.Receipt expected, GenerationJournal.Fault fault) throws IOException {
        var entries = entries(); journal.verify(expected); Entry old = entry(entries, transaction);
        if (old.phase != Phase.SEALED || !old.active.same(destination)) throw new IOException("Occupied, changed, or already-restored destination");
        entries.put(transaction, new Entry(transaction, Phase.RESTORED, old.original, old.original));
        return journal.commit(expected, encode(entries), fault);
    }

    /** Before exposing any native current/backup/alternate result, require exact generation and bytes. */
    public synchronized Snapshot authorizeCandidate(UUID transaction, Snapshot candidate, GenerationJournal.Receipt expected) throws IOException {
        var entries = entries(); journal.verify(expected); Entry entry = entry(entries, transaction);
        if (entry.phase == Phase.RESERVED || !entry.active.same(candidate)) throw new IOException("Uncommitted, stale, foreign, or conflicting load candidate");
        return entry.active; // Snapshot is immutable; byte arrays are defensively copied.
    }

    /** Managed storage load only; never asks native providers to recover a missing/corrupt generation. */
    public synchronized Snapshot loadOwned(UUID transaction, UUID owner, String provider, GenerationJournal.Receipt expected) throws IOException {
        var entries = entries(); journal.verify(expected); Entry entry = entry(entries, transaction);
        if (entry.phase == Phase.RESERVED || !entry.original.owner.equals(owner) || !entry.original.provider.equals(provider))
            throw new IOException("Pending or mismatched owner/provider");
        return entry.active;
    }
    private static boolean compatibleEmpty(Snapshot original, Snapshot empty) {
        return original.owner.equals(empty.owner) && original.provider.equals(empty.provider) && empty.partyIds.isEmpty()
                && original.pcIds.equals(empty.pcIds) && Arrays.equals(original.pc, empty.pc);
    }
    private static Entry entry(Map<UUID,Entry> entries, UUID transaction) throws IOException {
        Entry result = entries.get(transaction); if (result == null) throw new IOException("Unknown transaction; no fallthrough"); return result;
    }
    private TreeMap<UUID,Entry> entries() throws IOException {
        byte[] state = journal.state(); var entries = new TreeMap<UUID,Entry>();
        if (state.length == 0) {
            if (journal.receipt().generation() != 0) throw new IOException("Non-genesis empty state"); return entries;
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(state))) {
            if (in.readInt() != MAGIC) throw new IOException("Unknown guard state version");
            int count = in.readInt(); if (count <= 0 || count > MAX_ENTRIES) throw new IOException("Guard entry bound");
            Set<UUID> owners = new HashSet<>(), identities = new HashSet<>();
            for (int i=0;i<count;i++) {
                UUID tx=uuid(in); int ordinal=in.readUnsignedByte();
                if (ordinal >= Phase.values().length) throw new IOException("Unknown guard phase");
                Phase phase=Phase.values()[ordinal]; Snapshot original=snapshot(in),active=snapshot(in);
                if (original.partyIds.isEmpty() || entries.containsKey(tx) || !owners.add(original.owner)) throw new IOException("Duplicate/missing authority");
                for (UUID id : original.identities()) if (!identities.add(id)) throw new IOException("Conflicting global identity reservation");
                if ((phase==Phase.SEALED && !compatibleEmpty(original,active)) || (phase!=Phase.SEALED && !original.same(active)))
                    throw new IOException("Phase snapshot invariant broken");
                entries.put(tx,new Entry(tx,phase,original,active));
            }
            if (in.available()!=0) throw new IOException("Trailing guard state");
        } catch (IllegalArgumentException | NullPointerException e) { throw new IOException("Invalid serialized guard snapshot",e); }
        return entries;
    }
    private static byte[] encode(Map<UUID,Entry> entries) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);out.writeInt(entries.size());
            for(Entry entry:entries.values()) { uuid(out,entry.transaction);out.writeByte(entry.phase.ordinal());snapshot(out,entry.original);snapshot(out,entry.active); }
        }
        if(bytes.size()>GenerationJournal.MAX_STATE)throw new IOException("Aggregate snapshot exceeds bound");
        return bytes.toByteArray();
    }
    private static void snapshot(DataOutputStream out,Snapshot s)throws IOException {
        uuid(out,s.owner);out.writeUTF(s.provider);blob(out,s.party);ids(out,s.partyIds);blob(out,s.pc);ids(out,s.pcIds);
    }
    private static Snapshot snapshot(DataInputStream in)throws IOException {return new Snapshot(uuid(in),in.readUTF(),blob(in),ids(in,6),blob(in),ids(in,30));}
    private static void ids(DataOutputStream out,Set<UUID> ids)throws IOException{out.writeInt(ids.size());for(UUID id:new TreeSet<>(ids))uuid(out,id);}
    private static Set<UUID> ids(DataInputStream in,int limit)throws IOException{
        int n=in.readInt();if(n<0||n>limit)throw new IOException("Identity bound");Set<UUID> result=new HashSet<>();
        for(int i=0;i<n;i++)if(!result.add(uuid(in)))throw new IOException("Duplicate identity in serialized set");return result;
    }
    private static void blob(DataOutputStream out,byte[] bytes)throws IOException{out.writeInt(bytes.length);out.write(bytes);}
    private static byte[] blob(DataInputStream in)throws IOException{
        int n=in.readInt();if(n<=0||n>2*1024*1024||n>in.available())throw new IOException("Snapshot size invalid");return in.readNBytes(n);
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    @Override public synchronized void close()throws IOException{journal.close();}
}
