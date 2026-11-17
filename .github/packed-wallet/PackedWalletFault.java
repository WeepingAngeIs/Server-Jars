package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hard halt is restricted to this synthetic GitHub-hosted process/world. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class PackedWalletFault {
    static boolean done,waiting;static int phase;static UUID owner,id;
    static Map<String,Object> evidence=new LinkedHashMap<>();
    static void require(boolean x,String why){PackedWalletProof.require(x,why);}
    static int command(String s)throws Exception{return CertifiedJailSmoke.command(s);}
    static ServerPlayer player(){return CertifiedJailSmoke.target;}
    static Path world(){return CertifiedJailSmoke.world;}
    static void remember()throws Exception{id=CertifiedJailSmoke.record().id();CertifiedJailSmoke.last=id;Files.writeString(world().resolve("wallet-fault-id"),id.toString());}
    static void setup()throws Exception{
        var admin=CertifiedJailSmoke.admin;var p=player();admin.server.getPlayerList().op(admin.getGameProfile());admin.setInvulnerable(true);p.setInvulnerable(true);
        IntegratedJailSmoke.admin=admin;var min=IntegratedJailSmoke.site();admin.teleportTo(admin.serverLevel(),min.getX()+3.5,min.getY(),min.getZ()-7.5,0,0);
        require(admin.server.getCommands().getDispatcher().execute("jailbuild preview",admin.createCommandSourceStack())>0&&admin.server.getCommands().getDispatcher().execute("jailbuild place",admin.createCommandSourceStack())>0,"Fixture build failed");
        p.teleportTo(p.serverLevel(),min.getX()+9.5,min.getY()+1,min.getZ()+5.5,0,0);CertifiedJailSmoke.seed();
        require(command("jailcustody init")==1&&command("jailintegrated init")==1&&command("jailintegrated mode supported")==1,"Init failed");
    }
    static void run()throws Exception{
        var p=player();
        if(phase==1){
            setup();JailIntegrated.disposableCheckpoint(p,b->{if(b==JailIntegrated.Boundary.ITEMS_DURABLE)throw new IOException("Injected saved wallet removal before origin phase");});
            require(command("jail Visitor")==0,"Saved-removal interruption missing");remember();
            require(p.getInventory().isEmpty()&&CertifiedJailSmoke.items(id,null).durable().empty()&&JailCustodyService.quarantined(p),"Saved wallet removal not held");
            require(command("jail Visitor")==0,"Interrupted admission replayed");evidence.put("saved_removal_interrupted_and_held",true);
            waiting=true;p.connection.disconnect(Component.literal("Synthetic saved-wallet restart"));return;
        }
        id=UUID.fromString(Files.readString(world().resolve("wallet-fault-id")));CertifiedJailSmoke.last=id;CertifiedJailSmoke.original=Files.readAllBytes(world().resolve("expected-equipment.nbt"));
        if(phase==2){
            require(p.getInventory().isEmpty()&&JailCustodyService.quarantined(p),"Saved custody lost on restart");
            JailConfig.JAIL_PACKED_WALLETS.set(false);JailConfig.JAIL_PACKED_WALLETS.save();
            require(command("jailcustody recover "+id)==1,"Verified saved removal recovery failed");CertifiedJailSmoke.held();PackedWalletProof.occupiedDestination(p);
            require(command("unjail Visitor")==1,"Recovery/release incorrectly depends on new-admission switch");CertifiedJailSmoke.returned();
            CertifiedJailSmoke.saved();evidence.put("saved_removal_recovered_and_released_with_admission_off",true);waiting=true;p.connection.disconnect(Component.literal("Synthetic completed wallet return"));return;
        }
        if(phase==3){
            CertifiedJailSmoke.returned();require(command("unjail Visitor")==0,"Completed return regranted after restart");evidence.put("completed_release_survived_restart_without_duplication",true);
            UUID predecessor=id;Files.writeString(world().resolve("wallet-fault-predecessor"),predecessor.toString());
            JailConfig.JAIL_PACKED_WALLETS.set(true);JailConfig.JAIL_PACKED_WALLETS.save();
            JailIntegrated.disposableCheckpoint(p,b->{if(b==JailIntegrated.Boundary.ITEMS_MUTATED)throw new IOException("Injected wallet removal before player save");});
            require(command("jail Visitor")==0,"Pre-save removal interruption missing");remember();
            var port=CertifiedJailSmoke.items(id,predecessor);require(port.current().empty()&&!port.durable().empty()&&Arrays.equals(port.durable().inventory(),CertifiedJailSmoke.original),"Expected actual live/disk disagreement absent");
            require(command("jailcustody recover "+id)==0&&command("unjail Visitor")==0,"Unsaved mutation automatically replayed");
            evidence.put("unsaved_removal_not_replayed",true);evidence.put("hard_halt_before_player_save",true);finish();
            // No server stop/save, no save-file editing. OS releases only this disposable JVM's lock.
            Runtime.getRuntime().halt(77);return;
        }
        if(phase==4){
            PackedWalletProof.returned(p);require(JailCustodyService.quarantined(p),"Uncertain journal lost quarantine after crash");
            UUID predecessor=UUID.fromString(Files.readString(world().resolve("wallet-fault-predecessor")));
            byte[] before=CertifiedJailSmoke.items(id,predecessor).current().inventory();
            require(command("jailcustody recover "+id)==0&&command("unjail Visitor")==0&&command("jail Visitor")==0,"Uncertain crash phase was replayed");
            require(Arrays.equals(before,CertifiedJailSmoke.items(id,predecessor).current().inventory()),"Recovery duplicated, removed or mutated original wallet");
            evidence.put("hard_restart_restored_saved_source_without_replay",true);evidence.put("uncertain_outcome_remains_quarantined",true);finish();return;
        }
        throw new IOException("Unknown synthetic fault phase");
    }
    static void finish()throws Exception{
        evidence.put("commit",System.getenv("GITHUB_SHA"));evidence.put("phase",phase);evidence.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));evidence.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));
        Path report=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/packed-wallet-fault"+phase+".json");Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        Path result=Path.of("/tmp/uq-packed-wallet-fault");Files.writeString(result,"passed: packed-wallet fault phase "+phase+" "+evidence+"\n");
        for(Path f:List.of(report,result,world().resolve("wallet-fault-id")))try(var c=FileChannel.open(f,StandardOpenOption.WRITE)){c.force(true);}
        done=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.packedWalletFault"))return;
        try{
            if(waiting){if(event.getServer().getPlayerList().getPlayer(owner)!=null)return;waiting=false;evidence.put("real_disconnect_observed",true);finish();return;}
            if(done)return;
            var admin=event.getServer().getPlayerList().getPlayerByName("Dev");var p=event.getServer().getPlayerList().getPlayerByName("Visitor");
            if(admin==null||p==null||event.getServer().getTickCount()<120||p.tickCount<100)return;
            var world=event.getServer().getWorldPath(LevelResource.ROOT);
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))&&world.toRealPath().getFileName().toString().equals("jail_certified_smoke"),"Not isolated GitHub-hosted wallet world");
            done=true;owner=p.getUUID();CertifiedJailSmoke.admin=admin;CertifiedJailSmoke.target=p;CertifiedJailSmoke.world=world;phase=Integer.getInteger("uq.walletFaultPhase",0);run();
        }catch(Throwable error){done=true;waiting=false;error.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-packed-wallet-fault"),"failed: "+error+"\n");}catch(Exception ignored){}}
    }
}
