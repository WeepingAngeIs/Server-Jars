package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.*;

/** Synthetic fixture around unchanged JARs. It seeds a party once, then leaves it to Cobblemon. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class PlayerOnlySmoke {
    static boolean done,started;static int phase,stage,deadline,firstDeaths;static ServerPlayer corpse;
    static final Map<String,Object> proof=new LinkedHashMap<>();
    static void require(boolean b,String why){if(!b)throw new IllegalStateException(why);}
    static ServerPlayer p(){return CertifiedJailSmoke.target;}
    static ServerPlayer admin(){return CertifiedJailSmoke.admin;}
    static int cmd(String text)throws Exception{return CertifiedJailSmoke.command(text);}
    static int deaths(){return p().getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));}
    static void flag(String key){proof.put(key,true);}
    static void save()throws Exception{CertifiedJailSmoke.saved();}
    static Path file(String name){return CertifiedJailSmoke.world.resolve(name);}
    static void held()throws Exception{CertifiedJailSmoke.held();PackedWalletProof.held(p(),CertifiedJailSmoke.last);}
    static void returned()throws Exception{CertifiedJailSmoke.returned();PackedWalletProof.returned(p());}
    static void restrictions()throws Exception{
        int[] calls={0};admin().server.getCommands().getDispatcher().register(Commands.literal("uqplayeronly_forbidden").executes(c->++calls[0]));
        admin().server.getCommands().performPrefixedCommand(p().createCommandSourceStack(),"uqplayeronly_forbidden");require(calls[0]==0,"Jailed command executed");
        admin().server.getCommands().performPrefixedCommand(admin().server.createCommandSourceStack(),"uqplayeronly_forbidden");require(calls[0]==1,"Console control failed");
        var j=JailData.get(p().server).assignment(p().getUUID()).jail();
        admin().server.getCommands().performPrefixedCommand(admin().server.createCommandSourceStack(),"tp Visitor "+(j.x()+50)+" "+j.y()+" "+j.z());
        require(p().isAlive()&&p().distanceToSqr(j.x(),j.y(),j.z())<0.0001,"Teleport command bypassed jail");
        float hp=p().getHealth();p().invulnerableTime=0;p().hurt(p().damageSources().generic(),2);require(p().getHealth()==hp,"Outside damage protection changed");
        flag("commands_teleport_and_external_damage_still_blocked");
    }
    static void setup()throws Exception{
        admin().server.getPlayerList().op(admin().getGameProfile());admin().setInvulnerable(true);p().setInvulnerable(true);
        IntegratedJailSmoke.admin=admin();var min=IntegratedJailSmoke.site();admin().teleportTo(admin().serverLevel(),min.getX()+3.5,min.getY(),min.getZ()-7.5,0,0);
        require(admin().server.getCommands().getDispatcher().execute("jailbuild preview",admin().createCommandSourceStack())>0&&admin().server.getCommands().getDispatcher().execute("jailbuild place",admin().createCommandSourceStack())>0,"Jail placement failed");
        p().teleportTo(p().serverLevel(),min.getX()+9.5,min.getY()+1,min.getZ()+5.5,0,0);CertifiedJailSmoke.seed();
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get()&&!JailConfig.JAIL_PACKED_WALLETS.get(),"Defaults changed");flag("fresh_admission_and_wallet_defaults_off");
        PackedWalletProof.seed(p());CertifiedJailSmoke.flush();save();CertifiedJailSmoke.original=CertifiedJailSmoke.items(UUID.randomUUID(),null).current().inventory();Files.write(file("expected-equipment.nbt"),CertifiedJailSmoke.original);
        // Fixture setup only. No Pokemon custody/preservation or callbacks are installed or tested.
        var party=com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(p());require(party.isEmpty(),"Expected fresh synthetic party");
        require(party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse("eevee level=7"," ","=").create(p())),"Could not seed nonempty synthetic party");
        flag("party_nonempty_before_admission");
        require(cmd("jailcustody init")==1&&cmd("jailintegrated init")==1,"History initialization failed");
        require(cmd("jail Visitor")==0&&Arrays.equals(CertifiedJailSmoke.original,CertifiedJailSmoke.items(UUID.randomUUID(),null).current().inventory()),"Disabled admissions mutated inventory");
        require(cmd("jailintegrated mode supported")==1,"Cannot explicitly enable reviewed mode");
        JailConfig.JAIL_PACKED_WALLETS.set(false);require(cmd("jail Visitor")==0,"Wallet opt-in was bypassed");JailConfig.JAIL_PACKED_WALLETS.set(true);JailConfig.JAIL_PACKED_WALLETS.save();flag("wallet_opt_in_still_required");
        require(cmd("jailintegrated check Visitor")==1&&cmd("jail Visitor")==1,"Nonempty party prevented player-only jailing");
        CertifiedJailSmoke.last=CertifiedJailSmoke.record().id();held();p().setInvulnerable(false);restrictions();flag("nonempty_party_player_admitted_with_wallet_and_equipment");
        require(!p().serverLevel().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN),"Global immediate-respawn gamerule was changed");
        firstDeaths=deaths();corpse=p();CertifiedJailSmoke.cross();require(!p().isAlive()&&deaths()==firstDeaths+1,"Accepted escape did not kill player exactly once");
        flag("boundary_movement_caused_actual_player_death");stage=1;deadline=admin().server.getTickCount()+240;
    }
    static void afterAutomatic()throws Exception{
        if(!p().isAlive())return;
        require(p()!=corpse&&p().connection.player==p()&&PlayerOnlyJail.automaticCalls==1&&PlayerOnlyJail.automaticCompletions==1,"No completed server-initiated native respawn");
        require(PlayerOnlyJail.clientRespawnRequests==0&&deaths()==firstDeaths+1,"Respawn required a client action or repeated death");
        var j=JailData.get(p().server).assignment(p().getUUID()).jail();require(p().distanceToSqr(j.x(),j.y(),j.z())<0.0001,"Automatic respawn outside jail");held();
        Path report=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/player-only-client-Visitor-1.json");
        if(!Files.exists(report)||!com.google.gson.JsonParser.parseString(Files.readString(report)).getAsJsonObject().get("alive_after_death").getAsBoolean())return;
        flag("real_client_observed_automatic_alive_replacement");flag("automatic_respawn_inside_without_client_request");
        require(cmd("unjail Visitor")==1,"Release after autorespawn failed");returned();flag("exact_inventory_equipment_accessory_wallet_and_origin_return");
        var a=admin().server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("unyieldingquests","jail_escape"));require(a!=null&&p().getAdvancements().getOrStartProgress(a).isDone()&&!p().getAdvancements().award(a,"escape"),"Escape achievement duplicated");flag("escape_achievement_remains_one_time");
        require(cmd("jail Visitor")==1,"Repeated player admission failed");CertifiedJailSmoke.last=CertifiedJailSmoke.record().id();held();p().setInvulnerable(false);
        var jail=JailData.get(p().server).assignment(p().getUUID()).jail();p().setPos(jail.x()+8,jail.y(),jail.z());p().connection.resetPosition();
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p()));require(!p().isAlive()&&PlayerOnlyJail.fallbackDeaths==1&&deaths()==firstDeaths+2,"Outside tick bypass was not killed");flag("outside_position_tick_also_kills_player");
        Files.writeString(file("active-player-only"),CertifiedJailSmoke.last.toString());Files.writeString(file("expected-deaths"),Integer.toString(deaths()));
        require(cmd("jailintegrated mode disabled")==1,"Cannot disable new admissions");save();p().connection.disconnect(Component.literal("Disposable dead player reconnect test"));flag("disconnected_after_durable_death_before_auto_tick");pass();
    }
    static void resumed()throws Exception{
        require(p().isAlive()&&PlayerOnlyJail.automaticCalls==1&&PlayerOnlyJail.automaticCompletions==1&&PlayerOnlyJail.clientRespawnRequests==0,"Saved dead player did not automatically respawn on actual reconnect");
        require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get()&&deaths()==Integer.parseInt(Files.readString(file("expected-deaths"))),"Resume replayed death or depended on new-admission enable");held();flag("actual_restart_and_reconnect_auto_respawn_without_button");flag("recovery_works_with_new_admissions_disabled");restrictions();
        p().getInventory().setItem(32,new ItemStack(Items.STONE));CertifiedJailSmoke.flush();save();require(cmd("unjail Visitor")==0&&p().getInventory().getItem(32).is(Items.STONE),"Occupied wallet return destination overwritten");
        p().getInventory().setItem(32,ItemStack.EMPTY);CertifiedJailSmoke.flush();save();require(cmd("unjail Visitor")==1,"Safe release after restored destination failed");returned();flag("occupied_return_refused_then_exact_release");
        require(cmd("jailintegrated mode supported")==1&&cmd("jail Visitor")==1,"Cancellation fixture admission failed");CertifiedJailSmoke.last=CertifiedJailSmoke.record().id();held();p().setInvulnerable(false);
        var assignment=JailData.get(p().server).assignment(p().getUUID());var j=assignment.jail();BlockPos corner=BlockPos.containing(j.x()-3.5,j.y()-1,j.z()-3.5);var block=p().serverLevel().getBlockState(corner);
        p().serverLevel().setBlock(corner,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);CertifiedJailSmoke.cross();require(p().isAlive(),"Unsafe prison caused death");p().serverLevel().setBlock(corner,block,3);p().setPos(j.x(),j.y(),j.z());p().connection.resetPosition();flag("unsafe_destination_does_not_kill_or_respawn");
        final int[] cancellations={0};NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.living.LivingDeathEvent.class,event->{if(event.getEntity()==p()){cancellations[0]++;p().setHealth(20);event.setCanceled(true);}});
        CertifiedJailSmoke.cross();require(p().isAlive()&&cancellations[0]==1,"Death cancellation bypassed");p().invulnerableTime=0;CertifiedJailSmoke.cross();
        require(cancellations[0]==1&&p().isAlive()&&cmd("unjail Visitor")==0&&cmd("jailcustody recover "+CertifiedJailSmoke.last)==0,"Uncertain death was replayed or released");flag("cancelled_death_retains_quarantine_without_replay");
        Files.writeString(file("active-player-only"),CertifiedJailSmoke.last.toString());Files.writeString(file("expected-deaths"),Integer.toString(deaths()));require(cmd("jailintegrated mode disabled")==1,"Disable failed");save();pass();
    }
    static void cancelledRestart()throws Exception{
        require(p().isAlive()&&deaths()==Integer.parseInt(Files.readString(file("expected-deaths")))&&PlayerOnlyJail.automaticCalls==0&&PlayerOnlyJail.clientRespawnRequests==0,"Ambiguous death auto-replayed on restart");held();
        require(cmd("unjail Visitor")==0&&cmd("jailcustody recover "+CertifiedJailSmoke.last)==0,"Ambiguous history lost quarantine");flag("cancelled_attempt_quarantined_across_actual_restart");pass();
    }
    static void pass()throws Exception{
        require(PlayerOnlyJail.clientRespawnRequests==0,"Unexpected client Respawn request");
        proof.put("commit",System.getenv("GITHUB_SHA"));proof.put("phase",phase);proof.put("automatic_calls",PlayerOnlyJail.automaticCalls);proof.put("automatic_completions",PlayerOnlyJail.automaticCompletions);proof.put("client_respawn_requests",PlayerOnlyJail.clientRespawnRequests);proof.put("deaths",deaths());proof.put("jail_sha256",System.getenv("UQ_JAIL_SHA256"));proof.put("companion_sha256",System.getenv("UQ_BUNDLE_SHA256"));proof.put("production_changed",false);proof.put("pokemon_custody_installed",false);
        Files.writeString(Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/player-only-phase"+phase+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        Files.writeString(Path.of("/tmp/uq-player-only-result"),"passed: player-only phase "+phase+"\n");done=true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post event){
        if(!PlayerOnlyJail.enabled()||done)return;
        try{
            var a=event.getServer().getPlayerList().getPlayerByName("Dev");var target=event.getServer().getPlayerList().getPlayerByName("Visitor");if(a==null||target==null)return;
            CertifiedJailSmoke.admin=a;CertifiedJailSmoke.target=target;
            if(!started){if(event.getServer().getTickCount()<120||target.tickCount<100)return;started=true;phase=Integer.getInteger("uq.playerOnlyPhase");CertifiedJailSmoke.phase=phase;CertifiedJailSmoke.world=event.getServer().getWorldPath(LevelResource.ROOT);require(CertifiedJailSmoke.world.toRealPath().getFileName().toString().equals("jail_certified_smoke"),"Wrong disposable world");
                if(phase==1){setup();return;}
                CertifiedJailSmoke.original=Files.readAllBytes(file("expected-equipment.nbt"));CertifiedJailSmoke.last=UUID.fromString(Files.readString(file("active-player-only")));
                if(phase==2)resumed();else if(phase==3)cancelledRestart();else throw new IOException("Unknown phase");return;
            }
            if(stage==1){if(event.getServer().getTickCount()>deadline)throw new IOException("Automatic player respawn/client observation timed out");afterAutomatic();}
        }catch(Throwable failure){done=true;failure.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-player-only-result"),"failed: "+failure+"\n");}catch(IOException ignored){}}
    }
}
