package dev.unyielding.jail;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Confinement with retained inventory. Journals hold hashes and origin intent, never returnable item stacks. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class JailRetainedCustody {
    static final String CONTRACT="native-retained-inventory-v1";
    private static final String INVENTORY="retained_inventory_digest", BEFORE="origin_before_digest", RESPAWN="uq_jail_escape_respawn";
    private static final String DAMAGE_TICK="insanelib_own_damage_tick";
    private static final ThreadLocal<DeathScope> DEATH=new ThreadLocal<>();
    private static final ThreadLocal<DeathScope> CLONE=new ThreadLocal<>();
    private static final Map<MinecraftServer,JailEscrowJournal> RECORDS=new WeakHashMap<>();
    private static final class DeathScope {
        final ServerPlayer player;final Context context;final JailEscrowJournal.Entry attempt;int nativeRetentions;
        DeathScope(ServerPlayer player,Context context,JailEscrowJournal.Entry attempt){this.player=player;this.context=context;this.attempt=attempt;}
    }
    private record Context(JailEscrowJournal.Entry entry,CompoundTag plan,JailRetainedInventory inventory,JailRetainedOriginPort origin){
        UUID id(){return entry.id();}JailData.Assignment assignment(){return JailIntegrated.assignment(plan);}
    }
    private JailRetainedCustody(){}
    private static void require(boolean ok,String reason)throws IOException{if(!ok)throw new IOException(reason);}
    static boolean contract(CompoundTag plan){return CONTRACT.equals(plan.getString("contract"))
            && plan.contains("confiscate_inventory",Tag.TAG_BYTE)&&plan.getByte("confiscate_inventory")==0;}
    public static boolean active(ServerPlayer player)throws IOException{
        if(player.server==null)return false;
        var e=outstanding(player.server,player.getUUID());
        return e!=null&&contract(JailNbt.decode(e.snapshot()));
    }
    static Path root(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("retained-jail-v1");}
    private static JailEscrowJournal plans(MinecraftServer server)throws IOException{
        require(server.isSameThread()&&JailData.get(server).retainedCustodyInitialized(),"Retained confinement history is not initialized");
        var records=RECORDS.get(server);if(records==null){records=new JailEscrowJournal(root(server));RECORDS.put(server,records);}return records;
    }
    static List<JailEscrowJournal.Entry> records(MinecraftServer server)throws IOException{
        if(!JailData.get(server).retainedCustodyInitialized()&&Files.notExists(root(server),LinkOption.NOFOLLOW_LINKS))return List.of();
        return plans(server).list();
    }
    static JailEscrowJournal.Entry record(MinecraftServer server,UUID id)throws IOException{
        for(var entry:records(server))if(entry.id().equals(id))return entry;return null;
    }
    static byte[] completedOrigin(MinecraftServer server,UUID id,UUID owner)throws IOException{
        var parent=record(server,id);JailIntegrated.requireCompleted(parent,id,owner);
        require(contract(JailNbt.decode(parent.snapshot())),"Unknown completed retained confinement");
        try(var origins=journal(server,id,"origins")){
            JailIntegrated.requireCompleted(origins.read(id),id,owner);
            return JailOriginTransaction.intentForBoundReview(origins,plans(server),id,owner);
        }
    }
    static JailEscrowJournal.Entry outstanding(MinecraftServer server,UUID owner)throws IOException{
        var entries=records(server).stream().filter(e->e.owner().equals(owner)&&!JailEscrowJournal.terminal(e.state())).toList();
        require(entries.size()<=1,"Multiple retained confinement records require review");return entries.isEmpty()?null:entries.getFirst();
    }
    static boolean blocked(MinecraftServer server,UUID owner){try{return outstanding(server,owner)!=null;}catch(IOException|RuntimeException uncertain){return true;}}
    static void stopped(MinecraftServer server){var records=RECORDS.remove(server);if(records!=null)try{records.close();}catch(IOException ignored){}DEATH.remove();CLONE.remove();}
    private static void initialize(MinecraftServer server)throws IOException{
        if(JailData.get(server).retainedCustodyInitialized()){plans(server);return;}
        require(Files.notExists(root(server),LinkOption.NOFOLLOW_LINKS),"Existing retained history must not be reset");
        JailEscrowJournal.provisionNew(root(server));JailData.get(server).markRetainedCustodyInitialized();JailIntegrated.saveAssignments(server);plans(server);
    }
    private static JailEscrowJournal journal(MinecraftServer server,UUID tx,String kind)throws IOException{
        return new JailEscrowJournal(JailCustodyService.journalPath(server,"retained-jail-v1/"+tx+"/"+kind));
    }
    private static JailEscrowJournal.Entry pending(JailEscrowJournal journal)throws IOException{
        var entries=journal.list().stream().filter(e->!JailEscrowJournal.terminal(e.state())).toList();
        require(entries.size()<=1,"Ambiguous retained-inventory escape history");return entries.isEmpty()?null:entries.getFirst();
    }
    static boolean pending(MinecraftServer server,UUID id)throws IOException{try(var journal=journal(server,id,"escapes")){return pending(journal)!=null;}}
    private static Context context(ServerPlayer player,JailEscrowJournal.Entry entry)throws IOException{
        CompoundTag plan=JailNbt.decode(entry.snapshot());require(contract(plan)&&entry.owner().equals(player.getUUID()),"Unknown retained-inventory confinement");
        UUID retired=plan.hasUUID("retired")?plan.getUUID("retired"):null;
        var inventory=new JailRetainedInventory(player,entry.id(),retired,plan.getCompound(JailBackpacks.RECORDS));
        byte[] intent;try(var origins=journal(player.server,entry.id(),"origins")){
            intent=JailOriginTransaction.intentForBoundReview(origins,plans(player.server),entry.id(),player.getUUID());
        }
        return new Context(entry,plan,inventory,new JailRetainedOriginPort(player,inventory,entry.id(),retired,intent));
    }
    private static Context held(ServerPlayer player,boolean authoritative)throws IOException{
        require(player.server.isSameThread()&&(!authoritative||player.server.getPlayerList().getPlayer(player.getUUID())==player&&!player.hasDisconnected()),"Retained-inventory player is not authoritative");
        var entry=outstanding(player.server,player.getUUID());
        require(entry!=null&&entry.state()==JailEscrowJournal.State.HELD,"Retained confinement is not held");
        var c=context(player,entry);require(c.assignment().equals(JailData.get(player.server).assignment(player.getUUID())),"Retained assignment changed");
        c.inventory().verify(c.plan().getByteArray(INVENTORY),true);
        try(var origins=journal(player.server,c.id(),"origins")){
            var origin=origins.read(c.id());require(origin!=null&&origin.state()==JailEscrowJournal.State.HELD,"Retained origin is not held");
            byte[] intent=JailOriginTransaction.intentForBoundReview(origins,plans(player.server),c.id(),player.getUUID());
            require(c.origin().isTemporary(intent,c.id(),c.origin().current())&&c.origin().isTemporary(intent,c.id(),c.origin().durable()),"Retained origin or claims changed");
        }
        return c;
    }
    private static void prepare(ServerPlayer player,JailRetainedInventory inventory,JailRetainedOriginPort origin)throws IOException{
        require(JailHumanAudit.hasSpawnSwapContract(player),"Retained inventory requires the exact reviewed companion and Storage roster");
        JailCompanionTeleports.verifyNeutral(player);
        JailAdmissions.boundary(player,false);
        inventory.validateAdmission();origin.validateAdmission();origin.neutralBase();
    }
    static String check(ServerPlayer player)throws IOException{
        require(player.isAlive()&&!player.hasDisconnected(),"Player must be alive and online");
        boolean initialized=JailData.get(player.server).integratedCustodyInitialized();
        require(!initialized||JailIntegrated.outstanding(player.server,player.getUUID())==null,"Existing custody requires review");
        require(outstanding(player.server,player.getUUID())==null,"Existing retained confinement requires review");
        UUID tx=UUID.randomUUID(),retired=initialized?JailIntegrated.retired(player.server,player.getUUID()):null;
        var inventory=new JailRetainedInventory(player,tx,retired,null);
        prepare(player,inventory,new JailRetainedOriginPort(player,inventory,tx,retired,null));
        return "Supported retained-inventory confinement; confiscation OFF for new admissions. Independent safeguards remain required. Admissions "+(JailConfig.JAIL_CERTIFIED_ADMISSIONS.get()?"ON":"OFF")+".";
    }
    static void admit(CommandSourceStack actor,ServerPlayer player,int requestedCell)throws IOException{
        require(actor.getEntity()!=player&&!JailCommands.protectedFromJail(player),"Administrators/self cannot be jailed");
        JailIntegrated.permitted(actor,player);
        require(JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(),"Certified admissions remain disabled; changing inventory policy does not enable admission");
        JailAdmissions.contract(player);
        MinecraftServer server=player.server;var data=JailData.get(server);
        require(JailIntegrated.outstanding(server,player.getUUID())==null&&!JailCustodyService.blocked(server,player.getUUID())&&data.assignment(player.getUUID())==null,"Existing assignment/custody must be recovered first");
        require(data.geometry()==DetentionCenter.GEOMETRY&&data.location()!=null,"Retained inventory requires a placed numbered detention center");
        Loc first=data.location(),prior=Loc.of(player);int cell=JailIntegrated.roster(server).choose(requestedCell);
        Loc jail=DetentionCenter.cell(first,cell);int geometry=data.geometry();
        require(JailPlacement.intact(server,jail,geometry)&&JailCommands.safe(player,jail)&&JailCommands.safe(player,prior)
                &&(!(actor.getEntity() instanceof ServerPlayer staff)||JailCommands.claimAccess(staff,jail)),"Jail/return location unavailable, unsafe or claim denied");
        UUID id=UUID.randomUUID(),retired=JailIntegrated.retired(server,player.getUUID());
        var inventory=new JailRetainedInventory(player,id,retired,null);
        var origin=new JailRetainedOriginPort(player,inventory,id,retired,null);prepare(player,inventory,origin);
        byte[] digest=inventory.digest();
        inventory.persistAndVerify();origin.persistAndVerify();inventory.verify(digest,true);JailIntegrated.saveAssignments(server);
        require(first.equals(data.location())&&geometry==data.geometry(),"Facility changed during admission");JailIntegrated.roster(server).choose(cell);
        CompoundTag plan=new CompoundTag();plan.putString("contract",CONTRACT);plan.putBoolean("confiscate_inventory",false);
        plan.put("prior",prior.toTag());plan.put("jail",jail.toTag());plan.putInt("geometry",geometry);plan.putString("actor",actor.getTextName());
        plan.putByteArray(INVENTORY,digest);plan.putByteArray(BEFORE,JailOriginTransaction.hash(origin.current().state()));
        plan.put(JailBackpacks.RECORDS,inventory.bagDigests());if(retired!=null)plan.putUUID("retired",retired);
        // Complete origin planning is read-only and happens before durable reservation or native mutation.
        byte[] intent=origin.prepareIntent(id,JailOriginTransaction.hash(JailNbt.encode(plan)));origin.planTemporary(intent,id);
        JailCompanionTeleports.verifyNeutral(player);JailAdmissions.boundary(player,false);inventory.verify(digest,true);
        initialize(server);var records=plans(server);records.begin(id,player.getUUID(),JailNbt.encode(plan));
        Path directory=root(server).resolve(id.toString());Files.createDirectory(directory);
        try(var channel=FileChannel.open(root(server),StandardOpenOption.READ)){channel.force(true);}
        JailEscrowJournal.provisionNew(directory.resolve("origins"));JailEscrowJournal.provisionNew(directory.resolve("escapes"));
        // These are confinement reservation phases. No item journal or inventory transfer exists.
        records.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
        records.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
        try(var origins=journal(server,id,"origins")){
            JailOriginTransaction.temporaryForDisposableTest(origins,records,id,origin,phase->{
                JailIntegrated.permitted(actor,player);
                if(phase==JailOriginTransaction.Boundary.BEFORE_MUTATION)JailCompanionTeleports.verifyNeutral(player);
            });
        }
        inventory.verify(digest,true);JailIntegrated.permitted(actor,player);
        require(JailPlacement.intact(server,jail,geometry)&&JailCommands.safe(player,jail),"Jail changed during retained admission");
        var occupant=JailIntegrated.roster(server).occupant(cell);
        require(occupant!=null&&occupant.player().equals(player.getUUID()),"Retained cell reservation changed");
        data.assign(player.getUUID(),new JailData.Assignment(jail,prior,geometry,false));JailIntegrated.saveAssignments(server);
        JailIntegrated.moveSaved(player,jail,inventory);origin.persistAndVerify();inventory.verify(digest,true);
        actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Jailed "+player.getGameProfile().getName()+" in cell "+cell+"; inventory retained; confinement "+id),true);
    }
    static void release(CommandSourceStack actor,ServerPlayer player,JailEscrowJournal.Entry entry)throws IOException{
        release(actor,player,entry,false);
    }
    private static void release(CommandSourceStack actor,ServerPlayer player,JailEscrowJournal.Entry entry,boolean partialAdmission)throws IOException{
        JailIntegrated.permitted(actor,player);var c=context(player,entry);
        require(entry.state()==JailEscrowJournal.State.HELD||entry.state()==JailEscrowJournal.State.RESTORE_STARTED,"Retained confinement phase needs review");
        require(!pending(player.server,c.id()),"Retained escape outcome needs review before release");
        var assignment=JailData.get(player.server).assignment(player.getUUID());
        require(c.assignment().equals(assignment)||(partialAdmission||entry.state()==JailEscrowJournal.State.RESTORE_STARTED)&&assignment==null,"Retained assignment changed");
        Loc prior=c.assignment().prior();require(JailCommands.safe(player,prior)&&(!(actor.getEntity() instanceof ServerPlayer staff)||JailCommands.claimAccess(staff,prior)),"Return location unavailable or claim denied");
        c.inventory().verify(c.plan().getByteArray(INVENTORY),true);
        try(var origins=journal(player.server,c.id(),"origins")){
            JailRetainedRecovery.original(origins,plans(player.server),c.id(),c.origin(),step->JailIntegrated.permitted(actor,player));
        }
        if(entry.state()==JailEscrowJournal.State.HELD)plans(player.server).transition(c.id(),JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
        JailIntegrated.moveSaved(player,prior,c.inventory());c.origin().persistAndVerify();c.inventory().verify(c.plan().getByteArray(INVENTORY),true);
        JailData.get(player.server).remove(player.getUUID());JailIntegrated.saveAssignments(player.server);
        plans(player.server).transition(c.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
        actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Released "+player.getGameProfile().getName()+"; retained inventory was not transferred."),true);
    }
    /** Console-only explicit review. Missing or ambiguous native outcomes remain quarantined. */
    static void recover(CommandSourceStack actor,UUID id)throws IOException{
        recover(actor,id,false);
    }
    static void cancel(CommandSourceStack actor,UUID id)throws IOException{recover(actor,id,true);}
    private static void recover(CommandSourceStack actor,UUID id,boolean cancelOnly)throws IOException{
        require(actor.getEntity()==null&&actor.hasPermission(2),"Retained recovery requires the server console");
        var server=actor.getServer();var entry=record(server,id);require(entry!=null,"No retained confinement with this identity");
        var plan=JailNbt.decode(entry.snapshot());require(contract(plan),"Unknown retained confinement contract");
        var player=server.getPlayerList().getPlayer(entry.owner());require(player!=null,"Retained owner must be online");JailIntegrated.permitted(actor,player);
        if(JailEscrowJournal.terminal(entry.state())){
            actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Retained confinement "+id+" is already terminal; nothing was replayed."),false);return;
        }
        require(JailIntegrated.outstanding(server,entry.owner())==null,"Conflicting item custody history");
        var assigned=JailData.get(server).assignment(entry.owner());
        require(assigned==null||assigned.equals(JailIntegrated.assignment(plan)),"Another assignment must not be overwritten");
        require(!cancelOnly||assigned==null,"Active retained confinement cannot be cancelled as an unchanged admission");
        UUID retired=plan.hasUUID("retired")?plan.getUUID("retired"):null;
        var inventory=new JailRetainedInventory(player,id,retired,plan.getCompound(JailBackpacks.RECORDS));
        inventory.verify(plan.getByteArray(INVENTORY),true);
        Path originPath=root(server).resolve(id.toString()).resolve("origins");
        boolean absent=Files.notExists(originPath,LinkOption.NOFOLLOW_LINKS);
        if(absent){
            require(entry.state()==JailEscrowJournal.State.PREPARED&&assigned==null,"Missing retained origin history after reservation began");
            var origin=new JailRetainedOriginPort(player,inventory,id,retired,null);
            JailRetainedRecovery.unchanged(plans(server),null,id,origin,plan.getByteArray(BEFORE),()->unchangedBoundary(actor,player,inventory,plan));
        }else{
            JailEscrowJournal.State nativeState;
            try(var origins=journal(server,id,"origins")){var nativeEntry=origins.read(id);nativeState=nativeEntry==null?null:nativeEntry.state();
                if(nativeState==null||nativeState==JailEscrowJournal.State.PREPARED||nativeState==JailEscrowJournal.State.CANCELLED){
                    require(assigned==null,"An unchanged reservation must have no assignment");
                    byte[] intent=nativeEntry==null?null:JailOriginTransaction.intentForBoundReview(origins,plans(server),id,entry.owner());
                    var origin=new JailRetainedOriginPort(player,inventory,id,retired,intent);
                    JailRetainedRecovery.unchanged(plans(server),origins,id,origin,plan.getByteArray(BEFORE),()->unchangedBoundary(actor,player,inventory,plan));
                }
            }
            if(nativeState!=null&&nativeState!=JailEscrowJournal.State.PREPARED&&nativeState!=JailEscrowJournal.State.CANCELLED){
                require(!cancelOnly,"Native origin mutation started; use explicit retained recovery");
                var c=context(player,entry);
                if(pending(server,id)){confirmClone(actor,player,c);return;}
                // This explicit recovery also covers a saved temporary origin whose
                // assignment write never happened. Return it, never repeat admission.
                release(actor,player,entry,true);return;
            }
        }
        actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Cancelled unchanged retained admission "+id+"; no inventory or origin transfer was replayed."),true);
    }
    private static void unchangedBoundary(CommandSourceStack actor,ServerPlayer player,JailRetainedInventory inventory,CompoundTag plan)throws IOException{
        JailIntegrated.permitted(actor,player);
        require(JailData.get(player.server).assignment(player.getUUID())==null,"Retained assignment appeared during cancellation");
        inventory.verify(plan.getByteArray(INVENTORY),true);
        Path escapePath=root(player.server).resolve(inventory.transaction.toString()).resolve("escapes");
        if(Files.exists(escapePath,LinkOption.NOFOLLOW_LINKS))try(var escapes=journal(player.server,inventory.transaction,"escapes")){
            require(escapes.list().isEmpty(),"Escape activity prevents unchanged admission cancellation");
        }
    }
    /** Confirm only a replacement that already exists and was saved; never invoke respawn here. */
    private static void confirmClone(CommandSourceStack actor,ServerPlayer player,Context c)throws IOException{
        JailIntegrated.permitted(actor,player);destination(player,c,true);
        try(var escapes=journal(player.server,c.id(),"escapes")){
            var death=pending(escapes);require(death!=null&&death.state()==JailEscrowJournal.State.RESTORE_STARTED,"Retained escape has no exact saved replacement to confirm");
            boundDeath(player,c,death);var data=JailNbt.decode(death.snapshot());var disk=JailVanillaPort.readSavedPlayer(player);
            require(player.isAlive()&&player.getHealth()>0&&disk.getFloat("Health")>0
                    &&player.getPersistentData().hasUUID(RESPAWN)&&player.getPersistentData().getUUID(RESPAWN).equals(death.id())
                    &&disk.getCompound("NeoForgeData").hasUUID(RESPAWN)&&disk.getCompound("NeoForgeData").getUUID(RESPAWN).equals(death.id()),"Saved retained clone receipt is absent or foreign");
            c.inventory().verify(c.plan().getByteArray(INVENTORY),true);savedDestination(c,disk);
            try(var origins=journal(player.server,c.id(),"origins")){
                byte[] intent=JailOriginTransaction.intentForBoundReview(origins,plans(player.server),c.id(),player.getUUID());
                var live=c.origin().current();var saved=c.origin().durable();
                require(origins.read(c.id()).state()==JailEscrowJournal.State.HELD&&Arrays.equals(live.state(),saved.state())
                        &&c.origin().isTemporary(intent,c.id(),live)&&c.origin().isTemporary(intent,c.id(),saved),"Retained clone origin or unrelated saved state is not verified");
            }
            JailPackState.verifyClone(player,data,disk);JailIntegrated.permitted(actor,player);
            escapes.transition(death.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
            actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Confirmed the already-saved retained respawn; no clone or inventory transfer was repeated."),true);
        }
    }
    static boolean ownDamage(ServerPlayer player){var scope=DEATH.get();return scope!=null&&scope.player==player;}
    static boolean acceptedCorpse(ServerPlayer player){try{if(!active(player))return false;var e=outstanding(player.server,player.getUUID());try(var journal=journal(player.server,e.id(),"escapes")){var death=pending(journal);return death!=null&&(death.state()==JailEscrowJournal.State.HELD||death.state()==JailEscrowJournal.State.RESTORE_STARTED);}}catch(IOException uncertain){return true;}}
    static boolean pendingFoodDeath(ServerPlayer player)throws IOException{
        if(!active(player))return false;var e=outstanding(player.server,player.getUUID());
        try(var journal=journal(player.server,e.id(),"escapes")){var death=pending(journal);return death!=null&&JailNbt.decode(death.snapshot()).contains(JailFoodProgress.RECEIPT);}
    }
    /** Used only by vanilla's keep-inventory test during this exact, already-journaled lethal call. */
    public static boolean keepInventoryAtDrop(ServerPlayer player){
        var scope=DEATH.get();if(scope==null||scope.player!=player)return false;
        try{scope.context.inventory().verify(scope.context.plan().getByteArray(INVENTORY),false);scope.nativeRetentions++;return true;}
        catch(IOException unsafe){throw new IllegalStateException("Retained inventory changed during the verified death",unsafe);}
    }
    /** Vanilla performs its normal Inventory.replaceWith once; Jail never inserts or stages a stack. */
    public static boolean keepInventoryAtClone(ServerPlayer original,ServerPlayer replacement){
        var scope=CLONE.get();if(scope==null||scope.player!=original)return false;
        if(!JailAutoRespawn.isAutomaticInvocation(original)||original==replacement||!original.getUUID().equals(replacement.getUUID()))
            throw new IllegalStateException("Unverified retained native clone");
        try{require(Arrays.equals(scope.context.plan().getByteArray(INVENTORY),scope.context.inventory().digest()),"Retained corpse inventory changed");scope.nativeRetentions++;return true;}
        catch(IOException unsafe){throw new IllegalStateException("Retained clone source failed verification",unsafe);}
    }
    private static void destination(ServerPlayer player,Context c,boolean atJail)throws IOException{
        var a=c.assignment();var jail=a.jail();
        require(a.equals(JailData.get(player.server).assignment(player.getUUID()))&&JailPlacement.intact(player.server,jail,a.geometry())&&JailCommands.safe(player,jail)&&JailCommands.claimAccess(player,jail),"Retained jail destination is unsafe or changed");
        if(atJail)require(player.level().dimension().location().toString().equals(jail.dim())&&player.distanceToSqr(jail.x(),jail.y(),jail.z())<0.0001,"Retained player is not at the verified jail");
    }
    private static void savedDestination(Context c,CompoundTag saved)throws IOException{
        Loc jail=c.assignment().jail();var pos=saved.getList("Pos",Tag.TAG_DOUBLE);
        require(saved.getString("Dimension").equals(jail.dim())&&pos.size()==3&&pos.getDouble(0)==jail.x()
                &&pos.getDouble(1)==jail.y()&&pos.getDouble(2)==jail.z(),"Retained replacement was not saved at the exact jail destination");
    }
    static void attempt(ServerPlayer player){
        try{
            var c=held(player,true);if(pending(player.server,c.id())||!player.isAlive()||player.isInvulnerableTo(player.damageSources().generic()))return;
            destination(player,c,false);JailEscapeCustody.deathSurface(player,false);JailAdmissions.boundary(player,false);
            try(var journal=journal(player.server,c.id(),"escapes")){
                if(pending(journal)!=null)return;
                CompoundTag data=new CompoundTag();data.putUUID("custody",c.id());data.putByteArray("digest",JailOriginTransaction.hash(c.entry().snapshot()));
                data.putByteArray(INVENTORY,c.plan().getByteArray(INVENTORY));data.put("jail",c.assignment().jail().toTag());
                data.putInt("selected",player.getInventory().selected);data.putInt("deaths_before",player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS)));
                data.put("origin_receipt",player.getPersistentData().getCompound(JailNativeOriginPort.RECEIPT).copy());JailPackState.recordClone(player,data);
                if(JailHumanAudit.hasPackStateContract(player))data.putLong(DAMAGE_TICK,c.assignment().jail().level(player.server).getGameTime());
                UUID id=UUID.randomUUID();journal.begin(id,player.getUUID(),JailNbt.encode(data));
                require(JailEnforcement.teleportAuthorized(player,c.assignment().jail()),"Retained containment transfer failed");
                c.inventory().persistAndVerify();c.origin().persistAndVerify();destination(player,c,true);
                if(data.contains(DAMAGE_TICK,Tag.TAG_LONG))require(player.serverLevel().getGameTime()==data.getLong(DAMAGE_TICK),"Damage time changed");
                JailPackState.verifyAttachments(player,player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments"));
                var begun=journal.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
                require(DEATH.get()==null,"Nested retained death refused");var scope=new DeathScope(player,c,begun);DEATH.set(scope);JailFoodProgress.beginKill(player,data);
                try{player.hurt(player.damageSources().genericKill(),Math.max(1000.0F,player.getMaxHealth()+1));}
                finally{JailFoodProgress.endKill();DEATH.remove();}
                require(scope.nativeRetentions==1&&!player.isAlive()&&player.getHealth()<=0&&!player.hasDisconnected()
                        &&player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==data.getInt("deaths_before")+1,"Retained death/native inventory retention was not observed exactly once; no replay");
                c.inventory().persistAndVerify();c.origin().persistAndVerify();c.inventory().verify(c.plan().getByteArray(INVENTORY),true);
                require(JailVanillaPort.readSavedPlayer(player).getFloat("Health")<=0,"Retained corpse was not durably saved");
                journal.transition(id,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
            }
        }catch(IOException|RuntimeException failure){report(player,failure);}
    }
    private static void boundDeath(ServerPlayer player,Context c,JailEscrowJournal.Entry death)throws IOException{
        require(death!=null&&death.owner().equals(player.getUUID()),"Missing retained death record");var data=JailNbt.decode(death.snapshot());
        require(data.hasUUID("custody")&&data.getUUID("custody").equals(c.id())&&Arrays.equals(data.getByteArray("digest"),JailOriginTransaction.hash(c.entry().snapshot()))
                &&Arrays.equals(data.getByteArray(INVENTORY),c.plan().getByteArray(INVENTORY))&&data.getCompound("jail").equals(c.assignment().jail().toTag()),"Retained death binding changed");
        require(player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==data.getInt("deaths_before")+1,"Retained death count differs");
    }
    static void respawn(ServerPlayer corpse)throws Exception{
        require(!corpse.isAlive()&&corpse.getHealth()<=0&&!corpse.server.getWorldData().isHardcore(),"Retained automatic respawn requires a non-hardcore corpse");
        var c=held(corpse,true);destination(corpse,c,true);
        try(var journal=journal(corpse.server,c.id(),"escapes")){
            var death=pending(journal);require(death!=null&&death.state()==JailEscrowJournal.State.HELD,"Retained death is not held; no clone replay");boundDeath(corpse,c,death);
            CompoundTag data=JailNbt.decode(death.snapshot()),disk=JailVanillaPort.readSavedPlayer(corpse);
            require(disk.getFloat("Health")<=0&&disk.getCompound("NeoForgeData").getCompound(JailNativeOriginPort.RECEIPT).equals(data.getCompound("origin_receipt")),"Saved retained corpse/receipt disagrees");
            byte[] live=c.origin().current().state(),saved=c.origin().durable().state();
            if(!Arrays.equals(live,saved)){
                Long tick=JailEscapeCustody.verifiedDamageTick(death,corpse.getUUID(),c.id(),JailOriginTransaction.hash(c.entry().snapshot()),c.assignment().jail(),data.getInt("deaths_before")+1);
                require(JailPackState.onlyCorpseLightingDiffers(live,saved,tick),"Retained corpse has unrelated saved-state differences");
            }
            JailFoodProgress.verifyClone(corpse,data,disk);require(CLONE.get()==null,"Nested retained clone refused");
            var begun=journal.transition(death.id(),JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
            var scope=new DeathScope(corpse,c,begun);CLONE.set(scope);JailAutoRespawn.markAttempted(corpse);JailFoodProgress.beginClone(corpse,data.contains(JailFoodProgress.RECEIPT));
            try{JailAutoRespawn.invokeNative(corpse);}finally{CLONE.remove();JailFoodProgress.endClone();}
            ServerPlayer next=corpse.server.getPlayerList().getPlayer(corpse.getUUID());
            require(scope.nativeRetentions==1&&next!=null&&next!=corpse&&next.connection.player==next&&next.isAlive()&&!next.hasDisconnected(),"Retained native clone was not observed exactly once");
            var replacement=context(next,c.entry());destination(next,replacement,true);
            require(next.getPersistentData().hasUUID(RESPAWN)&&next.getPersistentData().getUUID(RESPAWN).equals(death.id()),"Retained replacement receipt missing");
            replacement.inventory().verify(c.plan().getByteArray(INVENTORY),false);replacement.inventory().persistAndVerify();replacement.origin().persistAndVerify();
            replacement.inventory().verify(c.plan().getByteArray(INVENTORY),true);CompoundTag replacementDisk=JailVanillaPort.readSavedPlayer(next);
            savedDestination(replacement,replacementDisk);JailPackState.verifyClone(next,data,replacementDisk);
            journal.transition(death.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);
            MinecraftJail.LOGGER.info("JAIL_RETAINED_AUTO_RESPAWN tx={} player={} without_client_request=true native_inventory_copy=1",c.id(),next.getUUID());
        }
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        var scope=CLONE.get();if(scope==null||event.getOriginal()!=scope.player||!event.isWasDeath()||!(event.getEntity() instanceof ServerPlayer next))return;
        try{
            var old=scope.player;var c=scope.context;var data=JailNbt.decode(scope.attempt.snapshot());
            require(JailAutoRespawn.isAutomaticInvocation(old)&&scope.nativeRetentions==1&&next.getUUID().equals(old.getUUID()),"Unverified retained native clone callback");
            var observed=new JailRetainedInventory(next,c.id(),c.plan().hasUUID("retired")?c.plan().getUUID("retired"):null,c.plan().getCompound(JailBackpacks.RECORDS));
            require(Arrays.equals(c.plan().getByteArray(INVENTORY),observed.digest())&&next.getInventory().selected==data.getInt("selected"),"Native clone changed retained slots or selection");
            var original=old.getPersistentData().getCompound(JailNativeOriginPort.RECEIPT);var target=next.getPersistentData();
            require(original.equals(data.getCompound("origin_receipt"))&&(!target.contains(JailNativeOriginPort.RECEIPT)||target.getCompound(JailNativeOriginPort.RECEIPT).equals(original))&&!target.contains(RESPAWN),"Retained origin/clone receipt conflict");
            JailFoodProgress.copyModifiers(old,next,data);
            target.put(JailNativeOriginPort.RECEIPT,original.copy());target.putUUID(RESPAWN,scope.attempt.id());
        }catch(IOException|RuntimeException failure){report(next,failure);}
    }
    private static void report(ServerPlayer player,Exception failure){MinecraftJail.LOGGER.error("Retained-inventory custody for {} requires review; no item repair or replay: {}",player.getUUID(),failure.toString());}
}
