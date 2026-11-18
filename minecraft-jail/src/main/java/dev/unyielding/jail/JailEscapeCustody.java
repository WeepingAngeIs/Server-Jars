package dev.unyielding.jail;

import dev.unyielding.jail.MinecraftJail;
import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One durable attempt, ordinary cancellable damage, native respawn, no item copying or death replay. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class JailEscapeCustody {
    private JailEscapeCustody(){}
    private static final String RESPAWN="uq_jail_escape_respawn";
    private static final ThreadLocal<UUID> OWN_DAMAGE=new ThreadLocal<>();
    static boolean ownDamage(ServerPlayer p){return p.getUUID().equals(OWN_DAMAGE.get());}
    static boolean acceptedCorpse(ServerPlayer p){try{UUID tx=JailIntegrated.activeTransaction(p.server,p.getUUID());if(tx==null)return false;try(var j=journal(p.server,tx)){var e=pending(j);return e!=null&&(e.state()==JailEscrowJournal.State.HELD||e.state()==JailEscrowJournal.State.RESTORE_STARTED);}}catch(IOException bad){return true;}}
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
        if(JailIntegrated.retainsXp(p)&&!JailXp.retained(JailNbt.decode(e.snapshot())))throw new IOException("Retained-XP contract lacks its linked source; no clone/replay authorized");
        CompoundTag t=JailNbt.decode(e.snapshot());if(!e.owner().equals(p.getUUID())||!t.hasUUID("custody")||!t.getUUID("custody").equals(c.transaction())||!Arrays.equals(c.digest(),t.getByteArray("digest"))||!c.assignment().jail().toTag().equals(t.getCompound("jail")))throw new IOException("Escape/custody binding changed");
    }
    private static void atJail(ServerPlayer p,JailIntegrated.EscapeContext c)throws IOException {destination(p,c);var l=c.assignment().jail();if(!p.level().dimension().location().toString().equals(l.dim())||p.distanceToSqr(l.x(),l.y(),l.z())>0.0001)throw new IOException("Respawn is not at verified jail");}
    private static void deathSurface(ServerPlayer p,boolean retainXp)throws IOException {
        if(retainXp)JailXp.live(p);
        if(!retainXp&&(p.totalExperience!=0||p.experienceLevel!=0||p.experienceProgress!=0)||!p.getActiveEffects().isEmpty()||p.getAbsorptionAmount()!=0)throw new IOException("Escape XP/potion custody unsupported; no lethal damage applied");
        var abilities=p.getAbilities();if(abilities.mayfly||abilities.flying||abilities.instabuild||abilities.invulnerable||!abilities.mayBuild||abilities.getFlyingSpeed()!=0.05F||abilities.getWalkingSpeed()!=0.1F)throw new IOException("Nondefault flight/creative abilities are not certified for death");
        var defaults=net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(net.minecraft.world.entity.EntityType.PLAYER);
        for(var raw:p.getAttributes().save()){
            var row=(CompoundTag)raw;var holder=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(net.minecraft.resources.ResourceLocation.parse(row.getString("id"))).orElseThrow();var live=p.getAttribute(holder);
            if(live==null||!defaults.hasAttribute(holder)||Double.compare(live.getBaseValue(),defaults.getBaseValue(holder))!=0)throw new IOException("Nondefault base attribute has no death custody adapter");
            for (var modifier:live.getModifiers()) {
                if (!JailPackState.intrinsic(p,row.getString("id"),modifier.id().toString(),modifier.amount(),modifier.operation().name()))
                    throw new IOException("Unaudited modifier "+modifier.id()+" on "+row.getString("id"));
            }
        }
        CompoundTag attachments=p.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments");
        JailPackState.verifyAttachments(p,attachments);
    }
    /** Called only by accepted movement crossing or verified fully-outside player enforcement. */
    static void attempt(ServerPlayer p){
        try{
            if(!JailData.get(p.server).integratedCustodyInitialized())return;UUID active=JailIntegrated.activeTransaction(p.server,p.getUUID());
            if(active==null||!JailIntegrated.expanded(p.server,active)||pending(p.server,active))return;
            if(!p.isAlive()||p.isInvulnerableTo(p.damageSources().generic()))return;var c=JailIntegrated.escapeContext(p,true,true);if(c==null)return;destination(p,c);
            boolean retainXp=JailIntegrated.retainsXp(p);deathSurface(p,retainXp);
            if(JailAdmissions.protectedCustody(p))JailAdmissions.boundary(p,retainXp);
            try(var j=journal(p.server,c.transaction())){
                if(pending(j)!=null)return;
                CompoundTag t=new CompoundTag();t.putUUID("custody",c.transaction());t.putByteArray("digest",c.digest());t.put("jail",c.assignment().jail().toTag());t.putInt("selected",p.getInventory().selected);
                JailPackState.recordClone(p,t);
                t.put("origin_receipt",p.getPersistentData().getCompound(JailNativeOriginPort.RECEIPT).copy());
                int deaths=p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));t.putInt("deaths_before",deaths);
                if(retainXp){t.putString("xp_policy",JailXp.POLICY);t.put("xp",JailXp.encode(JailXp.live(p)));}
                UUID id=UUID.randomUUID();j.begin(id,p.getUUID(),JailNbt.encode(t));
                if(!JailEnforcement.teleportAuthorized(p,c.assignment().jail()))throw new IOException("Escape containment transfer refused");
                c.inventory().persistAndVerify();c.origin().persistAndVerify();atJail(p,c);
                j.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
                // Preflight above still refuses invulnerability. Native kill damage handles respawn
                // grace for repeat escapes; damage/death cancellation remains honored. No replay.
                if(retainXp)JailXp.begin(p,t.getCompound("xp"));
                UUID previous=OWN_DAMAGE.get();OWN_DAMAGE.set(p.getUUID());try{p.hurt(p.damageSources().genericKill(),1000.0F);}finally{
                    if(previous==null)OWN_DAMAGE.remove();else OWN_DAMAGE.set(previous);
                    if(retainXp)JailXp.end(p,p.getHealth()<=0&&!p.isAlive()&&p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==deaths+1);
                }
                if(p.hasDisconnected()||p.getHealth()>0||p.isAlive()||p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))!=deaths+1)throw new IOException("Escape death prevented/unverified; explicit review required, no retry");
                c.inventory().persistAndVerify();c.origin().persistAndVerify();JailIntegrated.escapeContext(p,false,true);
                if(retainXp)JailXp.verify(p,t);
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
                if(JailXp.retained(t)){
                    JailXp.verify(old,t);
                    // Force a single delivery intent before touching the replacement's XP.
                    j.transition(e.id(),JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
                    JailXp.at(next,JailXp.Boundary.BEFORE_COPY);JailXp.cloneTo(old,next,e.id(),t);
                }
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
            CompoundTag attempt=JailNbt.decode(e.snapshot());if(JailXp.retained(attempt))JailXp.verifyClone(p,e.id(),attempt);
            if(explicit&&e.state()!=JailEscrowJournal.State.RESTORE_STARTED)throw new IOException("No saved respawn completion to confirm");
            if(e.state()==JailEscrowJournal.State.HELD)e=j.transition(e.id(),JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
            if(e.state()!=JailEscrowJournal.State.RESTORE_STARTED)throw new IOException("Uncertain escape phase; no death replay");
            c.inventory().persistAndVerify();c.origin().persistAndVerify();var disk=JailVanillaPort.readSavedPlayer(p);
            JailPackState.verifyClone(p,attempt,disk);
            var l=c.assignment().jail();var pos=disk.getList("Pos",6);
            if(disk.getFloat("Health")<=0||!disk.getCompound("NeoForgeData").hasUUID(RESPAWN)||!e.id().equals(disk.getCompound("NeoForgeData").getUUID(RESPAWN))||!disk.getString("Dimension").equals(l.dim())||pos.size()!=3||pos.getDouble(0)!=l.x()||pos.getDouble(1)!=l.y()||pos.getDouble(2)!=l.z())throw new IOException("Native respawn save not verified");
            if(JailXp.retained(attempt)){
                if(!JailXp.read(disk).equals(JailXp.read(attempt.getCompound("xp")))||!disk.getCompound("NeoForgeData").getCompound(JailXp.RECEIPT).equals(p.getPersistentData().getCompound(JailXp.RECEIPT)))throw new IOException("XP clone save/receipt not verified");
            }
            if(!explicit)checkpoint.saved();j.transition(e.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
        }
    }
    static void recover(ServerPlayer p)throws IOException {complete(p,true);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){
        if(event.isEndConquered()||!(event.getEntity() instanceof ServerPlayer p))return;
        if(!JailData.get(p.server).integratedCustodyInitialized())return;
        try{UUID tx=JailIntegrated.activeTransaction(p.server,p.getUUID());if(tx!=null&&JailIntegrated.expanded(p.server,tx)&&pending(p.server,tx))complete(p,false);}catch(IOException|RuntimeException bad){report(p,bad);}
    }
    private static void report(ServerPlayer p,Exception bad){MinecraftJail.LOGGER.error("Jail escape outcome for {} requires review; no death or item replay: {}",p.getUUID(),bad.toString());}
}
