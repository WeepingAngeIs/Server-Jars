package dev.unyielding.jail;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Strict read-only escrow replay plus forced, create-only migration evidence. No item transfer API. */
final class JailMigrationIO {
    private JailMigrationIO(){}
    static String hash(byte[] b){return HexFormat.of().formatHex(JailOriginTransaction.hash(b));}
    static String sha512(Path p)throws IOException {
        try{var d=MessageDigest.getInstance("SHA-512");try(var in=Files.newInputStream(p)){byte[] b=new byte[65536];for(int n;(n=in.read(b))>=0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static Path regular(Path root,String relative)throws IOException {
        Path base=root.toRealPath();Path rel=Path.of(relative);
        if(rel.isAbsolute()||relative.isBlank())throw new IOException("Absolute/empty evidence path");
        Path walk=base;for(Path part:rel){if(part.toString().equals("..")||part.toString().equals("."))throw new IOException("Traversal in evidence path");walk=walk.resolve(part);if(Files.isSymbolicLink(walk))throw new IOException("Symbolic evidence path");}
        if(!Files.isRegularFile(walk,LinkOption.NOFOLLOW_LINKS)||!walk.equals(walk.toRealPath())||!walk.startsWith(base))throw new IOException("Missing/unsafe evidence file: "+relative);
        Object n=Files.getAttribute(walk,"unix:nlink",LinkOption.NOFOLLOW_LINKS);if(!(n instanceof Number)||((Number)n).longValue()!=1)throw new IOException("Linked evidence refused");return walk;
    }
    static byte[] read(Path p)throws IOException {long n=Files.size(p);if(n<=0||n>32L*1024*1024)throw new IOException("Evidence length outside bound");byte[] b=Files.readAllBytes(p);if(b.length!=n||!Arrays.equals(b,Files.readAllBytes(p)))throw new IOException("Evidence changed during read");return b;}
    static void force(Path p)throws IOException {try(var f=FileChannel.open(p,StandardOpenOption.READ)){f.force(true);}}
    static void create(Path p,byte[] b)throws IOException {
        Files.createDirectories(p.getParent());try(var f=FileChannel.open(p,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){var v=ByteBuffer.wrap(b);while(v.hasRemaining())f.write(v);f.force(true);}force(p.getParent());
        if(!Arrays.equals(b,read(p)))throw new IOException("Evidence readback mismatch");
    }
    static List<JailEscrowJournal.Entry> entries(Path p)throws IOException {return entries(read(p));}
    static List<JailEscrowJournal.Entry> entries(byte[] bytes)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=0x4a455331||in.readInt()!=1)throw new IOException("Unknown escrow history");
        Map<UUID,JailEscrowJournal.Entry> rows=new LinkedHashMap<>();
        while(in.available()>0){
            if(in.readInt()!=0x4a454631)throw new IOException("Bad escrow frame");int n=in.readInt();if(n<41||n>4*1024*1024+40||n>in.available()-32)throw new IOException("Truncated/oversized escrow frame");
            byte[] body=in.readNBytes(n),hash=in.readNBytes(32);if(!MessageDigest.isEqual(JailOriginTransaction.hash(body),hash))throw new IOException("Escrow digest mismatch");
            var b=new DataInputStream(new ByteArrayInputStream(body));UUID id=new UUID(b.readLong(),b.readLong()),owner=new UUID(b.readLong(),b.readLong());int state=b.readInt(),length=b.readInt();
            if(state<0||state>=JailEscrowJournal.State.values().length||length<=0||length!=b.available())throw new IOException("Malformed escrow payload");
            var e=new JailEscrowJournal.Entry(id,owner,JailEscrowJournal.State.values()[state],b.readNBytes(length));var old=rows.get(id);
            if(old==null){if(e.state()!=JailEscrowJournal.State.PREPARED||rows.values().stream().anyMatch(x->x.owner().equals(owner)&&!terminal(x.state())))throw new IOException("Invalid initial escrow owner/state");}
            else if(!old.owner().equals(owner)||!Arrays.equals(old.snapshot(),e.snapshot())||!transition(old.state(),e.state()))throw new IOException("Rewritten identity, snapshot or invalid transition");
            rows.put(id,e);
        }
        return List.copyOf(rows.values());
    }
    private static boolean terminal(JailEscrowJournal.State s){return s==JailEscrowJournal.State.COMPLETE||s==JailEscrowJournal.State.CANCELLED;}
    private static boolean transition(JailEscrowJournal.State a,JailEscrowJournal.State b){return switch(a){
        case PREPARED->b==JailEscrowJournal.State.REMOVAL_STARTED||b==JailEscrowJournal.State.CANCELLED;
        case REMOVAL_STARTED->b==JailEscrowJournal.State.HELD;
        case HELD->b==JailEscrowJournal.State.RESTORE_STARTED;
        case RESTORE_STARTED->b==JailEscrowJournal.State.COMPLETE;
        default->false;};}
}
