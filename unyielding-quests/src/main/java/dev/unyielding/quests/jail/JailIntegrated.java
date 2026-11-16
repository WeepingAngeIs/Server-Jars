package dev.unyielding.quests.jail;

import dev.unyielding.quests.essentials.Loc;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;

/** Bounded command coordinator. No generic native callback support and no automatic replay.
 * New admissions remain hosted-disposable-only until the complete confinement surface is audited. */
public final class JailIntegrated {
    private JailIntegrated(){}
    public enum Boundary { PLAN_DURABLE, ITEMS_MUTATED, ITEMS_DURABLE, HUMAN_DURABLE, ASSIGNMENT_DURABLE, CONFINED, ORIGIN_RETURNED, ITEMS_RETURNED, RETURN_POSITION_DURABLE, ASSIGNMENT_REMOVED, COMPLETE }
    @FunctionalInterface public interface Checkpoint {void at(Boundary boundary)throws IOException;}
    private static Checkpoint checkpoint=b->{};
    private static final Map<MinecraftServer,JailEscrowJournal> plans=new WeakHashMap<>();
    public static void disposableCheckpoint(ServerPlayer p,Checkpoint value)throws IOException {if(!JailHumanAudit.disposable(p))throw new IOException("Fault hook unavailable");checkpoint=Objects.requireNonNull(value);}
    static Path root(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("integrated-jail-v1");}
    private static JailEscrowJournal plans(MinecraftServer s)throws IOException {
        if(!s.isSameThread()||!JailData.get(s).integratedCustodyInitialized())throw new IOException("Integrated history not durably initialized");JailEscrowJournal p=plans.get(s);if(p==null){p=new JailEscrowJournal(root(s));plans.put(s,p);}return p;
    }
    static boolean blocked(MinecraftServer s,UUID owner){
        if(!Files.exists(root(s),LinkOption.NOFOLLOW_LINKS))return plans.containsKey(s)||JailData.get(s).integratedCustodyInitialized();
        try{return plans(s).list().stream().anyMatch(e->e.owner().equals(owner)&&e.state()!=JailEscrowJournal.State.COMPLETE&&e.state()!=JailEscrowJournal.State.CANCELLED);}catch(IOException bad){return true;}
    }
    public static void stopped(MinecraftServer s){var p=plans.remove(s);if(p!=null)try{p.close();}catch(IOException ignored){}checkpoint=b->{};}
    static final String EXPANDED="native-equipment-luck-v1";
    private record Ports(JailVanillaCustody.InventoryPort inventory,JailOriginTransaction.Port origin,String contract){
        byte[] stable()throws IOException{return origin instanceof JailNativeOriginPort nativePort?nativePort.stableState():origin.current().state();}
    }
    private static Ports ports(MinecraftServer server,ServerPlayer player,UUID id,UUID retired,String contract)throws IOException {
        if(contract.equals("human-empty-v1"))return new Ports(new JailVanillaPort(player,id,retired),new JailHumanPort(player,id,retired),contract);
        if(!contract.equals(EXPANDED))throw new IOException("Unsupported custody contract");
        var inventory=new JailEquipmentPort(player,id,retired);byte[] existing=null;
        Path path=root(server).resolve(id.toString()).resolve("origins");
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))try(var origins=new JailEscrowJournal(JailCustodyService.journalPath(server,relative(id,"origins")))){if(origins.read(id)!=null)existing=JailOriginTransaction.intentForReview(origins,id,player.getUUID());}
        return new Ports(inventory,new JailNativeOriginPort(player,inventory,id,retired,existing),contract);
    }
    private static UUID retired(MinecraftServer s,UUID owner)throws IOException {UUID result=null;for(var p:plans(s).list())if(p.owner().equals(owner)&&p.state()==JailEscrowJournal.State.COMPLETE)result=p.id();return result;}
    private static JailEscrowJournal.Entry outstanding(MinecraftServer s,UUID owner)throws IOException {
        var list=plans(s).list().stream().filter(e->e.owner().equals(owner)&&e.state()!=JailEscrowJournal.State.COMPLETE&&e.state()!=JailEscrowJournal.State.CANCELLED).toList();if(list.size()>1)throw new IOException("Multiple outstanding command histories");return list.isEmpty()?null:list.getFirst();
    }
    public static UUID activeTransaction(MinecraftServer s,UUID owner)throws IOException {var e=outstanding(s,owner);return e==null?null:e.id();}
    private static void permitted(CommandSourceStack actor,ServerPlayer target)throws IOException {
        if(!actor.getServer().isSameThread()||!JailCommands.mayManage(actor)||actor.getEntity() instanceof ServerPlayer p&&JailCustodyService.quarantined(p))throw new IOException("Administrator permission lost");
        if(target.server.getPlayerList().getPlayer(target.getUUID())!=target||target.hasDisconnected()||!target.isAlive())throw new IOException("Target offline/dead; records retained");
    }
    static void auditItems(ServerPlayer p)throws IOException {
        if(p.isPassenger()||p.isVehicle()||p.isUsingItem()||!p.getShoulderEntityLeft().isEmpty()||!p.getShoulderEntityRight().isEmpty())throw new IOException("Active use, mounts or shoulder entities unsupported");
        if(!p.getMainHandItem().isEmpty()||!p.getOffhandItem().isEmpty())throw new IOException("Selected hand/offhand must be empty");for(var stack:p.getArmorSlots())if(!stack.isEmpty())throw new IOException("Equipped armor requires another audited adapter");
        for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.isEmpty())continue;
            if(!BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("minecraft")||!stack.getItem().getClass().getName().startsWith("net.minecraft.world.item.")||stack.getCount()<1||stack.getCount()>stack.getMaxStackSize())throw new IOException("Unsupported packed item provider/count");}
    }
    private static void saveAssignments(MinecraftServer s)throws IOException {
        CompoundTag before=JailData.get(s).save(new CompoundTag(),s.registryAccess());s.overworld().getDataStorage().save();
        try{net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}catch(RuntimeException bad){throw new IOException("Assignment IO failed",bad);}
        Path file=s.getWorldPath(LevelResource.ROOT).resolve("data/unyielding_jail.dat");JailVanillaPort.force(file);CompoundTag disk;try(var input=Files.newInputStream(file)){disk=NbtIo.readCompressed(input,NbtAccounter.create(16L*1024*1024));}
        if(!before.equals(disk.getCompound("data"))||!before.equals(JailData.get(s).save(new CompoundTag(),s.registryAccess())))throw new IOException("Assignment save mismatch");
    }
    private static void moveSaved(ServerPlayer p,Loc to,JailVanillaCustody.InventoryPort inventory)throws IOException {
        if(!JailEnforcement.teleportAuthorized(p,to))throw new IOException("Transfer failed; restrictions retained");inventory.persistAndVerify();CompoundTag disk=JailVanillaPort.readSavedPlayer(p);ListTag pos=disk.getList("Pos",6);
        if(!disk.getString("Dimension").equals(to.dim())||pos.size()!=3||pos.getDouble(0)!=to.x()||pos.getDouble(1)!=to.y()||pos.getDouble(2)!=to.z())throw new IOException("Transfer player save did not verify");
    }
    private static JailData.Assignment assignment(CompoundTag plan){return new JailData.Assignment(Loc.fromTag(plan.getCompound("jail")),Loc.fromTag(plan.getCompound("prior")),plan.getInt("geometry"));}
    private static void requireAssignment(MinecraftServer s,UUID owner,CompoundTag plan,boolean allowAbsent)throws IOException {var actual=JailData.get(s).assignment(owner);if(!(allowAbsent&&actual==null)&&!assignment(plan).equals(actual))throw new IOException("Assignment changed or missing; never overwrite another assignment");}
    private static String relative(UUID id,String kind){return "integrated-jail-v1/"+id+"/"+kind;}
    private static JailEscrowJournal journal(MinecraftServer s,UUID id,String kind)throws IOException{return new JailEscrowJournal(JailCustodyService.journalPath(s,relative(id,kind)));}
    private static JailCustodyLedger.Binding binding(MinecraftServer s,JailEscrowJournal.Entry entry)throws IOException {
        CompoundTag plan=JailNbt.decode(entry.snapshot());var b=JailCustodyService.ledger(s).get(entry.id());
        if(b==null||!b.owner().equals(entry.owner())||!b.items().equals(relative(entry.id(),"items"))||!b.origins().equals(relative(entry.id(),"origins"))||!Arrays.equals(b.digest(),plan.getByteArray("digest")))throw new IOException("Command/custody binding changed");return b;
    }
    private static BlockPos anchor(MinecraftServer s,Loc dest,Loc prior)throws IOException {
        var ledger=JailCustodyService.ledger(s);var level=dest.level(s);BlockPos base=BlockPos.containing(dest.x()-3.5,dest.y()-1,dest.z()-3.5);
        for(int x=8;x<=11;x++)for(int z=1;z<=5;z++){BlockPos pos=base.offset(x,1,z);var a=new JailCustodyLedger.Anchor(dest.dim(),pos.getX(),pos.getY(),pos.getZ());
            if(!pos.equals(BlockPos.containing(prior.x(),prior.y(),prior.z()))&&level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)&&level.getBlockState(pos).isAir()&&ledger.list().stream().noneMatch(b->b.anchor().equals(a)))return pos;}
        throw new IOException("No unused custody anchor; prior bindings retained");
    }
    /** Returns false only when there is no managed record; legacy releases remain separate. */
    public static boolean managed(MinecraftServer s,UUID owner)throws IOException {
        if(!Files.exists(root(s),LinkOption.NOFOLLOW_LINKS))return false;return outstanding(s,owner)!=null;
    }
    public static void admit(CommandSourceStack actor,ServerPlayer target)throws IOException {
        if(actor.getEntity()==target||JailCommands.protectedFromJail(target))throw new IOException("Administrators/self cannot be jailed");
        permitted(actor,target);if(!JailHumanAudit.disposable(target))throw new IOException("Production admissions remain disabled; native callback/death surfaces not certified");
        var s=target.server;var records=plans(s);if(outstanding(s,target.getUUID())!=null||JailCustodyService.blocked(s,target.getUUID())||JailData.get(s).assignment(target.getUUID())!=null)throw new IOException("Existing assignment/custody must be recovered first");
        var data=JailData.get(s);Loc dest=data.location(),prior=Loc.of(target);if(data.geometry()!=JailData.INTERROGATION_CHAIR_GEOMETRY||dest==null||!JailPlacement.intact(s,dest,data.geometry())||!JailCommands.safe(target,dest)||!JailCommands.safe(target,prior)||actor.getEntity() instanceof ServerPlayer p&&!JailCommands.claimAccess(p,dest))throw new IOException("Jail/return location unavailable, unsafe or claim denied");
        UUID id=UUID.randomUUID(),retired=retired(s,target.getUUID());String contract=Boolean.getBoolean("uq.expandedJail")?EXPANDED:"human-empty-v1";
        var ports=ports(s,target,id,retired,contract);var inventory=ports.inventory();var nativePort=ports.origin();if(contract.equals(EXPANDED))inventory.validateRemoval();else auditItems(target);var human=nativePort.current();byte[] stable=ports.stable();
        var source=inventory.current();if(source.empty()||source.removedBy()!=null||source.restoredBy()!=null||human.transaction()!=null)throw new IOException("Source empty or unresolved receipt");
        // Save/readback BEFORE the plan: no custody has started and no mutation occurred.
        inventory.persistAndVerify();nativePort.persistAndVerify();saveAssignments(s);if(!Arrays.equals(source.inventory(),inventory.current().inventory())||!Arrays.equals(stable,ports.stable()))throw new IOException("Source changed during baseline save");BlockPos anchor=anchor(s,dest,prior);
        CompoundTag plan=new CompoundTag();plan.putString("contract",contract);plan.put("prior",prior.toTag());plan.put("jail",dest.toTag());plan.putInt("geometry",data.geometry());plan.putByteArray("digest",JailOriginTransaction.hash(source.inventory()));plan.putByteArray("human",human.state());if(retired!=null)plan.putUUID("retired",retired);
        plan.putInt("x",anchor.getX());plan.putInt("y",anchor.getY());plan.putInt("z",anchor.getZ());plan.putString("actor",actor.getTextName());
        plan.putByteArray("origin_before",stable);
        records.begin(id,target.getUUID(),JailNbt.encode(plan));checkpoint.at(Boundary.PLAN_DURABLE);
        // Plan itself quarantines, even if binding or journal provisioning fails.
        Path directory=root(s).resolve(id.toString());Files.createDirectory(directory);
        // Force the UUID directory's name in its parent before any source removal.
        // Forcing items/origins and their immediate parent does not persist this ancestor entry.
        try(var metadata=java.nio.channels.FileChannel.open(root(s),StandardOpenOption.READ)){metadata.force(true);}
        JailEscrowJournal.provisionNew(directory.resolve("items"));JailEscrowJournal.provisionNew(directory.resolve("origins"));
        if(contract.equals(EXPANDED))for(String effect:List.of("remove-effects","return-effects","escapes"))JailEscrowJournal.provisionNew(directory.resolve(effect));
        JailCustodyService.ledger(s).bind(id,target.getUUID(),relative(id,"items"),relative(id,"origins"),plan.getByteArray("digest"),new JailCustodyLedger.Anchor(dest.dim(),anchor.getX(),anchor.getY(),anchor.getZ()),actor.getTextName());
        try(var items=journal(s,id,"items");var origins=journal(s,id,"origins")){
            permitted(actor,target);JailVanillaCustody.removeForDisposableTest(items,id,inventory,b->{permitted(actor,target);if(b==JailVanillaCustody.Boundary.MUTATED)checkpoint.at(Boundary.ITEMS_MUTATED);});checkpoint.at(Boundary.ITEMS_DURABLE);
            if(!Arrays.equals(stable,ports.stable()))throw new IOException("Human audit changed during removal");
            JailOriginTransaction.temporaryForDisposableTest(origins,items,id,nativePort,b->permitted(actor,target));checkpoint.at(Boundary.HUMAN_DURABLE);
        }
        records.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
        confine(actor,target,records.read(id),inventory);
        actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Jailed "+target.getGameProfile().getName()+"; custody "+id),true);
    }
    private static void confine(CommandSourceStack actor,ServerPlayer target,JailEscrowJournal.Entry entry,JailVanillaCustody.InventoryPort inventory)throws IOException {
        var s=target.server;var plan=JailNbt.decode(entry.snapshot());var intended=assignment(plan);permitted(actor,target);requireAssignment(s,target.getUUID(),plan,true);
        var b=binding(s,entry);BlockPos pos=new BlockPos(b.anchor().x(),b.anchor().y(),b.anchor().z());var level=intended.jail().level(s);
        if(!JailPlacement.intact(s,intended.jail(),intended.geometry())||!JailCommands.safe(target,intended.jail()))throw new IOException("Jail changed during admission");
        if(!JailCustodyService.physical(s,b)){if(!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir()||!level.setBlock(pos,dev.unyielding.quests.Registration.CUSTODY_VAULT.get().defaultBlockState(),3))throw new IOException("Custody anchor occupied or unavailable");}
        JailData.get(s).assign(target.getUUID(),intended);saveAssignments(s);checkpoint.at(Boundary.ASSIGNMENT_DURABLE);
        moveSaved(target,intended.jail(),inventory);checkpoint.at(Boundary.CONFINED);permitted(actor,target);plans(s).transition(entry.id(),JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
    }
    public static void release(CommandSourceStack actor,ServerPlayer target)throws IOException {
        permitted(actor,target);var e=outstanding(target.server,target.getUUID());if(e==null)throw new IOException("No managed outstanding transaction");var b=binding(target.server,e);
        JailCustodyService.action(actor,b.id(),b.revision(),"review");b=JailCustodyService.ledger(target.server).get(b.id());JailCustodyService.action(actor,b.id(),b.revision(),"restore");
    }
    record EscapeContext(UUID transaction,JailVanillaCustody.InventoryPort inventory,JailOriginTransaction.Port origin,JailData.Assignment assignment,byte[] digest){}
    static boolean expanded(MinecraftServer s,UUID id)throws IOException {var e=plans(s).read(id);return e!=null&&EXPANDED.equals(JailNbt.decode(e.snapshot()).getString("contract"));}
    /** No callback replay. The same bound, empty native stores are required even during clone/respawn. */
    static EscapeContext escapeContext(ServerPlayer p,boolean persist,boolean authoritative)throws IOException {
        if(!JailData.get(p.server).integratedCustodyInitialized())return null;
        var e=outstanding(p.server,p.getUUID());if(e==null)return null;var plan=JailNbt.decode(e.snapshot());
        if(!EXPANDED.equals(plan.getString("contract")))return null;
        if(e.state()!=JailEscrowJournal.State.HELD||!p.server.isSameThread()||authoritative&&(p.server.getPlayerList().getPlayer(p.getUUID())!=p||p.hasDisconnected()))throw new IOException("Escape custody not authoritative/held");
        var b=binding(p.server,e);if(!JailCustodyService.physical(p.server,b))throw new IOException("Escape custody interface unavailable");requireAssignment(p.server,p.getUUID(),plan,false);
        var ports=ports(p.server,p,e.id(),plan.hasUUID("retired")?plan.getUUID("retired"):null,EXPANDED);
        try(var items=journal(p.server,e.id(),"items");var origins=journal(p.server,e.id(),"origins")){
            var item=items.read(e.id());var origin=origins.read(e.id());
            if(item==null||origin==null||item.state()!=JailEscrowJournal.State.HELD||origin.state()!=JailEscrowJournal.State.HELD||!Arrays.equals(b.digest(),JailOriginTransaction.hash(item.snapshot())))throw new IOException("Escape item/origin phase not held");
            byte[] intent=JailOriginTransaction.intentForBoundReview(origins,items,e.id(),p.getUUID());var live=ports.inventory().current();
            if(!live.empty()||!e.id().equals(live.removedBy())||live.restoredBy()!=null||!ports.origin().isTemporary(intent,e.id(),ports.origin().current()))throw new IOException("Escape requires empty marked stores and verified temporary Human");
            if(persist){ports.inventory().persistAndVerify();ports.origin().persistAndVerify();}
            var disk=ports.inventory().durable();var nativeLive=ports.origin().current();var nativeDisk=ports.origin().durable();
            if(!disk.empty()||!e.id().equals(disk.removedBy())||disk.restoredBy()!=null||!Arrays.equals(live.inventory(),disk.inventory())||!Arrays.equals(nativeLive.state(),nativeDisk.state())||!ports.origin().isTemporary(intent,e.id(),nativeDisk))throw new IOException("Escape native saves/claims are ambiguous");
        }
        return new EscapeContext(e.id(),ports.inventory(),ports.origin(),assignment(plan),b.digest());
    }
    static JailCustodyService.VerifiedAdapter adapter(MinecraftServer s,JailCustodyLedger.Binding b)throws IOException {
        var e=plans(s).read(b.id());if(e==null||!e.owner().equals(b.owner())||!binding(s,e).equals(b)||!Set.of("human-empty-v1",EXPANDED).contains(JailNbt.decode(e.snapshot()).getString("contract")))throw new IOException("No audited integrated command binding");
        ServerPlayer p=s.getPlayerList().getPlayer(b.owner());if(p==null)throw new IOException("Player offline; custody retained");CompoundTag plan=JailNbt.decode(e.snapshot());UUID retired=plan.hasUUID("retired")?plan.getUUID("retired"):null;
        final Ports ports=ports(s,p,b.id(),retired,plan.getString("contract"));
        return new JailCustodyService.VerifiedAdapter(){
            CommandSourceStack actor;
            public void authorize(CommandSourceStack source)throws IOException{permitted(source,p);actor=source;}
            private void authority()throws IOException{if(actor==null)throw new IOException("No command authority");permitted(actor,p);}
            final JailVanillaCustody.InventoryPort inventory=ports.inventory();final JailOriginTransaction.Port human=ports.origin();
            public String descriptor(){return ports.contract()+" exact-slot command coordinator";}
            private boolean originComplete(JailEscrowJournal origins,JailEscrowJournal items)throws IOException {
                var o=origins.read(b.id());if(o==null||o.state()!=JailEscrowJournal.State.COMPLETE||!o.owner().equals(b.owner()))return false;byte[] intent=JailOriginTransaction.intentForBoundReview(origins,items,b.id(),b.owner());var live=human.current();var disk=human.durable();return human.isOriginal(intent,b.id(),live)&&Arrays.equals(live.state(),disk.state());
            }
            public JailCustodyService.Health inspect()throws IOException {
                if(!inventory.online())return JailCustodyService.Health.REVIEW_REQUIRED;var now=plans(s).read(b.id());
                if(ports.contract().equals(EXPANDED)&&JailEscapeCustody.pending(s,b.id()))return JailCustodyService.Health.REVIEW_REQUIRED;
                try(var items=journal(s,b.id(),"items");var origins=journal(s,b.id(),"origins")){
                    var item=items.read(b.id());if(item==null||!item.owner().equals(b.owner())||!Arrays.equals(JailOriginTransaction.hash(item.snapshot()),b.digest()))throw new IOException("Custody source binding changed");
                    if(now.state()==JailEscrowJournal.State.COMPLETE)return item.state()==JailEscrowJournal.State.COMPLETE&&originComplete(origins,items)&&JailData.get(s).assignment(b.owner())==null?JailCustodyService.Health.COMPLETE:JailCustodyService.Health.REVIEW_REQUIRED;
                    requireAssignment(s,b.owner(),plan,false);if(now.state()!=JailEscrowJournal.State.HELD||!JailCustodyService.physical(s,b)||JailVanillaCustody.inspect(items,b.id(),inventory)!=JailVanillaCustody.Inspection.HELD_VERIFIED)return JailCustodyService.Health.REVIEW_REQUIRED;
                    var state=JailOriginTransaction.inspect(origins,items,b.id(),human);return state==JailOriginTransaction.Inspection.TEMPORARY_VERIFIED||state==JailOriginTransaction.Inspection.COMPLETE_RECORDED&&originComplete(origins,items)?JailCustodyService.Health.READY:JailCustodyService.Health.REVIEW_REQUIRED;
                }
            }
            public void restore()throws IOException {
                authority();
                Loc prior=assignment(plan).prior();if(!JailCommands.safe(p,prior)||actor.getEntity() instanceof ServerPlayer staff&&!JailCommands.claimAccess(staff,prior))throw new IOException("Return position unavailable or claim denied");
                if(inspect()!=JailCustodyService.Health.READY)throw new IOException("Command release requires verified active custody");
                plans(s).transition(b.id(),JailEscrowJournal.State.HELD,JailEscrowJournal.State.RESTORE_STARTED);
                try(var items=journal(s,b.id(),"items");var origins=journal(s,b.id(),"origins")){
                    JailOriginTransaction.originalForDisposableTest(origins,items,b.id(),human,phase->authority());checkpoint.at(Boundary.ORIGIN_RETURNED);authority();
                    JailOriginTransaction.assertInventoryReleaseReady(origins,items,b.id(),human,false);
                    JailVanillaCustody.restoreForDisposableTest(items,b.id(),guarded(items,origins),phase->authority());checkpoint.at(Boundary.ITEMS_RETURNED);authority();
                    finish(items,origins);
                }
            }
            private JailVanillaCustody.InventoryPort guarded(JailEscrowJournal items,JailEscrowJournal origins){return new JailVanillaCustody.InventoryPort(){
                public UUID owner(){return inventory.owner();}public boolean online(){return inventory.online();}public JailVanillaCustody.View current()throws IOException{return inventory.current();}public JailVanillaCustody.View durable()throws IOException{return inventory.durable();}public void persistAndVerify()throws IOException{inventory.persistAndVerify();}public void clearAndMark(UUID tx)throws IOException{throw new IOException("Release adapter cannot remove");}public void validateSavedOutcome(UUID tx,boolean restored)throws IOException{inventory.validateSavedOutcome(tx,restored);}
                public JailVanillaCustody.View planRestore(byte[] bytes,UUID tx)throws IOException{JailOriginTransaction.assertInventoryReleaseReady(origins,items,tx,human,false);return inventory.planRestore(bytes,tx);}public void restoreExact(byte[] bytes,UUID tx)throws IOException{JailOriginTransaction.assertInventoryReleaseReady(origins,items,tx,human,true);inventory.restoreExact(bytes,tx);}
            };}
            private void finish(JailEscrowJournal items,JailEscrowJournal origins)throws IOException {
                authority();
                if(items.read(b.id()).state()!=JailEscrowJournal.State.COMPLETE||!originComplete(origins,items))throw new IOException("Native returns incomplete; restrictions retained");requireAssignment(s,b.owner(),plan,true);
                Loc prior=assignment(plan).prior();if(!JailCommands.safe(p,prior)||actor.getEntity() instanceof ServerPlayer staff&&!JailCommands.claimAccess(staff,prior))throw new IOException("Return location unavailable or claim denied; items returned but quarantine retained");
                moveSaved(p,prior,inventory);checkpoint.at(Boundary.RETURN_POSITION_DURABLE);authority();JailData.get(s).remove(b.owner());saveAssignments(s);checkpoint.at(Boundary.ASSIGNMENT_REMOVED);
                plans(s).transition(b.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);checkpoint.at(Boundary.COMPLETE);
            }
            public void confirmSaved()throws IOException {
                authority();boolean confirmed=false;
                try(var items=journal(s,b.id(),"items");var origins=journal(s,b.id(),"origins")){
                    var itemStatus=JailVanillaCustody.inspect(items,b.id(),inventory);
                    if(itemStatus==JailVanillaCustody.Inspection.REMOVAL_PERSISTED_REVIEW){JailVanillaCustody.confirmRemoved(items,b.id(),inventory);confirmed=true;}
                    var originStatus=JailOriginTransaction.inspect(origins,items,b.id(),human);
                    if(originStatus==JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW||originStatus==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW){JailOriginTransaction.confirmSavedForDisposableTest(origins,items,b.id(),human);confirmed=true;}
                    if(itemStatus==JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW&&originComplete(origins,items)){JailVanillaCustody.confirmRestoredForDisposableTest(items,b.id(),guarded(items,origins));confirmed=true;}
                }
                if(!confirmed)throw new IOException("No exact persisted native phase to confirm; use explicit coordinated recovery for command continuation");
            }
            public void recoverVerified()throws IOException {
                authority();
                if(ports.contract().equals(EXPANDED)&&JailEscapeCustody.pending(s,b.id())){JailEscapeCustody.recover(p);return;}
                var now=plans(s).read(b.id());try(var items=journal(s,b.id(),"items");var origins=journal(s,b.id(),"origins")){
                    if(now.state()==JailEscrowJournal.State.RESTORE_STARTED){
                        var status=JailOriginTransaction.inspect(origins,items,b.id(),human);if(status==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW)JailOriginTransaction.confirmSavedForDisposableTest(origins,items,b.id(),human);
                        if(!originComplete(origins,items))throw new IOException("No saved completed origin return; no replay authorized");
                        if(items.read(b.id()).state()==JailEscrowJournal.State.HELD){JailOriginTransaction.assertInventoryReleaseReady(origins,items,b.id(),human,false);JailVanillaCustody.restoreForDisposableTest(items,b.id(),guarded(items,origins),phase->authority());}
                        if(JailVanillaCustody.inspect(items,b.id(),inventory)==JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW)JailVanillaCustody.confirmRestoredForDisposableTest(items,b.id(),guarded(items,origins));
                        if(items.read(b.id()).state()!=JailEscrowJournal.State.COMPLETE)throw new IOException("No saved completed item return; no replay authorized");finish(items,origins);return;
                    }
                    if(now.state()==JailEscrowJournal.State.REMOVAL_STARTED||now.state()==JailEscrowJournal.State.PREPARED){
                        if(JailVanillaCustody.inspect(items,b.id(),inventory)==JailVanillaCustody.Inspection.REMOVAL_PERSISTED_REVIEW)JailVanillaCustody.confirmRemoved(items,b.id(),inventory);
                        if(JailVanillaCustody.inspect(items,b.id(),inventory)!=JailVanillaCustody.Inspection.HELD_VERIFIED)throw new IOException("No saved held inventory; no removal replay authorized");
                        var status=JailOriginTransaction.inspect(origins,items,b.id(),human);
                        if(status==JailOriginTransaction.Inspection.NO_RECORD&&Arrays.equals(plan.getByteArray("origin_before"),ports.stable())&&Arrays.equals(human.current().state(),human.durable().state()))JailOriginTransaction.temporaryForDisposableTest(origins,items,b.id(),human,phase->authority());
                        if(status==JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW)JailOriginTransaction.confirmSavedForDisposableTest(origins,items,b.id(),human);
                        if(JailOriginTransaction.inspect(origins,items,b.id(),human)!=JailOriginTransaction.Inspection.TEMPORARY_VERIFIED)throw new IOException("Origin receipt absent/uncertain; no switch replay authorized");
                        if(now.state()==JailEscrowJournal.State.PREPARED)now=plans(s).transition(b.id(),JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
                        confine(actor,p,now,inventory);return;
                    }
                    if(now.state()==JailEscrowJournal.State.COMPLETE&&items.read(b.id()).state()==JailEscrowJournal.State.COMPLETE&&originComplete(origins,items)&&JailData.get(s).assignment(b.owner())==null)return;
                    throw new IOException("No exact saved command outcome to confirm; records retained");
                }
            }
        };
    }
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("jailintegrated").requires(s->s.getEntity()==null&&s.hasPermission(2))
            .then(Commands.literal("init").executes(c->{try{var s=c.getSource().getServer();JailCustodyService.ledger(s);if(JailData.get(s).integratedCustodyInitialized()||Files.exists(root(s),LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing integrated history must not be reset");JailEscrowJournal.provisionNew(root(s));JailData.get(s).markIntegratedCustodyInitialized();saveAssignments(s);plans(s);c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Integrated history initialized; production admissions remain disabled."),true);return 1;}catch(IOException bad){return dev.unyielding.quests.essentials.Essentials.fail(c.getSource(),bad.getMessage());}}))
            .then(Commands.literal("cancel").then(Commands.argument("transaction",UuidArgument.uuid()).executes(c->{try{cancel(c.getSource(),UuidArgument.getUuid(c,"transaction"));return 1;}catch(IOException bad){return dev.unyielding.quests.essentials.Essentials.fail(c.getSource(),bad.getMessage());}}))));
    }
    /** Console-only cancellation after proving no native removal/switch ever started. No item insertion. */
    private static void cancel(CommandSourceStack actor,UUID id)throws IOException {
        var s=actor.getServer();var e=plans(s).read(id);if(e==null||e.state()!=JailEscrowJournal.State.PREPARED&&e.state()!=JailEscrowJournal.State.CANCELLED)throw new IOException("Only an unchanged prepared admission can be cancelled");
        ServerPlayer p=s.getPlayerList().getPlayer(e.owner());if(p==null)throw new IOException("Player offline");permitted(actor,p);CompoundTag plan=JailNbt.decode(e.snapshot());UUID retired=plan.hasUUID("retired")?plan.getUUID("retired"):null;
        var ports=ports(s,p,id,retired,plan.getString("contract"));var inventory=ports.inventory();var human=ports.origin();var live=inventory.current();var disk=inventory.durable();
        if(live.removedBy()!=null||live.restoredBy()!=null||disk.removedBy()!=null||disk.restoredBy()!=null||!Arrays.equals(live.inventory(),disk.inventory())||!Arrays.equals(JailOriginTransaction.hash(live.inventory()),plan.getByteArray("digest"))||!Arrays.equals(ports.stable(),plan.getByteArray("origin_before"))||!Arrays.equals(human.current().state(),human.durable().state())||JailData.get(s).assignment(e.owner())!=null)throw new IOException("Prepared source is no longer exactly unchanged");
        for(String kind:List.of("items","origins")){
            Path path=root(s).resolve(id.toString()).resolve(kind);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))continue;
            try(var journal=journal(s,id,kind)){var nativeEntry=journal.read(id);if(nativeEntry==null){if(!journal.list().isEmpty())throw new IOException("Foreign native cancellation history");continue;}
                if(kind.equals("items")&&nativeEntry.state()==JailEscrowJournal.State.CANCELLED&&nativeEntry.owner().equals(e.owner())&&Arrays.equals(JailOriginTransaction.hash(nativeEntry.snapshot()),plan.getByteArray("digest")))continue;
                if(!kind.equals("items")||nativeEntry.state()!=JailEscrowJournal.State.PREPARED||JailVanillaCustody.inspect(journal,id,inventory)!=JailVanillaCustody.Inspection.PREPARED_UNCHANGED)throw new IOException("Native mutation has started/has uncertain history; cancellation refused");
                journal.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);
            }
        }
        if(e.state()==JailEscrowJournal.State.PREPARED)plans(s).transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);var binding=JailCustodyService.ledger(s).get(id);if(binding!=null&&binding.state()!=JailCustodyLedger.State.RELEASED)JailCustodyService.ledger(s).transition(id,binding.revision(),JailCustodyLedger.State.RELEASED,"console","Unchanged admission cancelled; no items or origin were transferred");
    }
}
