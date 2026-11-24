package dev.unyielding.jail;

import java.io.IOException;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-only player escape recovery. No creature storage or client-input dependency. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class JailAutoRespawn {
    private JailAutoRespawn() {}
    private static final Set<ServerPlayer> ATTEMPTED=Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerPlayer> REPORTED=Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadLocal<ServerPlayer> AUTOMATIC=new ThreadLocal<>();
    /** Observation only. This marker grants no permission and cannot initiate a respawn. */
    public static boolean isAutomaticInvocation(ServerPlayer player){return AUTOMATIC.get()==player;}
    private static void require(boolean v,String why)throws IOException{if(!v)throw new IOException(why);}
    /** Runs before existing tick/login containment; never touches creatures or their stores. */
    static void outside(ServerPlayer p){
        if(p.server==null||!p.server.isSameThread()||!p.isAlive())return;
        var a=JailData.get(p.server).assignment(p.getUUID());if(a==null)return;
        var box=p.getBoundingBox();var j=a.jail();
        boolean outside=!p.level().dimension().location().toString().equals(j.dim())||JailEscapeBoundary.fullyOutside(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,j.x(),j.y(),j.z());
        if(!outside)return;
        // Existing attempt performs item/origin audits, safe placement, one durable death intent,
        // ordinary cancellable damage and dead-player save verification. Never bypass it.
        JailEscapeCustody.attempt(p);
    }
    private record VerifiedDeath(JailIntegrated.EscapeContext context,boolean food) {}
    private static VerifiedDeath verify(ServerPlayer p)throws Exception {
        require(p.server.isSameThread()&&p.server.getPlayerList().getPlayer(p.getUUID())==p&&!p.hasDisconnected(),"Dead player is not current/connected");
        require(!p.server.getWorldData().isHardcore(),"Hardcore respawn is not authorized");
        require(!p.isAlive()&&p.getHealth()<=0,"Not a dead player");
        var c=JailIntegrated.corpseContext(p,true);require(c!=null,"No held player custody");
        var a=c.assignment();var j=a.jail();
        require(a.equals(JailData.get(p.server).assignment(p.getUUID()))&&JailPlacement.intact(p.server,j,a.geometry())&&JailCommands.safe(p,j)&&JailCommands.claimAccess(p,j),"Unsafe or changed respawn destination");
        require(p.level().dimension().location().toString().equals(j.dim())&&p.distanceToSqr(j.x(),j.y(),j.z())<0.0001,"Corpse is not at the verified jail");
        try(var journal=new JailEscrowJournal(JailCustodyService.journalPath(p.server,"integrated-jail-v1/"+c.transaction()+"/escapes"))){
            var rows=journal.list().stream().filter(e->e.state()!=JailEscrowJournal.State.COMPLETE&&e.state()!=JailEscrowJournal.State.CANCELLED).toList();
            require(rows.size()==1,"Ambiguous or absent death attempt");var e=rows.getFirst();var t=JailNbt.decode(e.snapshot());
            require(e.state()==JailEscrowJournal.State.HELD&&e.owner().equals(p.getUUID())&&t.hasUUID("custody")&&t.getUUID("custody").equals(c.transaction())&&Arrays.equals(t.getByteArray("digest"),c.digest())&&t.getCompound("jail").equals(j.toTag()),"Death is not durably HELD and bound; no replay");
            require(p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==t.getInt("deaths_before")+1,"Death count differs from accepted attempt");
            CompoundTag disk=JailVanillaPort.readSavedPlayer(p),data=disk.getCompound("NeoForgeData");
            require(disk.getFloat("Health")<=0&&data.hasUUID(JailVanillaPort.REMOVED)&&data.getUUID(JailVanillaPort.REMOVED).equals(c.transaction())&&!data.contains(JailVanillaPort.RESTORED)&&data.getCompound(JailNativeOriginPort.RECEIPT).equals(t.getCompound("origin_receipt")),"Saved corpse or custody receipts disagree");
            if(JailXp.retained(t))JailXp.verify(p,t);
            JailFoodProgress.verifyClone(p,t,disk);
            return new VerifiedDeath(c,t.contains(JailFoodProgress.RECEIPT));
        }
    }
    /** A single native invocation after full durable-death verification; never retried in this session. */
    static void respawn(ServerPlayer p)throws Exception {
        if(JailRetainedCustody.active(p)){JailRetainedCustody.respawn(p);return;}
        var proof=verify(p);var c=proof.context();ATTEMPTED.add(p);
        JailFoodProgress.beginClone(p,proof.food());
        try{invokeNative(p);}
        finally{JailFoodProgress.endClone();}
        ServerPlayer next=p.server.getPlayerList().getPlayer(p.getUUID());
        require(next!=null&&next!=p&&next.connection.player==next&&next.isAlive()&&!next.hasDisconnected(),"Native replacement was not rebound");
        var jail=c.assignment().jail();
        require(c.assignment().equals(JailData.get(next.server).assignment(next.getUUID()))&&next.level().dimension().location().toString().equals(jail.dim())&&next.distanceToSqr(jail.x(),jail.y(),jail.z())<0.0001,"Replacement did not respawn inside jail");
        require(!JailEscapeCustody.pending(next.server,c.transaction()),"Respawn completion is uncertain; retain quarantine");
        MinecraftJail.LOGGER.info("JAIL_PLAYER_AUTO_RESPAWN tx={} player={} without_client_request=true",c.transaction(),next.getUUID());
    }
    static void markAttempted(ServerPlayer player){ATTEMPTED.add(player);}
    static void invokeNative(ServerPlayer player)throws IOException{
        if(AUTOMATIC.get()!=null||!player.server.isSameThread())throw new IOException("Nested/off-thread automatic respawn refused");
        AUTOMATIC.set(player);
        try{player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));}
        finally{AUTOMATIC.remove();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(ServerTickEvent.Post event){
        for(ServerPlayer p:List.copyOf(event.getServer().getPlayerList().getPlayers())){
            if(p.isAlive()||p.getHealth()>0||p.deathTime<5||p.hasDisconnected()||ATTEMPTED.contains(p)||JailData.get(p.server).assignment(p.getUUID())==null)continue;
            try{respawn(p);}
            catch(Exception failure){
                if(REPORTED.add(p))MinecraftJail.LOGGER.error("Automatic jail player respawn held for review: {} {}",p.getUUID(),failure.toString());
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ATTEMPTED.clear();REPORTED.clear();AUTOMATIC.remove();}
}

