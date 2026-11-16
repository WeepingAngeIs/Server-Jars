package dev.unyielding.bundlesmoke;

import com.google.gson.GsonBuilder;
import dev.unyielding.jail.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native lifecycle reference only. No jail admission override or production adapter. */
@EventBusSubscriber(modid="uq_bundle_smoke")
public final class NativeResourceProbe {
    static boolean started,waiting,finished;
    static UUID owner;
    static int phase;
    static String kind;
    static Path world;
    static ServerPlayer admin,player;
    static Map<String,Object> report=new LinkedHashMap<>();
    static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
    static void bind(){PartyWalletProbe.admin=admin;PartyWalletProbe.player=player;PartyWalletProbe.world=world;PartyWalletProbe.kind=kind;}
    static CompoundTag expected()throws Exception{return OriginCustodyPort.decode(Files.readAllBytes(world.resolve("party-wallet-expected.nbt")));}
    static CompoundTag position()throws Exception{return OriginCustodyPort.decode(Files.readAllBytes(world.resolve("native-resource-position.nbt")));}
    static ItemStack walletExpected()throws Exception{return ItemStack.parseOptional(player.registryAccess(),expected().getCompound("value").getCompound("Wallet"));}
    static List<ItemEntity> ground()throws Exception{
        CompoundTag pos=position();double x=pos.getDouble("x"),y=pos.getDouble("y"),z=pos.getDouble("z");
        player.serverLevel().getChunkAt(net.minecraft.core.BlockPos.containing(x,y,z));
        return player.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(x-32,y-32,z-32,x+32,y+32,z+32));
    }
    static void observe(String stage,boolean live)throws Exception{
        require(!JailAdmissions.protectedCustody(player),"Reference player unexpectedly has protected jail custody");
        if(kind.equals("party")){
            PartyWalletProbe.unchanged(stage);report.put(stage,Map.of("full_party_nbt_equal",true,"player_alive",player.isAlive()));
        }else{
            ItemStack expected=walletExpected();require(!expected.isEmpty(),"Expected occupied wallet snapshot absent");
            int equipped=ItemStack.matches(expected,PartyWalletProbe.wallet())?1:0;
            int inventory=0,dropped=0;
            for(int i=0;i<player.getInventory().getContainerSize();i++)if(ItemStack.matches(expected,player.getInventory().getItem(i)))inventory++;
            for(ItemEntity e:ground())if(ItemStack.matches(expected,e.getItem()))dropped++;
            report.put(stage,Map.of("equipped",equipped,"inventory",inventory,"ground",dropped,"dead_source",!player.isAlive()));
            // A dead source attachment can remain until native clone processing. Do not
            // label it spendable custody or restore it independently of the native handler.
            require(live ? equipped+inventory+dropped==1 : equipped+inventory+dropped>=1,"Native wallet did not conserve one redeemable instance after respawn");
        }
    }
    static void disconnect()throws Exception{PartyWalletProbe.save();waiting=true;player.connection.disconnect(Component.literal("Native reference persistence probe"));}
    static void finish()throws Exception{
        report.put("commit",System.getenv("GITHUB_SHA"));report.put("phase",phase);report.put("resource",kind);
        report.put("claim","unjailed native reference only; unsupported jail admissions remain blocked");
        Path out=Path.of(System.getenv("GITHUB_WORKSPACE"),"dist/packaged-smoke/native-"+kind+"-phase"+phase+".json");
        Files.writeString(out,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        Files.writeString(Path.of("/tmp/uq-native-resource-result"),"passed: native "+kind+" phase "+phase+"; "+report+"\n");finished=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("uq.nativeResourceProbe")||finished)return;
        try{
            if(waiting){if(event.getServer().getPlayerList().getPlayer(owner)!=null)return;report.put("real_disconnect_observed",true);waiting=false;finish();return;}
            if(started)return;
            admin=event.getServer().getPlayerList().getPlayerByName("Dev");player=event.getServer().getPlayerList().getPlayerByName("Visitor");
            if(admin==null||player==null||event.getServer().getTickCount()<120||player.tickCount<100)return;
            started=true;owner=player.getUUID();world=event.getServer().getWorldPath(LevelResource.ROOT);kind=System.getProperty("uq.partyWalletKind");phase=Integer.getInteger("uq.partyWalletPhase",0);bind();
            require("true".equals(System.getenv("GITHUB_ACTIONS"))&&"github-hosted".equals(System.getenv("RUNNER_ENVIRONMENT"))&&world.toRealPath().getFileName().toString().equals("jail_integrated_smoke"),"Not an isolated synthetic reference");
            require(Set.of("party","wallet").contains(kind),"Unknown resource");
            if(phase==1){
                require(!JailConfig.JAIL_CERTIFIED_ADMISSIONS.get(),"Reference changed default admission");
                PartyWalletProbe.command("gamerule keepInventory false");
                player.getInventory().clearContent();player.setInvulnerable(false);player.setHealth(player.getMaxHealth());
                player.serverLevel().getChunkAt(new net.minecraft.core.BlockPos(1000,5,1000));
                player.teleportTo(player.serverLevel(),1000.5,5,1000.5,0,0);admin.teleportTo(admin.serverLevel(),1200.5,5,1000.5,0,0);admin.setInvulnerable(true);
                CompoundTag pos=new CompoundTag();pos.putDouble("x",player.getX());pos.putDouble("y",player.getY());pos.putDouble("z",player.getZ());Files.write(world.resolve("native-resource-position.nbt"),OriginCustodyPort.encode(pos));
                PartyWalletProbe.seedResource();PartyWalletProbe.save();PartyWalletProbe.expected(PartyWalletProbe.snapshot());
                int deaths=player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS));
                require(!JailAdmissions.protectedCustody(player),"Cannot run native reference on jailed player");
                player.hurt(player.damageSources().generic(),1000F);
                require(!player.isAlive()&&player.getHealth()<=0&&player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.DEATHS))==deaths+1,"Native reference did not actually die");
                report.put("actual_native_death",true);observe("after_death",false);disconnect();
            }else if(phase==2){
                require(!player.isAlive(),"Dead player state was not persisted");observe("restart_while_dead",false);
                player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player=admin.server.getPlayerList().getPlayer(owner);require(player!=null&&player.isAlive()&&player.connection.player==player,"Native respawn did not rebind player");bind();
                report.put("actual_native_respawn",true);observe("after_respawn",true);disconnect();
            }else if(phase==3){
                require(player.isAlive(),"Live respawn not persisted");observe("restart_after_respawn",true);
                if(kind.equals("wallet")){
                    ItemStack expected=walletExpected();var matching=ground().stream().filter(e->ItemStack.matches(expected,e.getItem())).toList();
                    if(matching.size()==1){ItemEntity drop=matching.getFirst();player.teleportTo(player.serverLevel(),drop.getX(),drop.getY(),drop.getZ(),0,0);drop.playerTouch(player);observe("native_pickup",true);require(!drop.isAlive(),"Native wallet pickup did not remove its ground instance");report.put("wallet_needed_ground_pickup",true);}
                    else report.put("wallet_needed_ground_pickup",false);
                }
                PartyWalletProbe.save();finish();
            }else throw new IllegalStateException("Unknown native phase");
        }catch(Throwable bad){finished=true;bad.printStackTrace();try{Files.writeString(Path.of("/tmp/uq-native-resource-result"),"failed: "+bad+"\n");}catch(Exception ignored){}}
    }
}
