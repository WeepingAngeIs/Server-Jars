package dev.unyielding.jail;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Durable custody pointers and audit only. Never contains a second inventory. */
public final class JailCustodyLedger implements AutoCloseable {
    private static final int MAGIC=0x55435631, MAX=16384;
    public enum State { QUARANTINED, REVIEWED, RELEASED }
    public record Anchor(String dimension,int x,int y,int z) {
        public Anchor {if(dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid dimension");}
    }
    public record Binding(UUID id,UUID owner,String items,String origins,byte[] digest,Anchor anchor,State state,long revision,String reason) {
        public Binding {Objects.requireNonNull(id);Objects.requireNonNull(owner);path(items);path(origins);digest=digest.clone();if(digest.length!=32)throw new IllegalArgumentException("Invalid digest");Objects.requireNonNull(anchor);Objects.requireNonNull(state);text(reason,512);}
        @Override public byte[] digest(){return digest.clone();}
    }
    public record Audit(long sequence,UUID transaction,String actor,String reason,State state) {}
    private final FileChannel file;private final FileLock lock;private final Path path;private final Object fileIdentity;private byte[] chain=new byte[32];private long sequence;private boolean poisoned;
    private final Map<UUID,Binding> bindings=new LinkedHashMap<>();private final List<Audit> audit=new ArrayList<>();
    public static void provisionNew(Path directory)throws IOException {
        Files.createDirectory(directory);
        try(var f=FileChannel.open(directory.resolve("custody.v1"),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){write(f,ByteBuffer.allocate(8).putInt(MAGIC).putInt(1).flip());f.force(true);}
        forceDirectory(directory);forceDirectory(directory.getParent());
    }
    public JailCustodyLedger(Path directory)throws IOException {
        path=directory.resolve("custody.v1");fileIdentity=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();if(fileIdentity==null||Files.isSymbolicLink(path))throw new IOException("Cannot verify custody file identity");file=FileChannel.open(path,StandardOpenOption.READ,StandardOpenOption.WRITE);FileLock acquired=null;
        try{acquired=file.tryLock();if(acquired==null)throw new IOException("Custody ledger is already in use");lock=acquired;load();}catch(Throwable failure){if(acquired!=null)acquired.release();file.close();if(failure instanceof IOException io)throw io;throw new IOException("Invalid custody ledger",failure);}
    }
    static void path(String value){if(value==null||value.isBlank()||value.length()>512||!value.matches("[a-zA-Z0-9_./-]+")||value.startsWith("/")||Arrays.asList(value.split("/")).contains("..")||value.contains("//"))throw new IllegalArgumentException("Invalid custody path");}
    private static void text(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid audit text");}
    private static void forceDirectory(Path path)throws IOException{try(var f=FileChannel.open(path,StandardOpenOption.READ)){f.force(true);}}
    private static void write(FileChannel f,ByteBuffer b)throws IOException{while(b.hasRemaining())f.write(b);}
    private static byte[] digest(byte[] previous,byte[] payload){byte[] joined=Arrays.copyOf(previous,previous.length+payload.length);System.arraycopy(payload,0,joined,previous.length,payload.length);return JailOriginTransaction.hash(joined);}
    private byte[] read(int length)throws IOException{var b=ByteBuffer.allocate(length);while(b.hasRemaining())if(file.read(b)<0)throw new IOException("Truncated custody ledger; recovery required");return b.array();}
    private void load()throws IOException {
        file.position(0);var header=ByteBuffer.wrap(read(8));if(header.getInt()!=MAGIC||header.getInt()!=1)throw new IOException("Unknown custody ledger");
        while(file.position()<file.size()){
            int length=ByteBuffer.wrap(read(4)).getInt();if(length<1||length>MAX)throw new IOException("Invalid custody frame");byte[] payload=read(length),checksum=read(32);
            if(!MessageDigest.isEqual(checksum,digest(chain,payload)))throw new IOException("Custody audit checksum failed");apply(payload);chain=checksum;
        }
    }
    private void apply(byte[] payload)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(payload));long next=in.readLong();if(next!=sequence+1||next>100000)throw new IOException("Invalid custody sequence");
        UUID id=new UUID(in.readLong(),in.readLong()),owner=new UUID(in.readLong(),in.readLong());String items=in.readUTF(),origins=in.readUTF();byte[] hash=in.readNBytes(32);
        Anchor anchor=new Anchor(in.readUTF(),in.readInt(),in.readInt(),in.readInt());int ordinal=in.readUnsignedByte();if(ordinal>=State.values().length)throw new IOException("Unknown quarantine state");State state=State.values()[ordinal];String actor=in.readUTF(),reason=in.readUTF();text(actor,128);text(reason,512);if(in.available()!=0)throw new IOException("Trailing custody data");
        Binding old=bindings.get(id),now=new Binding(id,owner,items,origins,hash,anchor,state,next,reason);
        if(old==null&&state!=State.QUARANTINED)throw new IOException("New custody record was not quarantined");
        if(old!=null&&(!old.owner().equals(owner)||!old.items().equals(items)||!old.origins().equals(origins)||!MessageDigest.isEqual(old.digest(),hash)))throw new IOException("Custody identity changed");
        for(Binding other:bindings.values())if(!other.id().equals(id)&&other.anchor().equals(anchor))throw new IOException("Another prisoner owns this interface");
        bindings.put(id,now);audit.add(new Audit(next,id,actor,reason,state));sequence=next;
    }
    public Binding get(UUID id){return bindings.get(id);}public List<Binding> list(){return List.copyOf(bindings.values());}public List<Audit> audit(UUID id){return audit.stream().filter(e->e.transaction().equals(id)).toList();}
    public boolean healthy(){try{var attributes=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return !poisoned&&file.isOpen()&&attributes.isRegularFile()&&fileIdentity.equals(attributes.fileKey())&&attributes.size()==file.size();}catch(IOException bad){return false;}}
    /** Revalidate the authoritative index before a privileged recovery action. */
    public void verifyDisk()throws IOException {
        try{if(!healthy()||!fileIdentity.equals(Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey())||file.size()>64L*1024*1024)throw new IOException("Custody history missing, replaced or too large");
            file.position(0);var header=ByteBuffer.wrap(read(8));if(header.getInt()!=MAGIC||header.getInt()!=1)throw new IOException("Custody header changed");byte[] verified=new byte[32];long count=0;
            while(file.position()<file.size()){int length=ByteBuffer.wrap(read(4)).getInt();if(length<1||length>MAX)throw new IOException("Corrupt custody frame");byte[] payload=read(length),checksum=read(32);if(!MessageDigest.isEqual(checksum,digest(verified,payload)))throw new IOException("Custody audit checksum failed");verified=checksum;count++;}
            if(count!=sequence||!MessageDigest.isEqual(verified,chain))throw new IOException("Custody history changed outside coordinator");
        }catch(IOException failed){poisoned=true;throw failed;}
    }
    public boolean outstanding(UUID owner){return bindings.values().stream().anyMatch(b->b.owner().equals(owner)&&b.state()!=State.RELEASED);}
    public Binding bind(UUID id,UUID owner,String items,String origins,byte[] hash,Anchor anchor,String actor)throws IOException {
        if(bindings.containsKey(id))throw new IOException("Transaction already indexed; use its existing record");
        return append(new Binding(id,owner,items,origins,hash,anchor,State.QUARANTINED,sequence+1,"Imported custody; verified adapter review required"),actor);
    }
    public Binding transition(UUID id,long revision,State state,String actor,String reason)throws IOException {Binding old=expected(id,revision);return append(new Binding(id,old.owner(),old.items(),old.origins(),old.digest(),old.anchor(),state,sequence+1,reason),actor);}
    public Binding relocate(UUID id,long revision,Anchor anchor,String actor)throws IOException {Binding old=expected(id,revision);return append(new Binding(id,old.owner(),old.items(),old.origins(),old.digest(),anchor,State.QUARANTINED,sequence+1,"Interface rebound; custody retained and review required"),actor);}
    private Binding expected(UUID id,long revision)throws IOException{Binding b=get(id);if(b==null||b.revision()!=revision)throw new IOException("Stale or missing custody selection");return b;}
    private Binding append(Binding b,String actor)throws IOException {
        verifyDisk();text(actor,128);
        for(Binding other:bindings.values())if(!other.id().equals(b.id())&&other.anchor().equals(b.anchor()))throw new IOException("Another prisoner owns this interface");
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeLong(sequence+1);out.writeLong(b.id().getMostSignificantBits());out.writeLong(b.id().getLeastSignificantBits());out.writeLong(b.owner().getMostSignificantBits());out.writeLong(b.owner().getLeastSignificantBits());out.writeUTF(b.items());out.writeUTF(b.origins());out.write(b.digest());out.writeUTF(b.anchor().dimension());out.writeInt(b.anchor().x());out.writeInt(b.anchor().y());out.writeInt(b.anchor().z());out.writeByte(b.state().ordinal());out.writeUTF(actor);out.writeUTF(b.reason());byte[] payload=bytes.toByteArray();if(payload.length>MAX)throw new IOException("Custody frame too large");byte[] checksum=digest(chain,payload);
        try{file.position(file.size());write(file,ByteBuffer.allocate(4).putInt(payload.length).flip());write(file,ByteBuffer.wrap(payload));write(file,ByteBuffer.wrap(checksum));file.force(true);apply(payload);chain=checksum;return get(b.id());}catch(Throwable failed){poisoned=true;if(failed instanceof IOException io)throw io;throw new IOException("Custody write outcome uncertain",failed);}
    }
    @Override public void close()throws IOException{try{lock.release();}finally{file.close();}}
}
