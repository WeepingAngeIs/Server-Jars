package dev.unyielding.bundlesmoke;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Synthetic fault probes only. No production adapter or admission override. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class ContinuationProbe {
    static ServerPlayer admin, player;
    static Path world;
    static String kind;
    static int phase, ready;
    static UUID owner, transaction;
    static boolean started, waiting, finished;
    static final Map<String,Object> report = new LinkedHashMap<>();
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static boolean party(){return !kind.equals("wallet");}
    static boolean wallet(){return !kind.equals("party");}
    static void bind(String resource){
        PartyWalletProbe.admin=admin;PartyWalletProbe.player=player;PartyWalletProbe.world=world;
        PartyWalletProbe.owner=owner;PartyWalletProbe.kind=resource;
    }
    static int command(String text)throws Exception{return PartyWalletProbe.command(text);}
    static CompoundTag snapshot()throws Exception {
        CompoundTag out=new CompoundTag();out.putUUID("owner",owner);out.putString("kind",kind);
        if(party()){bind("party");out.put("party",PartyWalletProbe.snapshot());}
        if(wallet()){bind("wallet");out.put("wallet",PartyWalletProbe.snapshot());}
        return out;
    }
    static void unchanged(String stage)throws Exception {
        CompoundTag expected=OriginCustodyPort.decode(Files.readAllBytes(world.resolve("continuation-expected.nbt")));
        CompoundTag actual=snapshot();
        if(!actual.equals(expected)){
            Files.writeString(world.resolve("continuation-expected.snbt"),expected.toString());
            Files.writeString(world.resolve("continuation-actual.snbt"),actual.toString());
            throw new IllegalStateException("Resource mutation at "+stage+"; no normalization or compensating replay attempted");
        }
        report.put(stage,true);
    }
    static byte[] equipment()throws Exception{return new JailEquipmentPort(player,transaction==null?UUID.randomUUID():transaction,null,true).current().inventory();}
    static void refused()throws Exception {
        int count=JailCustodyService.ledger(admin.server).list().size();require(count==0,"Unexpected initial ledger");
        byte[] before=equipment();
        require(command("jailintegrated check Visitor")==0,"Unsupported preflight passed");
        require(command("jail Visitor")==0&&command("jail Visitor")==0,"Unsupported admission succeeded");
        require(command("unjail Visitor")==0,"Failed admission became releasable");
        require(JailCustodyService.ledger(admin.server).list().size()==count&&JailData.get(admin.server).assignment(owner)==null,"Refusal created custody");
        require(Arrays.equals(before,equipment()),"Refusal mutated ordinary equipment");
        unchanged("refused_admission_resource_equal");report.put("nonempty_admission_blocked",true);
    }
    static void init()throws Exception {
        bind(party()?"party":"wallet");
        // Reuse the completed original positive control and refusal setup.
        PartyWalletProbe.init();
        if(kind.equals("both")){bind("wallet");PartyWalletProbe.seedResource();}
        PartyWalletProbe.save();
        Files.write(world.resolve("continuation-expected.nbt"),OriginCustodyPort.encode(snapshot()));
        refused();report.put("supported_mode_opted_in_in_disposable_world",true);
    }
    static void introduce()throws Exception {
        unchanged("before_controlled_external_change");
        List<Pokemon> pokemon=new ArrayList<>();ItemStack stored=ItemStack.EMPTY;
        if(party()){
            var store=Cobblemon.INSTANCE.getStorage().getParty(player);store.forEach(pokemon::add);
            require(pokemon.size()==2,"Unexpected synthetic party count");
            for(Pokemon p:pokemon)require(store.remove(p),"Synthetic staging failed");
        }
        if(wallet()){bind("wallet");stored=PartyWalletProbe.wallet().copy();require(!stored.isEmpty(),"Wallet fixture absent");PartyWalletProbe.wallet(ItemStack.EMPTY);}
        PartyWalletProbe.save();
        require(command("jail Visitor")==1,"Supported empty-resource positive admission failed");
        transaction=JailCustodyService.ledger(admin.server).list().getLast().id();
        Files.writeString(world.resolve("continuation-transaction"),transaction.toString());
        if(party())for(Pokemon p:pokemon)require(Cobblemon.INSTANCE.getStorage().getParty(player).add(p),"Synthetic reintroduction failed");
        if(wallet()){bind("wallet");PartyWalletProbe.wallet(stored);}
        unchanged("external_addition_exact");PartyWalletProbe.save();
        report.put("supported_empty_resource_control_admitted",true);
    }
    static void deathBlocked()throws Exception {
        require(JailCustodyService.quarantined(player)&&JailAdmissions.protectedCustody(player),"Custody protection absent");
        int deaths=player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
        player.setInvulnerable(false);float health=player.getHealth();player.hurt(player.damageSources().generic(),1000F);
        require(player.isAlive()&&player.getHealth()==health,"Outside lethal damage was not blocked");
        RetainedXpSmoke.admin=admin;RetainedXpSmoke.target=player;RetainedXpSmoke.cross();
        require(player.isAlive()&&player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==deaths,"Unsafe escape death occurred");
        require(player.serverLevel().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(128)).isEmpty(),"Protected resource dropped");
        unchanged("unsafe_death_resource_equal");report.put("outside_death_blocked",true);report.put("escape_death_blocked",true);
    }
    static void release()throws Exception {
        byte[] before=equipment();int records=JailCustodyService.ledger(admin.server).list().size();
        int result=command("unjail Visitor");report.put("release_result",result);
        unchanged("release_resource_equal");
        if(kind.equals("party")){
            require(result==1&&!JailCustodyService.quarantined(player)&&JailData.get(admin.server).assignment(owner)==null,"Existing supported custody did not release");
            require(Arrays.equals(Files.readAllBytes(world.resolve("expected-equipment.nbt")),equipment()),"Equipment not restored exactly");
            require(command("unjail Visitor")==0,"Duplicate release succeeded");report.put("equipment_restored_exact",true);
        }else{
            require(result==0&&JailCustodyService.quarantined(player),"Ambiguous wallet outcome was accepted");
            // A failed transaction can advance to a safe journaled hold. Never require
            // all intermediate vanilla fields to be identical, or replay a compensation.
            require(JailCustodyService.ledger(admin.server).list().size()==records,"Release minted a second transaction");
            report.put("release_remains_held",true);
            report.put("equipment_equal_across_attempt",Arrays.equals(before,equipment()));
        }
    }
    static void verifyAfterReleaseRestart()throws Exception {
        unchanged("post_release_restart_resource_equal");
        byte[] before=equipment();int records=JailCustodyService.ledger(admin.server).list().size();
        if(kind.equals("party")){
            require(!JailCustodyService.quarantined(player)&&JailData.get(admin.server).assignment(owner)==null,"Released custody returned on restart");
            require(Arrays.equals(Files.readAllBytes(world.resolve("expected-equipment.nbt")),before),"Restored equipment not durable");
            require(command("unjail Visitor")==0,"Repeat release granted after restart");
            report.put("released_state_and_equipment_survived_restart",true);
        }else{
            require(JailCustodyService.quarantined(player),"Wallet hold vanished on restart");
            require(command("unjail Visitor")==0&&command("unjail Visitor")==0,"Held release replay succeeded");
            require(JailCustodyService.quarantined(player),"Repeated refusal removed hold");
            deathBlocked();report.put("wallet_hold_survived_restart_and_retries",true);
        }
        require(records==JailCustodyService.ledger(admin.server).list().size(),"Retry changed transaction count");
        require(Arrays.equals(before,equipment()),"Retry mutated saved equipment");
        unchanged("repeat_release_resource_equal");report.put("no_duplicate_transaction_or_grant",true);
    }
    static void disconnect()throws Exception{PartyWalletProbe.save();waiting=true;player.connection.disconnect(Component.literal("Synthetic continuation checkpoint"));}
    static void finish()throws Exception{
        report.put("commit",System.getenv("GITHUB_SHA"));report.put("kind",kind);report.put("phase",phase);
        report.put("production_baseline","4e3667a149d4e530bddb5326ee739ad386fe6a47");
        report.put("new_support_enabled",false);
        report.put("scope","Preservation and refusal fault probe, NOT occupied resource custody certification");
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/continuation-"+kind+"-phase"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.writeString(Path.of("/tmp/uq-resource-continuation-result"),"passed: "+kind+" phase "+phase+"; "+report+"\n");finished=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.resourceContinuation")||finished)return;
        try{
            if(waiting){if(event.getServer().getPlayerList().getPlayer(owner)!=null)return;waiting=false;report.put("real_disconnect_observed",true);finish();return;}
            if(started)return;
            admin=event.getServer().getPlayerList().getPlayerByName("Dev");player=event.getServer().getPlayerList().getPlayerByName("Visitor");
            if(admin==null||player==null||event.getServer().getTickCount()<120||++ready<100)return;
            started=true;owner=player.getUUID();world=event.getServer().getWorldPath(LevelResource.ROOT);
            kind=System.getProperty("uq.continuationKind");phase=Integer.getInteger("uq.continuationPhase",0);
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))&&world.toRealPath().getFileName().toString().equals("jail_integrated_smoke"),"Not a hosted synthetic world");
            require(Set.of("party","wallet","both").contains(kind),"Unknown fixture kind");bind(party()?"party":"wallet");
            if(phase==1){init();disconnect();}
            else if(phase==2){unchanged("restart_after_refusal_resource_equal");refused();introduce();deathBlocked();disconnect();}
            else if(phase==3){transaction=UUID.fromString(Files.readString(world.resolve("continuation-transaction")));unchanged("restart_in_custody_resource_equal");deathBlocked();release();disconnect();}
            else if(phase==4){transaction=UUID.fromString(Files.readString(world.resolve("continuation-transaction")));verifyAfterReleaseRestart();PartyWalletProbe.save();finish();}
            else throw new IllegalStateException("Invalid phase");
        }catch(Throwable bad){finished=true;waiting=false;bad.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-resource-continuation-result"),"failed: "+bad+"\n");}catch(Exception ignored){}}
    }
}
