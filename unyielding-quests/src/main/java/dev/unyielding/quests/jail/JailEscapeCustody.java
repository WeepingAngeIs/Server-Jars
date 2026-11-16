package dev.unyielding.quests.jail;

import dev.unyielding.quests.UnyieldingQuests;
import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One durable attempt, ordinary cancellable damage, native respawn, no item copying or death replay. */
@EventBusSubscriber(modid=UnyieldingQuests.MODID)
public final class JailEscapeCustody {
    private JailEscapeCustody(){}
    private static final String RESPAWN="uq_jail_escape_respawn";
    @FunctionalInterface public interface Checkpoint {void saved()throws IOException;}
    private static Checkpoint checkpoint=()->{};
    public static void disposableCheckpoint(ServerPlayer p,Checkpoint value)throws IOException {if(!JailHumanAudit.disposable(p))throw new IOException("Escape fault hook unavailable");checkpoint=Objects.requireNonNull(value);}
    private static JailEscrowJournal journal(MinecraftServer s,UUID tx)throws IOException {return new JailEscrowJournal(JailCustodyService.journalPath(s,"integrated-jail-v1/"+tx+"/escapes"));}
    private static boolean terminal(JailEscrowJournal.Entry e){return e.state()==JailEscrowJournal.State.COMPLETE||e.state()==JailEscrowJournal.State.CANCELLED;}
    private static JailEscrowJournal.Entry pending(JailEscrowJournal j)throws IOException {var rows=j.list().stream().filter(e->!terminal(e)).toList();if(rows.size()>1)throw new IOException("Multiple escape attempts; review required");return rows.isEmpty()?null:rows.getFirst();}
    static boolean pending(MinecraftServer s,UUID tx)throws IOException {try(var j=journal(s,tx)){return pending(j)!=null;}}
    private static void destination(ServerPlayer p,JailIntegrated.EscapeContext c)throws IOException {
        var a=c.assignment();if(!a.equals(JailData.get(p.server).assignment(p.getUUID()))||!JailPlacement.intact(p.server,a.jail(),a.geometry())||!JailCommands.safe(p,a.jail())||!JailCommands.claimAccess(p,a.jail()))throw new IOException("Escape jail destination unsafe, unavailable or protected");
    }
    private static void bound(ServerPlayer p,JailIntegrated.EscapeContext c,JailEscrowJournal.Entry e)throws IOException {
        CompoundTag t=JailNbt.decode(e.snapshot());if(!e.owner().equals(p.getUUID())||!t.hasUUID("custody")||!t.getUUID("custody").equals(c.transaction())||!Arrays.equals(c.digest(),t.getByteArray("digest"))||!c.assignment().jail().toTag().equals(t.getCompound("jail")))throw new IOException("Escape/custody binding changed");
    }
    private static void atJail(ServerPlayer p,JailIntegrated.EscapeContext c)throws IOException {destination(p,c);var l=c.assignment().jail();if(!p.level().dimension().location().toString().equals(l.dim())||p.distanceToSqr(l.x(),l.y(),l.z())>0.0001)throw new IOException("Respawn is not at verified jail");}
    /** Only called from a validated accepted movement crossing. Never called on ticks/login/respawn. */
    static void attempt(ServerPlayer p){
        try{
            if(!p.isAlive()||p.isInvulnerableTo(p.damageSources().generic()))return;var c=JailIntegrated.escapeContext(p,true,true);if(c==null)return;destination(p,c);
            try(var j=journal(p.server,c.transaction())){
                if(pending(j)!=null)return;
                CompoundTag t=new CompoundTag();t.putUUID("custody",c.transaction());t.putByteArray("digest",c.digest());t.put("jail",c.assignment().jail().toTag());t.putInt("selected",p.getInventory().selected);
                t.put("origin_receipt",p.getPersistentData().getCompound(JailNativeOriginPort.RECEIPT).copy());
                UUID id=UUID.randomUUID();j.begin(id,p.getUUID(),JailNbt.encode(t));
                if(!JailEnforcement.teleportAuthorized(p,c.assignment().jail()))throw new IOException("Escape containment transfer refused");
                c.inventory().persistAndVerify();c.origin().persistAndVerify();atJail(p,c);
                j.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
                // Respect invulnerability, damage and death cancellation hooks. No retry if they stop it.
                p.hurt(p.damageSources().generic(),Float.MAX_VALUE);
                if(p.isAlive())throw new IOException("Escape damage prevented; explicit review required, no retry");
                c.inventory().persistAndVerify();c.origin().persistAndVerify();JailIntegrated.escapeContext(p,false,true);
                if(JailVanillaPort.readSavedPlayer(p).getFloat("Health")>0)throw new IOException("Escape death not durably observed");
                j.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
            }
        }catch(IOException|RuntimeException bad){report(p,bad);}
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(!event.isWasDeath()||!(event.getEntity() instanceof ServerPlayer next)||!(event.getOriginal() instanceof ServerPlayer old))return;
        try{
            var c=JailIntegrated.escapeContext(old,false,false);if(c==null)return;
            try(var j=journal(old.server,c.transaction())){
                var e=pending(j);if(e==null)return;bound(old,c,e);
                if(e.state()!=JailEscrowJournal.State.HELD||old.isAlive()||!next.getInventory().isEmpty()||!next.getUUID().equals(old.getUUID()))throw new IOException("Unverified death clone; custody retained");
                CompoundTag t=JailNbt.decode(e.snapshot()),source=old.getPersistentData(),dest=next.getPersistentData();
                if(!source.hasUUID(JailVanillaPort.REMOVED)||!c.transaction().equals(source.getUUID(JailVanillaPort.REMOVED))||source.contains(JailVanillaPort.RESTORED)||!source.getCompound(JailNativeOriginPort.RECEIPT).equals(t.getCompound("origin_receipt")))throw new IOException("Death clone source receipts changed");
                for(String key:List.of(JailVanillaPort.REMOVED,JailNativeOriginPort.RECEIPT))if(dest.contains(key)&&!dest.get(key).equals(source.get(key)))throw new IOException("Conflicting replacement receipt");
                if(dest.contains(JailVanillaPort.RESTORED)||dest.contains(RESPAWN))throw new IOException("Replacement already has an escape/return receipt");
                // Native NeoOrigins copyOnDeath owns its attachment. We copy no attachment or stack.
                for(String key:List.of(JailVanillaPort.REMOVED,JailNativeOriginPort.RECEIPT))dest.put(key,source.get(key).copy());
                next.getInventory().selected=t.getInt("selected");dest.putUUID(RESPAWN,e.id());
            }
        }catch(IOException|RuntimeException bad){report(next,bad);}
    }
    private static void complete(ServerPlayer p,boolean explicit)throws IOException {
        if(!p.isAlive())throw new IOException("Escape respawn still pending");var c=JailIntegrated.escapeContext(p,true,true);if(c==null)throw new IOException("No escape custody");
        try(var j=journal(p.server,c.transaction())){
            var e=pending(j);if(e==null)return;bound(p,c,e);atJail(p,c);
            if(!p.getPersistentData().hasUUID(RESPAWN)||!e.id().equals(p.getPersistentData().getUUID(RESPAWN)))throw new IOException("No verified native respawn receipt; no replay authorized");
            if(explicit&&e.state()!=JailEscrowJournal.State.RESTORE_STARTED)throw new IOException("No saved respawn completion to confirm");
            if(e.state()==JailEscrowJournal.State.HELD)e=j.transition(e.id(),JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
            if(e.state()!=JailEscrowJournal.State.RESTORE_STARTED)throw new IOException("Uncertain escape phase; no death replay");
            c.inventory().persistAndVerify();c.origin().persistAndVerify();var disk=JailVanillaPort.readSavedPlayer(p);
            var l=c.assignment().jail();var pos=disk.getList("Pos",6);
            if(disk.getFloat("Health")<=0||!disk.getCompound("NeoForgeData").hasUUID(RESPAWN)||!e.id().equals(disk.getCompound("NeoForgeData").getUUID(RESPAWN))||!disk.getString("Dimension").equals(l.dim())||pos.size()!=3||pos.getDouble(0)!=l.x()||pos.getDouble(1)!=l.y()||pos.getDouble(2)!=l.z())throw new IOException("Native respawn save not verified");
            if(!explicit)checkpoint.saved();j.transition(e.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
        }
    }
    static void recover(ServerPlayer p)throws IOException {complete(p,true);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){
        if(event.isEndConquered()||!(event.getEntity() instanceof ServerPlayer p))return;
        try{UUID tx=JailIntegrated.activeTransaction(p.server,p.getUUID());if(tx!=null&&pending(p.server,tx))complete(p,false);}catch(IOException|RuntimeException bad){report(p,bad);}
    }
    private static void report(ServerPlayer p,Exception bad){UnyieldingQuests.LOGGER.error("Jail escape outcome for {} requires review; no death or item replay: {}",p.getUUID(),bad.toString());}
}
