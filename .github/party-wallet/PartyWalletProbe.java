package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Destructive synthetic fixtures only. Never compiled into a production JAR. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class PartyWalletProbe {
    private static ServerPlayer admin, player;
    private static Path world;
    private static int phase;
    private static String kind;
    private static boolean started, waiting, finished;
    private static UUID owner, transaction;
    private static final Map<String,Object> report = new LinkedHashMap<>();
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    private static int command(String text)throws Exception{return admin.server.getCommands().getDispatcher().execute(text,admin.server.createCommandSourceStack());}
    private static Object call(String type,Object target,String method,Class<?>[] signature,Object... args)throws Exception {
        return Class.forName(type).getMethod(method,signature).invoke(target,args);
    }
    private static Object walletHandler()throws Exception{return call("io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler",null,"get",new Class<?>[]{LivingEntity.class},player);}
    private static ItemStack wallet()throws Exception{return (ItemStack)call("io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler",walletHandler(),"getWallet",new Class<?>[0]);}
    private static void wallet(ItemStack stack)throws Exception{call("io.github.lightman314.lightmanscurrency.common.attachments.WalletHandler",walletHandler(),"setWallet",new Class<?>[]{ItemStack.class},stack);}
    private static CompoundTag snapshot()throws Exception {
        CompoundTag result=new CompoundTag();result.putUUID("owner",player.getUUID());result.putString("kind",kind);
        if(kind.equals("party"))result.put("value",Cobblemon.INSTANCE.getStorage().getParty(player).saveToNBT(new CompoundTag(),player.registryAccess()));
        else result.put("value",player.saveWithoutId(new CompoundTag()).getCompound("neoforge:attachments").getCompound("lightmanscurrency:wallet").copy());
        return result;
    }
    private static void save()throws Exception{admin.server.saveEverything(true,true,true);net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();}
    private static void expected(CompoundTag tag)throws Exception{Files.write(world.resolve("party-wallet-expected.nbt"),OriginCustodyPort.encode(tag));}
    private static void unchanged(String where)throws Exception{
        CompoundTag expected=OriginCustodyPort.decode(Files.readAllBytes(world.resolve("party-wallet-expected.nbt")));
        CompoundTag actual=snapshot();
        if(!actual.equals(expected)){
            Files.writeString(world.resolve("party-wallet-expected.snbt"),expected.toString());
            Files.writeString(world.resolve("party-wallet-actual.snbt"),actual.toString());
            throw new IllegalStateException("Exact resource state changed at "+where+"; snapshots retained for review, not normalized away");
        }
        report.put(where,true);
    }
    private static void seedResource()throws Exception {
        if(kind.equals("party")){
            var party=Cobblemon.INSTANCE.getStorage().getParty(player);
            require(party.iterator().hasNext()==false,"Synthetic player party is not empty");
            for(String spec:List.of("eevee level=7 shiny=yes","pikachu level=11")){
                Pokemon pokemon=PokemonProperties.Companion.parse(spec," ","=").create(player);
                require(party.add(pokemon),"Could not seed native synthetic party");
            }
            require(party.saveToNBT(new CompoundTag(),player.registryAccess()).toString().contains("eevee"),"Native species absent");
        }else{
            require(wallet().isEmpty(),"Synthetic player already has a wallet");
            Item item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:wallet_leather"));
            require(item.getClass().getName().equals("io.github.lightman314.lightmanscurrency.common.items.WalletItem"),"Pinned wallet provider absent");
            ItemStack stack=new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME,Component.literal("Synthetic custody wallet"));
            CompoundTag marker=new CompoundTag();marker.putString("probe",UUID.randomUUID().toString());stack.set(DataComponents.CUSTOM_DATA,CustomData.of(marker));
            Object wrapper=call(item.getClass().getName(),null,"getDataWrapper",new Class<?>[]{ItemStack.class},stack);
            Container coins=(Container)call("io.github.lightman314.lightmanscurrency.common.items.data.WalletDataWrapper",wrapper,"getContents",new Class<?>[0]);
            ItemStack gold=new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("lightmanscurrency:coin_gold")),11);
            gold.set(DataComponents.CUSTOM_NAME,Component.literal("Eleven marked coins"));coins.setItem(0,gold);
            call("io.github.lightman314.lightmanscurrency.common.items.data.WalletDataWrapper",wrapper,"setContents",new Class<?>[]{Container.class,LivingEntity.class},coins,player);
            wallet(stack);
            require(!wallet().isEmpty(),"Wallet API did not equip fixture");
            require(ItemStack.matches(wallet(),ItemStack.parseOptional(player.registryAccess(),(CompoundTag)wallet().save(player.registryAccess()))),"Native wallet serialization roundtrip failed");
            report.put("detached_wallet_codec_roundtrip",true);
        }
    }
    private static void init()throws Exception {
        admin.server.getPlayerList().op(admin.getGameProfile());admin.setInvulnerable(true);player.setInvulnerable(true);
        IntegratedJailSmoke.admin=admin;var min=IntegratedJailSmoke.site();
        admin.teleportTo(admin.serverLevel(),min.getX()+3.5,min.getY(),min.getZ()-7.5,0,0);
        require(admin.server.getCommands().getDispatcher().execute("jailbuild preview",admin.createCommandSourceStack())>0 && admin.server.getCommands().getDispatcher().execute("jailbuild place",admin.createCommandSourceStack())>0,"Jail placement failed");
        player.teleportTo(player.serverLevel(),min.getX()+9.5,min.getY()+1,min.getZ()+5.5,0,0);
        CertifiedJailSmoke.admin=admin;CertifiedJailSmoke.target=player;CertifiedJailSmoke.world=world;CertifiedJailSmoke.seed();
        require(command("jailcustody init")==1 && command("jailintegrated init")==1,"Custody init failed");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(),"Default admission unexpectedly enabled");
        require(command("jailintegrated check Visitor")==1,"Positive preflight did not pass before resource addition");
        require(command("jailintegrated mode supported")==1,"Could not opt in to existing supported contract");
        player.setInvulnerable(false);
        seedResource();save();expected(snapshot());
        blockedAdmission();
    }
    private static void blockedAdmission()throws Exception {
        int count=JailCustodyService.ledger(admin.server).list().size();
        require(count==0,"Unexpected pre-admission transaction");
        byte[] inventory=new JailEquipmentPort(player,UUID.randomUUID(),null,true).current().inventory();
        require(command("jailintegrated check Visitor")==0,"Unsupported preflight accepted");
        require(command("jail Visitor")==0 && command("jail Visitor")==0,"Unsupported resource admitted");
        require(command("unjail Visitor")==0,"Rejected admission created releasable custody");
        require(count==JailCustodyService.ledger(admin.server).list().size() && JailData.get(admin.server).assignment(owner)==null,"Failed admission created custody");
        require(Arrays.equals(inventory,new JailEquipmentPort(player,UUID.randomUUID(),null,true).current().inventory()),"Admission refusal changed ordinary inventory");
        unchanged("failed_admission_preserved_resource");report.put("new_admissions_blocked",true);
    }
    private static void introduceDuringCustody()throws Exception {
        // Remove only freshly-created synthetic fixtures, then reintroduce the exact objects.
        // This is an external-change fault probe, NOT a production escrow implementation.
        List<Pokemon> fixtures=new ArrayList<>();ItemStack stored=ItemStack.EMPTY;
        if(kind.equals("party")){
            var party=Cobblemon.INSTANCE.getStorage().getParty(player);party.forEach(fixtures::add);
            require(fixtures.size()==2,"Fixture party changed before controlled fault injection");
            for(Pokemon p:fixtures)require(party.remove(p),"Fixture staging failed");
        }else{stored=wallet().copy();wallet(ItemStack.EMPTY);}
        save();
        require(command("jail Visitor")==1,"Supported empty-resource control admission failed");
        transaction=JailCustodyService.ledger(admin.server).list().getLast().id();
        Files.writeString(world.resolve("party-wallet-transaction"),transaction.toString());
        if(kind.equals("party")){var party=Cobblemon.INSTANCE.getStorage().getParty(player);for(Pokemon p:fixtures)require(party.add(p),"Controlled external party reintroduction failed");}
        else wallet(stored);
        unchanged("external_resource_reintroduction_exact");save();
        deathBlocked();
    }
    private static void deathBlocked()throws Exception {
        require(JailCustodyService.quarantined(player) && JailData.get(admin.server).assignment(owner)!=null,"Quarantine missing");
        player.setInvulnerable(false);int deaths=player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
        float health=player.getHealth();player.hurt(player.damageSources().generic(),1000F);
        require(player.isAlive() && player.getHealth()==health,"External damage caused death or loss");
        RetainedXpSmoke.admin=admin;RetainedXpSmoke.target=player;RetainedXpSmoke.cross();
        require(player.isAlive() && player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==deaths,"Uncertified resource was subjected to escape death");
        require(player.serverLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(128)).isEmpty(),"Resource or custody items dropped");
        unchanged("unsafe_death_preserved_resource");report.put("escape_death_blocked",true);report.put("outside_damage_blocked",true);
    }
    private static void disconnect()throws Exception{save();waiting=true;player.connection.disconnect(Component.literal("Synthetic resource persistence probe"));}
    private static void finish()throws Exception{
        report.put("commit",System.getenv("GITHUB_SHA"));report.put("production_baseline","4e3667a149d4e530bddb5326ee739ad386fe6a47");report.put("phase",phase);report.put("resource",kind);
        report.put("claim","refusal and preservation only; no nonempty-party/occupied-wallet admission certification");
        Path out=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/resource-"+kind+"-phase"+phase+".json");
        Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.writeString(Path.of("/tmp/uq-party-wallet-result"),"passed: "+kind+" phase "+phase+"; "+report+"\n");finished=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.partyWalletProbe")||finished)return;
        try{
            if(waiting){if(event.getServer().getPlayerList().getPlayer(owner)!=null)return;waiting=false;report.put("real_disconnect_observed",true);finish();return;}
            if(started)return;
            admin=event.getServer().getPlayerList().getPlayerByName("Dev");player=event.getServer().getPlayerList().getPlayerByName("Visitor");
            if(admin==null||player==null||event.getServer().getTickCount()<120||player.tickCount<100)return;
            started=true;owner=player.getUUID();world=event.getServer().getWorldPath(LevelResource.ROOT);kind=System.getProperty("uq.partyWalletKind");phase=Integer.getInteger("uq.partyWalletPhase",0);
            require("true".equals(System.getenv("GITHUB_ACTIONS")) && "github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT")) && world.toRealPath().getFileName().toString().equals("jail_integrated_smoke"),"Not a dedicated synthetic runner/world");
            require(Set.of("party","wallet").contains(kind),"Unknown resource");
            if(phase==1){init();disconnect();}
            else if(phase==2){unchanged("restart_after_refusal_preserved_resource");blockedAdmission();introduceDuringCustody();disconnect();}
            else if(phase==3){
                unchanged("restart_in_quarantine_preserved_resource");transaction=UUID.fromString(Files.readString(world.resolve("party-wallet-transaction")));deathBlocked();
                int release=command("unjail Visitor");report.put("external_change_release_result",release);unchanged("release_attempt_preserved_resource");
                if(release==0){require(JailCustodyService.quarantined(player),"Failed release removed quarantine");report.put("release_remains_blocked",true);}
                else{
                    require(release==1 && JailData.get(admin.server).assignment(owner)==null && !JailCustodyService.quarantined(player),"Inconsistent successful release");
                    byte[] original=Files.readAllBytes(world.resolve("expected-equipment.nbt"));
                    require(Arrays.equals(original,new JailEquipmentPort(player,transaction,null,true).current().inventory()),"Successful release changed baseline equipment");
                    require(command("unjail Visitor")==0,"Repeated release succeeded");unchanged("repeat_release_preserved_resource");
                    report.put("release_of_existing_custody_succeeded_without_resource_transfer",true);
                }
                save();finish();
            }else throw new IllegalStateException("Unknown phase");
        }catch(Throwable bad){finished=true;waiting=false;bad.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-party-wallet-result"),"failed: "+bad+"\n");}catch(Exception ignored){}}
    }
}
