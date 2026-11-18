package dev.unyielding.jail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Protected record UI and quarantine. Only explicitly bound, audited adapters may restore. */
@EventBusSubscriber(modid=MinecraftJail.MODID)
public final class JailCustodyService {
    private JailCustodyService(){}
    public enum Health { READY, COMPLETE, REVIEW_REQUIRED }
    public interface VerifiedAdapter {
        default void authorize(CommandSourceStack actor)throws IOException {}
        String descriptor();Health inspect()throws IOException;
        void restore()throws IOException;
        /** Only phase confirmation through the verified coordinators, never raw insertion. */
        void confirmSaved()throws IOException;
        /** Explicit coordinated continuation; unsupported adapters keep their previous recovery behavior. */
        default void recoverVerified()throws IOException {throw new IOException("No audited coordinated recovery adapter");}
    }
    @FunctionalInterface public interface FixtureFactory {VerifiedAdapter open(MinecraftServer server,JailCustodyLedger.Binding binding)throws IOException;}
    @FunctionalInterface public interface FixtureCheckpoint {void afterVerifiedRestore()throws IOException;}
    private static FixtureFactory fixtureFactory;private static FixtureCheckpoint checkpoint=()->{};
    private static final Map<MinecraftServer,Context> contexts=new WeakHashMap<>();
    private static final class Context {JailCustodyLedger ledger;String unavailable;final Set<UUID> missing=new HashSet<>();}
    public static void installDisposableFixture(MinecraftServer server,FixtureFactory factory,FixtureCheckpoint after)throws IOException {
        if(!"true".equals(System.getenv("GITHUB_ACTIONS"))||!"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))||!Boolean.getBoolean("uq.custodyInterface")||!server.getWorldPath(LevelResource.ROOT).toRealPath().getFileName().toString().equals("jail_interface_smoke"))throw new IOException("Test adapter forbidden outside disposable hosted world");
        fixtureFactory=Objects.requireNonNull(factory);checkpoint=Objects.requireNonNull(after);
    }
    private static Path root(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("custody-interface-v1");}
    private static Context context(MinecraftServer server){return contexts.computeIfAbsent(server,key->{Context c=new Context();if(Files.exists(root(key),LinkOption.NOFOLLOW_LINKS))try{if(Files.isSymbolicLink(root(key)))throw new IOException("Custody ledger symlink");c.ledger=new JailCustodyLedger(root(key));}catch(IOException bad){c.unavailable=bad.getMessage();}return c;});}
    public static JailCustodyLedger ledger(MinecraftServer server)throws IOException {if(!server.isSameThread())throw new IOException("Custody action off server thread");Context c=context(server);if(c.unavailable!=null||c.ledger!=null&&!c.ledger.healthy())throw new IOException("Custody unavailable; preserve records and restore audited history: "+c.unavailable);if(c.ledger==null)throw new IOException("Custody index not initialized; console must explicitly initialize or recover history");return c.ledger;}
    public static boolean blocked(MinecraftServer server,UUID owner){if(server==null)return true;Context c=context(server);return JailIntegrated.blocked(server,owner)||c.unavailable!=null||c.ledger!=null&&(!c.ledger.healthy()||c.ledger.outstanding(owner));}
    public static boolean quarantined(ServerPlayer player){return player.server!=null&&blocked(player.server,player.getUUID());}
    static boolean anyOutstanding(MinecraftServer server)throws IOException {
        Context c=context(server);
        if(c.ledger==null && c.unavailable==null && !JailData.get(server).integratedCustodyInitialized()
                && Files.notExists(root(server),LinkOption.NOFOLLOW_LINKS))return false;
        var ledger=ledger(server);
        ledger.verifyDisk();
        return ledger.list().stream().anyMatch(b->b.state()!=JailCustodyLedger.State.RELEASED);
    }
    private static boolean manager(ServerPlayer player){return !quarantined(player)&&JailCommands.mayManage(player.createCommandSourceStack());}
    public static boolean physical(MinecraftServer server,JailCustodyLedger.Binding b){var a=b.anchor();ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(a.dimension())));BlockPos pos=new BlockPos(a.x(),a.y(),a.z());return level!=null&&level.hasChunkAt(pos)&&level.getBlockState(pos).is(dev.unyielding.jail.JailRegistration.CUSTODY_VAULT.get());}
    public static boolean valid(ServerPlayer player,JailCustodyLedger.Binding selected){
        if(!player.server.isSameThread()||!manager(player)||player.hasDisconnected()||!player.isAlive())return false;
        try{var index=ledger(player.server);var current=index.at(selected.anchor());if(current==null||!current.id().equals(selected.id())||current.revision()!=selected.revision()||!current.owner().equals(selected.owner())||!physical(player.server,current))return false;
            var a=current.anchor();return player.level().dimension().location().toString().equals(a.dimension())&&player.distanceToSqr(a.x()+0.5,a.y()+0.5,a.z()+0.5)<=64&&claim(player,(ServerLevel)player.level(),new BlockPos(a.x(),a.y(),a.z()));
        }catch(IOException|RuntimeException bad){return false;}
    }
    private static boolean claim(ServerPlayer actor,ServerLevel level,BlockPos pos){
        if(!net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims"))return false;
        try{Class<?> api=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");Object a=api.getMethod("get",MinecraftServer.class).invoke(null,actor.server);Object protection=api.getMethod("getChunkProtection").invoke(a);
            return Boolean.TRUE.equals(Class.forName("xaero.pac.common.server.claims.protection.api.IChunkProtectionAPI").getMethod("hasChunkAccess",net.minecraft.world.entity.Entity.class,ResourceLocation.class,int.class,int.class).invoke(protection,actor,level.dimension().location(),pos.getX()>>4,pos.getZ()>>4));
        }catch(ReflectiveOperationException|RuntimeException|LinkageError bad){return false;}
    }
    public static Path journalPath(MinecraftServer server,String relative)throws IOException {
        try{JailCustodyLedger.path(relative);}catch(IllegalArgumentException bad){throw new IOException("Invalid custody path",bad);}
        Path world=server.getWorldPath(LevelResource.ROOT).toRealPath(),path=world.resolve(relative).normalize();if(!path.startsWith(world))throw new IOException("Custody path escaped world");
        Path scan=world;for(Path part:world.relativize(path)){scan=scan.resolve(part);if(Files.isSymbolicLink(scan))throw new IOException("Custody path symlink refused");}
        if(!Files.isDirectory(path)||!path.toRealPath().startsWith(world))throw new IOException("Custody history missing; never create a replacement journal");return path;
    }
    public static JailEscrowJournal.Entry items(MinecraftServer server,JailCustodyLedger.Binding b)throws IOException {
        try(var journal=new JailEscrowJournal(journalPath(server,b.items()))){var e=journal.read(b.id());if(e==null||!e.owner().equals(b.owner())||!MessageDigest.isEqual(b.digest(),JailOriginTransaction.hash(e.snapshot())))throw new IOException("Custody UUID, owner or source digest changed");return e;}
    }
    private static VerifiedAdapter adapter(MinecraftServer server,JailCustodyLedger.Binding b)throws IOException {items(server,b);var adapter=fixtureFactory==null?JailIntegrated.adapter(server,b):fixtureFactory.open(server,b);if(adapter==null||adapter.descriptor()==null||adapter.descriptor().isBlank())throw new IOException("Unsupported custody adapter");return adapter;}
    public static void open(ServerPlayer player,BlockPos pos){
        try{if(!manager(player))throw new IOException("Administrator permission required; quarantine has no player bypass");var ledger=ledger(player.server);var b=ledger.at(new JailCustodyLedger.Anchor(player.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()));if(b==null)throw new IOException("Unbound custody interface; no inventory is stored here");if(!valid(player,b))throw new IOException("Interface, range, permissions or claim access changed");
            var rows=JailCustodyMenu.previews(player,items(player.server,b).snapshot());player.openMenu(new SimpleMenuProvider((id,inventory,ignored)->new JailCustodyMenu(id,inventory,b,rows),Component.literal("Custody "+b.owner().toString().substring(0,8)+" · "+b.state())));
        }catch(IOException|RuntimeException bad){player.sendSystemMessage(Component.literal("Custody: "+reason(bad)));}
    }
    private static String reason(Throwable bad){String s=bad.getMessage()==null?bad.getClass().getSimpleName():bad.getMessage();return s.replaceAll("[\\p{Cntrl}]"," ").substring(0,Math.min(s.replaceAll("[\\p{Cntrl}]"," ").length(),400));}
    private static JailCustodyLedger.Binding change(MinecraftServer server,JailCustodyLedger.Binding b,JailCustodyLedger.State state,String actor,String why)throws IOException {
        try{return ledger(server).transition(b.id(),b.revision(),state,actor,why);}catch(IOException failed){context(server).unavailable=failed.getMessage();throw failed;}
    }
    public static void action(CommandSourceStack actor,UUID id,long revision,String action)throws IOException {
        if(!JailCommands.mayManage(actor)||actor.getEntity() instanceof ServerPlayer player&&quarantined(player))throw new IOException("Administrator or console recovery permission required");
        MinecraftServer server=actor.getServer();var ledger=ledger(server);ledger.verifyDisk();var b=ledger.get(id);if(b==null||b.revision()!=revision)throw new IOException("Stale or cross-prisoner custody selection");
        String name=actor.getEntity()==null?"console:"+actor.getTextName():actor.getEntity().getUUID().toString();
        if(action.equals("hold")){
            if(!ledger.at(b.anchor()).id().equals(id))throw new IOException("This archived transaction no longer owns the locker");
            change(server,b,JailCustodyLedger.State.QUARANTINED,name,"Explicit administrator hold; review required");return;
        }
        if(b.state()==JailCustodyLedger.State.RELEASED){if(action.equals("restore"))return;throw new IOException("This transaction was already returned; records retained");}
        try{
            var adapter=adapter(server,b);adapter.authorize(actor);
            if(action.equals("confirm")){adapter.confirmSaved();change(server,b,JailCustodyLedger.State.QUARANTINED,name,"Explicit saved-phase confirmation via "+adapter.descriptor()+"; review before return");return;}
            if(action.equals("recover")){adapter.recoverVerified();change(server,b,JailCustodyLedger.State.QUARANTINED,name,"Explicit verified command continuation via "+adapter.descriptor()+"; review before final release");return;}
            Health health=adapter.inspect();
            if(health==Health.REVIEW_REQUIRED)throw new IOException("Native origin/accessory or player-save outcome is ambiguous; explicit saved-phase recovery required");
            if(action.equals("review")){change(server,b,JailCustodyLedger.State.REVIEWED,name,"Verified "+adapter.descriptor()+"; quarantine retained until return completes");return;}
            if(!action.equals("restore")||b.state()!=JailCustodyLedger.State.REVIEWED)throw new IOException("Review this exact transaction before restoring");
            b=change(server,b,JailCustodyLedger.State.QUARANTINED,name,"Return started; awaiting verified native journals and saves");
            if(health!=Health.COMPLETE)adapter.restore();
            if(adapter.inspect()!=Health.COMPLETE)throw new IOException("Return did not verify; records and quarantine retained");
            checkpoint.afterVerifiedRestore();change(server,b,JailCustodyLedger.State.RELEASED,name,"Native transaction completion verified; no further grants authorized");
        }catch(IOException|RuntimeException bad){var now=ledger.get(id);if(now!=null&&now.state()!=JailCustodyLedger.State.RELEASED)change(server,now,JailCustodyLedger.State.QUARANTINED,name,"Recovery required: "+reason(bad));throw new IOException(reason(bad),bad);}
    }
    public static void menuAction(ServerPlayer player,JailCustodyMenu menu,JailCustodyLedger.Binding b,String action){
        if(player.containerMenu!=menu||!valid(player,b)){player.closeContainer();return;}
        try{action(player.createCommandSourceStack(),b.id(),b.revision(),action);player.sendSystemMessage(Component.literal("Custody action recorded. Reopen the interface for current state."));}
        catch(IOException bad){player.sendSystemMessage(Component.literal("Custody: "+reason(bad)));}finally{player.closeContainer();}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();Context c=context(server);if(c.ledger!=null&&c.unavailable==null&&server.getTickCount()%20==0)for(var b:c.ledger.list())if(b.state()!=JailCustodyLedger.State.RELEASED){
            if(physical(server,b)){c.missing.remove(b.id());continue;}
            if(c.missing.add(b.id()))try{change(server,b,JailCustodyLedger.State.QUARANTINED,"server","Interface block or chunk unavailable; escrow retained");}catch(IOException bad){c.unavailable=bad.getMessage();}
        }
        for(var player:server.getPlayerList().getPlayers()){
            if(player.containerMenu instanceof JailCustodyMenu menu&&!menu.stillValid(player))player.closeContainer();
            if(quarantined(player)&&player.containerMenu!=player.inventoryMenu)player.closeContainer();
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){JailIntegrated.stopped(event.getServer());Context c=contexts.remove(event.getServer());if(c!=null&&c.ledger!=null)try{c.ledger.close();}catch(IOException ignored){}fixtureFactory=null;checkpoint=()->{};}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=Commands.literal("jailcustody").requires(JailCommands::mayManage);
        root.then(Commands.literal("list").executes(c->command(c.getSource(),()->list(c.getSource(),1)))
                .then(Commands.argument("page",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c->command(c.getSource(),()->list(c.getSource(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"page"))))));
        root.then(Commands.literal("init").requires(s->s.getEntity()==null&&s.hasPermission(2)).executes(c->command(c.getSource(),()->{Context state=context(c.getSource().getServer());if(state.ledger!=null||state.unavailable!=null||Files.exists(root(c.getSource().getServer()),LinkOption.NOFOLLOW_LINKS))throw new IOException("Existing custody history must be recovered, never reset");JailCustodyLedger.provisionNew(root(c.getSource().getServer()));state.ledger=new JailCustodyLedger(root(c.getSource().getServer()));return "Custody metadata initialized; admissions remain disabled.";})));
        root.then(Commands.literal("bind").requires(s->s.getEntity()==null&&s.hasPermission(2)).then(Commands.argument("transaction",UuidArgument.uuid()).then(Commands.argument("owner",UuidArgument.uuid()).then(Commands.argument("items",StringArgumentType.string()).then(Commands.argument("origins",StringArgumentType.string()).then(Commands.argument("anchor",BlockPosArgument.blockPos()).executes(c->command(c.getSource(),()->{
            var server=c.getSource().getServer();UUID id=UuidArgument.getUuid(c,"transaction"),owner=UuidArgument.getUuid(c,"owner");String items=StringArgumentType.getString(c,"items"),origins=StringArgumentType.getString(c,"origins");BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"anchor");var level=c.getSource().getLevel();if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos).isAir())throw new IOException("Interface requires an empty loaded block within world bounds");
            JailEscrowJournal.Entry entry;try(var journal=new JailEscrowJournal(journalPath(server,items));var nativeJournal=new JailEscrowJournal(journalPath(server,origins))){entry=journal.read(id);var original=nativeJournal.read(id);if(entry==null||!entry.owner().equals(owner)||original==null||!original.owner().equals(owner))throw new IOException("Missing or foreign linked histories");}
            ledger(server).bind(id,owner,items,origins,JailOriginTransaction.hash(entry.snapshot()),new JailCustodyLedger.Anchor(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()),"console:"+c.getSource().getTextName());if(!level.setBlock(pos,dev.unyielding.jail.JailRegistration.CUSTODY_VAULT.get().defaultBlockState(),3))throw new IOException("Interface placement failed; custody retained in quarantine");return "Indexed existing custody; prisoner quarantined, no copied inventory.";
        }))))))));
        for(String action:List.of("inspect","audit","review","confirm","recover","restore","hold"))root.then(Commands.literal(action).then(Commands.argument("transaction",UuidArgument.uuid()).executes(c->command(c.getSource(),()->{
            var ledger=ledger(c.getSource().getServer());var b=ledger.get(UuidArgument.getUuid(c,"transaction"));if(b==null)throw new IOException("Unknown transaction");
            if(action.equals("inspect")){items(c.getSource().getServer(),b);return b.id()+" owner="+b.owner()+" "+b.state()+" revision="+b.revision()+" · "+b.reason();}
            if(action.equals("audit"))return ledger.audit(b.id()).stream().skip(Math.max(0,ledger.audit(b.id()).size()-8)).map(e->e.sequence()+" "+e.actor()+" "+e.state()+" "+e.reason()).collect(java.util.stream.Collectors.joining(" | "));
            action(c.getSource(),b.id(),b.revision(),action);return "Recorded "+action+" for "+b.id()+"; no raw item grants.";
        }))));
        root.then(Commands.literal("reattach").then(Commands.argument("transaction",UuidArgument.uuid()).then(Commands.argument("anchor",BlockPosArgument.blockPos()).executes(c->command(c.getSource(),()->{
            var server=c.getSource().getServer();var b=ledger(server).get(UuidArgument.getUuid(c,"transaction"));if(b==null)throw new IOException("Unknown transaction");items(server,b);BlockPos pos=BlockPosArgument.getLoadedBlockPos(c,"anchor");var level=c.getSource().getLevel();if(level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.getBlockState(pos).isAir()||c.getSource().getEntity() instanceof ServerPlayer p&&!claim(p,level,pos))throw new IOException("Interface placement requires empty loaded space within world bounds and claim permission");
            ledger(server).relocate(b.id(),b.revision(),new JailCustodyLedger.Anchor(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()),c.getSource().getTextName());if(!level.setBlock(pos,dev.unyielding.jail.JailRegistration.CUSTODY_VAULT.get().defaultBlockState(),3))throw new IOException("Interface placement failed; custody retained in quarantine");return "Interface rebound; records retained and review required.";
        })))));
        dispatcher.register(root);
    }
    @FunctionalInterface interface Operation {String run()throws Exception;}
    private static String list(CommandSourceStack actor,int page)throws IOException {var records=ledger(actor.getServer()).list();long start=((long)page-1)*8;if(start>=records.size()&&!records.isEmpty())throw new IOException("No such custody page");return "Custody page "+page+" · "+records.size()+" retained records: "+records.stream().skip(start).limit(8).map(b->b.id()+" owner="+b.owner()+" "+b.state()+" · "+b.reason()).collect(java.util.stream.Collectors.joining(" | "));}
    private static int command(CommandSourceStack actor,Operation operation){try{String message=operation.run();actor.sendSuccess(()->Component.literal(message),false);return 1;}catch(Exception bad){actor.sendFailure(Component.literal("Custody: "+reason(bad)));return 0;}}
}
