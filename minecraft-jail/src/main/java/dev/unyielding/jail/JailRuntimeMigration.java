package dev.unyielding.jail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Explicit artifact transitions from reviewed predecessors. Original custody bytes are never rewritten.
 * The transition only selects the historical receipt namespace for its exact transaction.
 * All native restoration/death checks remain in their original coordinators. */
public final class JailRuntimeMigration {
    public static final String OLD_SHA512="0c992e69bcd694b0121653879a2b3395902c374db47653dc743eba1df8fa2c5ca37b6c8b02013e56f2a53b9c972e5e539d9832b58a1dd08009b1e457da9f79a6";
    static final String ATTACHED_SHA512="381284f166cb3eacf4de7342e6656a84521821ea083cc049987f00bfd80e0724a74966dd9821d5432c6f73a3f5a61f52909cd5364a160c5065389e2cf1851732";
    private static final String ROOT="jail-runtime-transitions-v1";
    private record Validation(MinecraftServer server,UUID owner,UUID transaction,String target,String previous){}
    private static final ThreadLocal<Validation> VALIDATION=new ThreadLocal<>();
    @FunctionalInterface public interface Checkpoint {void at(String boundary)throws IOException;}
    private static Checkpoint checkpoint=boundary->{};
    private JailRuntimeMigration(){}
    public static void disposableCheckpoint(ServerPlayer p,Checkpoint c)throws IOException {
        if(!JailHumanAudit.disposable(p))throw new IOException("Fault injection unavailable");checkpoint=Objects.requireNonNull(c);
    }
    private static void require(boolean v,String reason)throws IOException {if(!v)throw new IOException(reason);}
    private static Path world(MinecraftServer s)throws IOException {require(s.isSameThread(),"Migration is server-thread only");return s.getWorldPath(LevelResource.ROOT).toRealPath();}
    private static Path directory(MinecraftServer s,UUID tx)throws IOException {
        Path w=world(s),r=w.resolve(ROOT),d=r.resolve(tx.toString());
        if(Files.isSymbolicLink(r)||Files.isSymbolicLink(d))throw new IOException("Symbolic transition directory");
        return d;
    }
    private static JailEscrowJournal.Entry entry(Path file,UUID tx,UUID owner)throws IOException {
        var rows=JailMigrationIO.entries(file);var matches=rows.stream().filter(e->e.id().equals(tx)&&e.owner().equals(owner)).toList();
        require(matches.size()==1,"Missing or foreign transaction");return matches.getFirst();
    }
    private static Path source(MinecraftServer s,String name)throws IOException {return JailMigrationIO.regular(world(s),name);}
    private static String rel(UUID id,String kind){return "integrated-jail-v1/"+id+"/"+kind+"/escrow.v1";}
    /** Structural comparisons do not invoke callbacks, select an origin or grant a receipt exception. */
    private static CompoundTag identity(MinecraftServer s,UUID tx,UUID owner,boolean heldOnly)throws IOException {
        var plan=entry(source(s,"integrated-jail-v1/escrow.v1"),tx,owner);CompoundTag body=JailNbt.decode(plan.snapshot());
        require(Set.of(JailIntegrated.SUPPORTED,JailIntegrated.PACKED_WALLET).contains(body.getString("contract"))&&!body.hasUUID("retired"),"Only first supported/packed-wallet custody is migratable; XP/legacy contracts refused");
        require(plan.state()!=JailEscrowJournal.State.CANCELLED,"Cancelled transaction");
        if(heldOnly)require(plan.state()==JailEscrowJournal.State.HELD&&tx.equals(JailIntegrated.activeTransaction(s,owner)),"Source must be exactly HELD");
        var item=entry(source(s,rel(tx,"items")),tx,owner);var origin=entry(source(s,rel(tx,"origins")),tx,owner);var effect=entry(source(s,rel(tx,"remove-effects")),tx,owner);
        require(Arrays.equals(body.getByteArray("digest"),JailOriginTransaction.hash(item.snapshot())),"Plan/item digest mismatch");
        var b=JailCustodyService.ledger(s).get(tx);
        require(b!=null&&b.owner().equals(owner)&&b.items().equals("integrated-jail-v1/"+tx+"/items")&&b.origins().equals("integrated-jail-v1/"+tx+"/origins")&&Arrays.equals(b.digest(),body.getByteArray("digest")),"Custody pointer mismatch");
        CompoundTag jail=body.getCompound("jail");
        require(b.anchor().dimension().equals(jail.getString("dim"))&&b.anchor().x()==body.getInt("x")&&b.anchor().y()==body.getInt("y")&&b.anchor().z()==body.getInt("z"),"Jail anchor mismatch");
        if(heldOnly){
            require(b.state()!=JailCustodyLedger.State.RELEASED&&item.state()==JailEscrowJournal.State.HELD&&origin.state()==JailEscrowJournal.State.HELD&&effect.state()==JailEscrowJournal.State.HELD,"Partial native phase refused");
            require(JailMigrationIO.entries(source(s,rel(tx,"escapes"))).isEmpty()&&JailMigrationIO.entries(source(s,rel(tx,"return-effects"))).isEmpty(),"Prior escape/return attempt not supported by this migration");
        }
        CompoundTag out=new CompoundTag();out.putUUID("owner",owner);out.putUUID("transaction",tx);out.putString("contract",body.getString("contract"));
        out.putString("plan",JailMigrationIO.hash(plan.snapshot()));out.putString("items",JailMigrationIO.hash(item.snapshot()));out.putString("origins",JailMigrationIO.hash(origin.snapshot()));out.putString("effects",JailMigrationIO.hash(effect.snapshot()));
        out.putString("dimension",b.anchor().dimension());out.putInt("x",b.anchor().x());out.putInt("y",b.anchor().y());out.putInt("z",b.anchor().z());return out;
    }
    static void verifyArtifactPair(String predecessor,String target,String archiveHash,String runningHash)throws IOException {
        require(OLD_SHA512.equals(predecessor)||ATTACHED_SHA512.equals(predecessor),"Unapproved predecessor artifact");
        require(target!=null&&target.matches("[0-9a-f]{128}")&&!predecessor.equals(target),"Invalid target artifact");
        require(predecessor.equals(archiveHash),"Predecessor artifact copy mismatched");
        require(target.equals(runningHash),"Target artifact does not match explicitly approved bytes");
    }
    private static void pins(ServerPlayer p,String old,String target)throws IOException {
        Path archive=source(p.server,"jail-migration-inputs/old.jar");
        verifyArtifactPair(old,target,JailMigrationIO.sha512(archive),JailHumanAudit.ownArtifact());
        JailHumanAudit.runtime(p); // dependency certificate and exact companion remain mandatory
    }
    private static CompoundTag readManifest(MinecraftServer s,UUID tx)throws IOException {
        Path dir=directory(s,tx);return JailNbt.decode(JailMigrationIO.read(JailMigrationIO.regular(dir,"proposal.nbt")));
    }
    private static void backups(MinecraftServer s,UUID tx,CompoundTag manifest,boolean unchangedSources)throws IOException {
        Path dir=directory(s,tx);CompoundTag files=manifest.getCompound("files");require(!files.isEmpty(),"Missing recovery evidence");
        for(String name:files.getAllKeys()){
            byte[] copy=JailMigrationIO.read(JailMigrationIO.regular(dir,"backup/"+name));require(files.getString(name).equals(JailMigrationIO.hash(copy)),"Recovery copy changed: "+name);
            // Live player bytes can advance after a legitimate reconnect. Native receipt/state equality
            // is separately required by escapeContext; historical custody files may not change pre-commit.
            if(unchangedSources&&!name.startsWith("playerdata/")){
                byte[] live=JailMigrationIO.read(source(s,name));
                if(name.equals("custody-interface-v1/custody.v1")){
                    JailCustodyService.ledger(s).verifyDisk();
                    require(JailCustodyService.physical(s,JailCustodyService.ledger(s).get(tx)),"Custody interface must be loaded and intact");
                    JailMigrationIO.quarantineExtension(copy,live,tx);
                }else require(Arrays.equals(copy,live),"Pre-migration source changed: "+name);
            }
        }
    }
    private static void validate(ServerPlayer p,CompoundTag m)throws IOException {
        UUID tx=m.getUUID("transaction");pins(p,m.getString("old_artifact"),m.getString("new_artifact"));
        require(m.getInt("version")==1&&m.getUUID("owner").equals(p.getUUID())&&identity(p.server,tx,p.getUUID(),true).equals(m.getCompound("identity")),"Transaction/owner/jail identity differs");
        require(p.isAlive()&&!p.hasDisconnected()&&p.server.getPlayerList().getPlayer(p.getUUID())==p,"Migration requires authoritative living online player");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(),"Disable new admissions before migration");
        String actual=JailHumanAudit.runtime(p),old=JailHumanAudit.predecessorRuntime(p,m.getString("old_artifact"));
        require(actual.equals(m.getString("new_runtime"))&&old.equals(m.getString("old_runtime")),"Artifact or dependency fingerprint differs");
        require(VALIDATION.get()==null,"Nested validation refused");VALIDATION.set(new Validation(p.server,p.getUUID(),tx,actual,old));
        try{
            var context=JailIntegrated.escapeContext(p,false,true);require(context!=null&&context.transaction().equals(tx),"Native receipt validation failed");
            var a=context.assignment();require(JailPlacement.intact(p.server,a.jail(),a.geometry())&&JailCommands.safe(p,a.jail())&&JailCommands.claimAccess(p,a.jail()),"Unsafe/foreign jail");
            require(p.level().dimension().location().toString().equals(a.jail().dim())&&p.distanceToSqr(a.jail().x(),a.jail().y(),a.jail().z())<0.0001,"Player must be at the assigned jail anchor");
            var b=JailCustodyService.ledger(p.server).get(tx);require(JailIntegrated.adapter(p.server,b).inspect()==JailCustodyService.Health.READY,"Native item/origin receipts are ambiguous");
        }finally{VALIDATION.remove();}
    }
    public static String prepare(ServerPlayer p,String predecessor,String target)throws IOException {
        pins(p,predecessor,target);UUID tx=JailIntegrated.activeTransaction(p.server,p.getUUID());require(tx!=null,"No active transaction");Path dir=directory(p.server,tx);
        if(Files.exists(dir,LinkOption.NOFOLLOW_LINKS)){
            CompoundTag existing=readManifest(p.server,tx);
            require(predecessor.equals(existing.getString("old_artifact"))&&target.equals(existing.getString("new_artifact")),"Existing transition is bound to a different artifact pair");
            validate(p,existing);backups(p.server,tx,existing,true);return proposal(p.server,tx);
        }
        CompoundTag m=new CompoundTag();m.putInt("version",1);m.putUUID("owner",p.getUUID());m.putUUID("transaction",tx);m.putString("old_artifact",predecessor);m.putString("new_artifact",target);m.putString("old_runtime",JailHumanAudit.predecessorRuntime(p,predecessor));m.putString("new_runtime",JailHumanAudit.runtime(p));m.put("identity",identity(p.server,tx,p.getUUID(),true));
        validate(p,m);
        List<String> names=new ArrayList<>(List.of("integrated-jail-v1/escrow.v1","custody-interface-v1/custody.v1","data/unyielding_jail.dat","data/neoorigins_origin_claims.dat","playerdata/"+p.getUUID()+".dat","jail-migration-inputs/old.jar"));
        for(String kind:List.of("items","origins","remove-effects","return-effects","escapes"))names.add(rel(tx,kind));
        Map<String,byte[]> originals=new TreeMap<>();for(String name:names)originals.put(name,JailMigrationIO.read(source(p.server,name)));
        Path parent=dir.getParent();if(!Files.exists(parent)){Files.createDirectory(parent);JailMigrationIO.force(parent.getParent());}require(parent.equals(parent.toRealPath()),"Transition parent not canonical");Files.createDirectory(dir);JailMigrationIO.force(parent);
        CompoundTag files=new CompoundTag();for(var e:originals.entrySet()){JailMigrationIO.create(dir.resolve("backup").resolve(e.getKey()),e.getValue());files.putString(e.getKey(),JailMigrationIO.hash(e.getValue()));}
        // Force all created recovery directory entries, deepest first.
        try(var paths=Files.walk(dir)){for(Path d:paths.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList())JailMigrationIO.force(d);}
        m.put("files",files);byte[] manifest=JailNbt.encode(m);JailMigrationIO.create(dir.resolve("proposal.nbt"),manifest);
        JailEscrowJournal.provisionNew(dir.resolve("history"));try(var history=new JailEscrowJournal(dir.resolve("history"))){history.begin(tx,p.getUUID(),manifest);}
        backups(p.server,tx,m,true);validate(p,m);checkpoint.at("PREPARED");
        MinecraftJail.LOGGER.info("JAIL_MIGRATION_PREPARED tx={} owner={} old={} new={} proposal={}",tx,p.getUUID(),predecessor,target,JailMigrationIO.hash(manifest));return JailMigrationIO.hash(manifest);
    }
    public static String proposal(MinecraftServer s,UUID tx)throws IOException {return JailMigrationIO.hash(JailMigrationIO.read(JailMigrationIO.regular(directory(s,tx),"proposal.nbt")));}
    public static void commit(MinecraftServer s,UUID tx,String expected)throws IOException {
        require(expected!=null&&expected.matches("[0-9a-f]{64}")&&expected.equals(proposal(s,tx)),"Exact proposal receipt mismatch");
        CompoundTag m=readManifest(s,tx);require(m.hasUUID("owner")&&m.getUUID("transaction").equals(tx),"Foreign migration proposal");ServerPlayer p=s.getPlayerList().getPlayer(m.getUUID("owner"));require(p!=null,"Owner must be online");
        var current=entry(JailMigrationIO.regular(directory(s,tx),"history/escrow.v1"),tx,p.getUUID());require(Arrays.equals(current.snapshot(),JailNbt.encode(m)),"Migration history/proposal mismatch");
        if(current.state()==JailEscrowJournal.State.HELD){receiptRuntime(p,tx);return;}
        require(current.state()==JailEscrowJournal.State.PREPARED||current.state()==JailEscrowJournal.State.REMOVAL_STARTED,"Unknown transition phase");
        backups(s,tx,m,true);validate(p,m);
        try(var journal=new JailEscrowJournal(directory(s,tx).resolve("history"))){
            if(current.state()==JailEscrowJournal.State.PREPARED)journal.transition(tx,JailEscrowJournal.State.PREPARED,JailEscrowJournal.State.REMOVAL_STARTED);
            checkpoint.at("BEFORE_COMMIT");backups(s,tx,m,true);validate(p,m);
            journal.transition(tx,JailEscrowJournal.State.REMOVAL_STARTED,JailEscrowJournal.State.HELD);
        }
        checkpoint.at("COMMITTED");require(receiptRuntime(p,tx).equals(m.getString("old_runtime")),"Committed transition readback failed");
        MinecraftJail.LOGGER.info("JAIL_MIGRATION_COMMITTED tx={} owner={} proposal={} old={} new={}",tx,p.getUUID(),expected,m.getString("old_artifact"),m.getString("new_artifact"));
    }
    /** Current runtime is ALWAYS checked. Only the exact bound historical receipt gets its old namespace. */
    static String receiptRuntime(ServerPlayer p,UUID tx)throws IOException {
        String current=JailHumanAudit.runtime(p);var check=VALIDATION.get();
        if(check!=null&&check.server()==p.server&&check.owner().equals(p.getUUID())&&check.transaction().equals(tx)&&check.target().equals(current))return check.previous();
        Path dir=directory(p.server,tx);if(!Files.exists(dir,LinkOption.NOFOLLOW_LINKS))return current;
        CompoundTag m=readManifest(p.server,tx);require(m.getInt("version")==1&&m.hasUUID("owner")&&m.getUUID("owner").equals(p.getUUID())&&m.getUUID("transaction").equals(tx),"Foreign transition owner/transaction");
        String predecessor=m.getString("old_artifact");
        Path archived=JailMigrationIO.regular(dir,"backup/jail-migration-inputs/old.jar");
        verifyArtifactPair(predecessor,m.getString("new_artifact"),JailMigrationIO.sha512(archived),JailHumanAudit.ownArtifact());
        require(current.equals(m.getString("new_runtime")),"Transition does not approve this runtime");
        var e=entry(JailMigrationIO.regular(dir,"history/escrow.v1"),tx,p.getUUID());require(e.state()==JailEscrowJournal.State.HELD&&Arrays.equals(e.snapshot(),JailNbt.encode(m)),"Incomplete or mismatched transition; no receipt permission");
        require(identity(p.server,tx,p.getUUID(),false).equals(m.getCompound("identity")),"Historical binding changed");backups(p.server,tx,m,false);
        String old=JailHumanAudit.predecessorRuntime(p,predecessor);require(old.equals(m.getString("old_runtime")),"Historical runtime mismatch");return old;
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("jailmigration").requires(s->s.getEntity()==null&&s.hasPermission(2))
          .then(Commands.literal("prepare").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("old",StringArgumentType.word()).then(Commands.argument("new",StringArgumentType.word()).executes(c->{try{String digest=prepare(EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"old"),StringArgumentType.getString(c,"new"));c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Migration proposal "+digest+"; original custody unchanged. Explicit commit required."),true);return 1;}catch(Exception e){return JailText.fail(c.getSource(),"Migration refused: "+e.getMessage());}})))))
          .then(Commands.literal("commit").then(Commands.argument("transaction",UuidArgument.uuid()).then(Commands.argument("receipt",StringArgumentType.word()).executes(c->{try{commit(c.getSource().getServer(),UuidArgument.getUuid(c,"transaction"),StringArgumentType.getString(c,"receipt"));return 1;}catch(Exception e){return JailText.fail(c.getSource(),"Migration refused: "+e.getMessage());}})))));
    }
}
