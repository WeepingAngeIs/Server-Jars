package dev.unyielding.quests.jail;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Experimental origin transaction coordinator; no production admission or native mutation adapter. */
public final class JailOriginTransaction {
    private JailOriginTransaction() {}
    public enum Receipt { NONE, TEMPORARY, ORIGINAL }
    public record View(byte[] state, UUID transaction, Receipt receipt) {
        public View { state=Objects.requireNonNull(state).clone(); Objects.requireNonNull(receipt); }
        @Override public byte[] state(){return state.clone();}
    }
    public interface Port {
        UUID owner(); boolean online();
        /** Data-only registry, layer, callback/effect and claim audit. Never run native lifecycle here. */
        byte[] prepareIntent(UUID transaction,byte[] custodyDigest) throws IOException;
        void validateCustody(JailEscrowJournal.Entry custody,boolean releaseStarted) throws IOException;
        View current() throws IOException;
        View durable() throws IOException;
        View planTemporary(byte[] intent,UUID transaction) throws IOException;
        View planOriginal(byte[] intent,UUID transaction) throws IOException;
        void makeTemporary(byte[] intent,UUID transaction) throws IOException;
        void restoreOriginal(byte[] intent,UUID transaction) throws IOException;
        /** Native player + claims saves, force both files/directories, exact actual-file readback. */
        void persistAndVerify() throws IOException;
        boolean isTemporary(byte[] intent,UUID transaction,View view) throws IOException;
        boolean isOriginal(byte[] intent,UUID transaction,View view) throws IOException;
        boolean isPrepared(byte[] intent,View view) throws IOException;
    }
    public enum Boundary { BEFORE_PREPARE, PREPARED, STARTED, BEFORE_MUTATION, MUTATED, BEFORE_SAVE, PLAYER_AND_CLAIMS_DURABLE, BEFORE_COMPLETE, COMPLETE }
    @FunctionalInterface public interface Checkpoint {void at(Boundary boundary)throws IOException;}
    public enum Inspection { NO_RECORD, ORIGINAL_PREPARED, TEMPORARY_VERIFIED, TEMPORARY_SAVED_REVIEW, ORIGINAL_SAVED_REVIEW, COMPLETE_RECORDED, REVIEW_REQUIRED }
    public enum Result { VERIFIED, ALREADY_COMPLETE }
    private record Link(byte[] custodyDigest,byte[] intent) {}

