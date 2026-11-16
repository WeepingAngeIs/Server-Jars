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
    public enum Boundary { PLAN_DURABLE, ITEMS_DURABLE, HUMAN_DURABLE, ASSIGNMENT_DURABLE, CONFINED, ORIGIN_RETURNED, ITEMS_RETURNED, RETURN_POSITION_DURABLE, ASSIGNMENT_REMOVED, COMPLETE }
    @FunctionalInterface public interface Checkpoint {void at(Boundary boundary)throws IOException;}
    private static Checkpoint checkpoint=b->{};
    private static final Map<MinecraftServer,JailEscrowJournal> plans=new WeakHashMap<>();
    public static void disposableCheckpoint(ServerPlayer p,Checkpoint value)throws IOException {if(!JailHumanAudit.disposable(p))throw new IOException("Fault hook unavailable");checkpoint=Objects.requireNonNull(value);}
    static Path root(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("integrated-jail-v1");}
    private static JailEscrowJournal plans(MinecraftServer s)throws IOException {
        if(!s.isSameThread())throw new IOException("Command off server thread");JailEscrowJournal p=plans.get(s);if(p==null){p=new JailEscrowJournal(root(s));plans.put(s,p);}return p;
    }
    static boolean blocked(MinecraftServer s,UUID owner){
        if(!Files.exists(root(s),LinkOption.NOFOLLOW_LINKS))return false;
        try{return plans(s).list().stream().anyMatch(e->e.owner().equals(owner)&&e.state()!=JailEscrowJournal.State.COMPLETE&&e.state()!=JailEscrowJournal.State.CANCELLED);}catch(IOException bad){return true;}
    }
    public static void stopped(MinecraftServer s){var p=plans.remove(s);if(p!=null)try{p.close();}catch(IOException ignored){}checkpoint=b->{};}
    private static UUID retired(MinecraftServer s,UUID owner)throws IOException {UUID result=null;for(var p:plans(s).list())if(p.owner().equals(owner)&&p.state()==JailEscrowJournal.State.COMPLETE)result=p.id();return result;}
    private static JailEscrowJournal.Entry outstanding(MinecraftServer s,UUID owner)throws IOException {
        var list=plans(s).list().stream().filter(e->e.owner().equals(owner)&&e.state()!=JailEscrowJournal.State.COMPLETE&&e.state()!=JailEscrowJournal.State.CANCELLED).toList();if(list.size()>1)throw new IOException("Multiple outstanding command histories");return list.isEmpty()?null:list.getFirst();
    }
    public static UUID activeTransaction(MinecraftServer s,UUID owner)throws IOException {var e=outstanding(s,owner);return e==null?null:e.id();}
    private static void permitted(CommandSourceStack actor,ServerPlayer target)throws IOException {
        if(!actor.getServer().isSameThread()||!JailCommands.mayManage(actor)||actor.getEntity() instanceof ServerPlayer p&&JailCustodyService.quarantined(p))throw new IOException("Administrator permission lost");
        if(target.server.getPlayerList().getPlayer(target.getUUID())!=target||target.hasDisconnected()||!target.isAlive())throw new IOException("Target offline/dead; records retained");
    }
    private static void auditItems(ServerPlayer p)throws IOException {
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
    private static void moveSaved(ServerPlayer p,Loc to,JailVanillaPort inventory)throws IOException {
        if(!JailEnforcement.teleportAuthorized(p,to))throw new IOException("Transfer failed; restrictions retained");inventory.persistAndVerify();CompoundTag disk=inventory.savedPlayer();ListTag pos=disk.getList("Pos",6);
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
    private static BlockPos anchor(MinecraftServer s,Loc dest)throws IOException {
        var ledger=JailCustodyService.ledger(s);var level=dest.level(s);BlockPos base=BlockPos.containing(dest.x()-3.5,dest.y()-1,dest.z()-3.5);
        for(int x=8;x<=11;x++)for(int z=1;z<=5;z++){BlockPos pos=base.offset(x,1,z);var a=new JailCustodyLedger.Anchor(dest.dim(),pos.getX(),pos.getY(),pos.getZ());
            if(level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)&&level.getBlockState(pos).isAir()&&ledger.list().stream().noneMatch(b->b.anchor().equals(a)))return pos;}
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
        UUID id=UUID.randomUUID(),retired=retired(s,target.getUUID());var inventory=new JailVanillaPort(target,id,retired);auditItems(target);var nativePort=new JailHumanPort(target,id,retired);var human=nativePort.current();
        var source=inventory.current();if(source.empty()||source.removedBy()!=null||source.restoredBy()!=null||human.transaction()!=null)throw new IOException("Source empty or unresolved receipt");
        nativePort.persistAndVerify();if(!Arrays.equals(source.inventory(),inventory.current().inventory()))throw new IOException("Source changed during baseline save");
        // Save/readback BEFORE the plan: no custody has started and no mutation occurred.
        inventory.persistAndVerify();nativePort.persistAndVerify();saveAssignments(s);BlockPos anchor=anchor(s,dest);
        CompoundTag plan=new CompoundTag();plan.putString("contract","human-empty-v1");plan.put("prior",prior.toTag());plan.put("jail",dest.toTag());plan.putInt("geometry",data.geometry());plan.putByteArray("digest",JailOriginTransaction.hash(source.inventory()));plan.putByteArray("human",human.state());if(retired!=null)plan.putUUID("retired",retired);
        plan.putInt("x",anchor.getX());plan.putInt("y",anchor.getY());plan.putInt("z",anchor.getZ());plan.putString("actor",actor.getTextName());
        plan.putByteArray("origin_before",human.state());
        records.begin(id,target.getUUID(),JailNbt.encode(plan));checkpoint.at(Boundary.PLAN_DURABLE);
        // Plan itself quarantines, even if binding or journal provisioning fails.
        Path directory=root(s).resolve(id.toString());Files.createDirectory(directory);JailEscrowJournal.provisionNew(directory.resolve("items"));JailEscrowJournal.provisionNew(directory.resolve("origins"));
        JailCustodyService.ledger(s).bind(id,target.getUUID(),relative(id,"items"),relative(id,"origins"),plan.getByteArray("digest"),new JailCustodyLedger.Anchor(dest.dim(),anchor.getX(),anchor.getY(),anchor.getZ()),actor.getTextName());
        try(var items=journal(s,id,"items");var origins=journal(s,id,"origins")){
            permitted(actor,target);JailVanillaCustody.removeForDisposableTest(items,id,inventory,b->permitted(actor,target));checkpoint.at(Boundary.ITEMS_DURABLE);
            if(!Arrays.equals(human.state(),nativePort.current().state()))throw new IOException("Human audit changed during removal");
            JailOriginTransaction.temporaryForDisposableTest(origins,items,id,nativePort,b->permitted(actor,target));checkpoint.at(Boundary.HUMAN_DURABLE);
        }
        records.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
        confine(actor,target,records.read(id),inventory);
        actor.sendSuccess(()->net.minecraft.network.chat.Component.literal("Jailed "+target.getGameProfile().getName()+"; custody "+id),true);
    }
    private static void confine(CommandSourceStack actor,ServerPlayer target,JailEscrowJournal.Entry entry,JailVanillaPort inventory)throws IOException {
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
    static JailCustodyService.VerifiedAdapter adapter(MinecraftServer s,JailCustodyLedger.Binding b)throws IOException {
        var e=plans(s).read(b.id());if(e==null||!e.owner().equals(b.owner())||!binding(s,e).equals(b)||!JailNbt.decode(e.snapshot()).getString("contract").equals("human-empty-v1"))throw new IOException("No audited integrated command binding");
        ServerPlayer p=s.getPlayerList().getPlayer(b.owner());if(p==null)throw new IOException("Player offline; custody retained");CompoundTag plan=JailNbt.decode(e.snapshot());UUID retired=plan.hasUUID("retired")?plan.getUUID("retired"):null;
        return new JailCustodyService.VerifiedAdapter(){
            CommandSourceStack actor;
            public void authorize(CommandSourceStack source)throws IOException{permitted(source,p);actor=source;}
            private void authority()throws IOException{if(actor==null)throw new IOException("No command authority");permitted(actor,p);}
            final JailVanillaPort inventory=new JailVanillaPort(p,b.id(),retired);final JailHumanPort human=new JailHumanPort(p,b.id(),retired);
            public String descriptor(){return "human-empty-v1 exact-slot command coordinator";}
            private boolean originComplete(JailEscrowJournal origins)throws IOException {
                var o=origins.read(b.id());if(o==null||o.state()!=JailEscrowJournal.State.COMPLETE||!o.owner().equals(b.owner()))return false;byte[] intent=JailOriginTransaction.intentForReview(origins,b.id(),b.owner());var live=human.current();var disk=human.durable();return human.isOriginal(intent,b.id(),live)&&Arrays.equals(live.state(),disk.state());
            }
            public JailCustodyService.Health inspect()throws IOException {
                if(!inventory.online())return JailCustodyService.Health.REVIEW_REQUIRED;var now=plans(s).read(b.id());
                try(var items=journal(s,b.id(),"items");var origins=journal(s,b.id(),"origins")){
                    JailCustodyService.items(s,b);var item=items.read(b.id());
                    if(now.state()==JailEscrowJournal.State.COMPLETE)return item.state()==JailEscrowJournal.State.COMPLETE&&originComplete(origins)&&JailData.get(s).assignment(b.owner())==null?JailCustodyService.Health.COMPLETE:JailCustodyService.Health.REVIEW_REQUIRED;
                    requireAssignment(s,b.owner(),plan,false);if(now.state()!=JailEscrowJournal.State.HELD||!JailCustodyService.physical(s,b)||JailVanillaCustody.inspect(items,b.id(),inventory)!=JailVanillaCustody.Inspection.HELD_VERIFIED)return JailCustodyService.Health.REVIEW_REQUIRED;
                    var state=JailOriginTransaction.inspect(origins,items,b.id(),human);return state==JailOriginTransaction.Inspection.TEMPORARY_VERIFIED||state==JailOriginTransaction.Inspection.COMPLETE_RECORDED&&originComplete(origins)?JailCustodyService.Health.READY:JailCustodyService.Health.REVIEW_REQUIRED;
                }
            }
            public void restore()throws IOException {
                authority();
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
                public UUID owner(){return inventory.owner();}public boolean online(){return inventory.online();}public JailVanillaCustody.View current()throws IOException{return inventory.current();}public JailVanillaCustody.View durable()throws IOException{return inventory.durable();}public void persistAndVerify()throws IOException{inventory.persistAndVerify();}public void clearAndMark(UUID tx)throws IOException{throw new IOException("Release adapter cannot remove");}
                public JailVanillaCustody.View planRestore(byte[] bytes,UUID tx)throws IOException{JailOriginTransaction.assertInventoryReleaseReady(origins,items,tx,human,false);return inventory.planRestore(bytes,tx);}public void restoreExact(byte[] bytes,UUID tx)throws IOException{JailOriginTransaction.assertInventoryReleaseReady(origins,items,tx,human,true);inventory.restoreExact(bytes,tx);}
            };}
            private void finish(JailEscrowJournal items,JailEscrowJournal origins)throws IOException {
                authority();
                if(items.read(b.id()).state()!=JailEscrowJournal.State.COMPLETE||!originComplete(origins))throw new IOException("Native returns incomplete; restrictions retained");requireAssignment(s,b.owner(),plan,true);
                Loc prior=assignment(plan).prior();if(!JailCommands.safe(p,prior)||actor.getEntity() instanceof ServerPlayer staff&&!JailCommands.claimAccess(staff,prior))throw new IOException("Return location unavailable or claim denied; items returned but quarantine retained");
                moveSaved(p,prior,inventory);checkpoint.at(Boundary.RETURN_POSITION_DURABLE);authority();JailData.get(s).remove(b.owner());saveAssignments(s);checkpoint.at(Boundary.ASSIGNMENT_REMOVED);
                plans(s).transition(b.id(),JailEscrowJournal.State.RESTORE_STARTED,JailEscrowJournal.State.COMPLETE);checkpoint.at(Boundary.COMPLETE);
            }
            public void confirmSaved()throws IOException {
                authority();
                var now=plans(s).read(b.id());try(var items=journal(s,b.id(),"items");var origins=journal(s,b.id(),"origins")){
                    if(now.state()==JailEscrowJournal.State.RESTORE_STARTED){
                        var status=JailOriginTransaction.inspect(origins,items,b.id(),human);if(status==JailOriginTransaction.Inspection.ORIGINAL_SAVED_REVIEW)JailOriginTransaction.confirmSavedForDisposableTest(origins,items,b.id(),human);
                        if(!originComplete(origins))throw new IOException("No saved completed origin return; no replay authorized");
                        if(items.read(b.id()).state()==JailEscrowJournal.State.HELD){JailOriginTransaction.assertInventoryReleaseReady(origins,items,b.id(),human,false);JailVanillaCustody.restoreForDisposableTest(items,b.id(),guarded(items,origins),phase->authority());}
                        if(JailVanillaCustody.inspect(items,b.id(),inventory)==JailVanillaCustody.Inspection.RESTORE_PERSISTED_REVIEW)JailVanillaCustody.confirmRestoredForDisposableTest(items,b.id(),guarded(items,origins));
                        if(items.read(b.id()).state()!=JailEscrowJournal.State.COMPLETE)throw new IOException("No saved completed item return; no replay authorized");finish(items,origins);return;
                    }
                    if(now.state()==JailEscrowJournal.State.REMOVAL_STARTED||now.state()==JailEscrowJournal.State.PREPARED){
                        if(JailVanillaCustody.inspect(items,b.id(),inventory)==JailVanillaCustody.Inspection.REMOVAL_PERSISTED_REVIEW)JailVanillaCustody.confirmRemoved(items,b.id(),inventory);
                        if(JailVanillaCustody.inspect(items,b.id(),inventory)!=JailVanillaCustody.Inspection.HELD_VERIFIED)throw new IOException("No saved held inventory; no removal replay authorized");
                        var status=JailOriginTransaction.inspect(origins,items,b.id(),human);
                        if(status==JailOriginTransaction.Inspection.NO_RECORD&&Arrays.equals(plan.getByteArray("origin_before"),human.current().state())&&Arrays.equals(human.current().state(),human.durable().state()))JailOriginTransaction.temporaryForDisposableTest(origins,items,b.id(),human,phase->authority());
                        if(status==JailOriginTransaction.Inspection.TEMPORARY_SAVED_REVIEW)JailOriginTransaction.confirmSavedForDisposableTest(origins,items,b.id(),human);
                        if(JailOriginTransaction.inspect(origins,items,b.id(),human)!=JailOriginTransaction.Inspection.TEMPORARY_VERIFIED)throw new IOException("Origin receipt absent/uncertain; no switch replay authorized");
                        if(now.state()==JailEscrowJournal.State.PREPARED)now=plans(s).transition(b.id(),JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
                        confine(actor,p,now,inventory);return;
                    }
                    if(now.state()==JailEscrowJournal.State.COMPLETE&&items.read(b.id()).state()==JailEscrowJournal.State.COMPLETE&&originComplete(origins)&&JailData.get(s).assignment(b.owner())==null)return;
                    throw new IOException("No exact saved command outcome to confirm; records retained");
                }
            }
        };
    }
    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("jailintegrated").requires(s->s.getEntity()==null&&s.hasPermission(2))
            .then(Commands.literal("init").executes(c->{try{var s=c.getSource().getServer();JailCustodyService.ledger(s);if(Files.exists(root(s),LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing integrated history must not be reset");JailEscrowJournal.provisionNew(root(s));plans(s);c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Integrated history initialized; production admissions remain disabled."),true);return 1;}catch(IOException bad){return dev.unyielding.quests.essentials.Essentials.fail(c.getSource(),bad.getMessage());}}))
            .then(Commands.literal("cancel").then(Commands.argument("transaction",UuidArgument.uuid()).executes(c->{try{cancel(c.getSource(),UuidArgument.getUuid(c,"transaction"));return 1;}catch(IOException bad){return dev.unyielding.quests.essentials.Essentials.fail(c.getSource(),bad.getMessage());}}))));
    }
    /** Console-only cancellation after proving no native removal/switch ever started. No item insertion. */
    private static void cancel(CommandSourceStack actor,UUID id)throws IOException {
        var s=actor.getServer();var e=plans(s).read(id);if(e==null||e.state()!=JailEscrowJournal.State.PREPARED)throw new IOException("Only an unchanged prepared admission can be cancelled");
        ServerPlayer p=s.getPlayerList().getPlayer(e.owner());if(p==null)throw new IOException("Player offline");permitted(actor,p);CompoundTag plan=JailNbt.decode(e.snapshot());UUID retired=plan.hasUUID("retired")?plan.getUUID("retired"):null;
        var inventory=new JailVanillaPort(p,id,retired);var human=new JailHumanPort(p,id,retired);var live=inventory.current();var disk=inventory.durable();
        if(live.removedBy()!=null||live.restoredBy()!=null||disk.removedBy()!=null||disk.restoredBy()!=null||!Arrays.equals(live.inventory(),disk.inventory())||!Arrays.equals(JailOriginTransaction.hash(live.inventory()),plan.getByteArray("digest"))||!Arrays.equals(human.current().state(),plan.getByteArray("origin_before"))||!Arrays.equals(human.current().state(),human.durable().state())||JailData.get(s).assignment(e.owner())!=null)throw new IOException("Prepared source is no longer exactly unchanged");
        for(String kind:List.of("items","origins")){
            Path path=root(s).resolve(id.toString()).resolve(kind);if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))continue;
            try(var journal=journal(s,id,kind)){var nativeEntry=journal.read(id);if(nativeEntry==null)continue;
                if(!kind.equals("items")||nativeEntry.state()!=JailEscrowJournal.State.PREPARED||JailVanillaCustody.inspect(journal,id,inventory)!=JailVanillaCustody.Inspection.PREPARED_UNCHANGED)throw new IOException("Native mutation has started/has uncertain history; cancellation refused");
                journal.transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);
            }
        }
        plans(s).transition(id,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.CANCELLED);var binding=JailCustodyService.ledger(s).get(id);if(binding!=null)JailCustodyService.ledger(s).transition(id,binding.revision(),JailCustodyLedger.State.RELEASED,"console","Unchanged admission cancelled; no items or origin were transferred");
    }
}
