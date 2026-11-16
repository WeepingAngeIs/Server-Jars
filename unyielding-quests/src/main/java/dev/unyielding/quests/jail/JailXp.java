package dev.unyielding.quests.jail;

import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** XP stays in vanilla storage. Only a journal-bound verified death permits one native clone handoff. */
public final class JailXp {
    static final String POLICY="vanilla-xp-retained-v1", RECEIPT="uq_jail_xp_clone";
    private JailXp(){}
    private static final ThreadLocal<Scope> scope=new ThreadLocal<>();
    private static final class Scope {final ServerPlayer owner;final JailXpState xp;int drops;Scope(ServerPlayer p,JailXpState xp){owner=p;this.xp=xp;}}
    static boolean nonzero(ServerPlayer p){return p.totalExperience!=0||p.experienceLevel!=0||p.experienceProgress!=0;}
    static JailXpState read(CompoundTag n)throws IOException {
        if(!n.contains("XpLevel",3)||!n.contains("XpTotal",3)||!n.contains("XpP",5)||!n.contains("XpSeed",3)||!n.contains("Score",3))throw new IOException("Missing exact native XP fields");
        try{return new JailXpState(n.getInt("XpLevel"),n.getInt("XpTotal"),n.getFloat("XpP"),n.getInt("XpSeed"),n.getInt("Score"));}catch(IllegalArgumentException bad){throw new IOException(bad.getMessage(),bad);}
    }
    static JailXpState live(ServerPlayer p)throws IOException{return read(p.saveWithoutId(new CompoundTag()));}
    static CompoundTag encode(JailXpState s){CompoundTag n=new CompoundTag();n.putInt("XpLevel",s.level());n.putInt("XpTotal",s.total());n.putFloat("XpP",s.progress());n.putInt("XpSeed",s.seed());n.putInt("Score",s.score());return n;}
    static void begin(ServerPlayer p,CompoundTag expected)throws IOException {if(scope.get()!=null)throw new IOException("Nested XP death scope refused");var xp=read(expected);if(!xp.equals(live(p)))throw new IOException("XP changed before guarded death");scope.set(new Scope(p,xp));}
    static void end(ServerPlayer p,boolean accepted)throws IOException {var s=scope.get();scope.remove();if(s==null||s.owner!=p)throw new IOException("Missing XP death scope");if(!s.xp.equals(live(p))||accepted&&s.drops!=1)throw new IOException("Native XP/death-drop outcome differs; no clone replay authorized");}
    /** Invoked at the native dropExperience entry, not on every XP award or ordinary death. */
    public static boolean suppressDeathDrop(ServerPlayer p){var s=scope.get();if(s==null||s.owner!=p||!JailEscapeCustody.ownDamage(p))return false;s.drops++;return true;}
    static boolean retained(CompoundTag attempt)throws IOException {if(!attempt.contains("xp_policy"))return false;if(!POLICY.equals(attempt.getString("xp_policy"))||!attempt.contains("xp",10))throw new IOException("Unknown retained XP policy; no replay");read(attempt.getCompound("xp"));return true;}
    static void verify(ServerPlayer p,CompoundTag attempt)throws IOException {if(retained(attempt)&&!read(attempt.getCompound("xp")).equals(live(p)))throw new IOException("Retained XP changed; no automatic refund/rewind");}
    static void cloneTo(ServerPlayer old,ServerPlayer next,UUID attempt,CompoundTag snapshot)throws IOException {
        verify(old,snapshot);var xp=read(snapshot.getCompound("xp"));var source=JailVanillaPort.readSavedPlayer(old);if(!xp.equals(read(source)))throw new IOException("Saved old-player XP is not the linked source");
        // Native restoreFrom always copies XpSeed. keepInventory may already copy the XP tuple.
        var dest=live(next);if(dest.seed()!=xp.seed()||!(dest.equals(xp)||dest.level()==0&&dest.total()==0&&dest.progress()==0&&dest.score()==0))throw new IOException("Fresh clone has conflicting XP; no overwrite");
        if(next.getPersistentData().contains(RECEIPT))throw new IOException("Replacement already has an XP delivery receipt");
        next.experienceLevel=xp.level();next.totalExperience=xp.total();next.experienceProgress=xp.progress();next.setScore(xp.score());
        at(next,Boundary.AFTER_COPY);
        CompoundTag receipt=encode(xp);receipt.putUUID("attempt",attempt);next.getPersistentData().put(RECEIPT,receipt);
    }
    static void verifyClone(ServerPlayer p,UUID attempt,CompoundTag snapshot)throws IOException {
        verify(p,snapshot);CompoundTag receipt=p.getPersistentData().getCompound(RECEIPT),expected=encode(read(snapshot.getCompound("xp")));expected.putUUID("attempt",attempt);
        if(!expected.equals(receipt))throw new IOException("Missing exact XP clone receipt; no grant/replay authorized");
    }
    public enum Boundary { BEFORE_COPY, AFTER_COPY }
    @FunctionalInterface public interface Checkpoint {void at(Boundary boundary)throws IOException;}
    private static Checkpoint checkpoint=b->{};
    public static void disposableCheckpoint(ServerPlayer p,Checkpoint value)throws IOException {if(!JailHumanAudit.disposable(p))throw new IOException("XP fault hook unavailable");checkpoint=Objects.requireNonNull(value);}
    static void stopped(){checkpoint=b->{};scope.remove();}
    static void at(ServerPlayer p,Boundary b)throws IOException {checkpoint.at(b);}
}