    public static void temporaryForDisposableTest(JailEscrowJournal origins,JailEscrowJournal custody,UUID id,Port port,Checkpoint at)throws IOException {
        if(!port.online())throw new IOException("Origin player offline");
        if(origins.read(id)!=null||origins.list().stream().anyMatch(e->e.owner().equals(port.owner())&&!terminal(e.state())))throw new IOException("Existing origin transaction requires review");
        var inventory=custody(custody,id,port,false); byte[] digest=hash(inventory.snapshot());
        byte[] intent=port.prepareIntent(id,digest); View before=port.current();
        if(before.receipt()!=Receipt.NONE||before.transaction()!=null)throw new IOException("Unresolved origin receipt");
        View planned=port.planTemporary(intent,id);
        if(!port.isTemporary(intent,id,planned))throw new IOException("Invalid temporary origin plan");
        port.persistAndVerify(); requireSame(before,port.current(),port.durable());
        at.at(Boundary.BEFORE_PREPARE);
        origins.begin(id,port.owner(),encode(digest,intent)); at.at(Boundary.PREPARED);
        origins.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED); at.at(Boundary.STARTED);
        at.at(Boundary.BEFORE_MUTATION); requireSame(before,port.current(),port.durable()); linked(origins.read(id),custody,id,port,false);
        port.makeTemporary(intent,id); at.at(Boundary.MUTATED); requireSame(planned,port.current());
        at.at(Boundary.BEFORE_SAVE); port.persistAndVerify(); requireSame(planned,port.current(),port.durable());
        linked(origins.read(id),custody,id,port,false); at.at(Boundary.PLAYER_AND_CLAIMS_DURABLE); at.at(Boundary.BEFORE_COMPLETE);
        origins.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD); at.at(Boundary.COMPLETE);
    }
    public static Result originalForDisposableTest(JailEscrowJournal origins,JailEscrowJournal custody,UUID id,Port port,Checkpoint at)throws IOException {
        var entry=owned(origins,id,port);
        if(entry.state()==JailEscrowJournal.State.COMPLETE)return Result.ALREADY_COMPLETE;
        if(!port.online())throw new IOException("Origin player offline");
        Link link=linked(entry,custody,id,port,false);
        if(entry.state()!=JailEscrowJournal.State.HELD)throw new IOException("Origin phase requires review");
        View before=port.current();
        if(!port.isTemporary(link.intent(),id,before))throw new IOException("Temporary origin or claims changed; review required");
        // Progress/cooldowns may legitimately advance while held. Save and pin
        // the current state; restoring the origin must not rewind that state.
        port.persistAndVerify(); requireSame(before,port.current(),port.durable());
        View planned=port.planOriginal(link.intent(),id);
        if(!port.isOriginal(link.intent(),id,planned))throw new IOException("Invalid original origin plan");
        at.at(Boundary.BEFORE_PREPARE);
        origins.transition(id,JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED); at.at(Boundary.STARTED);
        at.at(Boundary.BEFORE_MUTATION); linked(origins.read(id),custody,id,port,false); requireSame(before,port.current(),port.durable());
        port.restoreOriginal(link.intent(),id); at.at(Boundary.MUTATED); requireSame(planned,port.current());
        at.at(Boundary.BEFORE_SAVE); port.persistAndVerify(); requireSame(planned,port.current(),port.durable());
        linked(origins.read(id),custody,id,port,false); at.at(Boundary.PLAYER_AND_CLAIMS_DURABLE); at.at(Boundary.BEFORE_COMPLETE);
        origins.transition(id,JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE); at.at(Boundary.COMPLETE);
        return Result.VERIFIED;
    }
    /** Inspection never invokes a callback, changes a claim, repeats a switch or promotes a phase. */
    public static Inspection inspect(JailEscrowJournal origins,JailEscrowJournal custody,UUID id,Port port)throws IOException {
        var entry=origins.read(id); if(entry==null)return Inspection.NO_RECORD;
        if(!entry.owner().equals(port.owner()))return Inspection.REVIEW_REQUIRED;
        if(entry.state()==JailEscrowJournal.State.COMPLETE)return Inspection.COMPLETE_RECORDED;
        try {
            Link link=linked(entry,custody,id,port,false); View live=port.current(); requireSame(live,port.durable());
            return switch(entry.state()) {
                case PREPARED -> port.isPrepared(link.intent(),live)?Inspection.ORIGINAL_PREPARED:Inspection.REVIEW_REQUIRED;
                case REMOVAL_STARTED -> port.isTemporary(link.intent(),id,live)?Inspection.TEMPORARY_SAVED_REVIEW:Inspection.REVIEW_REQUIRED;
                case HELD -> port.isTemporary(link.intent(),id,live)?Inspection.TEMPORARY_VERIFIED:Inspection.REVIEW_REQUIRED;
                case RESTORE_STARTED -> port.isOriginal(link.intent(),id,live)?Inspection.ORIGINAL_SAVED_REVIEW:Inspection.REVIEW_REQUIRED;
                default -> Inspection.REVIEW_REQUIRED;
            };
        }catch(IOException unsafe){return Inspection.REVIEW_REQUIRED;}
    }
    /** Explicit phase-only recovery of an already correct state. No origin/power/claim mutation or replay. */
    public static void confirmSavedForDisposableTest(JailEscrowJournal origins,JailEscrowJournal custody,UUID id,Port port)throws IOException {
        if(!port.online())throw new IOException("Origin player offline");
        Inspection status=inspect(origins,custody,id,port);
        if(status!=Inspection.TEMPORARY_SAVED_REVIEW&&status!=Inspection.ORIGINAL_SAVED_REVIEW)throw new IOException("No exact saved origin state to confirm");
        View before=port.current(); port.persistAndVerify(); requireSame(before,port.current(),port.durable());
        if(inspect(origins,custody,id,port)!=status)throw new IOException("Origin confirmation state changed");
        if(status==Inspection.TEMPORARY_SAVED_REVIEW)origins.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
        else origins.transition(id,JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
    }
    /** Must be enforced by the eventual single release entry point before custody can insert any items. */
    public static void assertInventoryReleaseReady(JailEscrowJournal origins,JailEscrowJournal custody,UUID id,Port port,boolean releaseStarted)throws IOException {
        var entry=owned(origins,id,port); Link link=linked(entry,custody,id,port,releaseStarted);
        if(!port.online()||entry.state()!=JailEscrowJournal.State.COMPLETE)throw new IOException("Origin restoration not complete; inventory retained");
        View live=port.current(); requireSame(live,port.durable());
        if(!port.isOriginal(link.intent(),id,live))throw new IOException("Origin/claim restoration no longer verified; inventory retained");
    }
    public static byte[] intentForReview(JailEscrowJournal origins,UUID id,UUID owner)throws IOException {
        var entry=origins.read(id);if(entry==null||!entry.owner().equals(owner))throw new IOException("Missing or foreign origin history");
        return decode(entry.snapshot()).intent().clone();
    }
    private static JailEscrowJournal.Entry owned(JailEscrowJournal origins,UUID id,Port port)throws IOException {
        var entry=origins.read(id);if(entry==null||!entry.owner().equals(port.owner()))throw new IOException("Missing or foreign origin transaction");return entry;
    }
    private static JailEscrowJournal.Entry custody(JailEscrowJournal journal,UUID id,Port port,boolean releaseStarted)throws IOException {
        var entry=journal.read(id);
        if(entry==null||!entry.owner().equals(port.owner())||(entry.state()!=JailEscrowJournal.State.HELD&&!(releaseStarted&&entry.state()==JailEscrowJournal.State.RESTORE_STARTED)))
            throw new IOException("Linked custody is not held");
        port.validateCustody(entry,releaseStarted);return entry;
    }
    private static Link linked(JailEscrowJournal.Entry entry,JailEscrowJournal inventory,UUID id,Port port,boolean releaseStarted)throws IOException {
        if(!entry.id().equals(id)||!entry.owner().equals(port.owner()))throw new IOException("Foreign origin/custody identity");
        Link link=decode(entry.snapshot()); var held=custody(inventory,id,port,releaseStarted);
        if(!MessageDigest.isEqual(link.custodyDigest(),hash(held.snapshot())))throw new IOException("Custody snapshot digest changed");return link;
    }
    private static boolean terminal(JailEscrowJournal.State state){return state==JailEscrowJournal.State.COMPLETE||state==JailEscrowJournal.State.CANCELLED;}
    private static void requireSame(View expected,View... observations)throws IOException {
        for(View actual:observations)if(!Objects.equals(expected.transaction(),actual.transaction())||expected.receipt()!=actual.receipt()||!Arrays.equals(expected.state(),actual.state()))throw new IOException("Origin state is partial or uncertain; review required");
    }
    private static byte[] encode(byte[] digest,byte[] intent)throws IOException {
        if(digest.length!=32||intent==null||intent.length==0||intent.length>4*1024*1024-40)throw new IOException("Invalid origin intent");
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(0x4a4f4c31);out.write(digest);out.writeInt(intent.length);out.write(intent);return bytes.toByteArray();
    }
    private static Link decode(byte[] bytes)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));if(in.readInt()!=0x4a4f4c31)throw new IOException("Unknown origin intent format");
        byte[] digest=in.readNBytes(32);int size=in.readInt();if(digest.length!=32||size<=0||size!=in.available())throw new IOException("Malformed origin link");return new Link(digest,in.readNBytes(size));
    }
    public static byte[] hash(byte[] data){try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}}
}
